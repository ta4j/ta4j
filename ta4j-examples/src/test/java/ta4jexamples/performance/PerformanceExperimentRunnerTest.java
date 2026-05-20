/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class PerformanceExperimentRunnerTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BENCHMARK_PROPERTY = "ta4j.runBenchmarks";

    @TempDir
    Path tempDir;

    @Test
    void cliRejectsInvalidBarCounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceExperimentRunner.RunnerCli
                        .parse(new String[] { "--experiment", "kalman-filter", "--barCounts", "0" }));

        assertEquals("--barCounts values must be positive", exception.getMessage());
    }

    @Test
    void runnerRejectsUnknownScenarios() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceExperimentRunner.run(new String[] { "--experiment", "kalman-filter", "--barCounts",
                        "16", "--scenarios", "missing", "--outputDir", tempDir.resolve("unknown").toString() }));

        assertEquals("Unknown scenario for kalman-filter: missing", exception.getMessage());
    }

    @Test
    void runnerWritesReusableKalmanArtifacts() throws Exception {
        Path outputDir = tempDir.resolve("kalman");

        PerformanceExperimentRunner.RunArtifacts artifacts = PerformanceExperimentRunner
                .run(new String[] { "--experiment", "kalman-filter", "--barCounts", "16", "--scenarios", "endOnly",
                        "--repetitions", "2", "--warmups", "0", "--outputDir", outputDir.toString(), "--profile" });

        assertEquals(outputDir.toAbsolutePath().normalize(), artifacts.outputDir());
        assertTrue(Files.exists(outputDir.resolve(PerformanceExperimentRunner.PERFORMANCE_FILE)));
        assertTrue(Files.exists(outputDir.resolve(PerformanceExperimentRunner.SUMMARY_FILE)));

        JsonObject json = artifacts.performanceJson();
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
    void comparisonRejectsChecksumMismatch() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 11L, 900L);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts with mismatched checksums", exception.getMessage());
    }

    @Test
    void comparisonRejectsMismatchedExperimentInputs() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 16, 10L, 1_000L);
        writePerformanceJson(candidateDir, 32, 10L, 900L);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts with different experiment inputs", exception.getMessage());
    }

    @Test
    void comparisonWritesDeltasForMatchingArtifacts() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L, 2_000L);
        writePerformanceJson(candidateDir, 10L, 500L, 900L);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.SUMMARY_FILE)));
        assertTrue(comparison.get("checksumMatch").getAsBoolean());
        assertEquals(-50d,
                comparison.getAsJsonArray("cells").get(0).getAsJsonObject().get("medianDeltaPct").getAsDouble());
        assertEquals(2d,
                comparison.getAsJsonArray("cells")
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("scaling")
                        .get("barCountScale")
                        .getAsDouble());
        assertEquals(1.8d,
                comparison.getAsJsonArray("cells")
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("scaling")
                        .get("candidateMedianScale")
                        .getAsDouble());
    }

    @Test
    void comparisonFailsWhenRegressionExceedsThreshold() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 1_200L);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d));

        assertEquals("Performance regression exceeded threshold", exception.getMessage());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.SUMMARY_FILE)));
    }

    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = BENCHMARK_PROPERTY, matches = "true")
    void benchmarkRunnerExecutesWhenExplicitlyEnabled() throws Exception {
        PerformanceExperimentRunner.main(new String[] { "--experiment", "kalman-filter", "--barCounts", "64",
                "--scenarios", "sequential,endOnly", "--repetitions", "1", "--warmups", "0", "--outputDir",
                tempDir.resolve("benchmark").toString() });
    }

    private void writePerformanceJson(Path outputDir, long checksum, long medianNanos) throws IOException {
        writePerformanceJson(outputDir, checksum, List.of(new ResultFixture(16, medianNanos)));
    }

    private void writePerformanceJson(Path outputDir, int barCount, long checksum, long medianNanos)
            throws IOException {
        writePerformanceJson(outputDir, checksum, List.of(new ResultFixture(barCount, medianNanos)));
    }

    private void writePerformanceJson(Path outputDir, long checksum, long firstMedianNanos, long secondMedianNanos)
            throws IOException {
        writePerformanceJson(outputDir, checksum,
                List.of(new ResultFixture(16, firstMedianNanos), new ResultFixture(32, secondMedianNanos)));
    }

    private void writePerformanceJson(Path outputDir, long checksum, List<ResultFixture> results) throws IOException {
        Files.createDirectories(outputDir);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("experimentId", "kalman-filter");
        root.addProperty("gitRef", "test");
        root.addProperty("repetitions", 1);
        root.addProperty("warmups", 0);
        JsonArray barCounts = new JsonArray();
        for (ResultFixture result : results) {
            barCounts.add(result.barCount());
        }
        root.add("barCounts", barCounts);
        JsonArray scenarioIds = new JsonArray();
        scenarioIds.add("endOnly");
        root.add("scenarioIds", scenarioIds);

        JsonArray resultArray = new JsonArray();
        for (ResultFixture fixture : results) {
            JsonObject result = new JsonObject();
            result.addProperty("scenarioId", "endOnly");
            result.addProperty("barCount", fixture.barCount());
            result.addProperty("checksum", checksum);
            result.addProperty("checksumStable", true);
            JsonObject stats = new JsonObject();
            stats.addProperty("medianNanos", fixture.medianNanos());
            stats.addProperty("operationsPerSecond", 1_000_000d);
            result.add("stats", stats);
            resultArray.add(result);
        }
        root.add("results", resultArray);

        Files.writeString(outputDir.resolve(PerformanceExperimentRunner.PERFORMANCE_FILE),
                GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private record ResultFixture(int barCount, long medianNanos) {
    }
}
