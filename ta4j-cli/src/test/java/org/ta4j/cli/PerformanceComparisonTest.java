/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

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
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformanceComparisonTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path tempDir;

    @Test
    void comparisonReportsChecksumMismatchInPayload() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 11L, 900L);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertFalse(comparison.get("checksumMatch").getAsBoolean());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.SUMMARY_FILE)));
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
    void comparisonIdentifiesMissingRequiredMetadata() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path baseArtifact = baseDir.resolve("performance.json");
        JsonObject base = JsonParser.parseString(Files.readString(baseArtifact)).getAsJsonObject();
        base.remove("warmups");
        Files.writeString(baseArtifact, GSON.toJson(base), StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertTrue(exception.getMessage().contains("missing required field 'warmups'"));
        assertTrue(exception.getMessage().contains(baseArtifact.toString()));
    }

    @Test
    void comparisonRejectsEmptyArtifactShape() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        Files.createDirectories(candidateDir);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        Files.writeString(candidateArtifact, "{}", StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));
        assertTrue(exception.getMessage().contains("missing required field 'experimentId'"));
        assertTrue(exception.getMessage().contains(candidateArtifact.toString()));
    }

    @Test
    void comparisonRejectsMistypedArtifactField() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        JsonObject candidate = JsonParser.parseString(Files.readString(candidateArtifact)).getAsJsonObject();
        candidate.addProperty("repetitions", "not-a-number");
        Files.writeString(candidateArtifact, GSON.toJson(candidate), StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));
        assertTrue(exception.getMessage().contains("field 'repetitions' must be a number"));
        assertTrue(exception.getMessage().contains(candidateArtifact.toString()));
    }

    @Test
    void comparisonRejectsMalformedPerformanceArtifact() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        Files.createDirectories(candidateDir);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        Files.writeString(candidateArtifact, "{\"results\": [truncated", StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));
        assertTrue(exception.getMessage().startsWith("Invalid performance artifact "));
        assertTrue(exception.getMessage().contains(candidateArtifact.toString()));
    }

    @Test
    void comparisonRejectsCrossHostArtifacts() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        JsonObject candidate = JsonParser.parseString(Files.readString(candidateArtifact)).getAsJsonObject();
        candidate.getAsJsonObject("host").addProperty("hostId", "sha256:other-host");
        Files.writeString(candidateArtifact, GSON.toJson(candidate), StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts from different hosts", exception.getMessage());
    }

    @Test
    void comparisonRejectsArtifactsWithDifferentOsVersions() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        JsonObject candidate = JsonParser.parseString(Files.readString(candidateArtifact)).getAsJsonObject();
        candidate.getAsJsonObject("host").addProperty("osVersion", "fixture-version-upgraded");
        Files.writeString(candidateArtifact, GSON.toJson(candidate), StandardCharsets.UTF_8);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts from different hosts", exception.getMessage());
    }

    @Test
    void comparisonRejectsArtifactsWithUnknownHostIdOnBothSides() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        setHostId(baseDir, "unknown");
        setHostId(candidateDir, "unknown");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts when the host ID is unknown", exception.getMessage());
    }

    @Test
    void comparisonRejectsArtifactWithUnknownHostIdOnOneSide() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        setHostId(candidateDir, "unknown");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Cannot compare performance artifacts when the host ID is unknown", exception.getMessage());
    }

    private void setHostId(Path artifactDir, String hostId) throws IOException {
        Path artifact = artifactDir.resolve("performance.json");
        JsonObject root = JsonParser.parseString(Files.readString(artifact)).getAsJsonObject();
        root.getAsJsonObject("host").addProperty("hostId", hostId);
        Files.writeString(artifact, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Test
    void comparisonRejectsDuplicateResultCells() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, List.of(new ResultFixture(16, 1_000L), new ResultFixture(16, 900L)));
        writePerformanceJson(candidateDir, 10L, List.of(new ResultFixture(16, 1_000L), new ResultFixture(16, 900L)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertEquals("Invalid performance artifact " + baseDir.resolve("performance.json")
                + ": duplicate result cell: endOnly:16", exception.getMessage());
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
    void comparisonReportsRegressionInPayloadInsteadOfThrowing() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 1_200L);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertFalse(comparison.get("regressionWithinThreshold").getAsBoolean());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.SUMMARY_FILE)));
        // The per-cell delta that failed the gate stays available to callers.
        double delta = comparison.getAsJsonArray("cells").get(0).getAsJsonObject().get("medianDeltaPct").getAsDouble();
        assertTrue(delta > 5d, "reported median delta must exceed the 5% gate");
    }

    @Test
    void comparisonReportsZeroBaselineRegressionInPayload() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 0L);
        writePerformanceJson(candidateDir, 10L, 1L);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertFalse(comparison.get("regressionWithinThreshold").getAsBoolean());
        assertTrue(comparison.getAsJsonArray("cells")
                .get(0)
                .getAsJsonObject()
                .get("medianDeltaPct")
                .getAsDouble() > 1e300d);
    }

    @Test
    void comparisonReportsUnboundedScalingFromZero() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 0L, 1L);
        writePerformanceJson(candidateDir, 10L, 0L, 1L);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        JsonObject scaling = comparison.getAsJsonArray("cells").get(1).getAsJsonObject().getAsJsonObject("scaling");
        assertTrue(scaling.get("baseMedianScale").getAsDouble() > 1e300d);
        assertTrue(scaling.get("candidateMedianScale").getAsDouble() > 1e300d);
        String summary = Files.readString(outputDir.resolve(PerformanceComparison.SUMMARY_FILE));
        assertTrue(summary.contains("| Infinity |"));
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

        JsonObject host = new JsonObject();
        host.addProperty("hostId", "sha256:fixture");
        host.addProperty("osName", "fixture-os");
        host.addProperty("osArch", "fixture-arch");
        host.addProperty("osVersion", "fixture-version");
        host.addProperty("javaVersion", "fixture-java");
        host.addProperty("jvmName", "fixture-jvm");
        host.addProperty("availableProcessors", 1);
        root.add("host", host);

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

        Files.writeString(outputDir.resolve("performance.json"), GSON.toJson(root) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    @Test
    void comparisonRejectsOutputDirAliasingBaseDir() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, baseDir, 5d));

        assertEquals("--output-dir must not refer to the --base-dir directory", exception.getMessage());
        assertFalse(Files.exists(baseDir.resolve(PerformanceComparison.SUMMARY_FILE)));
    }

    @Test
    void comparisonRejectsOutputDirSymlinkAliasOfCandidateDir() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path alias = tempDir.resolve("candidate-alias");
        Files.createSymbolicLink(alias, candidateDir);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, alias, 5d));

        assertEquals("--output-dir must not refer to the --candidate-dir directory", exception.getMessage());
        assertFalse(Files.exists(alias.resolve(PerformanceComparison.SUMMARY_FILE)));
    }

    @Test
    void comparisonAllowsOutputDirNestedInsideBaseDir() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path outputDir = baseDir.resolve("comparison");

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertFalse(comparison.isJsonNull());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
        assertFalse(Files.exists(baseDir.resolve(PerformanceComparison.SUMMARY_FILE)));
    }

    private record ResultFixture(int barCount, long medianNanos) {
    }
}
