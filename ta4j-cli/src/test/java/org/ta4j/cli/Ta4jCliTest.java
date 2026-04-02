/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class Ta4jCliTest {

    @TempDir
    Path tempDir;

    @Test
    void backtestProducesJsonAndChartArtifacts() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("backtest.json");
        Path chartFile = tempDir.resolve("backtest.jpg");

        int exitCode = runCli("backtest", "--data-file", dataFile.toString(), "--strategy", "sma-crossover", "--param",
                "fast=5", "--param", "slow=20", "--criteria", "net-profit,romad", "--output", outputFile.toString(),
                "--chart", chartFile.toString());

        assertThat(exitCode).isZero();
        assertThat(outputFile).exists();
        assertThat(chartFile).exists();

        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("command").getAsString()).isEqualTo("backtest");
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString()).contains("sma-crossover");
        assertThat(payload.getAsJsonObject("artifacts").get("chartFile").getAsString())
                .isEqualTo(chartFile.toAbsolutePath().normalize().toString());
    }

    @Test
    void walkForwardProducesConfigHashAndFoldBreakdown() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("walk-forward.json");

        int exitCode = runCli("walk-forward", "--data-file", dataFile.toString(), "--strategy", "sma-crossover",
                "--param", "fast=5", "--param", "slow=20", "--criteria", "gross-return", "--output",
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

        int exitCode = runCli("sweep", "--data-file", dataFile.toString(), "--strategy", "sma-crossover",
                "--param-grid", "fast=3,5", "--param-grid", "slow=20,30", "--top-k", "2", "--criteria", "net-profit",
                "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("candidateCount").getAsInt()).isEqualTo(4);
        assertThat(payload.get("topK").getAsInt()).isEqualTo(2);
        assertThat(payload.getAsJsonArray("leaderboard")).hasSize(2);
    }

    @Test
    void indicatorTestBuildsThresholdStrategy() throws Exception {
        Path dataFile = copyResource("AAPL-PT1D-20130102_20131231.csv");
        Path outputFile = tempDir.resolve("indicator-test.json");

        int exitCode = runCli("indicator-test", "--data-file", dataFile.toString(), "--indicator", "rsi", "--param",
                "period=14", "--entry-below", "30", "--exit-above", "70", "--output", outputFile.toString());

        assertThat(exitCode).isZero();
        JsonObject payload = readJson(outputFile);
        assertThat(payload.get("indicator").getAsString()).isEqualTo("rsi");
        assertThat(payload.getAsJsonObject("statement").get("strategyName").getAsString())
                .isEqualTo("rsi-indicator-test");
    }

    private int runCli(String... args) {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        int exitCode = Ta4jCli.run(args, new PrintWriter(stdout, true), new PrintWriter(stderr, true));
        assertThat(stderr.toString()).isBlank();
        return exitCode;
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
}
