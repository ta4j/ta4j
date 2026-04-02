/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.aggregator.BaseBarSeriesAggregator;
import org.ta4j.core.aggregator.DurationBarAggregator;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.SqnCriterion;
import org.ta4j.core.criteria.commissions.CommissionsCriterion;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.strategies.CCICorrectionStrategy;
import ta4jexamples.strategies.GlobalExtremaStrategy;
import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.strategies.RSI2Strategy;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared validation, execution, and serialization helpers for the ta4j CLI.
 *
 * <p>
 * This type keeps the command entry point thin while reusing existing ta4j
 * execution, reporting, charting, and datasource APIs. The helpers are scoped
 * to the CLI's bounded MVP surface so command behavior stays deterministic and
 * file-oriented for both users and agents.
 * </p>
 */
final class CliSupport {

    static final List<String> DEFAULT_BACKTEST_CRITERIA = List.of("net-profit", "romad", "total-fees");
    static final List<String> DEFAULT_WALK_FORWARD_CRITERIA = List.of("gross-return");
    static final List<String> DEFAULT_SWEEP_CRITERIA = List.of("net-profit");
    static final List<String> DEFAULT_INDICATOR_TEST_CRITERIA = List.of("net-profit", "sharpe");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final Map<String, Supplier<AnalysisCriterion>> CRITERIA = createCriterionRegistry();

    private CliSupport() {
    }

    static String toJson(Object value) {
        return GSON.toJson(value);
    }

    static BarSeries loadSeries(String dataFile, String timeframeToken, String fromDateToken, String toDateToken) {
        Objects.requireNonNull(dataFile, "dataFile");
        String normalizedPath = Path.of(dataFile).toAbsolutePath().normalize().toString();
        BarSeries loadedSeries;
        String lowerCasePath = dataFile.toLowerCase(Locale.ROOT);
        if (lowerCasePath.endsWith(".csv")) {
            loadedSeries = CsvFileBarSeriesDataSource.loadCsvSeries(normalizedPath);
        } else if (lowerCasePath.endsWith(".json")) {
            loadedSeries = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(normalizedPath);
        } else {
            throw new IllegalArgumentException("Unsupported data file format for " + dataFile + ". Use .csv or .json.");
        }

        if (loadedSeries == null || loadedSeries.isEmpty()) {
            throw new IllegalArgumentException("Unable to load bar data from " + dataFile + ".");
        }

        BarSeries effectiveSeries = loadedSeries;
        if (timeframeToken != null && !timeframeToken.isBlank()) {
            Duration timeframe = parseTimeframe(timeframeToken);
            String aggregatedName = loadedSeries.getName() + "-" + normalizeToken(timeframeToken);
            effectiveSeries = new BaseBarSeriesAggregator(new DurationBarAggregator(timeframe, true))
                    .aggregate(loadedSeries, aggregatedName);
        }

        Instant fromDate = parseOptionalInstant(fromDateToken, true);
        Instant toDate = parseOptionalInstant(toDateToken, false);
        if (fromDate != null || toDate != null) {
            effectiveSeries = sliceSeries(effectiveSeries, fromDate, toDate);
        }

        if (effectiveSeries == null || effectiveSeries.isEmpty()) {
            throw new IllegalArgumentException("The selected date/timeframe filter produced an empty series.");
        }
        return effectiveSeries;
    }

    static BacktestExecutor buildExecutor(BarSeries series, String executionModelToken, String commissionToken,
            String borrowRateToken) {
        TradeExecutionModel executionModel = resolveExecutionModel(executionModelToken);
        CostModel transactionCostModel = resolveTransactionCostModel(commissionToken);
        CostModel holdingCostModel = resolveHoldingCostModel(borrowRateToken);
        return new BacktestExecutor(series, transactionCostModel, holdingCostModel, executionModel);
    }

    static Num resolveAmount(BarSeries series, String capitalToken, String stakeAmountToken) {
        String resolved = stakeAmountToken;
        if (resolved == null || resolved.isBlank()) {
            resolved = capitalToken;
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = "1";
        }
        if (capitalToken != null && stakeAmountToken != null) {
            double capital = parsePositiveDouble(capitalToken, "capital");
            double stake = parsePositiveDouble(stakeAmountToken, "stake-amount");
            if (stake > capital) {
                throw new IllegalArgumentException("--stake-amount must not exceed --capital.");
            }
        }
        return series.numFactory().numOf(resolved);
    }

    static List<CriterionSpec> resolveCriteria(List<String> requestedAliases, List<String> defaults) {
        List<String> effectiveAliases = requestedAliases == null || requestedAliases.isEmpty() ? defaults
                : requestedAliases;
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
        for (String value : effectiveAliases) {
            if (value == null) {
                continue;
            }
            for (String token : value.split(",")) {
                String normalized = normalizeToken(token);
                if (!normalized.isEmpty()) {
                    normalizedAliases.add(normalized);
                }
            }
        }
        if (normalizedAliases.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion alias is required.");
        }

        List<CriterionSpec> criteria = new ArrayList<>(normalizedAliases.size());
        for (String alias : normalizedAliases) {
            Supplier<AnalysisCriterion> supplier = CRITERIA.get(alias);
            if (supplier == null) {
                throw new IllegalArgumentException("Unknown criterion alias: " + alias + ".");
            }
            criteria.add(new CriterionSpec(alias, supplier.get()));
        }
        return List.copyOf(criteria);
    }

    static Strategy buildStrategy(String strategyAlias, String strategyJsonPath, List<String> paramOptions,
            Integer unstableBars, BarSeries series) {
        Map<String, String> params = parseKeyValueOptions(paramOptions, "--param");
        Strategy strategy;
        String normalizedAlias = strategyAlias == null ? "" : normalizeToken(strategyAlias);
        if (strategyJsonPath != null && !strategyJsonPath.isBlank()) {
            try {
                String json = Files.readString(Path.of(strategyJsonPath));
                strategy = Strategy.fromJson(series, json);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Unable to read strategy JSON from " + strategyJsonPath + ".", ex);
            }
        } else if ("sma-crossover".equals(normalizedAlias)) {
            strategy = buildSmaCrossoverStrategy(series, params);
        } else if ("rsi2".equals(normalizedAlias)) {
            strategy = RSI2Strategy.buildStrategy(series);
        } else if ("cci-correction".equals(normalizedAlias)) {
            strategy = CCICorrectionStrategy.buildStrategy(series);
        } else if ("global-extrema".equals(normalizedAlias)) {
            strategy = GlobalExtremaStrategy.buildStrategy(series);
        } else if ("moving-momentum".equals(normalizedAlias)) {
            strategy = MovingMomentumStrategy.buildStrategy(series);
        } else {
            throw new IllegalArgumentException(
                    "Unknown strategy alias. Supported values are sma-crossover, rsi2, cci-correction, global-extrema, moving-momentum.");
        }

        if (unstableBars != null) {
            strategy.setUnstableBars(unstableBars);
        }
        return strategy;
    }

    static List<Strategy> buildSweepStrategies(String strategyAlias, List<String> paramOptions,
            List<String> gridOptions, Integer unstableBars, BarSeries series) {
        String normalizedAlias = normalizeToken(strategyAlias);
        if (!"sma-crossover".equals(normalizedAlias)) {
            throw new IllegalArgumentException("The MVP sweep command currently supports --strategy sma-crossover.");
        }

        Map<String, String> fixedParams = parseKeyValueOptions(paramOptions, "--param");
        Map<String, List<String>> gridParams = parseGridOptions(gridOptions);
        if (gridParams.isEmpty()) {
            throw new IllegalArgumentException("At least one --param-grid option is required for sweep.");
        }

        List<Map<String, String>> combinations = new ArrayList<>();
        buildCartesianProduct(new ArrayList<>(gridParams.keySet()), gridParams, 0, new LinkedHashMap<>(fixedParams),
                combinations);

        List<Strategy> strategies = new ArrayList<>(combinations.size());
        for (Map<String, String> combination : combinations) {
            Strategy strategy = buildSmaCrossoverStrategy(series, combination);
            if (unstableBars != null) {
                strategy.setUnstableBars(unstableBars);
            }
            strategies.add(strategy);
        }
        return List.copyOf(strategies);
    }

    static Strategy buildIndicatorTestStrategy(String indicatorAlias, List<String> paramOptions, Integer unstableBars,
            String entryBelowToken, String entryAboveToken, String exitBelowToken, String exitAboveToken,
            BarSeries series) {
        Map<String, String> params = parseKeyValueOptions(paramOptions, "--param");
        String normalizedAlias = normalizeToken(indicatorAlias);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> indicator = buildIndicator(normalizedAlias, params, series, closePrice);

        Rule entryRule;
        Rule exitRule;
        if (entryBelowToken != null || entryAboveToken != null || exitBelowToken != null || exitAboveToken != null) {
            entryRule = buildThresholdRule(indicator, entryBelowToken, entryAboveToken, series, "entry");
            exitRule = buildThresholdRule(indicator, exitBelowToken, exitAboveToken, series, "exit");
        } else {
            entryRule = new CrossedUpIndicatorRule(closePrice, indicator);
            exitRule = new CrossedDownIndicatorRule(closePrice, indicator);
        }

        String strategyName = normalizedAlias + "-indicator-test";
        BaseStrategy strategy = new BaseStrategy(strategyName, entryRule, exitRule);
        if (unstableBars != null) {
            strategy.setUnstableBars(unstableBars);
        } else {
            strategy.setUnstableBars(resolveDefaultIndicatorUnstableBars(params));
        }
        return strategy;
    }

    static WalkForwardConfig buildWalkForwardConfig(BarSeries series, CliArguments arguments) {
        WalkForwardConfig defaultConfig = WalkForwardConfig.defaultConfig(series);
        int minTrainBars = parseOptionalInt(arguments.optional("min-train-bars").orElse(null),
                defaultConfig.minTrainBars(), "min-train-bars");
        int testBars = parseOptionalInt(arguments.optional("test-bars").orElse(null), defaultConfig.testBars(),
                "test-bars");
        int stepBars = parseOptionalInt(arguments.optional("step-bars").orElse(null), defaultConfig.stepBars(),
                "step-bars");
        int purgeBars = parseOptionalInt(arguments.optional("purge-bars").orElse(null), defaultConfig.purgeBars(),
                "purge-bars");
        int embargoBars = parseOptionalInt(arguments.optional("embargo-bars").orElse(null), defaultConfig.embargoBars(),
                "embargo-bars");
        int holdoutBars = parseOptionalInt(arguments.optional("holdout-bars").orElse(null), defaultConfig.holdoutBars(),
                "holdout-bars");
        int primaryHorizonBars = parseOptionalInt(arguments.optional("primary-horizon-bars").orElse(null),
                defaultConfig.primaryHorizonBars(), "primary-horizon-bars");
        int optimizationTopK = parseOptionalInt(arguments.optional("optimization-top-k").orElse(null),
                defaultConfig.optimizationTopK(), "optimization-top-k");
        long seed = parseOptionalLong(arguments.optional("seed").orElse(null), defaultConfig.seed(), "seed");

        return new WalkForwardConfig(minTrainBars, testBars, stepBars, purgeBars, embargoBars, holdoutBars,
                primaryHorizonBars, defaultConfig.reportingHorizons(), optimizationTopK, defaultConfig.reportingTopKs(),
                seed);
    }

    static Integer parseOptionalInteger(String token, String optionName) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return parseOptionalInt(token, 0, optionName);
    }

    static Consumer<Integer> progressCallback(boolean enabled, PrintWriter err, String label) {
        if (!enabled) {
            return null;
        }
        return completed -> {
            if (completed == 1 || completed % 25 == 0) {
                err.printf("%s progress: %d%n", label, completed);
                err.flush();
            }
        };
    }

    static Path resolveOutputPath(String outputToken) throws IOException {
        if (outputToken == null || outputToken.isBlank()) {
            return null;
        }
        Path outputPath = Path.of(outputToken).toAbsolutePath().normalize();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        return outputPath;
    }

    static void writeJson(String json, Path outputPath, PrintWriter out) throws IOException {
        if (outputPath == null) {
            out.println(json);
            out.flush();
            return;
        }
        Files.writeString(outputPath, json);
    }

    static Path saveChart(String chartToken, BarSeries series, TradingStatement statement) throws IOException {
        if (chartToken == null || chartToken.isBlank()) {
            return null;
        }
        Path chartPath = Path.of(chartToken).toAbsolutePath().normalize();
        if (chartPath.getParent() != null) {
            Files.createDirectories(chartPath.getParent());
        }
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.createTradingRecordChart(series, statement.getStrategy().getName(),
                statement.getTradingRecord());
        ChartUtils.saveChartAsJPEG(chartPath.toFile(), chart, 1920, 1080);
        return chartPath;
    }

    static Map<String, Object> buildCommandMetadata(String command, BarSeries series, String dataFile,
            String timeframeToken, String fromDateToken, String toDateToken, String executionModelToken,
            String capitalToken, String stakeAmountToken, String commissionToken, String borrowRateToken,
            List<CriterionSpec> criteria, Path outputPath, Path chartPath) {
        Map<String, Object> metadata = linkedMap();
        metadata.put("command", command);
        metadata.put("generatedAtUtc", Instant.now().toString());

        Map<String, Object> input = linkedMap();
        input.put("dataFile", Path.of(dataFile).toAbsolutePath().normalize().toString());
        input.put("seriesName", series.getName());
        input.put("barCount", series.getBarCount());
        input.put("timeframe", timeframeToken == null ? null : normalizeToken(timeframeToken));
        input.put("fromDate", fromDateToken);
        input.put("toDate", toDateToken);
        metadata.put("input", input);

        Map<String, Object> execution = linkedMap();
        execution.put("executionModel",
                executionModelToken == null ? "next-open" : normalizeToken(executionModelToken));
        execution.put("capital", capitalToken);
        execution.put("stakeAmount", stakeAmountToken);
        execution.put("commission", commissionToken);
        execution.put("borrowRate", borrowRateToken);
        execution.put("criteria", criteria.stream().map(CriterionSpec::alias).toList());
        metadata.put("execution", execution);

        Map<String, Object> artifacts = linkedMap();
        artifacts.put("outputFile", outputPath == null ? null : outputPath.toString());
        artifacts.put("chartFile", chartPath == null ? null : chartPath.toString());
        metadata.put("artifacts", artifacts);
        return metadata;
    }

    static Map<String, Object> statementToMap(BarSeries series, TradingStatement statement,
            List<CriterionSpec> criteria) {
        Map<String, Object> result = linkedMap();
        result.put("strategyName", statement.getStrategy().getName());
        result.put("startingType", statement.getStrategy().getStartingType().name());
        result.put("unstableBars", statement.getStrategy().getUnstableBars());
        result.put("strategyJson", statement.getStrategy().toJson());
        result.put("positionCount", statement.getTradingRecord().getPositionCount());

        Map<String, Object> performance = linkedMap();
        performance.put("totalProfitLoss", numberString(statement.getPerformanceReport().totalProfitLoss));
        performance.put("totalProfitLossPercentage",
                numberString(statement.getPerformanceReport().totalProfitLossPercentage));
        performance.put("totalProfit", numberString(statement.getPerformanceReport().totalProfit));
        performance.put("totalLoss", numberString(statement.getPerformanceReport().totalLoss));
        result.put("performance", performance);

        PositionStatsReport statsReport = statement.getPositionStatsReport();
        Map<String, Object> positions = linkedMap();
        positions.put("profitCount", numberString(statsReport.getProfitCount()));
        positions.put("lossCount", numberString(statsReport.getLossCount()));
        positions.put("breakEvenCount", numberString(statsReport.getBreakEvenCount()));
        result.put("positions", positions);

        result.put("criteria", criterionScores(series, statement.getTradingRecord(), criteria));
        return result;
    }

    static Map<String, Object> backtestRuntimeToMap(BacktestRuntimeReport runtimeReport) {
        Map<String, Object> runtime = linkedMap();
        runtime.put("overallRuntime", runtimeReport.overallRuntime().toString());
        runtime.put("minStrategyRuntime", runtimeReport.minStrategyRuntime().toString());
        runtime.put("maxStrategyRuntime", runtimeReport.maxStrategyRuntime().toString());
        runtime.put("averageStrategyRuntime", runtimeReport.averageStrategyRuntime().toString());
        runtime.put("medianStrategyRuntime", runtimeReport.medianStrategyRuntime().toString());
        runtime.put("strategyCount", runtimeReport.strategyCount());
        return runtime;
    }

    static Map<String, Object> walkForwardToMap(BarSeries series, StrategyWalkForwardExecutionResult result,
            List<CriterionSpec> criteria) {
        Map<String, Object> walkForward = linkedMap();
        Map<String, Object> config = linkedMap();
        config.put("configHash", result.config().configHash());
        config.put("minTrainBars", result.config().minTrainBars());
        config.put("testBars", result.config().testBars());
        config.put("stepBars", result.config().stepBars());
        config.put("purgeBars", result.config().purgeBars());
        config.put("embargoBars", result.config().embargoBars());
        config.put("holdoutBars", result.config().holdoutBars());
        config.put("primaryHorizonBars", result.config().primaryHorizonBars());
        config.put("optimizationTopK", result.config().optimizationTopK());
        config.put("seed", result.config().seed());
        walkForward.put("config", config);

        walkForward.put("runtime", walkForwardRuntimeToMap(result.runtimeReport()));

        List<Map<String, Object>> folds = new ArrayList<>(result.folds().size());
        for (StrategyWalkForwardExecutionResult.FoldResult fold : result.folds()) {
            folds.add(foldToMap(series, fold, criteria));
        }
        walkForward.put("folds", folds);

        Map<String, Object> aggregateCriteria = linkedMap();
        for (CriterionSpec criterion : criteria) {
            Map<String, Object> values = linkedMap();
            Map<String, String> byFold = new LinkedHashMap<>();
            result.criterionValuesByFold(criterion.criterion())
                    .forEach((foldId, value) -> byFold.put(foldId, numberString(value)));
            values.put("byFold", byFold);
            values.put("holdout",
                    result.holdoutCriterionValue(criterion.criterion()).map(CliSupport::numberString).orElse(null));
            values.put("outOfSampleAverage", average(result.outOfSampleCriterionValues(criterion.criterion())));
            aggregateCriteria.put(criterion.alias(), values);
        }
        walkForward.put("criteria", aggregateCriteria);
        return walkForward;
    }

    private static Map<String, Object> walkForwardRuntimeToMap(WalkForwardRuntimeReport runtimeReport) {
        Map<String, Object> runtime = linkedMap();
        runtime.put("overallRuntime", runtimeReport.overallRuntime().toString());
        runtime.put("minFoldRuntime", runtimeReport.minFoldRuntime().toString());
        runtime.put("maxFoldRuntime", runtimeReport.maxFoldRuntime().toString());
        runtime.put("averageFoldRuntime", runtimeReport.averageFoldRuntime().toString());
        runtime.put("medianFoldRuntime", runtimeReport.medianFoldRuntime().toString());
        runtime.put("foldCount", runtimeReport.foldRuntimes().size());
        return runtime;
    }

    private static Map<String, Object> foldToMap(BarSeries series, StrategyWalkForwardExecutionResult.FoldResult fold,
            List<CriterionSpec> criteria) {
        Map<String, Object> foldMap = linkedMap();
        WalkForwardSplit split = fold.split();
        foldMap.put("foldId", split.foldId());
        foldMap.put("holdout", split.holdout());
        foldMap.put("trainStart", split.trainStart());
        foldMap.put("trainEnd", split.trainEnd());
        foldMap.put("testStart", split.testStart());
        foldMap.put("testEnd", split.testEnd());
        foldMap.put("purgeBars", split.purgeBars());
        foldMap.put("embargoBars", split.embargoBars());
        foldMap.put("runtime", fold.runtime().toString());
        foldMap.put("statement", statementToMap(series, fold.tradingStatement(), criteria));
        return foldMap;
    }

    private static String average(List<Num> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Num sum = values.getFirst().getNumFactory().zero();
        for (Num value : values) {
            sum = sum.plus(value);
        }
        return numberString(sum.dividedBy(values.getFirst().getNumFactory().numOf(values.size())));
    }

    private static Map<String, Object> criterionScores(BarSeries series, TradingRecord tradingRecord,
            List<CriterionSpec> criteria) {
        Map<String, Object> scores = linkedMap();
        for (CriterionSpec criterion : criteria) {
            scores.put(criterion.alias(), numberString(criterion.criterion().calculate(series, tradingRecord)));
        }
        return scores;
    }

    private static Map<String, Supplier<AnalysisCriterion>> createCriterionRegistry() {
        Map<String, Supplier<AnalysisCriterion>> criteria = new LinkedHashMap<>();
        criteria.put("gross-return", GrossReturnCriterion::new);
        criteria.put("net-return", NetReturnCriterion::new);
        criteria.put("gross-profit", GrossProfitCriterion::new);
        criteria.put("net-profit", NetProfitCriterion::new);
        criteria.put("net-profit-loss", NetProfitLossCriterion::new);
        criteria.put("sharpe", SharpeRatioCriterion::new);
        criteria.put("romad", ReturnOverMaxDrawdownCriterion::new);
        criteria.put("max-drawdown", MaximumDrawdownCriterion::new);
        criteria.put("total-fees", TotalFeesCriterion::new);
        criteria.put("commissions", CommissionsCriterion::new);
        criteria.put("sqn", SqnCriterion::new);
        criteria.put("expectancy", ExpectancyCriterion::new);
        return Map.copyOf(criteria);
    }

    private static TradeExecutionModel resolveExecutionModel(String executionModelToken) {
        if (executionModelToken == null || executionModelToken.isBlank()
                || "next-open".equals(normalizeToken(executionModelToken))) {
            return new TradeOnNextOpenModel();
        }
        if ("current-close".equals(normalizeToken(executionModelToken))) {
            return new TradeOnCurrentCloseModel();
        }
        throw new IllegalArgumentException(
                "Unsupported execution model. Supported values are next-open and current-close.");
    }

    private static CostModel resolveTransactionCostModel(String commissionToken) {
        if (commissionToken == null || commissionToken.isBlank()) {
            return new ZeroCostModel();
        }
        return new LinearTransactionCostModel(parseNonNegativeDouble(commissionToken, "commission"));
    }

    private static CostModel resolveHoldingCostModel(String borrowRateToken) {
        if (borrowRateToken == null || borrowRateToken.isBlank()) {
            return new ZeroCostModel();
        }
        return new LinearBorrowingCostModel(parseNonNegativeDouble(borrowRateToken, "borrow-rate"));
    }

    private static double parsePositiveDouble(String token, String optionName) {
        double parsed = parseNonNegativeDouble(token, optionName);
        if (parsed <= 0.0d) {
            throw new IllegalArgumentException("--" + optionName + " must be greater than zero.");
        }
        return parsed;
    }

    private static double parseNonNegativeDouble(String token, String optionName) {
        try {
            double parsed = Double.parseDouble(token);
            if (parsed < 0.0d) {
                throw new IllegalArgumentException("--" + optionName + " must be >= 0.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric value for --" + optionName + ": " + token + ".", ex);
        }
    }

    private static int parseOptionalInt(String token, int defaultValue, String optionName) {
        if (token == null || token.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer value for --" + optionName + ": " + token + ".", ex);
        }
    }

    private static long parseOptionalLong(String token, long defaultValue, String optionName) {
        if (token == null || token.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid long value for --" + optionName + ": " + token + ".", ex);
        }
    }

    private static Strategy buildSmaCrossoverStrategy(BarSeries series, Map<String, String> params) {
        int fast = parsePositiveInt(params.getOrDefault("fast", "5"), "fast");
        int slow = parsePositiveInt(params.getOrDefault("slow", "20"), "slow");
        if (fast >= slow) {
            throw new IllegalArgumentException("For sma-crossover, fast must be smaller than slow.");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator fastIndicator = new SMAIndicator(closePrice, fast);
        SMAIndicator slowIndicator = new SMAIndicator(closePrice, slow);

        Rule entryRule = new CrossedUpIndicatorRule(fastIndicator, slowIndicator);
        Rule exitRule = new CrossedDownIndicatorRule(fastIndicator, slowIndicator);
        BaseStrategy strategy = new BaseStrategy("sma-crossover-fast-" + fast + "-slow-" + slow, entryRule, exitRule);
        strategy.setUnstableBars(slow);
        return strategy;
    }

    private static Indicator<Num> buildIndicator(String alias, Map<String, String> params, BarSeries series,
            ClosePriceIndicator closePrice) {
        int period = parsePositiveInt(params.getOrDefault("period", "14"), "period");
        return switch (alias) {
        case "sma" -> new SMAIndicator(closePrice, period);
        case "ema" -> new EMAIndicator(closePrice, period);
        case "rsi" -> new RSIIndicator(closePrice, period);
        case "cci" -> new CCIIndicator(series, period);
        default ->
            throw new IllegalArgumentException("Unsupported indicator alias. Supported values are sma, ema, rsi, cci.");
        };
    }

    private static Rule buildThresholdRule(Indicator<Num> indicator, String belowToken, String aboveToken,
            BarSeries series, String phase) {
        if (belowToken != null && aboveToken != null) {
            throw new IllegalArgumentException("Use only one of --" + phase + "-below or --" + phase + "-above.");
        }
        if (belowToken == null && aboveToken == null) {
            throw new IllegalArgumentException(
                    "Threshold indicator tests require either --" + phase + "-below or --" + phase + "-above.");
        }
        if (belowToken != null) {
            return new UnderIndicatorRule(indicator, series.numFactory().numOf(belowToken));
        }
        return new OverIndicatorRule(indicator, series.numFactory().numOf(aboveToken));
    }

    private static int resolveDefaultIndicatorUnstableBars(Map<String, String> params) {
        return parsePositiveInt(params.getOrDefault("period", "14"), "period");
    }

    private static Map<String, String> parseKeyValueOptions(List<String> options, String optionName) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String option : options) {
            int separator = option.indexOf('=');
            if (separator <= 0 || separator == option.length() - 1) {
                throw new IllegalArgumentException("Invalid " + optionName + " value '" + option + "'. Use key=value.");
            }
            String key = normalizeToken(option.substring(0, separator));
            String value = option.substring(separator + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Invalid " + optionName + " value '" + option + "'. Use key=value.");
            }
            values.put(key, value);
        }
        return values;
    }

    private static Map<String, List<String>> parseGridOptions(List<String> options) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String option : options) {
            int separator = option.indexOf('=');
            if (separator <= 0 || separator == option.length() - 1) {
                throw new IllegalArgumentException("Invalid --param-grid value '" + option + "'. Use key=v1,v2,...");
            }
            String key = normalizeToken(option.substring(0, separator));
            String value = option.substring(separator + 1);
            List<String> entries = new ArrayList<>();
            for (String token : value.split(",")) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    entries.add(normalized);
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid --param-grid value '" + option + "'. At least one value is required.");
            }
            values.put(key, List.copyOf(entries));
        }
        return values;
    }

    private static void buildCartesianProduct(List<String> keys, Map<String, List<String>> gridParams, int index,
            Map<String, String> current, List<Map<String, String>> result) {
        if (index >= keys.size()) {
            result.add(new LinkedHashMap<>(current));
            return;
        }

        String key = keys.get(index);
        for (String value : gridParams.get(key)) {
            current.put(key, value);
            buildCartesianProduct(keys, gridParams, index + 1, current, result);
        }
    }

    private static int parsePositiveInt(String token, String optionName) {
        try {
            int parsed = Integer.parseInt(token);
            if (parsed <= 0) {
                throw new IllegalArgumentException("--" + optionName + " must be greater than zero.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer value for --" + optionName + ": " + token + ".", ex);
        }
    }

    private static Duration parseTimeframe(String token) {
        String normalized = normalizeToken(token);
        return switch (normalized) {
        case "1m" -> Duration.ofMinutes(1);
        case "5m" -> Duration.ofMinutes(5);
        case "15m" -> Duration.ofMinutes(15);
        case "1h" -> Duration.ofHours(1);
        case "4h" -> Duration.ofHours(4);
        case "1d" -> Duration.ofDays(1);
        default -> {
            try {
                yield Duration.parse(token);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Unsupported timeframe '" + token + "'. Use 1m, 5m, 15m, 1h, 4h, 1d, or an ISO-8601 duration.",
                        ex);
            }
        }
        };
    }

    private static BarSeries sliceSeries(BarSeries series, Instant fromDate, Instant toDate) {
        int startIndex = series.getBeginIndex();
        while (startIndex <= series.getEndIndex() && fromDate != null
                && series.getBar(startIndex).getEndTime().isBefore(fromDate)) {
            startIndex++;
        }

        int endIndexExclusive = series.getEndIndex() + 1;
        while (endIndexExclusive > startIndex && toDate != null
                && series.getBar(endIndexExclusive - 1).getEndTime().isAfter(toDate)) {
            endIndexExclusive--;
        }

        if (startIndex >= endIndexExclusive) {
            return null;
        }
        return series.getSubSeries(startIndex, endIndexExclusive);
    }

    private static Instant parseOptionalInstant(String token, boolean startOfRange) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            if (!token.contains("T")) {
                LocalDate date = LocalDate.parse(token);
                return startOfRange ? date.atStartOfDay().toInstant(ZoneOffset.UTC)
                        : date.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(token).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                Instant parsed = Instant.parse(token);
                return parsed;
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Invalid date value '" + token + "'. Use YYYY-MM-DD or an ISO-8601 instant.", ex);
            }
        }
    }

    private static String numberString(Num value) {
        return value == null ? null : value.toString();
    }

    private static String normalizeToken(String token) {
        return token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Binds a stable CLI alias to the concrete criterion instance used for a single
     * command execution.
     *
     * @param alias     stable command-line alias used in JSON output
     * @param criterion resolved analysis criterion instance
     */
    record CriterionSpec(String alias, AnalysisCriterion criterion) {
    }
}
