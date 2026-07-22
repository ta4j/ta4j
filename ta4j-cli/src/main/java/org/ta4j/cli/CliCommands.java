/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.walkforward.WalkForwardConfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Shared picocli command implementations for the grouped ta4j CLI command tree.
 *
 * <p>
 * Nested workflow classes keep parsing, help, and option ownership close to
 * picocli while delegating ta4j execution and artifact shaping to the
 * package-local support layer.
 * </p>
 *
 * @since 0.23.1
 */
public final class CliCommands {

    private CliCommands() {
    }

    /**
     * Common execution context for all concrete CLI workflow commands.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class WorkflowCommand implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        final PrintWriter out() {
            return spec.commandLine().getOut();
        }

        final PrintWriter err() {
            return spec.commandLine().getErr();
        }

        final boolean optionMatched(String optionName) {
            return spec.commandLine().getParseResult().hasMatchedOption(optionName);
        }
    }

    /**
     * Shared local bar-series input options.
     *
     * @since 0.23.1
     */
    @Command
    static final class DataOptions {

        @Option(names = "--data-file", required = true, paramLabel = "<path>", description = "Local CSV or JSON OHLCV file.")
        String dataFile;

        @Option(names = "--timeframe", paramLabel = "<duration>", description = "Resample bars to 1m, 5m, 15m, 1h, 4h, 1d, or ISO-8601 duration.")
        String timeframe;

        @Option(names = "--from-date", paramLabel = "<date>", description = "Inclusive start date or instant.")
        String fromDate;

        @Option(names = "--to-date", paramLabel = "<date>", description = "Inclusive end date or instant.")
        String toDate;
    }

    /**
     * Shared backtest execution options.
     *
     * @since 0.23.1
     */
    @Command
    static final class ExecutionOptions {

        @Option(names = "--execution-model", paramLabel = "<model>", description = "Execution model: next-open or current-close.")
        String executionModel;

        @Option(names = "--capital", paramLabel = "<number>", description = "Portfolio size used as the default stake.")
        String capital;

        @Option(names = "--stake-amount", paramLabel = "<number>", description = "Per-trade amount.")
        String stakeAmount;

        @Option(names = "--position-sizing", paramLabel = "<mode>", description = "Entry sizing: fixed, balance, or kelly.")
        String positionSizing;

        @Option(names = "--win-probability", paramLabel = "<probability>", description = "Kelly win probability in (0, 1).")
        String winProbability;

        @Option(names = "--payoff-ratio", paramLabel = "<ratio>", description = "Kelly average-win to average-loss ratio.")
        String payoffRatio;

        @Option(names = "--kelly-coefficient", paramLabel = "<coefficient>", description = "Kelly fraction multiplier; defaults to 1.")
        String kellyCoefficient;

        @Option(names = "--commission", paramLabel = "<rate>", description = "Non-negative transaction fee rate.")
        String commission;

        @Option(names = "--borrow-rate", paramLabel = "<rate>", description = "Non-negative holding cost rate.")
        String borrowRate;

        @Option(names = "--borrow-side", paramLabel = "<side>", description = "Borrowing-cost side: short, long, or both.")
        String borrowSide;

        final CliSupport.PositionSizingSpec resolvePositionSizing(BarSeries series) {
            return CliSupport.resolvePositionSizing(series, positionSizing, capital, stakeAmount, winProbability,
                    payoffRatio, kellyCoefficient);
        }
    }

    /**
     * Shared output and progress options.
     *
     * @since 0.23.1
     */
    @Command
    static final class ArtifactOptions {

        @Option(names = "--output", paramLabel = "<json-path>", description = "Write JSON output to this file instead of stdout.")
        String output;

        @Option(names = "--chart", paramLabel = "<jpeg-path>", description = "Optional JPEG chart output path.")
        String chart;

        @Option(names = "--progress", description = "Emit bounded progress messages to stderr.")
        boolean progress;
    }

    /**
     * Shared analysis-criterion options.
     *
     * @since 0.23.1
     */
    @Command
    static final class CriteriaOptions {

        @Option(names = "--criteria", arity = "1..*", paramLabel = "<criterion>", description = "Named criterion expressions or AnalysisCriterion class names.")
        List<String> criteria = new ArrayList<>();
    }

    /**
     * Strategy input options shared by strategy execution commands.
     *
     * @since 0.23.1
     */
    @Command
    static final class StrategyInputOptions {

        @Option(names = "--strategy", paramLabel = "<strategy>", description = "One compact strategy expression or NamedStrategy label.")
        String strategy;

        @Option(names = "--strategies", arity = "1..*", paramLabel = "<strategy>", description = "Comma-separated compact expressions or NamedStrategy labels.")
        List<String> strategies = new ArrayList<>();

        @Option(names = "--strategy-json-file", paramLabel = "<path>", description = "Canonical or version 2 Strategy JSON file.")
        String strategyJsonFile;

        @Option(names = "--strategies-json-file", paramLabel = "<path>", description = "JSON array of canonical or version 2 strategies.")
        String strategiesJsonFile;

        @Option(names = "--unstable-bars", paramLabel = "<count>", description = "Override strategy unstable bars.")
        String unstableBars;

        @Option(names = "--param", arity = "1..*", paramLabel = "key=value", description = "Command-specific fixed parameter.")
        List<String> params = new ArrayList<>();
    }

    /**
     * Walk-forward split and ranking options.
     *
     * @since 0.23.1
     */
    @Command
    static final class WalkForwardOptions {

        @Option(names = "--min-train-bars", paramLabel = "<count>", description = "Minimum training bars per fold.")
        String minTrainBars;

        @Option(names = "--test-bars", paramLabel = "<count>", description = "Out-of-sample test bars per fold.")
        String testBars;

        @Option(names = "--step-bars", paramLabel = "<count>", description = "Fold step size in bars.")
        String stepBars;

        @Option(names = "--purge-bars", paramLabel = "<count>", description = "Bars purged between train and test windows.")
        String purgeBars;

        @Option(names = "--embargo-bars", paramLabel = "<count>", description = "Bars embargoed after each test window.")
        String embargoBars;

        @Option(names = "--holdout-bars", paramLabel = "<count>", description = "Final holdout bars.")
        String holdoutBars;

        @Option(names = "--primary-horizon-bars", paramLabel = "<count>", description = "Primary reporting horizon.")
        String primaryHorizonBars;

        @Option(names = "--optimization-top-k", paramLabel = "<count>", description = "Candidate count retained during fold ranking.")
        String optimizationTopK;

        @Option(names = "--seed", paramLabel = "<long>", description = "Deterministic seed.")
        String seed;

        final WalkForwardConfig build(BarSeries series) {
            return CliSupport.buildWalkForwardConfig(series, minTrainBars, testBars, stepBars, purgeBars, embargoBars,
                    holdoutBars, primaryHorizonBars, optimizationTopK, seed);
        }
    }

    /**
     * Implements {@code ta4j-cli strategy backtest}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class StrategyBacktestWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Mixin
        StrategyInputOptions strategyInput = new StrategyInputOptions();

        @Mixin
        ExecutionOptions execution = new ExecutionOptions();

        @Mixin
        CriteriaOptions criteria = new CriteriaOptions();

        @Mixin
        ArtifactOptions artifacts = new ArtifactOptions();

        @Override
        public final Integer call() throws IOException {
            rejectUnsupportedParams("strategy backtest", "strategy", strategyInput.params);
            Integer unstableBars = CliSupport.parseOptionalInteger(strategyInput.unstableBars, "unstable-bars");
            List<CliSupport.CriterionSpec> resolvedCriteria = CliSupport.resolveCriteria(criteria.criteria,
                    CliSupport.DEFAULT_BACKTEST_CRITERIA);

            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyInput.strategy,
                    strategyInput.strategyJsonFile, strategyInput.strategies, strategyInput.strategiesJsonFile,
                    unstableBars, series);
            CliSupport.reportInvalidStrategies(resolvedStrategies.invalidStrategies(), err());
            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);

            List<TradingStatement> statements = new ArrayList<>(resolvedStrategies.strategies().size());
            List<BacktestRuntimeReport> runtimeReports = new ArrayList<>(resolvedStrategies.strategies().size());
            for (int index = 0; index < resolvedStrategies.strategies().size(); index++) {
                Strategy strategy = resolvedStrategies.strategies().get(index);
                BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy),
                        positionSizing.positionSizer(), strategy.getStartingType(),
                        resolvedStrategies.strategies().size() == 1
                                ? CliSupport.progressCallback(artifacts.progress, err(), "strategy backtest")
                                : null);
                statements.add(result.tradingStatements().getFirst());
                runtimeReports.add(result.runtimeReport());
                reportProgress(artifacts.progress && resolvedStrategies.strategies().size() > 1, err(),
                        "strategy backtest", index + 1);
            }

            List<Map<String, Object>> statementMaps = statements.stream()
                    .map(statement -> CliSupport.statementToMap(series, statement, resolvedCriteria))
                    .toList();
            TradingStatement statement = statements.getFirst();
            Path chartPath = CliSupport.saveChart(artifacts.chart, series, statement);
            Path outputPath = CliSupport.resolveOutputPath(artifacts.output);

            Map<String, Object> response = CliSupport.buildCommandMetadata("strategy backtest", series, data.dataFile,
                    data.timeframe, data.fromDate, data.toDate, execution.executionModel, positionSizing,
                    execution.commission, execution.borrowRate, execution.borrowSide, resolvedCriteria, outputPath,
                    chartPath);
            response.put("runtime",
                    CliSupport.backtestRuntimeToMap(CliSupport.aggregateBacktestRuntimes(runtimeReports)));
            response.put("strategyCount", resolvedStrategies.strategies().size());
            response.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
            response.put("invalidStrategies", resolvedStrategies.invalidStrategies());
            response.put("statement", statementMaps.getFirst());
            response.put("statements", statementMaps);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }
    }

    /**
     * Implements {@code ta4j-cli strategy walk-forward}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class StrategyWalkForwardWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Mixin
        StrategyInputOptions strategyInput = new StrategyInputOptions();

        @Mixin
        ExecutionOptions execution = new ExecutionOptions();

        @Mixin
        CriteriaOptions criteria = new CriteriaOptions();

        @Mixin
        WalkForwardOptions walkForward = new WalkForwardOptions();

        @Mixin
        ArtifactOptions artifacts = new ArtifactOptions();

        @Override
        public final Integer call() throws IOException {
            rejectUnsupportedParams("strategy walk-forward", "strategy", strategyInput.params);
            Integer unstableBars = CliSupport.parseOptionalInteger(strategyInput.unstableBars, "unstable-bars");
            List<CliSupport.CriterionSpec> resolvedCriteria = CliSupport.resolveCriteria(criteria.criteria,
                    CliSupport.DEFAULT_WALK_FORWARD_CRITERIA);

            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            WalkForwardConfig config = walkForward.build(series);
            CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyInput.strategy,
                    strategyInput.strategyJsonFile, strategyInput.strategies, strategyInput.strategiesJsonFile,
                    unstableBars, series);
            CliSupport.reportInvalidStrategies(resolvedStrategies.invalidStrategies(), err());

            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);

            List<Map<String, Object>> resultEntries = new ArrayList<>(resolvedStrategies.strategies().size());
            TradingStatement primaryStatement = null;
            Map<String, Object> primaryBacktest = null;
            Map<String, Object> primaryBacktestRuntime = null;
            Map<String, Object> primaryWalkForward = null;
            for (int index = 0; index < resolvedStrategies.strategies().size(); index++) {
                Strategy strategy = resolvedStrategies.strategies().get(index);
                BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy),
                        positionSizing.positionSizer(), strategy.getStartingType());
                StrategyWalkForwardExecutionResult walkForwardResult = executor.executeWalkForward(strategy,
                        positionSizing.positionSizer(), strategy.getStartingType(), config,
                        resolvedStrategies.strategies().size() == 1
                                ? CliSupport.progressCallback(artifacts.progress, err(), "strategy walk-forward")
                                : null);

                TradingStatement statement = backtest.tradingStatements().getFirst();
                Map<String, Object> backtestMap = CliSupport.statementToMap(series, statement, resolvedCriteria);
                Map<String, Object> backtestRuntimeMap = CliSupport.backtestRuntimeToMap(backtest.runtimeReport());
                Map<String, Object> walkForwardMap = CliSupport.walkForwardToMap(series, walkForwardResult,
                        resolvedCriteria);
                Map<String, Object> resultEntry = new LinkedHashMap<>();
                resultEntry.put("backtest", backtestMap);
                resultEntry.put("backtestRuntime", backtestRuntimeMap);
                resultEntry.put("walkForward", walkForwardMap);
                resultEntries.add(resultEntry);

                if (primaryStatement == null) {
                    primaryStatement = statement;
                    primaryBacktest = backtestMap;
                    primaryBacktestRuntime = backtestRuntimeMap;
                    primaryWalkForward = walkForwardMap;
                }
                reportProgress(artifacts.progress && resolvedStrategies.strategies().size() > 1, err(),
                        "strategy walk-forward", index + 1);
            }

            Path chartPath = CliSupport.saveChart(artifacts.chart, series, primaryStatement);
            Path outputPath = CliSupport.resolveOutputPath(artifacts.output);
            Map<String, Object> response = CliSupport.buildCommandMetadata("strategy walk-forward", series,
                    data.dataFile, data.timeframe, data.fromDate, data.toDate, execution.executionModel, positionSizing,
                    execution.commission, execution.borrowRate, execution.borrowSide, resolvedCriteria, outputPath,
                    chartPath);
            response.put("strategyCount", resolvedStrategies.strategies().size());
            response.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
            response.put("invalidStrategies", resolvedStrategies.invalidStrategies());
            response.put("backtest", primaryBacktest);
            response.put("backtestRuntime", primaryBacktestRuntime);
            response.put("walkForward", primaryWalkForward);
            response.put("results", resultEntries);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }
    }

    /**
     * Implements {@code ta4j-cli strategy sweep}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class StrategySweepWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Mixin
        ExecutionOptions execution = new ExecutionOptions();

        @Mixin
        CriteriaOptions criteria = new CriteriaOptions();

        @Mixin
        ArtifactOptions artifacts = new ArtifactOptions();

        @Option(names = "--unstable-bars", paramLabel = "<count>", description = "Override strategy unstable bars.")
        String unstableBars;

        @Option(names = "--param", arity = "1..*", paramLabel = "key=value", description = "Fixed parameter for every candidate.")
        List<String> params = new ArrayList<>();

        @Option(names = "--param-grid", arity = "1..*", paramLabel = "key=v1,v2", description = "Candidate grid dimension.")
        List<String> paramGrids = new ArrayList<>();

        @Option(names = "--top-k", paramLabel = "<count>", description = "Number of ranked candidates to keep.")
        String topKToken;

        @Override
        public final Integer call() throws IOException {
            Integer parsedUnstableBars = CliSupport.parseOptionalInteger(unstableBars, "unstable-bars");
            Integer parsedTopK = CliSupport.parseOptionalInteger(topKToken, "top-k");
            if (parsedTopK != null && parsedTopK <= 0) {
                throw new IllegalArgumentException("--top-k must be greater than zero.");
            }
            int topK = parsedTopK == null ? 5 : parsedTopK;
            List<CliSupport.CriterionSpec> resolvedCriteria = CliSupport.resolveCriteria(criteria.criteria,
                    CliSupport.DEFAULT_SWEEP_CRITERIA);

            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            List<Strategy> strategies = CliSupport.buildSweepStrategies(params, paramGrids, parsedUnstableBars, series);
            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);

            BacktestExecutionResult sweepResult = executor.executeAndKeepTopK(strategies,
                    positionSizing.positionSizer(), strategies.getFirst().getStartingType(),
                    resolvedCriteria.getFirst().criterion(), topK,
                    CliSupport.progressCallback(artifacts.progress, err(), "strategy sweep"));
            TradingStatement topStatement = sweepResult.tradingStatements().isEmpty() ? null
                    : sweepResult.tradingStatements().getFirst();
            Path chartPath = topStatement == null ? null : CliSupport.saveChart(artifacts.chart, series, topStatement);
            Path outputPath = CliSupport.resolveOutputPath(artifacts.output);

            List<Map<String, Object>> leaderboard = sweepResult.tradingStatements()
                    .stream()
                    .map(statement -> CliSupport.statementToMap(series, statement, resolvedCriteria))
                    .toList();

            Map<String, Object> response = CliSupport.buildCommandMetadata("strategy sweep", series, data.dataFile,
                    data.timeframe, data.fromDate, data.toDate, execution.executionModel, positionSizing,
                    execution.commission, execution.borrowRate, execution.borrowSide, resolvedCriteria, outputPath,
                    chartPath);
            response.put("candidateCount", strategies.size());
            response.put("topK", topK);
            response.put("runtime", CliSupport.backtestRuntimeToMap(sweepResult.runtimeReport()));
            response.put("leaderboard", leaderboard);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }
    }

    /**
     * Implements {@code ta4j-cli forecast run}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class ForecastRunWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Option(names = "--state-model", defaultValue = "ewma", paramLabel = "<model>", description = "Return state: ewma, rough-volatility, or change-point.")
        String stateModel;

        @Option(names = "--target", defaultValue = "price", paramLabel = "<target>", description = "Output target: state, return, or price.")
        String target;

        @Option(names = "--index", paramLabel = "<index>", description = "Decision index; defaults to the series end.")
        Integer index;

        @Option(names = "--horizon", defaultValue = "1", paramLabel = "<bars>", description = "Positive forecast horizon in bars.")
        int horizon;

        @Option(names = "--samples", defaultValue = "1000", paramLabel = "<count>", description = "Monte Carlo terminal path count.")
        int samples;

        @Option(names = "--lookback-bars", paramLabel = "<count>", description = "Historical shock lookback; defaults to up to 252 available returns.")
        Integer lookbackBars;

        @Option(names = "--seed", defaultValue = "42", paramLabel = "<long>", description = "Deterministic simulation seed.")
        long seed;

        @Option(names = "--shock-model", defaultValue = "standardized-empirical", paramLabel = "<model>", description = "Shocks: historical-bootstrap, standardized-empirical, or normal.")
        String shockModel;

        @Option(names = "--volatility-mode", defaultValue = "constant", paramLabel = "<mode>", description = "Within-path volatility: constant or ewma.")
        String volatilityMode;

        @Option(names = "--volatility-decay", defaultValue = "0.94", paramLabel = "<factor>", description = "EWMA volatility decay in (0, 1).")
        double volatilityDecay;

        @Option(names = "--quantiles", split = ",", defaultValue = "0.05,0.5,0.95", paramLabel = "<probability>", description = "Comma-separated quantile probabilities in [0, 1].")
        List<Double> quantiles = new ArrayList<>();

        @Option(names = "--output", paramLabel = "<json-path>", description = "Write JSON output to this file instead of stdout.")
        String output;

        @Override
        public final Integer call() throws IOException {
            rejectProjectionOptionsForStateTarget();
            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            Path outputPath = CliSupport.resolveOutputPath(output);
            int resolvedLookbackBars = lookbackBars == null ? Math.max(1, Math.min(252, series.getBarCount() - 1))
                    : lookbackBars;
            CliSupport.ForecastRequest request = new CliSupport.ForecastRequest(stateModel, target, index, horizon,
                    samples, resolvedLookbackBars, seed, shockModel, volatilityMode, volatilityDecay, quantiles);
            Map<String, Object> response = CliSupport.buildForecastReport(series, data.dataFile, data.timeframe,
                    data.fromDate, data.toDate, request, outputPath);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }

        private void rejectProjectionOptionsForStateTarget() {
            if (!"state".equalsIgnoreCase(target.trim())) {
                return;
            }
            List<String> projectionOptions = List
                    .of("--samples", "--lookback-bars", "--seed", "--shock-model", "--volatility-mode",
                            "--volatility-decay", "--quantiles")
                    .stream()
                    .filter(this::optionMatched)
                    .toList();
            if (!projectionOptions.isEmpty()) {
                throw new IllegalArgumentException(String.join(", ", projectionOptions)
                        + " may only be used with --target return or --target price.");
            }
        }
    }

    /**
     * Implements {@code ta4j-cli indicator test}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class IndicatorTestWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Mixin
        ExecutionOptions execution = new ExecutionOptions();

        @Mixin
        CriteriaOptions criteria = new CriteriaOptions();

        @Mixin
        ArtifactOptions artifacts = new ArtifactOptions();

        @Option(names = "--indicator", paramLabel = "<indicator>", description = "Compact numeric indicator expression or serialized JSON.")
        String indicatorJson;

        @Option(names = "--indicator-json-file", paramLabel = "<path>", description = "Serialized numeric Indicator JSON file.")
        String indicatorJsonFile;

        @Option(names = "--unstable-bars", paramLabel = "<count>", description = "Override strategy unstable bars.")
        String unstableBars;

        @Option(names = "--entry-below", paramLabel = "<number>", description = "Enter when indicator is below this value.")
        String entryBelow;

        @Option(names = "--entry-above", paramLabel = "<number>", description = "Enter when indicator is above this value.")
        String entryAbove;

        @Option(names = "--exit-below", paramLabel = "<number>", description = "Exit when indicator is below this value.")
        String exitBelow;

        @Option(names = "--exit-above", paramLabel = "<number>", description = "Exit when indicator is above this value.")
        String exitAbove;

        @Option(names = "--param", arity = "1..*", paramLabel = "key=value", description = "Unsupported for indicator inputs.")
        List<String> params = new ArrayList<>();

        @Override
        public final Integer call() throws IOException {
            rejectUnsupportedParams("indicator test", "indicator", params);
            Integer parsedUnstableBars = CliSupport.parseOptionalInteger(unstableBars, "unstable-bars");
            List<CliSupport.CriterionSpec> resolvedCriteria = CliSupport.resolveCriteria(criteria.criteria,
                    CliSupport.DEFAULT_INDICATOR_TEST_CRITERIA);

            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            CliSupport.ResolvedIndicator resolvedIndicator = CliSupport.resolveIndicator(indicatorJson,
                    indicatorJsonFile, series);
            Strategy strategy = CliSupport.buildIndicatorTestStrategy(indicatorJson, indicatorJsonFile,
                    parsedUnstableBars, entryBelow, entryAbove, exitBelow, exitAbove, series);
            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);
            BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy),
                    positionSizing.positionSizer(), strategy.getStartingType(),
                    CliSupport.progressCallback(artifacts.progress, err(), "indicator test"));
            TradingStatement statement = result.tradingStatements().getFirst();
            Path chartPath = CliSupport.saveChart(artifacts.chart, series, statement);
            Path outputPath = CliSupport.resolveOutputPath(artifacts.output);

            Map<String, Object> response = CliSupport.buildCommandMetadata("indicator test", series, data.dataFile,
                    data.timeframe, data.fromDate, data.toDate, execution.executionModel, positionSizing,
                    execution.commission, execution.borrowRate, execution.borrowSide, resolvedCriteria, outputPath,
                    chartPath);
            response.put("indicatorType", resolvedIndicator.typeName());
            response.put("indicatorJson", resolvedIndicator.json());
            response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
            response.put("statement", CliSupport.statementToMap(series, statement, resolvedCriteria));
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }
    }

    /**
     * Implements {@code ta4j-cli rule test}.
     *
     * @since 0.23.1
     */
    @Command
    public abstract static class RuleTestWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Mixin
        ExecutionOptions execution = new ExecutionOptions();

        @Mixin
        CriteriaOptions criteria = new CriteriaOptions();

        @Mixin
        WalkForwardOptions walkForward = new WalkForwardOptions();

        @Mixin
        ArtifactOptions artifacts = new ArtifactOptions();

        @Option(names = "--entry-rule", paramLabel = "<rule>", description = "Compact entry-rule expression or NamedRule label.")
        String entryRuleLabel;

        @Option(names = "--entry-rule-json-file", paramLabel = "<path>", description = "Serialized entry Rule JSON file.")
        String entryRuleJsonFile;

        @Option(names = "--exit-rule", paramLabel = "<rule>", description = "Compact exit-rule expression or NamedRule label.")
        String exitRuleLabel;

        @Option(names = "--exit-rule-json-file", paramLabel = "<path>", description = "Serialized exit Rule JSON file.")
        String exitRuleJsonFile;

        @Option(names = "--unstable-bars", paramLabel = "<count>", description = "Override strategy unstable bars.")
        String unstableBars;

        @Option(names = "--param", arity = "1..*", paramLabel = "key=value", description = "Unsupported for rule inputs.")
        List<String> params = new ArrayList<>();

        @Override
        public final Integer call() throws IOException {
            rejectUnsupportedParams("rule test", "rule", params);
            Integer parsedUnstableBars = CliSupport.parseOptionalInteger(unstableBars, "unstable-bars");
            List<CliSupport.CriterionSpec> resolvedCriteria = CliSupport.resolveCriteria(criteria.criteria,
                    CliSupport.DEFAULT_RULE_TEST_CRITERIA);

            BarSeries series = CliSupport.loadSeries(data.dataFile, data.timeframe, data.fromDate, data.toDate);
            WalkForwardConfig config = walkForward.build(series);
            Strategy strategy = CliSupport.buildRuleTestStrategy(entryRuleLabel, entryRuleJsonFile, exitRuleLabel,
                    exitRuleJsonFile, parsedUnstableBars, series);
            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);
            BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy),
                    positionSizing.positionSizer(), strategy.getStartingType());
            StrategyWalkForwardExecutionResult walkForwardResult = executor.executeWalkForward(strategy,
                    positionSizing.positionSizer(), strategy.getStartingType(), config,
                    CliSupport.progressCallback(artifacts.progress, err(), "rule test"));

            TradingStatement statement = backtest.tradingStatements().getFirst();
            Path chartPath = CliSupport.saveChart(artifacts.chart, series, statement);
            Path outputPath = CliSupport.resolveOutputPath(artifacts.output);

            Map<String, Object> response = CliSupport.buildCommandMetadata("rule test", series, data.dataFile,
                    data.timeframe, data.fromDate, data.toDate, execution.executionModel, positionSizing,
                    execution.commission, execution.borrowRate, execution.borrowSide, resolvedCriteria, outputPath,
                    chartPath);
            response.put("entryRuleName", strategy.getEntryRule().getName());
            response.put("entryRuleJson", strategy.getEntryRule().toJson());
            response.put("exitRuleName", strategy.getExitRule().getName());
            response.put("exitRuleJson", strategy.getExitRule().toJson());
            response.put("backtest", CliSupport.statementToMap(series, statement, resolvedCriteria));
            response.put("backtestRuntime", CliSupport.backtestRuntimeToMap(backtest.runtimeReport()));
            response.put("walkForward", CliSupport.walkForwardToMap(series, walkForwardResult, resolvedCriteria));
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }
    }

    private static void reportProgress(boolean enabled, PrintWriter err, String label, int completed) {
        if (!enabled) {
            return;
        }
        if (completed == 1 || completed % 25 == 0) {
            err.printf("%s progress: %d%n", label, completed);
            err.flush();
        }
    }

    private static void rejectUnsupportedParams(String command, String inputKind, List<String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        String guidance = switch (inputKind) {
        case "strategy" -> "Encode parameters in compact shorthand, NamedStrategy labels, or serialized strategy JSON.";
        case "indicator" -> "Encode indicator parameters in compact shorthand or serialized indicator JSON.";
        case "rule" -> "Encode rule parameters in compact shorthand, NamedRule labels, or serialized rule JSON.";
        default -> "Use the command-specific structured inputs instead.";
        };
        throw new IllegalArgumentException("The " + command + " command does not accept --param. " + guidance);
    }
}
