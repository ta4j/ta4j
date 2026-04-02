/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.backtest.StrategyWalkForwardExecutionResult;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.WalkForwardConfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
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
            default -> throw new IllegalArgumentException("Unknown command '" + arguments.command()
                    + "'. Supported commands are backtest, walk-forward, sweep, indicator-test.");
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
        String strategyAlias = arguments.optional("strategy").orElse(null);
        String strategyJson = arguments.optional("strategy-json").orElse(null);
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
        Integer unstableBars = arguments.optional("unstable-bars").map(value -> Integer.parseInt(value)).orElse(null);
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_BACKTEST_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        Strategy strategy = CliSupport.buildStrategy(strategyAlias, strategyJson, params, unstableBars, series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType(), CliSupport.progressCallback(progress, err, "backtest"));
        TradingStatement statement = result.tradingStatements().getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);

        Map<String, Object> response = CliSupport.buildCommandMetadata("backtest", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, null,
                chartPath);
        response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
        response.put("statement", CliSupport.statementToMap(series, statement, criteria));

        String json = CliSupport.toJson(response);
        Path outputPath = CliSupport.writeJson(json, output, out);
        response = CliSupport.buildCommandMetadata("backtest", series, dataFile, timeframe, fromDate, toDate,
                executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath, chartPath);
        response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
        response.put("statement", CliSupport.statementToMap(series, statement, criteria));
        json = CliSupport.toJson(response);
        if (outputPath != null) {
            CliSupport.writeJson(json, outputPath.toString(), out);
        }
        return 0;
    }

    private static int executeWalkForward(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String strategyAlias = arguments.require("strategy");
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
        Integer unstableBars = arguments.optional("unstable-bars").map(value -> Integer.parseInt(value)).orElse(null);
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_WALK_FORWARD_CRITERIA);

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        Strategy strategy = CliSupport.buildStrategy(strategyAlias, null, params, unstableBars, series);
        WalkForwardConfig config = CliSupport.buildWalkForwardConfig(series, arguments);
        arguments.assertNoUnknownOptions();

        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        BacktestExecutionResult backtest = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType());
        StrategyWalkForwardExecutionResult walkForward = executor.executeWalkForward(strategy, amount,
                strategy.getStartingType(), config, CliSupport.progressCallback(progress, err, "walk-forward"));
        TradingStatement statement = backtest.tradingStatements().getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);

        Map<String, Object> response = CliSupport.buildCommandMetadata("walk-forward", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, null,
                chartPath);
        response.put("backtest", CliSupport.statementToMap(series, statement, criteria));
        response.put("backtestRuntime", CliSupport.backtestRuntimeToMap(backtest.runtimeReport()));
        response.put("walkForward", CliSupport.walkForwardToMap(series, walkForward, criteria));

        String json = CliSupport.toJson(response);
        Path outputPath = CliSupport.writeJson(json, output, out);
        if (outputPath != null) {
            response = CliSupport.buildCommandMetadata("walk-forward", series, dataFile, timeframe, fromDate, toDate,
                    executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath, chartPath);
            response.put("backtest", CliSupport.statementToMap(series, statement, criteria));
            response.put("backtestRuntime", CliSupport.backtestRuntimeToMap(backtest.runtimeReport()));
            response.put("walkForward", CliSupport.walkForwardToMap(series, walkForward, criteria));
            CliSupport.writeJson(CliSupport.toJson(response), outputPath.toString(), out);
        }
        return 0;
    }

    private static int executeSweep(CliArguments arguments, PrintWriter out, PrintWriter err) throws IOException {
        String dataFile = arguments.require("data-file");
        String strategyAlias = arguments.require("strategy");
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
        Integer unstableBars = arguments.optional("unstable-bars").map(value -> Integer.parseInt(value)).orElse(null);
        int topK = arguments.optional("top-k").map(Integer::parseInt).orElse(5);
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        List<String> paramGrids = arguments.list("param-grid");
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_SWEEP_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        List<Strategy> strategies = CliSupport.buildSweepStrategies(strategyAlias, params, paramGrids, unstableBars,
                series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);

        BacktestExecutionResult sweepResult = executor.executeAndKeepTopK(strategies, amount,
                strategies.getFirst().getStartingType(), criteria.getFirst().criterion(), topK,
                CliSupport.progressCallback(progress, err, "sweep"));
        TradingStatement topStatement = sweepResult.tradingStatements().isEmpty() ? null
                : sweepResult.tradingStatements().getFirst();
        Path chartPath = topStatement == null ? null : CliSupport.saveChart(chart, series, topStatement);

        List<Map<String, Object>> leaderboard = sweepResult.tradingStatements()
                .stream()
                .map(statement -> CliSupport.statementToMap(series, statement, criteria))
                .toList();

        Map<String, Object> response = CliSupport.buildCommandMetadata("sweep", series, dataFile, timeframe, fromDate,
                toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, null, chartPath);
        response.put("candidateCount", strategies.size());
        response.put("topK", topK);
        response.put("runtime", CliSupport.backtestRuntimeToMap(sweepResult.runtimeReport()));
        response.put("leaderboard", leaderboard);

        String json = CliSupport.toJson(response);
        Path outputPath = CliSupport.writeJson(json, output, out);
        if (outputPath != null) {
            response = CliSupport.buildCommandMetadata("sweep", series, dataFile, timeframe, fromDate, toDate,
                    executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath, chartPath);
            response.put("candidateCount", strategies.size());
            response.put("topK", topK);
            response.put("runtime", CliSupport.backtestRuntimeToMap(sweepResult.runtimeReport()));
            response.put("leaderboard", leaderboard);
            CliSupport.writeJson(CliSupport.toJson(response), outputPath.toString(), out);
        }
        return 0;
    }

    private static int executeIndicatorTest(CliArguments arguments, PrintWriter out, PrintWriter err)
            throws IOException {
        String dataFile = arguments.require("data-file");
        String indicatorAlias = arguments.require("indicator");
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
        Integer unstableBars = arguments.optional("unstable-bars").map(Integer::parseInt).orElse(null);
        String entryBelow = arguments.optional("entry-below").orElse(null);
        String entryAbove = arguments.optional("entry-above").orElse(null);
        String exitBelow = arguments.optional("exit-below").orElse(null);
        String exitAbove = arguments.optional("exit-above").orElse(null);
        boolean progress = arguments.flag("progress");
        List<String> params = arguments.list("param");
        List<CliSupport.CriterionSpec> criteria = CliSupport.resolveCriteria(arguments.list("criteria"),
                CliSupport.DEFAULT_INDICATOR_TEST_CRITERIA);
        arguments.assertNoUnknownOptions();

        BarSeries series = CliSupport.loadSeries(dataFile, timeframe, fromDate, toDate);
        Strategy strategy = CliSupport.buildIndicatorTestStrategy(indicatorAlias, params, unstableBars, entryBelow,
                entryAbove, exitBelow, exitAbove, series);
        BacktestExecutor executor = CliSupport.buildExecutor(series, executionModel, commission, borrowRate);
        Num amount = CliSupport.resolveAmount(series, capital, stakeAmount);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(strategy), amount,
                strategy.getStartingType(), CliSupport.progressCallback(progress, err, "indicator-test"));
        TradingStatement statement = result.tradingStatements().getFirst();
        Path chartPath = CliSupport.saveChart(chart, series, statement);

        Map<String, Object> response = CliSupport.buildCommandMetadata("indicator-test", series, dataFile, timeframe,
                fromDate, toDate, executionModel, capital, stakeAmount, commission, borrowRate, criteria, null,
                chartPath);
        response.put("indicator", indicatorAlias);
        response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
        response.put("statement", CliSupport.statementToMap(series, statement, criteria));

        String json = CliSupport.toJson(response);
        Path outputPath = CliSupport.writeJson(json, output, out);
        if (outputPath != null) {
            response = CliSupport.buildCommandMetadata("indicator-test", series, dataFile, timeframe, fromDate, toDate,
                    executionModel, capital, stakeAmount, commission, borrowRate, criteria, outputPath, chartPath);
            response.put("indicator", indicatorAlias);
            response.put("runtime", CliSupport.backtestRuntimeToMap(result.runtimeReport()));
            response.put("statement", CliSupport.statementToMap(series, statement, criteria));
            CliSupport.writeJson(CliSupport.toJson(response), outputPath.toString(), out);
        }
        return 0;
    }

    private static String usage() {
        return """
                Usage:
                  ta4j-cli backtest --data-file <path> (--strategy <alias> | --strategy-json <path>) [options]
                  ta4j-cli walk-forward --data-file <path> --strategy <alias> [options]
                  ta4j-cli sweep --data-file <path> --strategy sma-crossover --param-grid fast=5,10 --param-grid slow=20,50 [options]
                  ta4j-cli indicator-test --data-file <path> --indicator <alias> [options]

                Common options:
                  --timeframe 1m|5m|15m|1h|4h|1d|PT...
                  --from-date YYYY-MM-DD|ISO-INSTANT
                  --to-date YYYY-MM-DD|ISO-INSTANT
                  --execution-model next-open|current-close
                  --capital <number>
                  --stake-amount <number>
                  --commission <rate>
                  --borrow-rate <rate>
                  --criteria alias[,alias...]
                  --output <json-path>
                  --chart <jpeg-path>
                  --progress
                  --unstable-bars <count>

                Strategy options:
                  --param key=value
                  Supported strategy aliases: sma-crossover, rsi2, cci-correction, global-extrema, moving-momentum

                Sweep options:
                  --param-grid key=v1,v2,...
                  --top-k <count>

                Indicator-test options:
                  --indicator sma|ema|rsi|cci
                  --entry-below <number> | --entry-above <number>
                  --exit-below <number> | --exit-above <number>
                  --param period=<count>
                """;
    }
}
