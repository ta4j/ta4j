/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class PerformanceExperimentRunnerTest {

    private static final String BENCHMARK_PROPERTY = "ta4j.runBenchmarks";

    @TempDir
    Path tempDir;

    @Test
    void cliRejectsInvalidBarCounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PerformanceExperimentRunner.RunRequest("kalman-filter", List.of(0), List.of(), 1, 0,
                        Optional.empty(), false));

        assertEquals("barCounts values must be positive", exception.getMessage());
    }

    @Test
    void runRequestSortsAndDeduplicatesBarCounts() {
        PerformanceExperimentRunner.RunRequest request = new PerformanceExperimentRunner.RunRequest("kalman-filter",
                List.of(10000, 1000, 5000, 1000), List.of(), 1, 0, Optional.empty(), false);

        assertEquals(List.of(1000, 5000, 10000), request.barCounts());
    }

    @Test
    void cliRejectsOversizedBarCounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PerformanceExperimentRunner.RunRequest("kalman-filter",
                        List.of(PerformanceExperimentRunner.MAX_BAR_COUNT + 1), List.of(), 1, 0, Optional.empty(),
                        false));

        assertEquals("barCounts values must not exceed " + PerformanceExperimentRunner.MAX_BAR_COUNT,
                exception.getMessage());
    }

    @Test
    void runRejectsUnboundedTotalWorkBeforeAnyScenarioRuns() {
        Path outputDir = tempDir.resolve("oversized");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceExperimentRunner.run(new PerformanceExperimentRunner.RunRequest("kalman-filter",
                        List.of(PerformanceExperimentRunner.MAX_BAR_COUNT), List.of(), 1_000_000, 1_000_000,
                        Optional.of(outputDir), false)));

        assertTrue(exception.getMessage().contains("must not exceed"), exception.getMessage());
        assertFalse(Files.exists(outputDir), "No artifacts may be written when the work bound rejects the experiment");
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs({ org.junit.jupiter.api.condition.OS.LINUX,
            org.junit.jupiter.api.condition.OS.MAC })
    void commandOutputReturnsEmptyWhenProcessTimesOut() {
        Optional<String> output = PerformanceExperimentRunner.commandOutput(100, TimeUnit.MILLISECONDS, "/bin/sh", "-c",
                "sleep 5; printf late");

        assertTrue(output.isEmpty());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs({ org.junit.jupiter.api.condition.OS.LINUX,
            org.junit.jupiter.api.condition.OS.MAC })
    void commandOutputReturnsTrimmedOutputForSuccessfulProcess() {
        Optional<String> output = PerformanceExperimentRunner.commandOutput(5, TimeUnit.SECONDS, "/bin/sh", "-c",
                "printf ' ok '");

        assertEquals(Optional.of("ok"), output);
    }

    @Test
    void runnerRejectsUnknownScenarios() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceExperimentRunner.run(new PerformanceExperimentRunner.RunRequest("kalman-filter",
                        List.of(16), List.of("missing"), 5, 1, Optional.of(tempDir.resolve("unknown")), false)));

        assertEquals("Unknown scenario for kalman-filter: missing", exception.getMessage());
    }

    @Test
    void runnerWritesReusableKalmanArtifacts() throws Exception {
        Path outputDir = tempDir.resolve("kalman");

        PerformanceExperimentRunner.RunArtifacts artifacts = PerformanceExperimentRunner
                .run(new PerformanceExperimentRunner.RunRequest("kalman-filter", List.of(16), List.of("endOnly"), 2, 0,
                        Optional.of(outputDir), true));

        assertEquals(outputDir.toAbsolutePath().normalize(), artifacts.outputDir());
        assertTrue(Files.exists(outputDir.resolve("performance.json")));
        assertTrue(Files.exists(outputDir.resolve(PerformanceExperimentRunner.SUMMARY_FILE)));

        JsonObject json = JsonParser.parseString(artifacts.performanceJson()).getAsJsonObject();
        assertEquals("kalman-filter", json.get("experimentId").getAsString());
        assertEquals(2, json.get("repetitions").getAsInt());
        assertEquals(0, json.get("warmups").getAsInt());
        assertEquals(List.of("endOnly"),
                json.getAsJsonArray("scenarioIds").asList().stream().map(element -> element.getAsString()).toList());
        assertTrue(json.getAsJsonObject("host").has("hostId"));
        assertFalse(json.getAsJsonObject("host").has("hostname"),
                "Shared benchmark artifacts should not expose raw hostnames");
        assertTrue(json.has("profileHints"));

        JsonObject result = json.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals("endOnly", result.get("scenarioId").getAsString());
        assertEquals(16, result.get("barCount").getAsInt());
        assertTrue(result.get("checksumStable").getAsBoolean());
        assertTrue(result.getAsJsonObject("stats").get("operationsPerSecond").getAsDouble() > 0d);
        assertTrue(result.getAsJsonArray("measurements")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("counters")
                .has("sourceReads"));
    }

    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = BENCHMARK_PROPERTY, matches = "true")
    void benchmarkRunnerExecutesWhenExplicitlyEnabled() throws Exception {
        PerformanceExperimentRunner.run(new PerformanceExperimentRunner.RunRequest("kalman-filter", List.of(64),
                List.of("sequential", "endOnly"), 1, 0, Optional.of(tempDir.resolve("benchmark")), false));
    }
}
