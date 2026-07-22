/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ta4j.cli.performance.PerformanceComparison;
import org.ta4j.cli.performance.PerformanceExperimentRunner;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.walkforward.WalkForwardConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.AutoComplete;
import picocli.CommandLine;
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
final class CliCommands {

    private CliCommands() {
    }

    /**
     * Common execution context for all concrete CLI workflow commands.
     *
     * @since 0.23.1
     */
    @Command
    abstract static class WorkflowCommand implements Callable<Integer> {

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

        final InputStream in() {
            return ((Ta4jCli) spec.root().userObject()).input();
        }
    }

    @Command
    abstract static class GroupCommand implements Runnable {

        @Spec
        private CommandSpec spec;

        @Override
        public final void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
    }

    @Command(name = "strategy", description = "Run and tune strategy workflows.", mixinStandardHelpOptions = true, subcommands = {
            StrategyBacktestCommand.class, StrategyWalkForwardCommand.class, StrategySweepCommand.class })
    static final class StrategyCommand extends GroupCommand {
    }

    @Command(name = "indicator", description = "Exercise serialized indicators.", mixinStandardHelpOptions = true, subcommands = IndicatorTestCommand.class)
    static final class IndicatorCommand extends GroupCommand {
    }

    @Command(name = "rule", description = "Exercise serialized or named rules.", mixinStandardHelpOptions = true, subcommands = RuleTestCommand.class)
    static final class RuleCommand extends GroupCommand {
    }

    @Command(name = "forecast", description = "Inspect return state and produce deterministic forecasts.", mixinStandardHelpOptions = true, subcommands = ForecastRunCommand.class)
    static final class ForecastCommand extends GroupCommand {
    }

    @Command(name = "performance", description = "Run and compare reproducible performance experiments.", mixinStandardHelpOptions = true, subcommands = {
            PerformanceRunCommand.class, PerformanceCompareCommand.class })
    static final class PerformanceCommand extends GroupCommand {
    }

    @Command(name = "backtest", description = "Run strategies against one dataset.", mixinStandardHelpOptions = true)
    static final class StrategyBacktestCommand extends StrategyBacktestWorkflow {
    }

    @Command(name = "walk-forward", description = "Run leakage-safe walk-forward evaluation.", mixinStandardHelpOptions = true)
    static final class StrategyWalkForwardCommand extends StrategyWalkForwardWorkflow {
    }

    @Command(name = "sweep", description = "Rank a bounded strategy parameter grid.", mixinStandardHelpOptions = true)
    static final class StrategySweepCommand extends StrategySweepWorkflow {
    }

    @Command(name = "test", description = "Backtest a serialized indicator as a signal.", mixinStandardHelpOptions = true)
    static final class IndicatorTestCommand extends IndicatorTestWorkflow {
    }

    @Command(name = "test", description = "Backtest serialized or named entry and exit rules.", mixinStandardHelpOptions = true)
    static final class RuleTestCommand extends RuleTestWorkflow {
    }

    @Command(name = "run", description = "Inspect a return state or produce a forecast.", mixinStandardHelpOptions = true)
    static final class ForecastRunCommand extends ForecastRunWorkflow {
    }

    @Command(name = "run", description = "Run a named performance experiment.", mixinStandardHelpOptions = true)
    static final class PerformanceRunCommand extends WorkflowCommand {

        @Option(names = "--experiment", defaultValue = "kalman-filter", paramLabel = "<id>", description = "Experiment id.")
        String experimentId;

        @Option(names = "--bar-counts", split = ",", defaultValue = "1000,5000,10000,50000", paramLabel = "<count>", description = "Positive bar counts.")
        List<Integer> barCounts;

        @Option(names = "--scenarios", split = ",", arity = "1..*", paramLabel = "<id>", description = "Scenario ids, or omit for experiment defaults.")
        List<String> scenarioIds = List.of();

        @Option(names = "--repetitions", defaultValue = "5", paramLabel = "<count>", description = "Measured repetitions per cell.")
        int repetitions;

        @Option(names = "--warmups", defaultValue = "1", paramLabel = "<count>", description = "Warmup repetitions per cell.")
        int warmups;

        @Option(names = "--output-dir", paramLabel = "<dir>", description = "Artifact directory.")
        Path outputDir;

        @Option(names = "--profile", description = "Emit profiler hints in performance.json.")
        boolean profile;

        @Override
        public Integer call() throws IOException {
            Optional<Path> requestedOutputDir = outputDir == null ? Optional.empty() : Optional.of(outputDir);
            PerformanceExperimentRunner.RunRequest request = new PerformanceExperimentRunner.RunRequest(experimentId,
                    barCounts, scenarioIds, repetitions, warmups, requestedOutputDir, profile);
            PerformanceExperimentRunner.RunArtifacts artifacts = PerformanceExperimentRunner.run(request);
            Map<String, Object> response = CliSupport.buildResponse("performance run");
            Map<String, Object> result = CliSupport.result(response);
            result.put("outputDir", artifacts.outputDir().toAbsolutePath().normalize().toString());
            result.put("performanceFile",
                    artifacts.outputDir().resolve("performance.json").toAbsolutePath().normalize().toString());
            result.put("summaryFile",
                    artifacts.outputDir().resolve("summary.md").toAbsolutePath().normalize().toString());
            out().println(CliSupport.toJson(response));
            return 0;
        }
    }

    @Command(name = "compare", description = "Compare two performance experiment runs.", mixinStandardHelpOptions = true)
    static final class PerformanceCompareCommand extends WorkflowCommand {

        @Option(names = "--base-dir", required = true, paramLabel = "<dir>", description = "Baseline artifact directory.")
        Path baseDir;

        @Option(names = "--candidate-dir", required = true, paramLabel = "<dir>", description = "Candidate artifact directory.")
        Path candidateDir;

        @Option(names = "--output-dir", required = true, paramLabel = "<dir>", description = "Comparison artifact directory.")
        Path outputDir;

        @Option(names = "--max-regression-pct", defaultValue = "5", paramLabel = "<pct>", description = "Allowed median runtime regression percentage.")
        double maxRegressionPct;

        @Override
        public Integer call() throws IOException {
            if (maxRegressionPct < 0d) {
                throw new IllegalArgumentException("--max-regression-pct must be non-negative");
            }
            JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, maxRegressionPct);
            Map<String, Object> response = CliSupport.buildResponse("performance compare");
            Map<String, Object> result = CliSupport.result(response);
            result.put("outputDir", outputDir.toAbsolutePath().normalize().toString());
            result.put("comparisonFile", outputDir.resolve("comparison.json").toAbsolutePath().normalize().toString());
            result.put("summaryFile", outputDir.resolve("summary.md").toAbsolutePath().normalize().toString());
            result.put("comparison", comparison);
            out().println(CliSupport.toJson(response));
            return 0;
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

        @Option(names = "--data-format", paramLabel = "<format>", description = "Input format for --data-file -: csv or json.")
        String dataFormat;

        @Option(names = "--timeframe", paramLabel = "<duration>", description = "Resample bars to 1m, 5m, 15m, 1h, 4h, 1d, or ISO-8601 duration.")
        String timeframe;

        @Option(names = "--from-date", paramLabel = "<date>", description = "Inclusive start date or instant.")
        String fromDate;

        @Option(names = "--to-date", paramLabel = "<date>", description = "Inclusive end date or instant.")
        String toDate;

        BarSeries loadSeries(InputStream input) {
            return CliSupport.loadSeries(dataFile, dataFormat, input, timeframe, fromDate, toDate);
        }
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

        @Option(names = "--reproducible", description = "Omit timestamps, paths, and timing metadata from JSON output.")
        boolean reproducible;
    }

    /**
     * Shared analysis-criterion options.
     *
     * @since 0.23.1
     */
    @Command
    static final class CriteriaOptions {

        @Option(names = { "--criterion",
                "--criteria" }, arity = "1..*", paramLabel = "<criterion>", description = "Repeatable named criterion expression or AnalysisCriterion class name.")
        List<String> criteria = new ArrayList<>();

        @Option(names = "--criterion-json", paramLabel = "<json>", description = "Repeatable canonical analysis-criterion JSON descriptor.")
        List<String> criterionJson = new ArrayList<>();

        @Option(names = "--criteria-file", paramLabel = "<path>", description = "Repeatable criterion JSON object or array file.")
        List<String> criteriaFiles = new ArrayList<>();

        List<CliSupport.CriterionSpec> resolve(List<String> defaults) {
            return CliSupport.resolveCriteria(criteria, criterionJson, criteriaFiles, defaults);
        }
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

        @Option(names = "--invalid-input", defaultValue = "fail", paramLabel = "<policy>", description = "Invalid batch input policy: fail or skip.")
        String invalidInputPolicy;

        void enforceInvalidInputPolicy(CliSupport.ResolvedStrategies resolvedStrategies, PrintWriter err) {
            CliSupport.enforceInvalidStrategyPolicy(resolvedStrategies.invalidStrategies(), invalidInputPolicy, err);
        }
    }

    /**
     * Emits a machine-readable catalog of supported aliases and execution models.
     *
     * @since 0.23.1
     */
    @Command(name = "catalog", description = "List supported aliases, models, and JSON schema details.", mixinStandardHelpOptions = true)
    static final class CatalogCommand extends WorkflowCommand {

        @Override
        public Integer call() {
            out().println(CliSupport.toJson(CliSupport.buildCatalogReport()));
            return 0;
        }
    }

    /**
     * Generates shell completion from the live picocli command model.
     *
     * @since 0.23.1
     */
    @Command(name = "completion", description = "Generate a shell completion script.", mixinStandardHelpOptions = true)
    static final class CompletionCommand extends WorkflowCommand {

        @Option(names = "--shell", defaultValue = "bash", paramLabel = "<shell>", description = "Completion shell: bash.")
        String shell;

        @Override
        public Integer call() {
            if (!"bash".equalsIgnoreCase(shell.trim())) {
                throw new IllegalArgumentException("Unsupported completion shell '" + shell + "'. Use bash.");
            }
            CommandLine commandLine = new CommandLine(new Ta4jCli(InputStream.nullInputStream()));
            commandLine.setCaseInsensitiveEnumValuesAllowed(true);
            out().print(AutoComplete.bash("ta4j-cli", commandLine));
            return 0;
        }
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
    abstract static class StrategyBacktestWorkflow extends WorkflowCommand {

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
            List<CliSupport.CriterionSpec> resolvedCriteria = criteria.resolve(CliSupport.DEFAULT_BACKTEST_CRITERIA);

            BarSeries series = data.loadSeries(in());
            CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyInput.strategy,
                    strategyInput.strategyJsonFile, strategyInput.strategies, strategyInput.strategiesJsonFile,
                    unstableBars, series);
            strategyInput.enforceInvalidInputPolicy(resolvedStrategies, err());
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
                    chartPath, artifacts.reproducible);
            Map<String, Object> payload = CliSupport.result(response);
            CliSupport.putRunMetadata(response, "runtime",
                    CliSupport.backtestRuntimeToMap(CliSupport.aggregateBacktestRuntimes(runtimeReports)));
            payload.put("strategyCount", resolvedStrategies.strategies().size());
            payload.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
            payload.put("invalidStrategies",
                    CliSupport.outputInvalidStrategies(resolvedStrategies.invalidStrategies(), artifacts.reproducible,
                            strategyInput.strategyJsonFile, strategyInput.strategiesJsonFile));
            payload.put("statement", statementMaps.getFirst());
            payload.put("statements", statementMaps);
            CliSupport.markPartial(response, resolvedStrategies.invalidStrategies());
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
    abstract static class StrategyWalkForwardWorkflow extends WorkflowCommand {

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
            List<CliSupport.CriterionSpec> resolvedCriteria = criteria
                    .resolve(CliSupport.DEFAULT_WALK_FORWARD_CRITERIA);

            BarSeries series = data.loadSeries(in());
            WalkForwardConfig config = walkForward.build(series);
            CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyInput.strategy,
                    strategyInput.strategyJsonFile, strategyInput.strategies, strategyInput.strategiesJsonFile,
                    unstableBars, series);
            strategyInput.enforceInvalidInputPolicy(resolvedStrategies, err());

            BacktestExecutor executor = CliSupport.buildExecutor(series, execution.executionModel, execution.commission,
                    execution.borrowRate, execution.borrowSide);
            CliSupport.PositionSizingSpec positionSizing = execution.resolvePositionSizing(series);

            List<Map<String, Object>> resultEntries = new ArrayList<>(resolvedStrategies.strategies().size());
            List<Map<String, Object>> runtimeEntries = new ArrayList<>(resolvedStrategies.strategies().size());
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
                resultEntry.put("walkForward", walkForwardMap);
                resultEntries.add(resultEntry);
                Map<String, Object> runtimeEntry = new LinkedHashMap<>();
                runtimeEntry.put("backtest", backtestRuntimeMap);
                runtimeEntry.put("walkForward", CliSupport.walkForwardRuntimeToMap(walkForwardResult.runtimeReport()));
                runtimeEntries.add(runtimeEntry);

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
                    chartPath, artifacts.reproducible);
            Map<String, Object> payload = CliSupport.result(response);
            payload.put("strategyCount", resolvedStrategies.strategies().size());
            payload.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
            payload.put("invalidStrategies",
                    CliSupport.outputInvalidStrategies(resolvedStrategies.invalidStrategies(), artifacts.reproducible,
                            strategyInput.strategyJsonFile, strategyInput.strategiesJsonFile));
            payload.put("backtest", primaryBacktest);
            payload.put("walkForward", primaryWalkForward);
            payload.put("results", resultEntries);
            CliSupport.putRunMetadata(response, "backtestRuntime", primaryBacktestRuntime);
            CliSupport.putRunMetadata(response, "results", runtimeEntries);
            CliSupport.markPartial(response, resolvedStrategies.invalidStrategies());
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
    abstract static class StrategySweepWorkflow extends WorkflowCommand {

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
            List<CliSupport.CriterionSpec> resolvedCriteria = criteria.resolve(CliSupport.DEFAULT_SWEEP_CRITERIA);

            BarSeries series = data.loadSeries(in());
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
                    chartPath, artifacts.reproducible);
            Map<String, Object> payload = CliSupport.result(response);
            payload.put("candidateCount", strategies.size());
            payload.put("topK", topK);
            payload.put("leaderboard", leaderboard);
            CliSupport.putRunMetadata(response, "runtime",
                    CliSupport.backtestRuntimeToMap(sweepResult.runtimeReport()));
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
    abstract static class ForecastRunWorkflow extends WorkflowCommand {

        @Mixin
        DataOptions data = new DataOptions();

        @Option(names = "--state-model", defaultValue = "ewma", paramLabel = "<model>", description = "Return state: ewma, rough-volatility, or change-point.")
        String stateModel;

        @Option(names = "--target", defaultValue = "price", paramLabel = "<target>", description = "Output target: state, return, or price.")
        String target;

        @Option(names = "--projection-model", defaultValue = "monte-carlo", paramLabel = "<model>", description = "Return projection: monte-carlo or analog.")
        String projectionModel;

        @Option(names = "--calibration", defaultValue = "none", paramLabel = "<mode>", description = "Projection calibration: none or conformal.")
        String calibration;

        @Option(names = "--price-model", defaultValue = "auto", paramLabel = "<model>", description = "Price conversion: auto, empirical, or lognormal.")
        String priceModel;

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

        @Option(names = "--neighbor-count", defaultValue = "30", paramLabel = "<count>", description = "Analog nearest-neighbor count.")
        int neighborCount;

        @Option(names = "--minimum-neighbor-count", defaultValue = "5", paramLabel = "<count>", description = "Analog minimum usable-neighbor count.")
        int minimumNeighborCount;

        @Option(names = "--standardize-features", negatable = true, defaultValue = "true", description = "Standardize analog features over eligible history.")
        boolean standardizeFeatures;

        @Option(names = "--coverage", defaultValue = "0.90", paramLabel = "<probability>", description = "Conformal target coverage in (0, 1).")
        double coverage;

        @Option(names = "--calibration-window", defaultValue = "252", paramLabel = "<count>", description = "Conformal rolling calibration window.")
        int calibrationWindow;

        @Option(names = "--minimum-calibration-count", defaultValue = "30", paramLabel = "<count>", description = "Conformal minimum matured forecast count.")
        int minimumCalibrationCount;

        @Option(names = "--quantiles", split = ",", defaultValue = "0.05,0.5,0.95", paramLabel = "<probability>", description = "Comma-separated quantile probabilities in [0, 1].")
        List<Double> quantiles = new ArrayList<>();

        @Option(names = "--output", paramLabel = "<json-path>", description = "Write JSON output to this file instead of stdout.")
        String output;

        @Option(names = "--reproducible", description = "Omit timestamps and paths from JSON output.")
        boolean reproducible;

        @Override
        public final Integer call() throws IOException {
            validateModelSpecificOptions();
            BarSeries series = data.loadSeries(in());
            Path outputPath = CliSupport.resolveOutputPath(output);
            int resolvedLookbackBars = lookbackBars == null ? Math.max(1, Math.min(252, series.getBarCount() - 1))
                    : lookbackBars;
            int resolvedNeighborCount = optionMatched("--neighbor-count") ? neighborCount
                    : Math.min(neighborCount, resolvedLookbackBars);
            int resolvedMinimumNeighborCount = optionMatched("--minimum-neighbor-count") ? minimumNeighborCount
                    : Math.min(minimumNeighborCount, resolvedNeighborCount);
            CliSupport.ForecastRequest request = new CliSupport.ForecastRequest(stateModel, target, projectionModel,
                    calibration, priceModel, index, horizon, samples, resolvedLookbackBars, seed, shockModel,
                    volatilityMode, volatilityDecay, resolvedNeighborCount, resolvedMinimumNeighborCount,
                    standardizeFeatures, coverage, calibrationWindow, minimumCalibrationCount, quantiles);
            Map<String, Object> response = CliSupport.buildForecastReport(series, data.dataFile, data.timeframe,
                    data.fromDate, data.toDate, request, outputPath, reproducible);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath, out());
            return 0;
        }

        private void validateModelSpecificOptions() {
            String normalizedTarget = target.trim().toLowerCase(Locale.ROOT);
            if ("state".equals(normalizedTarget)) {
                rejectMatchedOptions(
                        List.of("--projection-model", "--calibration", "--price-model", "--horizon", "--samples",
                                "--lookback-bars", "--seed", "--shock-model", "--volatility-mode", "--volatility-decay",
                                "--neighbor-count", "--minimum-neighbor-count", "--standardize-features", "--coverage",
                                "--calibration-window", "--minimum-calibration-count", "--quantiles"),
                        "may only be used with --target return or --target price.");
                return;
            }
            if ("monte-carlo".equalsIgnoreCase(projectionModel.trim())) {
                rejectMatchedOptions(List.of("--neighbor-count", "--minimum-neighbor-count", "--standardize-features"),
                        "may only be used with --projection-model analog.");
            } else if ("analog".equalsIgnoreCase(projectionModel.trim())) {
                rejectMatchedOptions(
                        List.of("--samples", "--seed", "--shock-model", "--volatility-mode", "--volatility-decay"),
                        "may only be used with --projection-model monte-carlo.");
            }
            if ("none".equalsIgnoreCase(calibration.trim())) {
                rejectMatchedOptions(List.of("--coverage", "--calibration-window", "--minimum-calibration-count"),
                        "requires --calibration conformal.");
            }
            if (!"price".equals(normalizedTarget)) {
                rejectMatchedOptions(List.of("--price-model"), "may only be used with --target price.");
            }
        }

        private void rejectMatchedOptions(List<String> options, String guidance) {
            List<String> matched = options.stream().filter(this::optionMatched).toList();
            if (!matched.isEmpty()) {
                throw new IllegalArgumentException(String.join(", ", matched) + " " + guidance);
            }
        }
    }

    /**
     * Implements {@code ta4j-cli indicator test}.
     *
     * @since 0.23.1
     */
    @Command
    abstract static class IndicatorTestWorkflow extends WorkflowCommand {

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
            List<CliSupport.CriterionSpec> resolvedCriteria = criteria
                    .resolve(CliSupport.DEFAULT_INDICATOR_TEST_CRITERIA);

            BarSeries series = data.loadSeries(in());
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
                    chartPath, artifacts.reproducible);
            Map<String, Object> payload = CliSupport.result(response);
            payload.put("indicatorType", resolvedIndicator.typeName());
            payload.put("indicatorJson", JsonParser.parseString(resolvedIndicator.json()));
            payload.put("statement", CliSupport.statementToMap(series, statement, resolvedCriteria));
            CliSupport.putRunMetadata(response, "runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
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
    abstract static class RuleTestWorkflow extends WorkflowCommand {

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
            List<CliSupport.CriterionSpec> resolvedCriteria = criteria.resolve(CliSupport.DEFAULT_RULE_TEST_CRITERIA);

            BarSeries series = data.loadSeries(in());
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
                    chartPath, artifacts.reproducible);
            Map<String, Object> payload = CliSupport.result(response);
            payload.put("entryRuleName", strategy.getEntryRule().getName());
            payload.put("entryRuleJson", JsonParser.parseString(strategy.getEntryRule().toJson()));
            payload.put("exitRuleName", strategy.getExitRule().getName());
            payload.put("exitRuleJson", JsonParser.parseString(strategy.getExitRule().toJson()));
            payload.put("backtest", CliSupport.statementToMap(series, statement, resolvedCriteria));
            payload.put("walkForward", CliSupport.walkForwardToMap(series, walkForwardResult, resolvedCriteria));
            CliSupport.putRunMetadata(response, "backtestRuntime",
                    CliSupport.backtestRuntimeToMap(backtest.runtimeReport()));
            CliSupport.putRunMetadata(response, "walkForwardRuntime",
                    CliSupport.walkForwardRuntimeToMap(walkForwardResult.runtimeReport()));
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
