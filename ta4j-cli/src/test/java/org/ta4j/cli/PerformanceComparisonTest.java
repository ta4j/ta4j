/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertTrue(exception.getMessage().contains("different experiment input"), exception.getMessage());
    }

    @Test
    void comparisonRejectsMismatchedJvmOptions() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateFile = candidateDir.resolve("performance.json");
        JsonObject root = JsonParser.parseString(Files.readString(candidateFile)).getAsJsonObject();
        root.getAsJsonObject("host").addProperty("jvmOptionsFingerprint", "sha256:other-jvm");
        Files.writeString(candidateFile, GSON.toJson(root), StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertTrue(exception.getMessage().contains("jvmOptionsFingerprint"), exception.getMessage());
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

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "0", "-3", "1.5", "\"16\"" })
    void comparisonRejectsInvalidBarCountElements(String element) throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        JsonObject candidate = JsonParser.parseString(Files.readString(candidateArtifact)).getAsJsonObject();
        candidate.getAsJsonArray("barCounts").add(JsonParser.parseString(element));
        Files.writeString(candidateArtifact, GSON.toJson(candidate), StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));
        assertTrue(exception.getMessage().contains("barCounts[1] must be a positive integer"));
        assertTrue(exception.getMessage().contains(candidateArtifact.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "\"  \"", "7" })
    void comparisonRejectsInvalidScenarioIdElements(String element) throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateArtifact = candidateDir.resolve("performance.json");
        JsonObject candidate = JsonParser.parseString(Files.readString(candidateArtifact)).getAsJsonObject();
        candidate.getAsJsonArray("scenarioIds").add(JsonParser.parseString(element));
        Files.writeString(candidateArtifact, GSON.toJson(candidate), StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));
        assertTrue(exception.getMessage().contains("scenarioIds[1] must be a non-blank string"));
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertTrue(exception.getMessage().contains("different host"), exception.getMessage());
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, tempDir.resolve("comparison"), 5d));

        assertTrue(exception.getMessage().contains("different host"), exception.getMessage());
    }

    @Test
    void comparisonRejectsArtifactsWithUnknownHostIdOnBothSides() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        setHostId(baseDir, "unknown");
        setHostId(candidateDir, "unknown");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
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

        assertEquals("Duplicate result cell: endOnly:16", exception.getMessage());
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
        host.addProperty("jvmOptionsFingerprint", "sha256:fixture-jvm");
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
    void comparisonRejectsCandidateDirAliasingBaseDir() throws Exception {
        Path baseDir = tempDir.resolve("base");
        writePerformanceJson(baseDir, 10L, 1_000L);
        Path symlinkAlias = tempDir.resolve("base-alias");
        Files.createSymbolicLink(symlinkAlias, baseDir);
        Path outputDir = tempDir.resolve("comparison");

        for (Path candidateDir : List.of(baseDir, tempDir.resolve("other").resolve("..").resolve("base"),
                symlinkAlias)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d));

            assertEquals("--candidate-dir must not refer to the --base-dir directory", exception.getMessage());
        }
        assertFalse(Files.exists(outputDir));
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

    @ParameterizedTest
    @ValueSource(strings = { "base", "candidate" })
    public void comparisonRejectsOversizedArtifactsBeforeWritingOutput(String oversizedSide) throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path oversizedFile = tempDir.resolve(oversizedSide).resolve("performance.json");
        try (RandomAccessFile file = new RandomAccessFile(oversizedFile.toFile(), "rw")) {
            file.setLength((long) CliSupport.MAX_SERIALIZED_INPUT_BYTES + 1);
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d));

        assertEquals(
                "Serialized input " + oversizedFile + " exceeds " + CliSupport.MAX_SERIALIZED_INPUT_BYTES + " bytes.",
                exception.getMessage());
        assertFalse(Files.exists(outputDir), "An oversized input must not produce comparison artifacts");
    }

    @Test
    public void comparisonAcceptsAnArtifactAtTheSerializedInputLimit() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, 10L, 1_000L);
        writePerformanceJson(candidateDir, 10L, 900L);
        Path candidateFile = candidateDir.resolve("performance.json");
        String json = Files.readString(candidateFile, StandardCharsets.UTF_8);
        Files.writeString(candidateFile,
                json + " ".repeat(CliSupport.MAX_SERIALIZED_INPUT_BYTES - json.getBytes(StandardCharsets.UTF_8).length),
                StandardCharsets.UTF_8);

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertTrue(comparison.get("checksumMatch").getAsBoolean());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "repetitions=0", "repetitions=-1", "repetitions=0.5", "repetitions=2147483648",
            "repetitions=1e309", "warmups=-1", "warmups=0.5", "warmups=2147483648", "barCount=0", "barCount=-1",
            "barCount=16.5", "barCount=4294967312", "checksum=10.5", "checksum=18446744073709551626", "medianNanos=-1",
            "medianNanos=0.5", "medianNanos=9223372036854775808", "medianNanos=1e309", "operationsPerSecond=-1",
            "operationsPerSecond=-1e-400", "operationsPerSecond=1e309", "operationsPerSecond=1e-2147483649",
            "availableProcessors=0", "availableProcessors=1.5", "availableProcessors=2147483648" })
    public void comparisonRejectsInvalidNumericInputsBeforeWritingOutput(String invalidNumber) throws Exception {
        String[] parts = invalidNumber.split("=", 2);
        String field = parts[0];
        for (String side : List.of("base", "candidate")) {
            Path caseDir = tempDir.resolve(side);
            Path baseDir = caseDir.resolve("base");
            Path candidateDir = caseDir.resolve("candidate");
            Path outputDir = caseDir.resolve("comparison");
            writePerformanceJson(baseDir, 10L, 1_000L);
            writePerformanceJson(candidateDir, 10L, 900L);
            Path invalidArtifact = caseDir.resolve(side).resolve("performance.json");
            JsonObject artifact = JsonParser.parseString(Files.readString(invalidArtifact)).getAsJsonObject();
            JsonObject result = artifact.getAsJsonArray("results").get(0).getAsJsonObject();
            JsonObject target = switch (field) {
            case "medianNanos", "operationsPerSecond" -> result.getAsJsonObject("stats");
            case "barCount", "checksum" -> result;
            case "availableProcessors" -> artifact.getAsJsonObject("host");
            default -> artifact;
            };
            target.add(field, JsonParser.parseString(parts[1]));
            Files.writeString(invalidArtifact, GSON.toJson(artifact), StandardCharsets.UTF_8);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d));

            assertTrue(exception.getMessage().contains(invalidArtifact.toString()), exception.getMessage());
            assertTrue(exception.getMessage().contains("'" + field + "'"), exception.getMessage());
            assertFalse(Files.exists(outputDir), "Invalid numeric input must not produce comparison artifacts");
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, Long.MAX_VALUE })
    public void comparisonAcceptsExactNumericBoundaries(long medianNanos) throws Exception {
        Path baseDir = tempDir.resolve("base");
        Path candidateDir = tempDir.resolve("candidate");
        Path outputDir = tempDir.resolve("comparison");
        writePerformanceJson(baseDir, Integer.MAX_VALUE, Long.MIN_VALUE, medianNanos);
        writePerformanceJson(candidateDir, Integer.MAX_VALUE, Long.MIN_VALUE, medianNanos);
        for (Path dir : List.of(baseDir, candidateDir)) {
            Path file = dir.resolve("performance.json");
            JsonObject artifact = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            artifact.addProperty("warmups", 0);
            artifact.getAsJsonArray("results")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("stats")
                    .addProperty("operationsPerSecond", 0.5d);
            Files.writeString(file, GSON.toJson(artifact), StandardCharsets.UTF_8);
        }

        JsonObject comparison = PerformanceComparison.compare(baseDir, candidateDir, outputDir, 5d);

        assertTrue(comparison.get("checksumMatch").getAsBoolean());
        assertTrue(comparison.get("regressionWithinThreshold").getAsBoolean());
        assertTrue(Files.exists(outputDir.resolve(PerformanceComparison.COMPARISON_FILE)));
    }

    private record ResultFixture(int barCount, long medianNanos) {
    }
}
