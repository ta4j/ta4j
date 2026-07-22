/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
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
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.forecast.AnalogReturnProjectionIndicator;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator;
import org.ta4j.core.indicators.forecast.OnlineChangePointForecastStateIndicator;
import org.ta4j.core.indicators.forecast.RollingConformalForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.RoughVolatilityForecastStateIndicator;
import org.ta4j.core.indicators.forecast.adapters.LognormalApproximationPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.OnlineChangePointForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.RoughVolatilityForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.named.NamedAssetKind;
import org.ta4j.core.named.NamedAssetRegistry;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.PositionStatsReport;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.named.NamedRule;
import org.ta4j.core.strategy.named.NamedStrategy;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared validation, execution, and serialization helpers for the ta4j CLI.
 *
 * <p>
 * This type keeps the command entry point thin while reusing existing ta4j
 * execution, reporting, charting, and datasource APIs. The helpers are scoped
 * to the CLI's bounded MVP surface so command behavior stays deterministic and
 * file-oriented for both users and agents.
 * </p>
 *
 * @since 0.23.1
 */
final class CliSupport {

    static final int SCHEMA_VERSION = 1;

    static final List<String> DEFAULT_BACKTEST_CRITERIA = List.of(NetProfitCriterion.class.getName(),
            ReturnOverMaxDrawdownCriterion.class.getName(), TotalFeesCriterion.class.getName());
    static final List<String> DEFAULT_WALK_FORWARD_CRITERIA = List.of(GrossReturnCriterion.class.getName());
    static final List<String> DEFAULT_SWEEP_CRITERIA = List.of(NetProfitCriterion.class.getName());
    static final List<String> DEFAULT_INDICATOR_TEST_CRITERIA = List.of(NetProfitCriterion.class.getName(),
            SharpeRatioCriterion.class.getName());
    static final List<String> DEFAULT_RULE_TEST_CRITERIA = List.of(NetProfitCriterion.class.getName(),
            SharpeRatioCriterion.class.getName());
    static final String NAMED_STRATEGY_EXAMPLE = "DayOfWeekStrategy_MONDAY_FRIDAY";
    static final String NAMED_RULE_EXAMPLE = "RsiThresholdRule_BELOW_14_30";
    private static final String STRATEGY_INPUT_GUIDANCE = "Use --strategy, --strategies, --strategy-json-file, or --strategies-json-file.";
    private static final String INDICATOR_INPUT_GUIDANCE = "Use --indicator with compact shorthand or serialized JSON, or use --indicator-json-file.";
    private static final String RULE_INPUT_GUIDANCE = "Use --entry-rule or --entry-rule-json-file together with --exit-rule or --exit-rule-json-file.";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private CliSupport() {
    }

    static String toJson(Object value) {
        return GSON.toJson(value);
    }

    static BarSeries loadSeries(String dataFile, String timeframeToken, String fromDateToken, String toDateToken) {
        return loadSeries(dataFile, null, System.in, timeframeToken, fromDateToken, toDateToken);
    }

    static BarSeries loadSeries(String dataFile, String dataFormat, InputStream input, String timeframeToken,
            String fromDateToken, String toDateToken) {
        Objects.requireNonNull(dataFile, "dataFile");
        if ("-".equals(dataFile)) {
            String normalizedFormat = normalizeToken(dataFormat);
            if (!"csv".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
                throw new IllegalArgumentException(
                        "--data-format csv or --data-format json is required when --data-file is -.");
            }
            Objects.requireNonNull(input, "input");
            Path temporaryFile = null;
            try {
                temporaryFile = Files.createTempFile("ta4j-cli-stdin-", "." + normalizedFormat);
                Files.copy(input, temporaryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return loadSeries(temporaryFile.toString(), null, System.in, timeframeToken, fromDateToken,
                        toDateToken);
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to read bar data from stdin.", exception);
            } finally {
                if (temporaryFile != null) {
                    try {
                        Files.deleteIfExists(temporaryFile);
                    } catch (IOException ignored) {
                        temporaryFile.toFile().deleteOnExit();
                    }
                }
            }
        }

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
        return buildExecutor(series, executionModelToken, commissionToken, borrowRateToken, null);
    }

    static BacktestExecutor buildExecutor(BarSeries series, String executionModelToken, String commissionToken,
            String borrowRateToken, String borrowSideToken) {
        TradeExecutionModel executionModel = resolveExecutionModel(executionModelToken);
        CostModel transactionCostModel = resolveTransactionCostModel(commissionToken);
        CostModel holdingCostModel = resolveHoldingCostModel(borrowRateToken, borrowSideToken);
        return new BacktestExecutor(series, transactionCostModel, holdingCostModel, executionModel);
    }

    static Num resolveAmount(BarSeries series, String capitalToken, String stakeAmountToken) {
        Double capital = null;
        if (capitalToken != null && !capitalToken.isBlank()) {
            capital = parsePositiveDouble(capitalToken, "capital");
        }
        Double stake = null;
        if (stakeAmountToken != null && !stakeAmountToken.isBlank()) {
            stake = parsePositiveDouble(stakeAmountToken, "stake-amount");
        }

        String resolved = stakeAmountToken;
        if (resolved == null || resolved.isBlank()) {
            resolved = capitalToken;
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = "1";
        }
        if (capital != null && stake != null) {
            if (stake > capital) {
                throw new IllegalArgumentException("--stake-amount must not exceed --capital.");
            }
        }
        return series.numFactory().numOf(resolved);
    }

    static PositionSizingSpec resolvePositionSizing(BarSeries series, String modeToken, String capitalToken,
            String stakeAmountToken, String winProbabilityToken, String payoffRatioToken, String coefficientToken) {
        String mode = modeToken == null || modeToken.isBlank() ? "fixed" : normalizeToken(modeToken);
        return switch (mode) {
        case "fixed" -> {
            rejectOption(winProbabilityToken, "--win-probability", "--position-sizing kelly");
            rejectOption(payoffRatioToken, "--payoff-ratio", "--position-sizing kelly");
            rejectOption(coefficientToken, "--kelly-coefficient", "--position-sizing kelly");
            yield new PositionSizingSpec(mode, capitalToken, stakeAmountToken, null, null, null,
                    PositionSizer.fixed(resolveAmount(series, capitalToken, stakeAmountToken)));
        }
        case "balance" -> {
            requireOption(capitalToken, "--capital", "--position-sizing balance");
            rejectOption(stakeAmountToken, "--stake-amount", "--position-sizing fixed");
            rejectOption(winProbabilityToken, "--win-probability", "--position-sizing kelly");
            rejectOption(payoffRatioToken, "--payoff-ratio", "--position-sizing kelly");
            rejectOption(coefficientToken, "--kelly-coefficient", "--position-sizing kelly");
            double capital = parsePositiveDouble(capitalToken, "capital");
            yield new PositionSizingSpec(mode, capitalToken, null, null, null, null, PositionSizer.balance(capital));
        }
        case "kelly" -> {
            requireOption(capitalToken, "--capital", "--position-sizing kelly");
            requireOption(winProbabilityToken, "--win-probability", "--position-sizing kelly");
            requireOption(payoffRatioToken, "--payoff-ratio", "--position-sizing kelly");
            rejectOption(stakeAmountToken, "--stake-amount", "--position-sizing fixed");
            double capital = parsePositiveDouble(capitalToken, "capital");
            double winProbability = parseProbability(winProbabilityToken, "win-probability");
            double payoffRatio = parsePositiveDouble(payoffRatioToken, "payoff-ratio");
            double coefficient = coefficientToken == null || coefficientToken.isBlank() ? 1.0d
                    : parsePositiveDouble(coefficientToken, "kelly-coefficient");
            yield new PositionSizingSpec(mode, capitalToken, null, winProbabilityToken, payoffRatioToken,
                    coefficientToken == null || coefficientToken.isBlank() ? "1" : coefficientToken,
                    PositionSizer.kelly(capital, winProbability, payoffRatio, coefficient));
        }
        default -> throw new IllegalArgumentException(
                "Unsupported --position-sizing '" + modeToken + "'. Use fixed, balance, or kelly.");
        };
    }

    static List<CriterionSpec> resolveCriteria(List<String> requestedTypes, List<String> defaults) {
        return resolveCriteria(requestedTypes, List.of(), List.of(), defaults);
    }

    static List<CriterionSpec> resolveCriteria(List<String> requestedTypes, List<String> requestedJson,
            List<String> jsonFiles, List<String> defaults) {
        boolean hasExplicitInput = requestedTypes != null && !requestedTypes.isEmpty()
                || requestedJson != null && !requestedJson.isEmpty() || jsonFiles != null && !jsonFiles.isEmpty();
        List<String> effectiveTypes = hasExplicitInput ? requestedTypes : defaults;
        LinkedHashSet<String> normalizedInputs = new LinkedHashSet<>();
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();
        for (String value : effectiveTypes == null ? List.<String>of() : effectiveTypes) {
            if (value == null) {
                continue;
            }
            for (String token : registry.splitTopLevel(value)) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    normalizedInputs.add(normalized);
                }
            }
        }
        if (requestedJson != null) {
            requestedJson.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(normalizedInputs::add);
        }
        if (jsonFiles != null) {
            for (String file : jsonFiles) {
                normalizedInputs.addAll(readCriterionJsonInputs(file));
            }
        }
        if (normalizedInputs.isEmpty()) {
            throw new IllegalArgumentException("At least one analysis criterion is required.");
        }

        LinkedHashMap<String, CriterionSpec> criteriaByJson = new LinkedHashMap<>();
        for (String input : normalizedInputs) {
            CriterionSpec criterion = resolveCriterion(input);
            criteriaByJson.putIfAbsent(criterion.json(), criterion);
        }
        return List.copyOf(criteriaByJson.values());
    }

    static Strategy buildStrategy(String strategyLabel, String strategyJsonFile, Integer unstableBars,
            BarSeries series) {
        Strategy strategy;
        String requestedStrategy = strategyLabel == null ? "" : strategyLabel.trim();
        if (strategyJsonFile != null && !strategyJsonFile.isBlank()) {
            strategy = buildStrategyFromJsonPath(strategyJsonFile, unstableBars, series);
        } else {
            strategy = buildStrategyExpression(requestedStrategy, series);
        }

        if (unstableBars != null && (strategyJsonFile == null || strategyJsonFile.isBlank())) {
            strategy.setUnstableBars(unstableBars);
        }
        return strategy;
    }

    static ResolvedStrategies resolveStrategies(String strategyToken, String strategyJsonFile,
            List<String> strategyLabels, String strategiesJsonFile, Integer unstableBars, BarSeries series) {
        List<Strategy> validStrategies = new ArrayList<>();
        List<String> invalidStrategies = new ArrayList<>();
        boolean hasStrategyInput = false;

        if (strategyToken != null && !strategyToken.isBlank()) {
            hasStrategyInput = true;
            try {
                validStrategies.add(buildStrategy(strategyToken, null, unstableBars, series));
            } catch (IllegalArgumentException ex) {
                invalidStrategies.add("--strategy " + strategyToken + ": " + ex.getMessage());
            }
        }

        if (strategyJsonFile != null && !strategyJsonFile.isBlank()) {
            hasStrategyInput = true;
            try {
                validStrategies.add(buildStrategyFromJsonPath(strategyJsonFile, unstableBars, series));
            } catch (IllegalArgumentException ex) {
                invalidStrategies.add("--strategy-json-file " + strategyJsonFile + ": " + ex.getMessage());
            }
        }

        if (strategyLabels != null && !strategyLabels.isEmpty()) {
            hasStrategyInput = true;
            for (String label : parseStrategyInputs(strategyLabels, invalidStrategies)) {
                try {
                    validStrategies.add(buildStrategyExpression(label, series));
                    if (unstableBars != null) {
                        validStrategies.getLast().setUnstableBars(unstableBars);
                    }
                } catch (IllegalArgumentException ex) {
                    invalidStrategies.add("--strategies " + label + ": " + ex.getMessage());
                }
            }
        }

        if (strategiesJsonFile != null && !strategiesJsonFile.isBlank()) {
            hasStrategyInput = true;
            validStrategies.addAll(
                    loadStrategiesFromJsonArrayFile(strategiesJsonFile, unstableBars, series, invalidStrategies));
        }

        if (!hasStrategyInput) {
            throw new IllegalArgumentException("At least one strategy input is required. " + STRATEGY_INPUT_GUIDANCE);
        }
        if (validStrategies.isEmpty()) {
            throw new IllegalArgumentException(noValidStrategiesMessage(invalidStrategies));
        }
        return new ResolvedStrategies(List.copyOf(validStrategies), List.copyOf(invalidStrategies));
    }

    static void reportInvalidStrategies(List<String> invalidStrategies, PrintWriter err) {
        if (invalidStrategies == null || invalidStrategies.isEmpty()) {
            return;
        }
        err.println("Skipping invalid strategy inputs:");
        for (String message : invalidStrategies) {
            err.println("- " + message);
        }
        err.flush();
    }

    static void enforceInvalidStrategyPolicy(List<String> invalidStrategies, String policyToken, PrintWriter err) {
        String policy = normalizeToken(policyToken);
        if (!"fail".equals(policy) && !"skip".equals(policy)) {
            throw new IllegalArgumentException(
                    "Unsupported --invalid-input policy '" + policyToken + "'. Use fail or skip.");
        }
        if (invalidStrategies == null || invalidStrategies.isEmpty()) {
            return;
        }
        if ("fail".equals(policy)) {
            throw new IllegalArgumentException("Invalid strategy inputs:\n" + String.join("\n", invalidStrategies)
                    + "\nUse --invalid-input skip to run only valid inputs.");
        }
        reportInvalidStrategies(invalidStrategies, err);
    }

    static BacktestRuntimeReport aggregateBacktestRuntimes(List<BacktestRuntimeReport> runtimeReports) {
        if (runtimeReports == null || runtimeReports.isEmpty()) {
            return BacktestRuntimeReport.empty();
        }

        List<BacktestRuntimeReport.StrategyRuntime> strategyRuntimes = new ArrayList<>();
        Duration overallRuntime = Duration.ZERO;
        for (BacktestRuntimeReport runtimeReport : runtimeReports) {
            if (runtimeReport == null) {
                continue;
            }
            overallRuntime = overallRuntime.plus(runtimeReport.overallRuntime());
            strategyRuntimes.addAll(runtimeReport.strategyRuntimes());
        }
        if (strategyRuntimes.isEmpty()) {
            return BacktestRuntimeReport.empty();
        }

        List<Duration> durations = strategyRuntimes.stream()
                .map(BacktestRuntimeReport.StrategyRuntime::runtime)
                .toList();
        return new BacktestRuntimeReport(overallRuntime, minDuration(durations), maxDuration(durations),
                averageDuration(durations), medianDuration(durations), strategyRuntimes);
    }

    static List<Strategy> buildSweepStrategies(List<String> paramOptions, List<String> gridOptions,
            Integer unstableBars, BarSeries series) {
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

    static ResolvedIndicator resolveIndicator(String indicatorJson, String indicatorJsonFile, BarSeries series) {
        String input = readSerializedInput("--indicator", indicatorJson, "--indicator-json-file", indicatorJsonFile,
                INDICATOR_INPUT_GUIDANCE);
        Indicator<?> rawIndicator;
        try {
            rawIndicator = indicatorJsonFile != null && !indicatorJsonFile.isBlank()
                    || input.stripLeading().startsWith("{") ? Indicator.fromJson(series, input)
                            : Indicator.fromExpression(series, input);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid indicator shorthand or serialized JSON input.", ex);
        }

        Indicator<Num> indicator = castNumericIndicator(rawIndicator);
        return new ResolvedIndicator(indicator, indicator.toJson(), indicator.getClass().getName());
    }

    static Strategy buildIndicatorTestStrategy(String indicatorJson, String indicatorJsonFile, Integer unstableBars,
            String entryBelowToken, String entryAboveToken, String exitBelowToken, String exitAboveToken,
            BarSeries series) {
        ResolvedIndicator resolvedIndicator = resolveIndicator(indicatorJson, indicatorJsonFile, series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> indicator = resolvedIndicator.indicator();

        Rule entryRule;
        Rule exitRule;
        if (entryBelowToken != null || entryAboveToken != null || exitBelowToken != null || exitAboveToken != null) {
            entryRule = buildThresholdRule(indicator, entryBelowToken, entryAboveToken, series, "entry");
            exitRule = buildThresholdRule(indicator, exitBelowToken, exitAboveToken, series, "exit");
        } else {
            entryRule = new CrossedUpIndicatorRule(closePrice, indicator);
            exitRule = new CrossedDownIndicatorRule(closePrice, indicator);
        }

        String strategyName = indicator.getClass().getSimpleName() + "-indicator-test";
        BaseStrategy strategy = new BaseStrategy(strategyName, entryRule, exitRule);
        if (unstableBars != null) {
            strategy.setUnstableBars(unstableBars);
        } else {
            strategy.setUnstableBars(indicator.getCountOfUnstableBars());
        }
        return strategy;
    }

    static Map<String, Object> buildForecastReport(BarSeries series, String dataFile, String timeframeToken,
            String fromDateToken, String toDateToken, ForecastRequest request, Path outputPath, boolean reproducible) {
        int index = request.index() == null ? series.getEndIndex() : request.index();
        if (index < series.getBeginIndex() || index > series.getEndIndex()) {
            throw new IllegalArgumentException(
                    "--index must be between " + series.getBeginIndex() + " and " + series.getEndIndex() + ".");
        }
        if (request.horizon() <= 0) {
            throw new IllegalArgumentException("--horizon must be greater than zero.");
        }

        ReturnIndicator returns = new LogReturnIndicator(series);
        String stateModel = normalizeToken(request.stateModel());
        ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator = switch (stateModel) {
        case "ewma" -> new EwmaReturnForecastStateIndicator(returns);
        case "rough-volatility" ->
            RoughVolatilityForecastStateIndicator.builder(returns).horizon(request.horizon()).build();
        case "change-point" -> new OnlineChangePointForecastStateIndicator(returns);
        default -> throw new IllegalArgumentException("Unsupported --state-model '" + request.stateModel()
                + "'. Use ewma, rough-volatility, or change-point.");
        };

        String target = normalizeToken(request.target());
        if (!Set.of("state", "return", "price").contains(target)) {
            throw new IllegalArgumentException(
                    "Unsupported --target '" + request.target() + "'. Use state, return, or price.");
        }
        ReturnMomentState state = stateIndicator.getValue(index);

        Map<String, Object> response = buildResponse("forecast run");
        Map<String, Object> result = result(response);
        result.put("input",
                buildInputMetadata(series, dataFile, timeframeToken, fromDateToken, toDateToken, reproducible));

        Map<String, Object> configuration = linkedMap();
        configuration.put("stateModel", stateModel);
        configuration.put("target", target);
        configuration.put("index", index);
        if (!"state".equals(target)) {
            String projectionModel = normalizeToken(request.projectionModel());
            String calibration = normalizeToken(request.calibration());
            configuration.put("projectionModel", projectionModel);
            configuration.put("calibration", calibration);
            configuration.put("horizon", request.horizon());
            configuration.put("lookbackBars", request.lookbackBars());
            configuration.put("quantiles", request.quantiles());
            if ("monte-carlo".equals(projectionModel)) {
                configuration.put("samples", request.samples());
                configuration.put("seed", request.seed());
                configuration.put("shockModel", normalizeToken(request.shockModel()));
                configuration.put("volatilityMode", normalizeToken(request.volatilityMode()));
                configuration.put("volatilityDecay", request.volatilityDecay());
            } else if ("analog".equals(projectionModel)) {
                configuration.put("neighborCount", request.neighborCount());
                configuration.put("minimumNeighborCount", request.minimumNeighborCount());
                configuration.put("standardizeFeatures", request.standardizeFeatures());
            } else {
                throw new IllegalArgumentException("Unsupported --projection-model '" + request.projectionModel()
                        + "'. Use monte-carlo or analog.");
            }
            if ("conformal".equals(calibration)) {
                configuration.put("coverage", request.coverage());
                configuration.put("calibrationWindow", request.calibrationWindow());
                configuration.put("minimumCalibrationCount", request.minimumCalibrationCount());
            } else if (!"none".equals(calibration)) {
                throw new IllegalArgumentException(
                        "Unsupported --calibration '" + request.calibration() + "'. Use none or conformal.");
            }
        }
        result.put("configuration", configuration);

        Map<String, Object> decision = linkedMap();
        decision.put("index", index);
        decision.put("endTime", series.getBar(index).getEndTime().toString());
        decision.put("closePrice", numberString(series.getBar(index).getClosePrice()));
        result.put("decision", decision);
        result.put("state", forecastStateToMap(stateIndicator, state));

        if (!"state".equals(target)) {
            ProjectionSelection projection = buildForecastProjection(series, returns, stateIndicator, request, target);
            configuration.put("resolvedPriceModel", projection.priceModel());
            Forecast forecast = projection.indicator().getValue(index);
            result.put("forecast", forecastToMap(forecast));
        }

        addRunMetadata(response, reproducible, dataFile, outputPath, null);
        return response;
    }

    private static ProjectionSelection buildForecastProjection(BarSeries series, ReturnIndicator returns,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, ForecastRequest request,
            String target) {
        String projectionModel = normalizeToken(request.projectionModel());
        ReturnForecastProjectionIndicator returnProjection = switch (projectionModel) {
        case "monte-carlo" -> buildMonteCarloReturnForecast(stateIndicator, request);
        case "analog" -> buildAnalogReturnForecast(stateIndicator, request);
        default -> throw new IllegalArgumentException(
                "Unsupported --projection-model '" + request.projectionModel() + "'. Use monte-carlo or analog.");
        };

        String calibration = normalizeToken(request.calibration());
        if ("conformal".equals(calibration)) {
            returnProjection = RollingConformalForecastProjectionIndicator
                    .cumulativeLogReturnBuilder(returnProjection, returns)
                    .targetCoverage(request.coverage())
                    .calibrationWindow(request.calibrationWindow())
                    .minimumCalibrationCount(request.minimumCalibrationCount())
                    .build();
        } else if (!"none".equals(calibration)) {
            throw new IllegalArgumentException(
                    "Unsupported --calibration '" + request.calibration() + "'. Use none or conformal.");
        }

        if ("return".equals(target)) {
            return new ProjectionSelection(returnProjection, null);
        }

        String requestedPriceModel = normalizeToken(request.priceModel());
        String resolvedPriceModel = "auto".equals(requestedPriceModel)
                ? "monte-carlo".equals(projectionModel) && "none".equals(calibration) ? "empirical" : "lognormal"
                : requestedPriceModel;
        ForecastProjectionIndicator priceProjection;
        if ("empirical".equals(resolvedPriceModel)) {
            if (!"monte-carlo".equals(projectionModel) || !"none".equals(calibration)) {
                throw new IllegalArgumentException(
                        "--price-model empirical requires --projection-model monte-carlo and --calibration none.");
            }
            priceProjection = buildMonteCarloPriceForecast(series, stateIndicator, request);
        } else if ("lognormal".equals(resolvedPriceModel)) {
            double[] quantiles = request.quantiles().stream().mapToDouble(Double::doubleValue).toArray();
            priceProjection = LognormalApproximationPriceForecastIndicator
                    .builder(new ClosePriceIndicator(series), returnProjection)
                    .quantiles(quantiles)
                    .build();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported --price-model '" + request.priceModel() + "'. Use auto, empirical, or lognormal.");
        }
        return new ProjectionSelection(priceProjection, resolvedPriceModel);
    }

    private static ReturnForecastProjectionIndicator buildMonteCarloReturnForecast(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, ForecastRequest request) {
        if (request.samples() <= 0) {
            throw new IllegalArgumentException("--samples must be greater than zero.");
        }
        if (request.lookbackBars() <= 0) {
            throw new IllegalArgumentException("--lookback-bars must be greater than zero.");
        }
        if (!Double.isFinite(request.volatilityDecay()) || request.volatilityDecay() <= 0.0d
                || request.volatilityDecay() >= 1.0d) {
            throw new IllegalArgumentException("--volatility-decay must be in (0, 1).");
        }
        double[] quantiles = request.quantiles().stream().mapToDouble(Double::doubleValue).toArray();
        MonteCarloReturnProjectionIndicator.ShockModel shockModel = parseShockModel(request.shockModel());
        MonteCarloReturnProjectionIndicator.VolatilityUpdateMode volatilityMode = parseVolatilityMode(
                request.volatilityMode());
        return MonteCarloReturnProjectionIndicator.builder(stateIndicator)
                .horizon(request.horizon())
                .iterationCount(request.samples())
                .lookbackBarCount(request.lookbackBars())
                .seed(request.seed())
                .shockModel(shockModel)
                .volatilityUpdateMode(volatilityMode)
                .volatilityDecayFactor(request.volatilityDecay())
                .quantiles(quantiles)
                .build();
    }

    private static ForecastProjectionIndicator buildMonteCarloPriceForecast(BarSeries series,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, ForecastRequest request) {
        double[] quantiles = request.quantiles().stream().mapToDouble(Double::doubleValue).toArray();
        return MonteCarloPriceForecastIndicator.builder(new ClosePriceIndicator(series), stateIndicator)
                .horizon(request.horizon())
                .iterationCount(request.samples())
                .lookbackBarCount(request.lookbackBars())
                .seed(request.seed())
                .shockModel(parseShockModel(request.shockModel()))
                .volatilityUpdateMode(parseVolatilityMode(request.volatilityMode()))
                .volatilityDecayFactor(request.volatilityDecay())
                .quantiles(quantiles)
                .build();
    }

    private static ReturnForecastProjectionIndicator buildAnalogReturnForecast(
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, ForecastRequest request) {
        if (request.lookbackBars() <= 0) {
            throw new IllegalArgumentException("--lookback-bars must be greater than zero.");
        }
        double[] quantiles = request.quantiles().stream().mapToDouble(Double::doubleValue).toArray();
        return AnalogReturnProjectionIndicator.builder(stateIndicator)
                .horizon(request.horizon())
                .lookbackBarCount(request.lookbackBars())
                .neighborCount(request.neighborCount())
                .minimumNeighborCount(request.minimumNeighborCount())
                .standardizeFeatures(request.standardizeFeatures())
                .quantiles(quantiles)
                .build();
    }

    private static MonteCarloReturnProjectionIndicator.ShockModel parseShockModel(String token) {
        return switch (normalizeToken(token)) {
        case "historical-bootstrap" -> MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP;
        case "standardized-empirical" -> MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL;
        case "normal" -> MonteCarloReturnProjectionIndicator.ShockModel.NORMAL;
        default -> throw new IllegalArgumentException("Unsupported --shock-model '" + token
                + "'. Use historical-bootstrap, standardized-empirical, or normal.");
        };
    }

    private static MonteCarloReturnProjectionIndicator.VolatilityUpdateMode parseVolatilityMode(String token) {
        return switch (normalizeToken(token)) {
        case "constant" -> MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT;
        case "ewma" -> MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA;
        default ->
            throw new IllegalArgumentException("Unsupported --volatility-mode '" + token + "'. Use constant or ewma.");
        };
    }

    private static Map<String, Object> forecastStateToMap(
            ReturnForecastStateIndicator<? extends ReturnMomentState> indicator, ReturnMomentState state) {
        Map<String, Object> result = linkedMap();
        result.put("type", state.getClass().getName());
        result.put("indicatorType", indicator.getClass().getName());
        result.put("index", state.index());
        result.put("stable", state.isStable());
        result.put("observationCount", state.observationCount());
        result.put("representation", state.representation().name());
        result.put("mean", numberString(state.mean()));
        result.put("drift", numberString(state.drift()));
        result.put("variance", numberString(state.variance()));
        result.put("volatility", numberString(state.volatility()));

        if (state instanceof RoughVolatilityForecastState roughState) {
            result.put("roughnessHurst", numberString(roughState.roughnessHurst()));
            result.put("volOfVol", numberString(roughState.volOfVol()));
            result.put("horizonVariances",
                    roughState.horizonVarianceForecasts().stream().map(CliSupport::numberString).toList());
        } else if (state instanceof OnlineChangePointForecastState changePointState) {
            result.put("recentChangeWindow", changePointState.recentChangeWindow());
            result.put("recentChangeProbability", numberString(changePointState.recentChangeProbability()));
            result.put("mostLikelyRunLength", changePointState.mostLikelyRunLength());
            result.put("topRunLengths", changePointState.topRunLengths().stream().map(posterior -> {
                Map<String, Object> entry = linkedMap();
                entry.put("runLength", posterior.runLength());
                entry.put("probability", numberString(posterior.probability()));
                entry.put("mean", numberString(posterior.mean()));
                entry.put("variance", numberString(posterior.variance()));
                return entry;
            }).toList());
        }
        return result;
    }

    private static Map<String, Object> forecastToMap(Forecast forecast) {
        Map<String, Object> result = linkedMap();
        result.put("decisionIndex", forecast.decisionIndex());
        result.put("horizon", forecast.horizon());
        result.put("stable", forecast.isStable());
        result.put("support", forecastSupportToMap(forecast.support()));
        result.put("mean", numberString(forecast.mean()));
        result.put("median", numberString(forecast.median()));
        result.put("standardDeviation", numberString(forecast.standardDeviation()));
        Map<String, String> quantiles = new LinkedHashMap<>();
        forecast.quantiles()
                .forEach((probability, value) -> quantiles.put(Double.toString(probability), numberString(value)));
        result.put("quantiles", quantiles);
        return result;
    }

    private static Map<String, Object> forecastSupportToMap(ForecastSupport support) {
        Map<String, Object> result = linkedMap();
        if (support instanceof ForecastSupport.Empirical empirical) {
            result.put("type", "empirical");
            result.put("count", empirical.count());
        } else if (support instanceof ForecastSupport.Analytic analytic) {
            result.put("type", "analytic");
            result.put("assumption", analytic.assumption());
        } else {
            result.put("type", "unavailable");
        }
        return result;
    }

    static WalkForwardConfig buildWalkForwardConfig(BarSeries series, String minTrainBarsToken, String testBarsToken,
            String stepBarsToken, String purgeBarsToken, String embargoBarsToken, String holdoutBarsToken,
            String primaryHorizonBarsToken, String optimizationTopKToken, String seedToken) {
        WalkForwardConfig defaultConfig = WalkForwardConfig.defaultConfig(series);
        int minTrainBars = parseOptionalInt(minTrainBarsToken, defaultConfig.minTrainBars(), "min-train-bars");
        int testBars = parseOptionalInt(testBarsToken, defaultConfig.testBars(), "test-bars");
        int stepBars = parseOptionalInt(stepBarsToken, defaultConfig.stepBars(), "step-bars");
        int purgeBars = parseOptionalInt(purgeBarsToken, defaultConfig.purgeBars(), "purge-bars");
        int embargoBars = parseOptionalInt(embargoBarsToken, defaultConfig.embargoBars(), "embargo-bars");
        int holdoutBars = parseOptionalInt(holdoutBarsToken, defaultConfig.holdoutBars(), "holdout-bars");
        int primaryHorizonBars = parseOptionalInt(primaryHorizonBarsToken, defaultConfig.primaryHorizonBars(),
                "primary-horizon-bars");
        int optimizationTopK = parseOptionalInt(optimizationTopKToken, defaultConfig.optimizationTopK(),
                "optimization-top-k");
        long seed = parseOptionalLong(seedToken, defaultConfig.seed(), "seed");

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
        Path outputParent = outputPath.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
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
        Path chartParent = chartPath.getParent();
        if (chartParent != null) {
            Files.createDirectories(chartParent);
        }
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.createTradingRecordChart(series, statement.getStrategy().getName(),
                statement.getTradingRecord());
        ChartUtils.saveChartAsJPEG(chartPath.toFile(), chart, 1920, 1080);
        return chartPath;
    }

    static Map<String, Object> buildCommandMetadata(String command, BarSeries series, String dataFile,
            String timeframeToken, String fromDateToken, String toDateToken, String executionModelToken,
            PositionSizingSpec positionSizing, String commissionToken, String borrowRateToken, String borrowSideToken,
            List<CriterionSpec> criteria, Path outputPath, Path chartPath, boolean reproducible) {
        Map<String, Object> metadata = buildResponse(command);
        Map<String, Object> result = result(metadata);
        result.put("input",
                buildInputMetadata(series, dataFile, timeframeToken, fromDateToken, toDateToken, reproducible));

        Map<String, Object> execution = linkedMap();
        execution.put("executionModel",
                executionModelToken == null ? "next-open" : normalizeToken(executionModelToken));
        execution.put("positionSizing", positionSizing.mode());
        execution.put("capital", positionSizing.capital());
        execution.put("stakeAmount", positionSizing.stakeAmount());
        execution.put("winProbability", positionSizing.winProbability());
        execution.put("payoffRatio", positionSizing.payoffRatio());
        execution.put("kellyCoefficient", positionSizing.kellyCoefficient());
        execution.put("commission", commissionToken);
        execution.put("borrowRate", borrowRateToken);
        execution.put("borrowSide", borrowRateToken == null || borrowRateToken.isBlank() ? null
                : borrowSideToken == null || borrowSideToken.isBlank() ? "short" : normalizeToken(borrowSideToken));
        execution.put("criteria", criteria.stream().map(CliSupport::criterionMetadata).toList());
        result.put("execution", execution);

        addRunMetadata(metadata, reproducible, dataFile, outputPath, chartPath);
        return metadata;
    }

    private static Map<String, Object> buildInputMetadata(BarSeries series, String dataFile, String timeframeToken,
            String fromDateToken, String toDateToken, boolean reproducible) {
        Map<String, Object> input = linkedMap();
        if (!reproducible || "-".equals(dataFile)) {
            input.put("dataFile", dataFile);
            input.put("seriesName", "-".equals(dataFile) ? "stdin" : series.getName());
        }
        input.put("seriesSha256", seriesSha256(series));
        input.put("barCount", series.getBarCount());
        input.put("beginIndex", series.getBeginIndex());
        input.put("endIndex", series.getEndIndex());
        input.put("timeframe", timeframeToken == null ? null : normalizeToken(timeframeToken));
        input.put("fromDate", fromDateToken);
        input.put("toDate", toDateToken);
        return input;
    }

    static Map<String, Object> buildResponse(String command) {
        Map<String, Object> response = linkedMap();
        response.put("schemaVersion", SCHEMA_VERSION);
        response.put("status", "success");
        response.put("command", command);
        response.put("result", linkedMap());
        return response;
    }

    static Map<String, Object> buildCatalogReport() {
        Map<String, Object> response = buildResponse("catalog");
        Map<String, Object> catalog = result(response);
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        Map<String, Object> aliases = linkedMap();
        aliases.put("strategies", registry.aliases(NamedAssetKind.STRATEGY));
        aliases.put("indicators", registry.aliases(NamedAssetKind.INDICATOR));
        aliases.put("rules", registry.aliases(NamedAssetKind.RULE));
        aliases.put("criteria", registry.aliases(NamedAssetKind.ANALYSIS_CRITERION));
        catalog.put("aliases", aliases);

        Map<String, Object> forecasting = linkedMap();
        forecasting.put("stateModels", List.of("ewma", "rough-volatility", "change-point"));
        forecasting.put("targets", List.of("state", "return", "price"));
        forecasting.put("projectionModels", List.of("monte-carlo", "analog"));
        forecasting.put("calibrations", List.of("none", "conformal"));
        forecasting.put("priceModels", List.of("auto", "empirical", "lognormal"));
        catalog.put("forecasting", forecasting);

        Map<String, Object> execution = linkedMap();
        execution.put("executionModels", List.of("next-open", "current-close"));
        execution.put("positionSizing", List.of("fixed", "balance", "kelly"));
        execution.put("borrowSides", List.of("short", "long", "both"));
        execution.put("invalidInputPolicies", List.of("fail", "skip"));
        catalog.put("execution", execution);

        Map<String, Object> output = linkedMap();
        output.put("schemaVersion", SCHEMA_VERSION);
        output.put("statuses", List.of("success", "partial", "error"));
        output.put("errorFormats", List.of("text", "json"));
        catalog.put("output", output);
        return response;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> result(Map<String, Object> response) {
        return (Map<String, Object>) response.get("result");
    }

    @SuppressWarnings("unchecked")
    static void putRunMetadata(Map<String, Object> response, String key, Object value) {
        Map<String, Object> run = (Map<String, Object>) response.get("run");
        if (run != null) {
            run.put(key, value);
        }
    }

    static void markPartial(Map<String, Object> response, List<String> invalidInputs) {
        if (invalidInputs != null && !invalidInputs.isEmpty()) {
            response.put("status", "partial");
        }
    }

    private static void addRunMetadata(Map<String, Object> response, boolean reproducible, String dataFile,
            Path outputPath, Path chartPath) {
        if (reproducible) {
            return;
        }
        Map<String, Object> run = linkedMap();
        run.put("generatedAtUtc", Instant.now().toString());
        run.put("resolvedDataFile",
                "-".equals(dataFile) ? null : Path.of(dataFile).toAbsolutePath().normalize().toString());
        Map<String, Object> artifacts = linkedMap();
        artifacts.put("outputFile", outputPath == null ? null : outputPath.toString());
        artifacts.put("chartFile", chartPath == null ? null : chartPath.toString());
        run.put("artifacts", artifacts);
        response.put("run", run);
    }

    private static Map<String, Object> criterionMetadata(CriterionSpec criterion) {
        Map<String, Object> metadata = linkedMap();
        metadata.put("id", criterion.name());
        metadata.put("type", criterion.className());
        metadata.put("expression", criterion.expression());
        metadata.put("descriptor", JsonParser.parseString(criterion.json()));
        return metadata;
    }

    private static String seriesSha256(BarSeries series) {
        MessageDigest digest = sha256Digest();
        for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
            Bar bar = series.getBar(index);
            String row = bar.getEndTime() + "|" + bar.getOpenPrice() + "|" + bar.getHighPrice() + "|"
                    + bar.getLowPrice() + "|" + bar.getClosePrice() + "|" + bar.getVolume() + "\n";
            digest.update(row.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(String value) {
        MessageDigest digest = sha256Digest();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    static Map<String, Object> statementToMap(BarSeries series, TradingStatement statement,
            List<CriterionSpec> criteria) {
        Map<String, Object> result = linkedMap();
        result.put("strategyName", statement.getStrategy().getName());
        result.put("startingType", statement.getStrategy().getStartingType().name());
        result.put("unstableBars", statement.getStrategy().getUnstableBars());
        result.put("strategyJson", JsonParser.parseString(statement.getStrategy().toJson()));
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
            aggregateCriteria.put(criterion.name(), values);
        }
        walkForward.put("criteria", aggregateCriteria);
        return walkForward;
    }

    static Map<String, Object> walkForwardRuntimeToMap(WalkForwardRuntimeReport runtimeReport) {
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

    private static List<Map<String, Object>> criterionScores(BarSeries series, TradingRecord tradingRecord,
            List<CriterionSpec> criteria) {
        List<Map<String, Object>> scores = new ArrayList<>(criteria.size());
        for (CriterionSpec criterion : criteria) {
            Map<String, Object> score = criterionMetadata(criterion);
            score.put("value", numberString(criterion.criterion().calculate(series, tradingRecord)));
            scores.add(score);
        }
        return List.copyOf(scores);
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

    private static CostModel resolveHoldingCostModel(String borrowRateToken, String borrowSideToken) {
        if (borrowRateToken == null || borrowRateToken.isBlank()) {
            if (borrowSideToken != null && !borrowSideToken.isBlank()) {
                throw new IllegalArgumentException("--borrow-side requires --borrow-rate.");
            }
            return new ZeroCostModel();
        }
        LinearBorrowingCostModel.Applicability applicability = switch (normalizeToken(borrowSideToken)) {
        case "", "short" -> LinearBorrowingCostModel.Applicability.SHORT_ONLY;
        case "long" -> LinearBorrowingCostModel.Applicability.LONG_ONLY;
        case "both" -> LinearBorrowingCostModel.Applicability.BOTH;
        default -> throw new IllegalArgumentException(
                "Unsupported --borrow-side '" + borrowSideToken + "'. Use short, long, or both.");
        };
        return new LinearBorrowingCostModel(parseNonNegativeDouble(borrowRateToken, "borrow-rate"), applicability);
    }

    private static double parseProbability(String token, String optionName) {
        double parsed = parseNonNegativeDouble(token, optionName);
        if (parsed <= 0.0d || parsed >= 1.0d) {
            throw new IllegalArgumentException("--" + optionName + " must be in (0, 1).");
        }
        return parsed;
    }

    private static void requireOption(String token, String optionName, String mode) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(optionName + " is required with " + mode + ".");
        }
    }

    private static void rejectOption(String token, String optionName, String supportedMode) {
        if (token != null && !token.isBlank()) {
            throw new IllegalArgumentException(optionName + " is only valid with " + supportedMode + ".");
        }
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
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("Invalid numeric value for --" + optionName + ": " + token + ".");
            }
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

    private static CriterionSpec resolveCriterion(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            throw new IllegalArgumentException("Criterion inputs must not be blank.");
        }

        AnalysisCriterion criterion;
        try {
            criterion = requestedType.stripLeading().startsWith("{") ? AnalysisCriterion.fromJson(requestedType)
                    : AnalysisCriterion.fromExpression(requestedType);
        } catch (RuntimeException ex) {
            String inputKind = requestedType.stripLeading().startsWith("{") ? "JSON"
                    : requestedType.contains(".") ? "input" : "shorthand";
            throw new IllegalArgumentException("Invalid analysis criterion " + inputKind + ": " + requestedType + ".",
                    ex);
        }
        String json = criterion.toJson();
        String expression;
        try {
            expression = criterion.toExpression();
        } catch (IllegalArgumentException exception) {
            expression = null;
        }
        String name = expression == null || expression.isBlank()
                ? criterion.getClass().getSimpleName() + "-" + sha256(json).substring(0, 8)
                : expression;
        return new CriterionSpec(name, criterion.getClass().getName(), json, expression, criterion);
    }

    private static List<String> readCriterionJsonInputs(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("--criteria-file paths must not be blank.");
        }
        Path path = Path.of(file).toAbsolutePath().normalize();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path));
            if (root.isJsonObject()) {
                return List.of(root.toString());
            }
            if (root.isJsonArray()) {
                List<String> inputs = new ArrayList<>();
                JsonArray array = root.getAsJsonArray();
                for (int index = 0; index < array.size(); index++) {
                    JsonElement element = array.get(index);
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException(
                                "--criteria-file " + file + "[" + index + "] must be a JSON object.");
                    }
                    inputs.add(element.toString());
                }
                return List.copyOf(inputs);
            }
            throw new IllegalArgumentException("--criteria-file " + file + " must contain a JSON object or array.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read --criteria-file " + file + ".", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Invalid JSON in --criteria-file " + file + ".", exception);
        }
    }

    static Strategy buildRuleTestStrategy(String entryRuleLabel, String entryRuleJsonFile, String exitRuleLabel,
            String exitRuleJsonFile, Integer unstableBars, BarSeries series) {
        Rule entryRule = buildRule(entryRuleLabel, entryRuleJsonFile, "--entry-rule", "--entry-rule-json-file", series);
        Rule exitRule = buildRule(exitRuleLabel, exitRuleJsonFile, "--exit-rule", "--exit-rule-json-file", series);
        BaseStrategy strategy = new BaseStrategy("rule-test-" + entryRule.getName() + "-to-" + exitRule.getName(),
                entryRule, exitRule);
        if (unstableBars != null) {
            strategy.setUnstableBars(unstableBars);
        }
        return strategy;
    }

    private static Strategy buildStrategyExpression(String strategyInput, BarSeries series) {
        if (strategyInput == null || strategyInput.isBlank()) {
            throw unknownStrategyValue(strategyInput);
        }
        NamedStrategy.initializeRegistry("ta4jexamples.strategies");
        try {
            return Strategy.fromExpression(series, strategyInput);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid strategy shorthand or label '" + strategyInput
                    + "'. Use a compact expression such as SMA(7,21) or a NamedStrategy label such as "
                    + NAMED_STRATEGY_EXAMPLE + ".", ex);
        }
    }

    private static Rule buildRule(String ruleLabel, String ruleJsonFile, String labelOptionName, String jsonOptionName,
            BarSeries series) {
        boolean hasLabel = ruleLabel != null && !ruleLabel.isBlank();
        boolean hasJsonFile = ruleJsonFile != null && !ruleJsonFile.isBlank();
        if (hasLabel == hasJsonFile) {
            throw new IllegalArgumentException(
                    "Provide exactly one of " + labelOptionName + " or " + jsonOptionName + ". " + RULE_INPUT_GUIDANCE);
        }
        return hasJsonFile ? buildRuleFromJsonPath(ruleJsonFile, series) : buildRuleInput(ruleLabel, series);
    }

    private static Rule buildRuleInput(String ruleLabel, BarSeries series) {
        if (ruleLabel == null || ruleLabel.isBlank()) {
            throw unknownRuleValue(ruleLabel);
        }
        NamedRule.initializeRegistry("ta4jexamples.rules");
        List<String> labelTokens = NamedRule.splitLabel(ruleLabel);
        String simpleName = labelTokens.getFirst();
        if (simpleName.isBlank() || NamedRule.lookup(simpleName).isEmpty()) {
            try {
                return Rule.fromExpression(series, ruleLabel);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid rule shorthand or label '" + ruleLabel
                        + "'. Use a compact expression such as CrossedUp(SMA(7),SMA(21)) or a NamedRule label such as "
                        + NAMED_RULE_EXAMPLE + ".", ex);
            }
        }

        Class<? extends NamedRule> ruleType = NamedRule.requireRegistered(simpleName);
        String[] parameters = labelTokens.size() == 1 ? new String[0]
                : labelTokens.subList(1, labelTokens.size()).toArray(new String[0]);
        try {
            java.lang.reflect.Constructor<? extends NamedRule> constructor = ruleType
                    .getDeclaredConstructor(BarSeries.class, String[].class);
            constructor.setAccessible(true);
            return constructor.newInstance(new Object[] { series, parameters });
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(
                    "Named rule missing (BarSeries, String...) constructor: " + ruleType.getName() + ".", ex);
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                throw new IllegalArgumentException("Named rule reconstruction failed for label '" + ruleLabel + "': "
                        + illegalArgumentException.getMessage(), illegalArgumentException);
            }
            throw new IllegalArgumentException("Unable to construct named rule '" + ruleLabel + "'.", ex);
        }
    }

    private static Rule buildRuleFromJsonPath(String ruleJsonPath, BarSeries series) {
        try {
            String json = Files.readString(Path.of(ruleJsonPath));
            return buildRuleFromJsonString(json, ruleJsonPath, series);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read rule JSON from " + ruleJsonPath + ".", ex);
        }
    }

    private static Rule buildRuleFromJsonString(String json, String sourceDescription, BarSeries series) {
        try {
            return Rule.fromJson(series, json);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid serialized rule from " + sourceDescription + ".", ex);
        }
    }

    private static Strategy buildStrategyFromJsonPath(String strategyJsonPath, Integer unstableBars, BarSeries series) {
        try {
            String json = Files.readString(Path.of(strategyJsonPath));
            return buildStrategyFromJsonString(json, strategyJsonPath, unstableBars, series);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read strategy JSON from " + strategyJsonPath + ".", ex);
        }
    }

    private static Strategy buildStrategyFromJsonString(String json, String sourceDescription, Integer unstableBars,
            BarSeries series) {
        try {
            Strategy strategy = Strategy.fromJson(series, json);
            if (unstableBars != null) {
                strategy.setUnstableBars(unstableBars);
            }
            return strategy;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid serialized strategy from " + sourceDescription + ".", ex);
        }
    }

    private static List<String> parseStrategyInputs(List<String> strategyLabels, List<String> invalidStrategies) {
        List<String> labels = new ArrayList<>();
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();
        for (String rawValue : strategyLabels) {
            if (rawValue == null) {
                continue;
            }
            for (String token : registry.splitTopLevel(rawValue)) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    invalidStrategies.add("--strategies contains an empty strategy label.");
                } else {
                    labels.add(trimmed);
                }
            }
        }
        return labels;
    }

    private static List<Strategy> loadStrategiesFromJsonArrayFile(String strategiesJsonFile, Integer unstableBars,
            BarSeries series, List<String> invalidStrategies) {
        List<Strategy> validStrategies = new ArrayList<>();
        String json;
        try {
            json = Files.readString(Path.of(strategiesJsonFile));
        } catch (IOException ex) {
            invalidStrategies.add("--strategies-json-file " + strategiesJsonFile + ": Unable to read JSON array file.");
            return List.of();
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException ex) {
            invalidStrategies.add("--strategies-json-file " + strategiesJsonFile + ": Invalid JSON content.");
            return List.of();
        }
        if (!parsed.isJsonArray()) {
            invalidStrategies.add("--strategies-json-file " + strategiesJsonFile
                    + ": Expected a JSON array containing one or more serialized strategies.");
            return List.of();
        }

        JsonArray strategyArray = parsed.getAsJsonArray();
        if (strategyArray.isEmpty()) {
            invalidStrategies.add(
                    "--strategies-json-file " + strategiesJsonFile + ": Expected at least one serialized strategy.");
            return List.of();
        }

        for (int index = 0; index < strategyArray.size(); index++) {
            JsonElement entry = strategyArray.get(index);
            if (!entry.isJsonObject()) {
                invalidStrategies.add("--strategies-json-file " + strategiesJsonFile + "[" + index
                        + "]: Each array element must be a serialized strategy object.");
                continue;
            }
            try {
                validStrategies.add(buildStrategyFromJsonString(entry.toString(),
                        strategiesJsonFile + "[" + index + "]", unstableBars, series));
            } catch (IllegalArgumentException ex) {
                invalidStrategies
                        .add("--strategies-json-file " + strategiesJsonFile + "[" + index + "]: " + ex.getMessage());
            }
        }
        return List.copyOf(validStrategies);
    }

    private static String noValidStrategiesMessage(List<String> invalidStrategies) {
        StringBuilder message = new StringBuilder("No valid strategies to run.");
        if (invalidStrategies != null && !invalidStrategies.isEmpty()) {
            message.append(System.lineSeparator()).append("Strategy input errors:");
            for (String invalidStrategy : invalidStrategies) {
                message.append(System.lineSeparator()).append("- ").append(invalidStrategy);
            }
        }
        message.append(System.lineSeparator()).append(STRATEGY_INPUT_GUIDANCE);
        return message.toString();
    }

    private static String readSerializedInput(String inlineOptionName, String inlineValue, String fileOptionName,
            String fileValue, String guidance) {
        boolean hasInlineValue = inlineValue != null && !inlineValue.isBlank();
        boolean hasFileValue = fileValue != null && !fileValue.isBlank();
        if (hasInlineValue == hasFileValue) {
            throw new IllegalArgumentException(
                    "Provide exactly one of " + inlineOptionName + " or " + fileOptionName + ". " + guidance);
        }
        if (hasInlineValue) {
            return inlineValue;
        }
        try {
            return Files.readString(Path.of(fileValue));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read serialized input from " + fileValue + ".", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Indicator<Num> castNumericIndicator(Indicator<?> rawIndicator) {
        int sampleIndex = rawIndicator.getBarSeries().getEndIndex();
        Object sampleValue = rawIndicator.getValue(sampleIndex);
        if (!(sampleValue instanceof Num)) {
            throw new IllegalArgumentException("--indicator must deserialize to an Indicator<Num>.");
        }
        return (Indicator<Num>) rawIndicator;
    }

    private static Duration minDuration(List<Duration> durations) {
        return durations.stream().min(Duration::compareTo).orElse(Duration.ZERO);
    }

    private static Duration maxDuration(List<Duration> durations) {
        return durations.stream().max(Duration::compareTo).orElse(Duration.ZERO);
    }

    private static Duration averageDuration(List<Duration> durations) {
        long totalNanos = 0L;
        for (Duration duration : durations) {
            totalNanos += duration.toNanos();
        }
        return Duration.ofNanos(totalNanos / durations.size());
    }

    private static Duration medianDuration(List<Duration> durations) {
        List<Duration> sorted = new ArrayList<>(durations);
        sorted.sort(Duration::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        long lower = sorted.get(middle - 1).toNanos();
        long upper = sorted.get(middle).toNanos();
        return Duration.ofNanos((lower + upper) / 2L);
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

    private static IllegalArgumentException unknownStrategyValue(String strategyValue) {
        return new IllegalArgumentException("Unknown strategy label '" + strategyValue
                + "'. Use a NamedStrategy label such as " + NAMED_STRATEGY_EXAMPLE + ".");
    }

    private static IllegalArgumentException unknownRuleValue(String ruleValue) {
        return new IllegalArgumentException(
                "Unknown rule label '" + ruleValue + "'. Use a NamedRule label such as " + NAMED_RULE_EXAMPLE + ".");
    }

    private static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Binds one operator-facing criterion input and concrete implementation to the
     * instance used for a single command execution.
     *
     * @param name       canonical compact name used as the result identifier
     * @param className  resolved {@link AnalysisCriterion} implementation class
     * @param json       canonical lossless descriptor JSON
     * @param expression compact expression when the registry can render one
     * @param criterion  resolved analysis criterion instance
     * @since 0.23.1
     */
    record CriterionSpec(String name, String className, String json, String expression, AnalysisCriterion criterion) {
    }

    /**
     * Validated command inputs for one forecast state/projection evaluation.
     *
     * @param stateModel              EWMA, rough-volatility, or change-point state
     * @param target                  state, cumulative return, or terminal price
     *                                output
     * @param projectionModel         Monte Carlo or analog return projection
     * @param calibration             optional conformal calibration
     * @param priceModel              empirical or lognormal price conversion
     * @param index                   decision index, or {@code null} for the series
     *                                end
     * @param horizon                 positive forecast horizon in bars
     * @param samples                 Monte Carlo terminal path count
     * @param lookbackBars            historical shock lookback in bars
     * @param seed                    deterministic simulation seed
     * @param shockModel              simulated shock source
     * @param volatilityMode          within-path volatility behavior
     * @param volatilityDecay         EWMA decay for within-path variance updates
     * @param neighborCount           maximum analog neighbor count
     * @param minimumNeighborCount    minimum usable analog neighbors
     * @param standardizeFeatures     whether analog features are standardized
     * @param coverage                conformal target coverage
     * @param calibrationWindow       conformal rolling window
     * @param minimumCalibrationCount minimum matured conformal rows
     * @param quantiles               reported quantile probabilities
     * @since 0.23.1
     */
    record ForecastRequest(String stateModel, String target, String projectionModel, String calibration,
            String priceModel, Integer index, int horizon, int samples, int lookbackBars, long seed, String shockModel,
            String volatilityMode, double volatilityDecay, int neighborCount, int minimumNeighborCount,
            boolean standardizeFeatures, double coverage, int calibrationWindow, int minimumCalibrationCount,
            List<Double> quantiles) {

        ForecastRequest {
            quantiles = List.copyOf(Objects.requireNonNull(quantiles, "quantiles"));
        }
    }

    private record ProjectionSelection(ForecastProjectionIndicator indicator, String priceModel) {
    }

    /**
     * Validated position-sizing selection and its operator-facing configuration.
     *
     * @param mode             fixed, balance, or Kelly sizing mode
     * @param capital          configured starting capital, when present
     * @param stakeAmount      fixed per-entry amount, when present
     * @param winProbability   Kelly win probability, when present
     * @param payoffRatio      Kelly payoff ratio, when present
     * @param kellyCoefficient Kelly fraction multiplier, when present
     * @param positionSizer    configured ta4j position sizer
     * @since 0.23.1
     */
    record PositionSizingSpec(String mode, String capital, String stakeAmount, String winProbability,
            String payoffRatio, String kellyCoefficient, PositionSizer positionSizer) {
    }

    /**
     * Represents the successfully resolved strategies together with any skipped
     * invalid inputs.
     *
     * @param strategies        valid strategies that can be executed
     * @param invalidStrategies descriptive messages for rejected inputs
     * @since 0.23.1
     */
    record ResolvedStrategies(List<Strategy> strategies, List<String> invalidStrategies) {
    }

    /**
     * Represents a successfully reconstructed numeric indicator together with the
     * canonical JSON used for execution metadata.
     *
     * @param indicator numeric indicator instance
     * @param json      canonical serialized indicator JSON
     * @param typeName  runtime type name
     * @since 0.23.1
     */
    record ResolvedIndicator(Indicator<Num> indicator, String json, String typeName) {
    }
}
