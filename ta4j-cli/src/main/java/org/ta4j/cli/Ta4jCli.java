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
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.walkforward.WalkForwardConfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-class ta4j command-line entry point for bounded local workflows.
 *
 * <p>
 * The CLI intentionally keeps the MVP surface narrow: it wraps existing ta4j
 * backtest, walk-forward, reporting, and charting APIs behind deterministic
 * file-based commands instead of introducing a second execution runtime.
 * </p>
 *
 * @since 0.22.7
 */
public final class Ta4jCli {

    private Ta4jCli() {
    }

    /**
     * Executes the CLI.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        try {
            CliArguments arguments = CliArguments.parse(args);
            if ("help".equalsIgnoreCase(arguments.command()) || arguments.flag("help")) {
                out.println(usage());
                out.flush();
                return 0;
            }
            return switch (arguments.command()) {
            case "backtest" -> executeBacktest(arguments, out, err);
            case "walk-forward" -> executeWalkForward(arguments, out, err);
            case "sweep" -> executeSweep(arguments, out, err);
            case "indicator-test" -> executeIndicatorTest(arguments, out, err);
            case "rule-test" -> executeRuleTest(arguments, out, err);
            default -> throw new IllegalArgumentException("Unknown command '" + arguments.command()
                    + "'. Supported commands are backtest, walk-forward, sweep, indicator-test, rule-test.");
            };
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println();
            err.println(usage());
            err.flush();
            return 2;
        } catch (IOException ex) {
            err.println("I/O failure: " + ex.getMessage());
            err.flush();
            return 1;
        }
    }

    private static int executeBacktest(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String strategyLabel = arguments.optional("strategy").orElse(null);
        String strategyJsonFile = arguments.optional("strategy-json-file").orElse(null);
        List<String> strategies = arguments.list("strategies");
        String strategiesJsonFile = arguments.optional("strategies-json-file").orElse(null);
        String timeframe = arguments.optional("timeframe").orElse(null);
        String fromDate = arguments.optional("from-date").orElse(null);
        String toDate = arguments.optional("to-date").orElse(null);
        String executionModel = arguments.optional("execution-model").orElse(null);
        String capital = arguments.optional("capital").orElse(null);
        String stakeAmount = arguments.optional("stake-amount").orElse(null);
        String commission = arguments.optional("commission").orElse(null);
        String borrowRate = arguments.optional("borrow-rate").orElse(null);
        String output = arguments.optional("output").orElse(null);
        String chart = arguments.optional("chart").orElse(null);
        Integer unstableBars = CliSupport.parseOptionalInteger(arguments.optional("unstable-bars").orElse(null),
                "unstable-bars");
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        rejectUnsupportedParams("backtest", params);
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_BACKTEST_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyLabel, strategyJsonFile,
                strategies, strategiesJsonFile, unstableBars, series);
        CliSupport.reportInvalidStrategies(resolvedStrategies.invalidStrategies(), err);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        List<TradingStatement> statements = new ArrayList<>(resolvedStrategies.strategies().size());
        List<BacktestRuntimeReport> runtimeReports = new ArrayList<>(resolvedStrategies.strategies().size());
        for (int index = 0; index < resolvedStrategies.strategies().size(); index++) {
            Strategy strategy = resolvedStrategies.strategies().get(index);
            BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), amount,
                    strategy.getStartingType(),
                    resolvedStrategies.strategies().size() == 1 ? CliSupport.progressCallback(progress, err, "backtest")
                            : null);
            statements.add(result.tradingStatements().getFirst());
            runtimeReports.add(result.runtimeReport());
            reportProgress(progress && resolvedStrategies.strategies().size() > 1, err, "backtest", index + 1);
        }

        List<Map<String, Object>> statementMaps = statements.stream()
                .map(statement -> CliSupport.statementToMap(series, statement, criteria))
                .toList();
        TradingStatement statement = statements.getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);
        Path outputPath = CliSupport.resolveOutputPath(output);

        Map<String, Object> response = CliSupport.buildCommandMetadata("backtest", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath,
                chartPath);
        response.put("runtime", CliSupport.backtestRuntimeToMap(CliSupport.aggregateBacktestRuntimes(runtimeReports)));
        response.put("strategyCount", resolvedStrategies.strategies().size());
        response.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
        response.put("invalidStrategies", resolvedStrategies.invalidStrategies());
        response.put("statement", statementMaps.getFirst());
        response.put("statements", statementMaps);
        CliSupport.writeJson(CliSupport.toJson(response), outputPath, out);
        return 0;
    }

    private static int executeWalkForward(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String strategyLabel = arguments.optional("strategy").orElse(null);
        String strategyJsonFile = arguments.optional("strategy-json-file").orElse(null);
        List<String> strategies = arguments.list("strategies");
        String strategiesJsonFile = arguments.optional("strategies-json-file").orElse(null);
        String timeframe = arguments.optional("timeframe").orElse(null);
        String fromDate = arguments.optional("from-date").orElse(null);
        String toDate = arguments.optional("to-date").orElse(null);
        String executionModel = arguments.optional("execution-model").orElse(null);
        String capital = arguments.optional("capital").orElse(null);
        String stakeAmount = arguments.optional("stake-amount").orElse(null);
        String commission = arguments.optional("commission").orElse(null);
        String borrowRate = arguments.optional("borrow-rate").orElse(null);
        String output = arguments.optional("output").orElse(null);
        String chart = arguments.optional("chart").orElse(null);
        Integer unstableBars = CliSupport.parseOptionalInteger(arguments.optional("unstable-bars").orElse(null),
                "unstable-bars");
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        rejectUnsupportedParams("walk-forward", params);
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_WALK_FORWARD_CRITERIA);

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, arguments);
        arguments.assertNoUnknownOptions();
        CliSupport.ResolvedStrategies resolvedStrategies = CliSupport.resolveStrategies(strategyLabel, strategyJsonFile,
                strategies, strategiesJsonFile, unstableBars, series);
        CliSupport.reportInvalidStrategies(resolvedStrategies.invalidStrategies(), err);

        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        List<Map<String, Object>> resultEntries = new ArrayList<>(resolvedStrategies.strategies().size());
        TradingStatement primaryStatement = null;
        Map<String, Object> primaryBacktest = null;
        Map<String, Object> primaryBacktestRuntime = null;
        Map<String, Object> primaryWalkForward = null;
        for (int index = 0; index < resolvedStrategies.strategies().size(); index++) {
            Strategy strategy = resolvedStrategies.strategies().get(index);
            BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy), amount,
                    strategy.getStartingType());
            StrategyWalkForwardExecutionResult walkForward = executor.executeWalkForward(strategy, amount,
                    strategy.getStartingType(), config,
                    resolvedStrategies.strategies().size() == 1
                            ? CliSupport.progressCallback(progress, err, "walk-forward")
                            : null);

            TradingStatement statement = backtest.tradingStatements().getFirst();
            Map<String, Object> backtestMap = CliSupport.statementToMap(series, statement, criteria);
            Map<String, Object> backtestRuntimeMap = CliSupport.backtestRuntimeToMap(backtest.runtimeReport());
            Map<String, Object> walkForwardMap = CliSupport.walkForwardToMap(series, walkForward, criteria);
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
            reportProgress(progress && resolvedStrategies.strategies().size() > 1, err, "walk-forward", index + 1);
        }

        TradingStatement statement = primaryStatement;
        Path chartPath = CliSupport.saveChart(chart, series, statement);
        Path outputPath = CliSupport.resolveOutputPath(output);

        Map<String, Object> response = CliSupport.buildCommandMetadata("walk-forward", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath,
                chartPath);
        response.put("strategyCount", resolvedStrategies.strategies().size());
        response.put("invalidStrategyCount", resolvedStrategies.invalidStrategies().size());
        response.put("invalidStrategies", resolvedStrategies.invalidStrategies());
        response.put("backtest", primaryBacktest);
        response.put("backtestRuntime", primaryBacktestRuntime);
        response.put("walkForward", primaryWalkForward);
        response.put("results", resultEntries);
        CliSupport.writeJson(CliSupport.toJson(response), outputPath, out);
        return 0;
    }

    private static int executeSweep(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String timeframe = arguments.optional("timeframe").orElse(null);
        String fromDate = arguments.optional("from-date").orElse(null);
        String toDate = arguments.optional("to-date").orElse(null);
        String executionModel = arguments.optional("execution-model").orElse(null);
        String capital = arguments.optional("capital").orElse(null);
        String stakeAmount = arguments.optional("stake-amount").orElse(null);
        String commission = arguments.optional("commission").orElse(null);
        String borrowRate = arguments.optional("borrow-rate").orElse(null);
        String output = arguments.optional("output").orElse(null);
        String chart = arguments.optional("chart").orElse(null);
        Integer unstableBars = CliSupport.parseOptionalInteger(arguments.optional("unstable-bars").orElse(null),
                "unstable-bars");
        String topKToken = arguments.optional("top-k").orElse(null);
        Integer parsedTopK = CliSupport.parseOptionalInteger(topKToken, "top-k");
        int topK = parsedTopK == null ? 5 : parsedTopK;
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        List<String> paramGrids = arguments.list("param-grid");
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_SWEEP_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        List<Strategy> strategies = CliSupport.buildSweepStrategies(params, paramGrids, unstableBars, series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        BacktestExecutionResult sweepResult = executor.executeAndKeepTopK(strategies, amount,
                strategies.getFirst().getStartingType(), criteria.getFirst().criterion(), topK,
                CliSupport.progressCallback(progress, err, "sweep"));
        TradingStatement topStatement = sweepResult.tradingStatements().isEmpty() ? null
                : sweepResult.tradingStatements().getFirst();
        Path chartPath = topStatement == null ? null : CliSupport.saveChart(chart, series, topStatement);
        Path outputPath = CliSupport.resolveOutputPath(output);

        List<Map<String, Object>> leaderboard = sweepResult.tradingStatements()
                .stream()
                .map(statement -> CliSupport.statementToMap(series, statement, criteria))
                .toList();

        Map<String, Object> response = CliSupport.buildCommandMetadata("sweep", series, dataFile, timeframe, fromDate,
                toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath, chartPath);
        response.put("candidateCount", strategies.size());
        response.put("topK", topK);
        response.put("runtime", CliSupport.backtestRuntimeToMap(sweepResult.runtimeReport()));
        response.put("leaderboard", leaderboard);
        CliSupport.writeJson(CliSupport.toJson(response), outputPath, out);
        return 0;
    }

    private static int executeIndicatorTest(CliArguments arguments, PrintWriter out, PrintWriter err)
            throws IOException {
        String dataFile = arguments.require("data-file");
        String indicatorJson = arguments.optional("indicator").orElse(null);
        String indicatorJsonFile = arguments.optional("indicator-json-file").orElse(null);
        String timeframe = arguments.optional("timeframe").orElse(null);
        String fromDate = arguments.optional("from-date").orElse(null);
        String toDate = arguments.optional("to-date").orElse(null);
        String executionModel = arguments.optional("execution-model").orElse(null);
        String capital = arguments.optional("capital").orElse(null);
        String stakeAmount = arguments.optional("stake-amount").orElse(null);
        String commission = arguments.optional("commission").orElse(null);
        String borrowRate = arguments.optional("borrow-rate").orElse(null);
        String output = arguments.optional("output").orElse(null);
        String chart = arguments.optional("chart").orElse(null);
        Integer unstableBars = CliSupport.parseOptionalInteger(arguments.optional("unstable-bars").orElse(null),
                "unstable-bars");
        String entryBelow = arguments.optional("entry-below").orElse(null);
        String entryAbove = arguments.optional("entry-above").orElse(null);
        String exitBelow = arguments.optional("exit-below").orElse(null);
        String exitAbove = arguments.optional("exit-above").orElse(null);
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        rejectUnsupportedParams("indicator-test", params);
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_INDICATOR_TEST_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        CliSupport.ResolvedIndicator resolvedIndicator = CliSupport.resolveIndicator(indicatorJson, indicatorJsonFile,
                series);
        Strategy strategy = CliSupport.buildIndicatorTestStrategy(indicatorJson, indicatorJsonFile, unstableBars,
                entryBelow, entryAbove, exitBelow, exitAbove, series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType(), CliSupport.progressCallback(progress, err, "indicator-test"));
        TradingStatement statement = result.tradingStatements().getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);
        Path outputPath = CliSupport.resolveOutputPath(output);

        Map<String, Object> response = CliSupport.buildCommandMetadata("indicator-test", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath,
                chartPath);
        response.put("indicatorType", resolvedIndicator.typeName());
        response.put("indicatorJson", resolvedIndicator.json());
        response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
        response.put("statement", CliSupport.statementToMap(series, statement, criteria));
        CliSupport.writeJson(CliSupport.toJson(response), outputPath, out);
        return 0;
    }

    private static int executeRuleTest(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String entryRuleLabel = arguments.optional("entry-rule").orElse(null);
        String entryRuleJsonFile = arguments.optional("entry-rule-json-file").orElse(null);
        String exitRuleLabel = arguments.optional("exit-rule").orElse(null);
        String exitRuleJsonFile = arguments.optional("exit-rule-json-file").orElse(null);
        String timeframe = arguments.optional("timeframe").orElse(null);
        String fromDate = arguments.optional("from-date").orElse(null);
        String toDate = arguments.optional("to-date").orElse(null);
        String executionModel = arguments.optional("execution-model").orElse(null);
        String capital = arguments.optional("capital").orElse(null);
        String stakeAmount = arguments.optional("stake-amount").orElse(null);
        String commission = arguments.optional("commission").orElse(null);
        String borrowRate = arguments.optional("borrow-rate").orElse(null);
        String output = arguments.optional("output").orElse(null);
        String chart = arguments.optional("chart").orElse(null);
        Integer unstableBars = CliSupport.parseOptionalInteger(arguments.optional("unstable-bars").orElse(null),
                "unstable-bars");
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        rejectUnsupportedParams("rule-test", params);
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_RULE_TEST_CRITERIA);

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, arguments);
        arguments.assertNoUnknownOptions();

        Strategy strategy = CliSupport.buildRuleTestStrategy(entryRuleLabel, entryRuleJsonFile, exitRuleLabel,
                exitRuleJsonFile, unstableBars, series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);
        BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType());
        StrategyWalkForwardExecutionResult walkForward = executor.executeWalkForward(strategy, amount,
                strategy.getStartingType(), config, CliSupport.progressCallback(progress, err, "rule-test"));

        TradingStatement statement = backtest.tradingStatements().getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);
        Path outputPath = CliSupport.resolveOutputPath(output);

        Map<String, Object> response = CliSupport.buildCommandMetadata("rule-test", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath,
                chartPath);
        response.put("entryRuleName", strategy.getEntryRule().getName());
        response.put("entryRuleJson", strategy.getEntryRule().toJson());
        response.put("exitRuleName", strategy.getExitRule().getName());
        response.put("exitRuleJson", strategy.getExitRule().toJson());
        response.put("backtest", CliSupport.statementToMap(series, statement, criteria));
        response.put("backtestRuntime", CliSupport.backtestRuntimeToMap(backtest.runtimeReport()));
        response.put("walkForward", CliSupport.walkForwardToMap(series, walkForward, criteria));
        CliSupport.writeJson(CliSupport.toJson(response), outputPath, out);
        return 0;
    }

    private static String usage() {
        return """
                Usage:
                  ta4j-cli backtest --data-file <path> <strategy-input> [options]
                  ta4j-cli walk-forward --data-file <path> <strategy-input> [options]
                  ta4j-cli sweep --data-file <path> --param-grid fast=5,10 --param-grid slow=20,50 [options]
                  ta4j-cli indicator-test --data-file <path> (--indicator <json> | --indicator-json-file <path>) [options]
                  ta4j-cli rule-test --data-file <path> <entry-rule-input> <exit-rule-input> [options]

                Common options:
                  --timeframe 1m|5m|15m|1h|4h|1d|PT...
                  --from-date YYYY-MM-DD|ISO-INSTANT
                  --to-date YYYY-MM-DD|ISO-INSTANT
                  --execution-model next-open|current-close
                  --capital <number>
                  --stake-amount <number>
                  --commission <rate>
                  --borrow-rate <rate>
                  --criteria fqcn[,fqcn...]
                  --output <json-path>
                  --chart <jpeg-path>
                  --progress
                  --unstable-bars <count>

                Strategy options:
                  --strategy <named-strategy-label>
                  --strategies <label[,label...]>
                  --strategy-json-file <path>
                  --strategies-json-file <path>
                  NamedStrategy labels: <SimpleClassName>_<param1>_<param2>... (for example DayOfWeekStrategy_MONDAY_FRIDAY)
                  You may combine strategy inputs. Invalid entries are reported and skipped when at least one valid strategy remains.

                Sweep options:
                  Fixed to the bounded sma-crossover template
                  --param key=value
                  --param-grid key=v1,v2,...
                  --top-k <count>

                Indicator-test options:
                  --indicator <serialized-indicator-json>
                  --indicator-json-file <path>
                  --entry-below <number> | --entry-above <number>
                  --exit-below <number> | --exit-above <number>

                Rule-test options:
                  --entry-rule <named-rule-label> | --entry-rule-json-file <path>
                  --exit-rule <named-rule-label> | --exit-rule-json-file <path>
                  --min-train-bars, --test-bars, --step-bars, --purge-bars, --embargo-bars, --holdout-bars
                  --primary-horizon-bars, --optimization-top-k, --seed
                """;
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

    private static void rejectUnsupportedParams(String command, List<String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        String guidance = switch (command) {
        case "backtest", "walk-forward" ->
            "Encode parameter values in NamedStrategy labels or serialized strategy JSON.";
        case "indicator-test" -> "Encode indicator parameters in serialized indicator JSON.";
        case "rule-test" -> "Encode rule parameters in NamedRule labels or serialized rule JSON.";
        default -> "Use the command-specific structured inputs instead.";
        };
        throw new IllegalArgumentException("The " + command + " command does not accept --param. " + guidance);
    }
}
