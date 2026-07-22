/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.criteria.Annualization;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class Ta4jCliTest {

    @TempDir
    Path tempDir;

    @Test
    void helpUsesProgressiveDisclosure() {
        CliRunResult rootHelp = runCliAllowingError("--help");
        CliRunResult strategyHelp = runCliAllowingError("strategy", "--help");
        CliRunResult forecastHelp = runCliAllowingError("forecast", "run", "--help");
        CliRunResult performanceRunHelp = runCliAllowingError("performance", "run", "--help");

        assertThat(rootHelp.exitCode()).isZero();
        assertThat(rootHelp.stdout()).contains("Usage: ta4j-cli")
                .contains("strategy")
                .contains("indicator")
                .contains("rule")
                .contains("forecast")
                .contains("performance")
                .contains("catalog")
                .contains("completion");
        assertThat(rootHelp.stdout()).doesNotContain("backtest      Run strategies against one dataset.");

        assertThat(strategyHelp.exitCode()).isZero();
        assertThat(strategyHelp.stdout()).contains("Usage: ta4j-cli strategy")
                .contains("backtest")
                .contains("walk-forward")
                .contains("sweep");
        assertThat(strategyHelp.stdout()).doesNotContain("--data-file");

        assertThat(forecastHelp.exitCode()).isZero();
        assertThat(forecastHelp.stdout()).contains("Usage: ta4j-cli forecast run")
                .contains("--state-model")
                .contains("--target")
                .contains("--quantiles");

        assertThat(performanceRunHelp.exitCode()).isZero();
        assertThat(performanceRunHelp.stdout()).contains("Usage: ta4j-cli performance run")
                .contains("--bar-counts")
                .contains("--output-dir");
        assertThat(performanceRunHelp.stdout()).doesNotContain("--base-dir");
    }

    @Test
    void versionIsAlwaysVisible() {
        CliRunResult result = runCliAllowingError("--version");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("ta4j-cli ").doesNotEndWith("ta4j-cli " + System.lineSeparator());
    }

    @Test
    void catalogAndCompletionAreGeneratedFromTheLiveCommandModel() {
        CliRunResult catalogRun = runCliAllowingError("catalog");
        CliRunResult completionRun = runCliAllowingError("completion", "--shell", "bash");

        assertThat(catalogRun.exitCode()).isZero();
        JsonObject catalog = result(JsonParser.parseString(catalogRun.stdout()).getAsJsonObject());
        assertThat(catalog.getAsJsonObject("aliases").getAsJsonArray("indicators").toString()).contains("SMA", "RSI");
        assertThat(catalog.getAsJsonObject("forecasting").getAsJsonArray("projectionModels").toString())
                .contains("monte-carlo", "analog");
        assertThat(completionRun.exitCode()).isZero();
        assertThat(completionRun.stdout()).contains("ta4j-cli", "_complete_ta4j-cli");
    }

    @Test
    void structuredErrorsDistinguishUsageFailures() {
        CliRunResult result = runCliAllowingError("--error-format", "json", "backtest");

        assertThat(result.exitCode()).isEqualTo(2);
        JsonObject payload = JsonParser.parseString(result.stderr()).getAsJsonObject();
        assertThat(payload.get("schemaVersion").getAsInt()).isEqualTo(1);
        assertThat(payload.get("status").getAsString()).isEqualTo("error");
        assertThat(payload.getAsJsonObject("error").get("category").getAsString()).isEqualTo("usage");
    }

    @Test
    void oldFlatCommandsFailAsUnknownCommands() {
        CliRunResult result = runCliAllowingError("backtest");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Unmatched argument at index 0: 'backtest'");
    }

    @Test
    void oldCamelCasePerformanceFlagsFailAsUnknownOptions() {
        CliRunResult result = runCliAllowingError("performance", "run", "--barCounts", "16");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Unknown options: '--barCounts', '16'");
    }

    @Test
    void backtestProducesJsonAndChartArtifacts() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path strategyJsonFile = writeSerializedStrategy("backtest-strategy.json", sampleSweepStrategy(series));
        Path outputFile = tempDir.resolve("backtest.json");
        Path chartFile = tempDir.resolve("backtest.jpg");

        int exitCode = runCli("strategy", "backtest", "--data-file", dataFile.toString(), "--strategy-json-file",
                strategyJsonFile.toString(), "--criteria",
                NetProfitCriterion.class.getName() + ",org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion",
                "--output", outputFile.toString(), "--chart", chartFile.toString());

        assertThat(exitCode).isZero();
        assertThat(outputFile).exists();
        assertThat(chartFile).exists();

        JsonObject payload = readJson(outputFile);
        JsonObject result = result(payload);
        assertThat(payload.get("command").getAsString()).isEqualTo("strategy backtest");
        assertThat(payload.get("schemaVersion").getAsInt()).isEqualTo(1);
        assertThat(result.getAsJsonObject("statement").get("strategyName").getAsString()).contains("sma-crossover");
        JsonObject artifacts = payload.getAsJsonObject("run").getAsJsonObject("artifacts");
        assertThat(artifacts.get("outputFile").getAsString())
                .isEqualTo(outputFile.toAbsolutePath().normalize().toString());
        assertThat(artifacts.get("chartFile").getAsString())
                .isEqualTo(chartFile.toAbsolutePath().normalize().toString());
    }

    @Test
    void walkForwardProducesConfigHashAndFoldBreakdown() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path strategyJsonFile = writeSerializedStrategy("walk-forward-strategy.json", sampleSweepStrategy(series));
        Path outputFile = tempDir.resolve("walk-forward.json");

        int exitCode = runCli("strategy", "walk-forward", "--data-file", dataFile.toString(), "--strategy-json-file",
                strategyJsonFile.toString(), "--criteria", GrossReturnCriterion.class.getName(), "--output",
                outputFile.toString(), "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20",
                "--holdout-bars", "20");

        assertThat(exitCode).isZero();
        JsonObject walkForward = result(readJson(outputFile)).getAsJsonObject("walkForward");
        assertThat(walkForward.getAsJsonObject("config").get("configHash").getAsString()).isNotBlank();
        JsonArray folds = walkForward.getAsJsonArray("folds");
        assertThat(folds).isNotEmpty();
    }

    @Test
    void sweepProducesDeterministicLeaderBoard() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("sweep.json");

        int exitCode = runCli("strategy", "sweep", "--data-file", dataFile.toString(), "--param-grid", "fast=3,5",
                "--param-grid", "slow=20,30", "--top-k", "2", "--criteria", NetProfitCriterion.class.getName(),
                "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject result = result(readJson(outputFile));
        assertThat(result.get("candidateCount").getAsInt()).isEqualTo(4);
        assertThat(result.get("topK").getAsInt()).isEqualTo(2);
        assertThat(result.getAsJsonArray("leaderboard")).hasSize(2);
    }

    @Test
    void indicatorTestBuildsThresholdStrategy() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("indicator-test.json");

        int exitCode = runCli("indicator", "test", "--data-file", dataFile.toString(), "--indicator", "RSI(14)",
                "--entry-below", "30", "--exit-above", "70", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject result = result(readJson(outputFile));
        assertThat(result.get("indicatorType").getAsString()).isEqualTo(RSIIndicator.class.getName());
        assertThat(result.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("RSIIndicator-indicator-test");
    }

    @Test
    void ruleTestBuildsBacktestAndWalkForwardArtifactsFromNamedRules() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("rule-test.json");

        int exitCode = runCli("rule", "test", "--data-file", dataFile.toString(), "--entry-rule",
                "RsiThresholdRule_BELOW_14_30", "--exit-rule", "RsiThresholdRule_ABOVE_14_70", "--output",
                outputFile.toString(), "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20",
                "--holdout-bars", "20");

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        JsonObject result = result(payload);
        assertThat(result.get("entryRuleName").getAsString()).isEqualTo("RsiThresholdRule_BELOW_14_30");
        assertThat(result.get("exitRuleName").getAsString()).isEqualTo("RsiThresholdRule_ABOVE_14_70");
        assertThat(result.get("backtest")).isNotNull();
        assertThat(payload.getAsJsonObject("run").get("backtestRuntime")).isNotNull();
        assertThat(result.get("walkForward")).isNotNull();
    }

    @Test
    void performanceExperimentWritesArtifacts() throws Exception {
        Path outputDir = tempDir.resolve("performance-experiment");

        int exitCode = runCli("performance", "run", "--experiment", "kalman-filter", "--bar-counts", "16",
                "--scenarios", "endOnly", "--repetitions", "1", "--warmups", "0", "--output-dir", outputDir.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputDir.resolve("performance.json"));
        assertThat(payload.get("experimentId").getAsString()).isEqualTo("kalman-filter");
        assertThat(payload.getAsJsonArray("results")).hasSize(1);
        assertThat(outputDir.resolve("summary.md")).exists();
    }

    @Test
    void performanceCompareWritesArtifacts() throws Exception {
        Path baseDir = tempDir.resolve("performance-base");
        Path candidateDir = tempDir.resolve("performance-candidate");
        Path comparisonDir = tempDir.resolve("performance-comparison");
        runCli("performance", "run", "--experiment", "kalman-filter", "--bar-counts", "16", "--scenarios", "endOnly",
                "--repetitions", "1", "--warmups", "0", "--output-dir", baseDir.toString());
        runCli("performance", "run", "--experiment", "kalman-filter", "--bar-counts", "16", "--scenarios", "endOnly",
                "--repetitions", "1", "--warmups", "0", "--output-dir", candidateDir.toString());

        int exitCode = runCli("performance", "compare", "--base-dir", baseDir.toString(), "--candidate-dir",
                candidateDir.toString(), "--output-dir", comparisonDir.toString(), "--max-regression-pct", "100000");

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(comparisonDir.resolve("comparison.json"));
        assertThat(payload.get("experimentId").getAsString()).isEqualTo("kalman-filter");
        assertThat(payload.get("checksumMatch").getAsBoolean()).isTrue();
        assertThat(comparisonDir.resolve("summary.md")).exists();
    }

    @Test
    void backtestAcceptsNamedStrategyLabels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("named-strategy-backtest.json");

        int exitCode = runCli("strategy", "backtest", "--data-file", dataFile.toString(), "--strategy",
                "DayOfWeekStrategy_MONDAY_FRIDAY", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject result = result(readJson(outputFile));
        assertThat(result.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
    }

    @Test
    void backtestAcceptsCompactStrategyAndCriterionExpressionsWithDynamicSizing() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("expression-backtest.json");

        int exitCode = runCli("strategy", "backtest", "--data-file", dataFile.toString(), "--strategy", "SMA(7,21)",
                "--criteria", "NetProfit,ReturnOverMaxDrawdown", "--position-sizing", "balance", "--capital", "10000",
                "--borrow-rate", "0", "--borrow-side", "both", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject result = result(readJson(outputFile));
        assertThat(result.getAsJsonObject("statement").get("strategyName").getAsString()).isEqualTo("SMA(7,21)");
        assertThat(result.getAsJsonObject("statement").getAsJsonArray("criteria")).hasSize(2);
        assertThat(result.getAsJsonObject("execution").get("positionSizing").getAsString()).isEqualTo("balance");
        assertThat(result.getAsJsonObject("execution").get("borrowSide").getAsString()).isEqualTo("both");
    }

    @Test
    void backtestAcceptsVersionTwoStrategyJson() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path strategyFile = tempDir.resolve("strategy-v2.json");
        Path outputFile = tempDir.resolve("strategy-v2-backtest.json");
        Files.writeString(strategyFile, """
                {
                  "version": 2,
                  "name": "v2-sma",
                  "entryRule": {"type": "CrossedUpIndicatorRule", "args": ["SMA(7)", "SMA(21)"]},
                  "exitRule": {"type": "CrossedDownIndicatorRule", "args": ["SMA(7)", "SMA(21)"]}
                }
                """);

        int exitCode = runCli("strategy", "backtest", "--data-file", dataFile.toString(), "--strategy-json-file",
                strategyFile.toString(), "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        assertThat(result(readJson(outputFile)).getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("v2-sma");
    }

    @Test
    void forecastRunProducesDeterministicEmpiricalPriceSummary() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("forecast.json");

        int exitCode = runCli("forecast", "run", "--data-file", dataFile.toString(), "--state-model", "ewma",
                "--target", "price", "--horizon", "3", "--samples", "25", "--quantiles", "0.05,0.5,0.95", "--output",
                outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        JsonObject result = result(payload);
        assertThat(payload.get("command").getAsString()).isEqualTo("forecast run");
        assertThat(result.getAsJsonObject("state").get("stable").getAsBoolean()).isTrue();
        assertThat(result.getAsJsonObject("forecast").get("horizon").getAsInt()).isEqualTo(3);
        assertThat(result.getAsJsonObject("configuration").get("lookbackBars").getAsInt()).isEqualTo(251);
        assertThat(result.getAsJsonObject("forecast").getAsJsonObject("support").get("count").getAsInt()).isEqualTo(25);
    }

    @Test
    void forecastRunComposesAnalogConformalAndLognormalModels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path analogOutput = tempDir.resolve("analog-price.json");
        Path conformalOutput = tempDir.resolve("analog-conformal-return.json");

        int analogExit = runCli("forecast", "run", "--data-file", dataFile.toString(), "--target", "price",
                "--projection-model", "analog", "--price-model", "lognormal", "--lookback-bars", "120",
                "--neighbor-count", "10", "--minimum-neighbor-count", "5", "--output", analogOutput.toString());
        int conformalExit = runCli("forecast", "run", "--data-file", dataFile.toString(), "--target", "return",
                "--projection-model", "analog", "--calibration", "conformal", "--lookback-bars", "40",
                "--neighbor-count", "10", "--minimum-neighbor-count", "5", "--calibration-window", "80",
                "--minimum-calibration-count", "10", "--output", conformalOutput.toString());

        assertThat(analogExit).isZero();
        assertThat(conformalExit).isZero();
        JsonObject analogResult = result(readJson(analogOutput));
        assertThat(analogResult.getAsJsonObject("configuration").get("resolvedPriceModel").getAsString())
                .isEqualTo("lognormal");
        assertThat(analogResult.getAsJsonObject("forecast").getAsJsonObject("support").get("assumption").getAsString())
                .isEqualTo("lognormal-moment-match");
        JsonObject conformalResult = result(readJson(conformalOutput));
        assertThat(conformalResult.getAsJsonObject("configuration").get("calibration").getAsString())
                .isEqualTo("conformal");
        assertThat(conformalResult.getAsJsonObject("forecast").get("stable").getAsBoolean()).isTrue();
    }

    @Test
    void criterionJsonPreservesTradeSamplingConfiguration() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("criterion-json.json");
        String criterionJson = new SharpeRatioCriterion(0.03d, SamplingFrequency.TRADE, Annualization.ANNUALIZED,
                ZoneOffset.UTC).toJson();

        int exitCode = runCli("strategy", "backtest", "--data-file", dataFile.toString(), "--strategy", "SMA(7,21)",
                "--criterion-json", criterionJson, "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonArray scores = result(readJson(outputFile)).getAsJsonObject("statement").getAsJsonArray("criteria");
        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).getAsJsonObject().get("descriptor").toString()).contains("TRADE", "0.03");
    }

    @Test
    void reproducibleOutputIsByteStableAndOmitsRunMetadata() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult first = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategy", "SMA(7,21)", "--reproducible");
        CliRunResult second = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategy", "SMA(7,21)", "--reproducible");

        assertThat(first.exitCode()).isZero();
        assertThat(first.stderr()).isBlank();
        assertThat(second.stdout()).isEqualTo(first.stdout());
        JsonObject payload = JsonParser.parseString(first.stdout()).getAsJsonObject();
        assertThat(payload.has("run")).isFalse();
        assertThat(result(payload).getAsJsonObject("input").get("seriesSha256").getAsString()).hasSize(64);
    }

    @Test
    void stdinDataSupportsComposableAgentPipelines() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        InputStream input = new ByteArrayInputStream(Files.readAllBytes(dataFile));

        CliRunResult run = runCliAllowingError(input, "strategy", "backtest", "--data-file", "-", "--data-format",
                "csv", "--strategy", "SMA(7,21)", "--reproducible");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isBlank();
        JsonObject inputMetadata = result(JsonParser.parseString(run.stdout()).getAsJsonObject())
                .getAsJsonObject("input");
        assertThat(inputMetadata.get("dataFile").getAsString()).isEqualTo("-");
        assertThat(inputMetadata.get("seriesName").getAsString()).isEqualTo("stdin");
    }

    @Test
    void forecastStateRejectsProjectionOnlyOptions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("forecast", "run", "--data-file", dataFile.toString(), "--target",
                "state", "--horizon", "3");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("--horizon may only be used with --target return or --target price.");
    }

    @Test
    void backtestFailsBeforeExecutionWhenAnyBatchInputIsInvalidByDefault() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("should-not-exist.json");

        CliRunResult result = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategies", "SMA(7,21),MissingStrategy_VALUE", "--output", outputFile.toString());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Invalid strategy inputs:", "MissingStrategy_VALUE",
                "Use --invalid-input skip to run only valid inputs.");
        assertThat(outputFile).doesNotExist();
    }

    @Test
    void backtestCombinesStrategyInputsAndSkipsInvalidEntries() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy serializedStrategy = sampleSweepStrategy(series);
        Path strategyJsonFile = writeSerializedStrategy("strategy.json", serializedStrategy);
        Path strategiesJsonFile = tempDir.resolve("strategies.json");
        Path outputFile = tempDir.resolve("backtest-batch.json");
        Files.writeString(strategiesJsonFile, "[" + serializedStrategy.toJson() + ",\"invalid\"]");

        CliRunResult result = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategy", "DayOfWeekStrategy_MONDAY_FRIDAY", "--strategies",
                "HourOfDayStrategy_9_17,MissingStrategy_VALUE", "--strategy-json-file", strategyJsonFile.toString(),
                "--strategies-json-file", strategiesJsonFile.toString(), "--invalid-input", "skip", "--output",
                outputFile.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipping invalid strategy inputs:")
                .contains("--strategies MissingStrategy_VALUE")
                .contains("--strategies-json-file " + strategiesJsonFile + "[1]");

        JsonObject payload = readJson(outputFile);
        JsonObject resultPayload = result(payload);
        assertThat(payload.get("status").getAsString()).isEqualTo("partial");
        assertThat(resultPayload.get("strategyCount").getAsInt()).isEqualTo(4);
        assertThat(resultPayload.get("invalidStrategyCount").getAsInt()).isEqualTo(2);
        assertThat(resultPayload.getAsJsonArray("statements")).hasSize(4);
        assertThat(resultPayload.getAsJsonArray("invalidStrategies")).hasSize(2);
        assertThat(resultPayload.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
    }

    @Test
    void walkForwardSupportsStrategiesJsonFileAndPreservesPrimaryFields() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Strategy serializedStrategy = sampleSweepStrategy(series);
        Path strategiesJsonFile = tempDir.resolve("walk-forward-strategies.json");
        Path outputFile = tempDir.resolve("walk-forward-batch.json");
        Files.writeString(strategiesJsonFile, "[" + serializedStrategy.toJson() + ",{\"bad\":true}]");

        CliRunResult result = runCliAllowingError("strategy", "walk-forward", "--data-file", dataFile.toString(),
                "--strategies-json-file", strategiesJsonFile.toString(), "--invalid-input", "skip", "--output",
                outputFile.toString(), "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20",
                "--holdout-bars", "20");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipping invalid strategy inputs:")
                .contains("--strategies-json-file " + strategiesJsonFile + "[1]");

        JsonObject payload = readJson(outputFile);
        JsonObject resultPayload = result(payload);
        assertThat(payload.get("status").getAsString()).isEqualTo("partial");
        assertThat(resultPayload.get("strategyCount").getAsInt()).isEqualTo(1);
        assertThat(resultPayload.get("invalidStrategyCount").getAsInt()).isEqualTo(1);
        assertThat(resultPayload.get("backtest")).isNotNull();
        assertThat(payload.getAsJsonObject("run").get("backtestRuntime")).isNotNull();
        assertThat(resultPayload.get("walkForward")).isNotNull();
        assertThat(resultPayload.getAsJsonArray("results")).hasSize(1);
    }

    @Test
    void backtestRejectsInvalidUnstableBarsValue() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategy", "DayOfWeekStrategy_MONDAY_FRIDAY", "--unstable-bars", "abc");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Invalid integer value for --unstable-bars: abc.");
    }

    @Test
    void sweepRejectsInvalidTopKValue() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("strategy", "sweep", "--data-file", dataFile.toString(),
                "--param-grid", "fast=3,5", "--param-grid", "slow=20,30", "--top-k", "abc");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Invalid integer value for --top-k: abc.");
    }

    @Test
    void sweepRejectsNonPositiveTopKValue() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("strategy", "sweep", "--data-file", dataFile.toString(),
                "--param-grid", "fast=3,5", "--param-grid", "slow=20,30", "--top-k", "0");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("--top-k must be greater than zero.");
    }

    @Test
    void backtestRejectsParamOptions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategy", "DayOfWeekStrategy_MONDAY_FRIDAY", "--param", "entry=MONDAY");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains(
                "The strategy backtest command does not accept --param. Encode parameters in compact shorthand, NamedStrategy labels, or serialized strategy JSON.");
    }

    @Test
    void indicatorTestRejectsParamOptions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("indicator", "test", "--data-file", dataFile.toString(),
                "--indicator", "{\"type\":\"EMAIndicator\"}", "--param", "period=14");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains(
                "The indicator test command does not accept --param. Encode indicator parameters in compact shorthand or serialized indicator JSON.");
    }

    @Test
    void backtestFailsFastWhenEveryStrategyInputIsInvalid() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("strategy", "backtest", "--data-file", dataFile.toString(),
                "--strategies", "MissingStrategy_VALUE", "--strategies-json-file",
                tempDir.resolve("missing.json").toString());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("No valid strategies to run.")
                .contains("--strategies MissingStrategy_VALUE")
                .contains("--strategies-json-file " + tempDir.resolve("missing.json"))
                .contains("Use --strategy, --strategies, --strategy-json-file, or --strategies-json-file.");
    }

    private int runCli(String... args) {
        CliRunResult result = runCliAllowingError(args);
        assertThat(result.stderr()).withFailMessage("CLI stderr output should be blank but was:%n%s", result.stderr())
                .isBlank();
        return result.exitCode();
    }

    private CliRunResult runCliAllowingError(String... args) {
        return runCliAllowingError(InputStream.nullInputStream(), args);
    }

    private CliRunResult runCliAllowingError(InputStream input, String... args) {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        int exitCode = Ta4jCli.run(args, input, new PrintWriter(stdout, true), new PrintWriter(stderr, true));
        return new CliRunResult(exitCode, stdout.toString(), stderr.toString());
    }

    private Path copyResource(String resourceName) throws IOException {
        Path target = tempDir.resolve(resourceName);
        try (var inputStream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resourceName))) {
            Files.copy(inputStream, target);
        }
        return target;
    }

    private JsonObject readJson(Path jsonFile) throws IOException {
        return JsonParser.parseString(Files.readString(jsonFile)).getAsJsonObject();
    }

    private JsonObject result(JsonObject payload) {
        return payload.getAsJsonObject("result");
    }

    private Strategy sampleSweepStrategy(BarSeries series) {
        return CliSupport.buildSweepStrategies(List.of(), List.of("fast=5", "slow=20"), null, series).getFirst();
    }

    private Path writeSerializedStrategy(String fileName, Strategy strategy) throws IOException {
        Path strategyJsonFile = tempDir.resolve(fileName);
        Files.writeString(strategyJsonFile, strategy.toJson());
        return strategyJsonFile;
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}
