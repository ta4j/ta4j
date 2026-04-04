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
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import ta4jexamples.rules.RsiThresholdRule;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class Ta4jCliTest {

    @TempDir
    Path tempDir;

    @Test
    void backtestProducesJsonAndChartArtifacts() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path strategyJsonFile = writeSerializedStrategy("backtest-strategy.json", sampleSweepStrategy(series));
        Path outputFile = tempDir.resolve("backtest.json");
        Path chartFile = tempDir.resolve("backtest.jpg");

        int exitCode = runCli("backtest", "--data-file", dataFile.toString(), "--strategy-json-file",
                strategyJsonFile.toString(), "--criteria",
                NetProfitCriterion.class.getName() + ",org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion",
                "--output", outputFile.toString(), "--chart", chartFile.toString());

        assertThat(exitCode).isZero();
        assertThat(outputFile).exists();
        assertThat(chartFile).exists();

        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("command").getAsString()).isEqualTo("backtest");
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString()).contains("sma-crossover");
        assertThat(payload.getAsJsonObject("artifacts").get("outputFile").getAsString())
                .isEqualTo(outputFile.toAbsolutePath().normalize().toString());
        assertThat(payload.getAsJsonObject("artifacts").get("chartFile").getAsString())
                .isEqualTo(chartFile.toAbsolutePath().normalize().toString());
    }

    @Test
    void walkForwardProducesConfigHashAndFoldBreakdown() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        Path strategyJsonFile = writeSerializedStrategy("walk-forward-strategy.json", sampleSweepStrategy(series));
        Path outputFile = tempDir.resolve("walk-forward.json");

        int exitCode = runCli("walk-forward", "--data-file", dataFile.toString(), "--strategy-json-file",
                strategyJsonFile.toString(), "--criteria", GrossReturnCriterion.class.getName(), "--output",
                outputFile.toString(), "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20",
                "--holdout-bars", "20");

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        JsonObject walkForward = payload.getAsJsonObject("walkForward");
        assertThat(walkForward.getAsJsonObject("config").get("configHash").getAsString()).isNotBlank();
        JsonArray folds = walkForward.getAsJsonArray("folds");
        assertThat(folds).isNotEmpty();
    }

    @Test
    void sweepProducesDeterministicLeaderBoard() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("sweep.json");

        int exitCode = runCli("sweep", "--data-file", dataFile.toString(), "--param-grid", "fast=3,5", "--param-grid",
                "slow=20,30", "--top-k", "2", "--criteria", NetProfitCriterion.class.getName(), "--output",
                outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("candidateCount").getAsInt()).isEqualTo(4);
        assertThat(payload.get("topK").getAsInt()).isEqualTo(2);
        assertThat(payload.getAsJsonArray("leaderboard")).hasSize(2);
    }

    @Test
    void indicatorTestBuildsThresholdStrategy() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        BarSeries series = CliSupport.loadSeries(dataFile.toString(), null, null, null);
        String indicatorJson = new RSIIndicator(new ClosePriceIndicator(series), 14).toJson();
        Path outputFile = tempDir.resolve("indicator-test.json");

        int exitCode = runCli("indicator-test", "--data-file", dataFile.toString(), "--indicator", indicatorJson,
                "--entry-below", "30", "--exit-above", "70", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("indicatorType").getAsString()).isEqualTo(RSIIndicator.class.getName());
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("RSIIndicator-indicator-test");
    }

    @Test
    void ruleTestBuildsBacktestAndWalkForwardArtifactsFromNamedRules() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("rule-test.json");

        int exitCode = runCli("rule-test", "--data-file", dataFile.toString(), "--entry-rule",
                "RsiThresholdRule_BELOW_14_30", "--exit-rule", "RsiThresholdRule_ABOVE_14_70", "--output",
                outputFile.toString(), "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20",
                "--holdout-bars", "20");

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("entryRuleName").getAsString()).isEqualTo("RsiThresholdRule_BELOW_14_30");
        assertThat(payload.get("exitRuleName").getAsString()).isEqualTo("RsiThresholdRule_ABOVE_14_70");
        assertThat(payload.get("backtest")).isNotNull();
        assertThat(payload.get("backtestRuntime")).isNotNull();
        assertThat(payload.get("walkForward")).isNotNull();
    }

    @Test
    void backtestAcceptsNamedStrategyLabels() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("named-strategy-backtest.json");

        int exitCode = runCli("backtest", "--data-file", dataFile.toString(), "--strategy",
                "DayOfWeekStrategy_MONDAY_FRIDAY", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("DayOfWeekStrategy_MONDAY_FRIDAY");
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

        CliRunResult result = runCliAllowingError("backtest", "--data-file", dataFile.toString(), "--strategy",
                "DayOfWeekStrategy_MONDAY_FRIDAY", "--strategies", "HourOfDayStrategy_9_17,MissingStrategy_VALUE",
                "--strategy-json-file", strategyJsonFile.toString(), "--strategies-json-file",
                strategiesJsonFile.toString(), "--output", outputFile.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipping invalid strategy inputs:")
                .contains("--strategies MissingStrategy_VALUE")
                .contains("--strategies-json-file " + strategiesJsonFile + "[1]");

        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("strategyCount").getAsInt()).isEqualTo(4);
        assertThat(payload.get("invalidStrategyCount").getAsInt()).isEqualTo(2);
        assertThat(payload.getAsJsonArray("statements")).hasSize(4);
        assertThat(payload.getAsJsonArray("invalidStrategies")).hasSize(2);
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString())
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

        CliRunResult result = runCliAllowingError("walk-forward", "--data-file", dataFile.toString(),
                "--strategies-json-file", strategiesJsonFile.toString(), "--output", outputFile.toString(),
                "--min-train-bars", "120", "--test-bars", "40", "--step-bars", "20", "--holdout-bars", "20");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipping invalid strategy inputs:")
                .contains("--strategies-json-file " + strategiesJsonFile + "[1]");

        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("strategyCount").getAsInt()).isEqualTo(1);
        assertThat(payload.get("invalidStrategyCount").getAsInt()).isEqualTo(1);
        assertThat(payload.get("backtest")).isNotNull();
        assertThat(payload.get("backtestRuntime")).isNotNull();
        assertThat(payload.get("walkForward")).isNotNull();
        assertThat(payload.getAsJsonArray("results")).hasSize(1);
    }

    @Test
    void backtestRejectsInvalidUnstableBarsValue() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("backtest", "--data-file", dataFile.toString(), "--strategy",
                "DayOfWeekStrategy_MONDAY_FRIDAY", "--unstable-bars", "abc");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Invalid integer value for --unstable-bars: abc.");
    }

    @Test
    void sweepRejectsInvalidTopKValue() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("sweep", "--data-file", dataFile.toString(), "--param-grid",
                "fast=3,5", "--param-grid", "slow=20,30", "--top-k", "abc");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Invalid integer value for --top-k: abc.");
    }

    @Test
    void backtestRejectsParamOptions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("backtest", "--data-file", dataFile.toString(), "--strategy",
                "DayOfWeekStrategy_MONDAY_FRIDAY", "--param", "entry=MONDAY");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains(
                "The backtest command does not accept --param. Encode parameter values in NamedStrategy labels or serialized strategy JSON.");
    }

    @Test
    void indicatorTestRejectsParamOptions() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("indicator-test", "--data-file", dataFile.toString(), "--indicator",
                "{\"type\":\"EMAIndicator\"}", "--param", "period=14");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains(
                "The indicator-test command does not accept --param. Encode indicator parameters in serialized indicator JSON.");
    }

    @Test
    void backtestFailsFastWhenEveryStrategyInputIsInvalid() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");

        CliRunResult result = runCliAllowingError("backtest", "--data-file", dataFile.toString(), "--strategies",
                "MissingStrategy_VALUE", "--strategies-json-file", tempDir.resolve("missing.json").toString());

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
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        int exitCode = Ta4jCli.run(args, new PrintWriter(stdout, true), new PrintWriter(stderr, true));
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
