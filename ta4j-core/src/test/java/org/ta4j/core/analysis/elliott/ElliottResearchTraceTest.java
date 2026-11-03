/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DoubleNum;

class ElliottResearchTraceTest {

    private static final long SEED = 5_252_026L;

    @TempDir
    Path directory;

    @Test
    void realTraceReconstructsEveryHypothesisModeTally() throws Exception {
        final StudyRunner runner = runner(StudyRunner.Partitions.lockedDefault(), 2);
        final Path file = directory.resolve("traces/BTC.real.jsonl");
        final StudyReport report;
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            report = runner.evaluate("BTC", buildSeries(24), 0, 23, trace);
        }

        final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file);
        assertTrue(parsed.complete());
        final Map<String, Counts> rebuilt = new HashMap<>();
        for (final JsonObject record : parsed.records()) {
            final String section = record.get("section").getAsString();
            if (!"h1".equals(section) && !"h2".equals(section)) {
                continue;
            }
            final String key = section + "|" + record.get("mode").getAsString() + "|"
                    + record.get("partition").getAsString();
            final Counts counts = rebuilt.computeIfAbsent(key, ignored -> new Counts());
            counts.add(record);
        }

        int comparedModes = 0;
        long completeObservations = 0;
        for (final String section : List.of("h1", "h2")) {
            final List<StudyReport.ModeReport> modes = "h1".equals(section) ? report.h1().modes() : report.h2().modes();
            for (final StudyReport.ModeReport mode : modes) {
                comparedModes++;
                for (final StudyReport.PartitionMetrics metrics : mode.partitions()) {
                    final String key = section + "|" + mode.mode() + "|" + metrics.partition();
                    assertEquals(Counts.of(metrics), rebuilt.getOrDefault(key, new Counts()), key);
                    completeObservations += metrics.completeCount();
                }
            }
        }
        assertEquals(5, comparedModes);
        assertTrue(completeObservations > 0);
        assertTrue(rebuilt.values().stream().anyMatch(counts -> counts.evidencePass > 0));
    }

    @Test
    void repeatedCompleteObservationsShareOneCandidateKey() throws Exception {
        final Path file = directory.resolve("BTC.real.jsonl");
        traceEvaluation(file, buildSeries(24), 0, 23);

        final Map<String, List<JsonObject>> completeByKey = new HashMap<>();
        for (final JsonObject record : ElliottResearchTrace.read(file).records()) {
            if ("h1".equals(record.get("section").getAsString())
                    && "COMPLETE".equals(record.get("status").getAsString())) {
                final JsonArray candidates = record.getAsJsonArray("candidates");
                assertEquals(1, candidates.size());
                final JsonObject candidate = candidates.get(0).getAsJsonObject();
                completeByKey.computeIfAbsent(candidate.get("candidateKey").getAsString(), ignored -> new ArrayList<>())
                        .add(record);
            }
        }

        final long observations = completeByKey.values().stream().mapToLong(List::size).sum();
        assertTrue(observations > completeByKey.size(), "occupancy must span several as-of rows");
        for (final List<JsonObject> rows : completeByKey.values()) {
            final Set<Integer> asOf = new HashSet<>();
            final Set<JsonElement> placements = new HashSet<>();
            for (final JsonObject row : rows) {
                assertTrue(asOf.add(row.get("asOfIndex").getAsInt()));
                placements.add(row.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonArray("placement"));
            }
            assertEquals(1, placements.size());
        }
    }

    @Test
    void ambiguousCandidatesKeepTheirOwnKeysAndRuleRows() throws Exception {
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5,
                alternating(10, 20, 14, 26, 18, 32, 24, 40, 30, 48, 36, 19));
        assertEquals(TopologyStatus.AMBIGUOUS, analysis.status());
        final List<List<RuleEvidence>> evidence = new ArrayList<>();
        for (int i = 0; i < analysis.candidates().size(); i++) {
            evidence.add(List.of(RuleEvidence.scored("first", 0.25d * (i + 1), List.of("candidate-" + i), "scored"),
                    i % 2 == 0 ? RuleEvidence.pass("second", List.of("raw-" + i), "pass")
                            : RuleEvidence.fail("second", List.of("raw-" + i), "fail")));
        }
        final Path file = directory.resolve("ambiguous.jsonl");
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            trace.topology(StudyObserver.Scope.real("h2", "all-rules", "MOTIVE_5", List.of("first", "second"), "d"),
                    "calibration", 11, Instant.parse("2018-01-12T00:00:00Z"),
                    alternating(10, 20, 14, 26, 18, 32, 24, 40, 30, 48, 36, 19), analysis, evidence);
        }

        final JsonObject record = ElliottResearchTrace.read(file).records().get(0);
        assertEquals("AMBIGUOUS", record.get("status").getAsString());
        final JsonArray candidates = record.getAsJsonArray("candidates");
        assertEquals(analysis.candidates().size(), candidates.size());
        final Set<String> keys = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            final JsonObject candidate = candidates.get(i).getAsJsonObject();
            keys.add(candidate.get("candidateKey").getAsString());
            assertEquals(ElliottResearchTrace.candidateKey(analysis.candidates().get(i)),
                    candidate.get("candidateKey").getAsString());
            assertTrue(candidate.get("version")
                    .getAsString()
                    .startsWith(candidate.get("candidateKey").getAsString() + "@"));
            final JsonArray rules = candidate.getAsJsonArray("rules");
            assertEquals(2, rules.size());
            assertEquals(0.25d * (i + 1), rules.get(0).getAsJsonObject().get("score").getAsDouble(), 1e-12);
            assertEquals("candidate-" + i,
                    rules.get(0).getAsJsonObject().getAsJsonArray("observations").get(0).getAsString());
            assertEquals(i % 2 == 0 ? "PASS" : "FAIL", rules.get(1).getAsJsonObject().get("state").getAsString());
            assertTrue(rules.get(1).getAsJsonObject().get("score").isJsonNull());
        }
        assertEquals(analysis.candidates().size(), keys.size());
    }

    @Test
    void candidateIdentityCoversTheFullPlacementAndVersionsAreContentAddressed() throws Exception {
        final TopologyCandidate first = motive(10, 20, 14, 26, 18, 32);
        final TopologyCandidate sameEndpoints = motive(10, 20, 15, 26, 18, 32);
        assertEquals(first.startBarIndex(), sameEndpoints.startBarIndex());
        assertEquals(first.endBarIndex(), sameEndpoints.endBarIndex());
        assertNotEquals(ElliottResearchTrace.candidateKey(first), ElliottResearchTrace.candidateKey(sameEndpoints));
        assertEquals(ElliottResearchTrace.candidateKey(first),
                ElliottResearchTrace.candidateKey(motive(10, 20, 14, 26, 18, 32)));

        final List<RuleEvidence> passing = List.of(RuleEvidence.pass("first", List.of("a"), "pass"));
        final List<RuleEvidence> failing = List.of(RuleEvidence.fail("first", List.of("a"), "fail"));
        final List<String> forward = versions("forward.jsonl", first, List.of(passing, passing, failing));
        final List<String> reverse = versions("reverse.jsonl", first, List.of(failing, passing));

        final String key = ElliottResearchTrace.candidateKey(first);
        assertTrue(forward.stream().allMatch(version -> version.startsWith(key + "@")), forward.toString());
        assertEquals(forward.get(0), forward.get(1));
        assertNotEquals(forward.get(0), forward.get(2));
        // Identical evidence names the same version whatever the file or emission
        // order.
        assertEquals(forward.get(0), reverse.get(1));
        assertEquals(forward.get(2), reverse.get(0));
        // The shared helper relation records reuse names exactly the version the
        // trace writes.
        assertEquals(forward.get(0), ElliottResearchTrace.candidateVersion(first, passing));
        assertEquals(forward.get(2), ElliottResearchTrace.candidateVersion(first, failing));
    }

    private List<String> versions(final String name, final TopologyCandidate candidate,
            final List<List<RuleEvidence>> emissions) throws Exception {
        final TopologyAnalysis analysis = new TopologyAnalysis(TopologyStatus.COMPLETE, WaveDirection.BULLISH,
                List.of(candidate), "complete", -1, -1);
        final StudyObserver.Scope scope = StudyObserver.Scope.real("h2", "all-rules", "MOTIVE_5", List.of("first"),
                "d");
        final Path file = directory.resolve(name);
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            for (int i = 0; i < emissions.size(); i++) {
                trace.topology(scope, "calibration", 5 + i,
                        Instant.parse("2018-01-06T00:00:00Z").plusSeconds(i * 86_400L), candidate.pivots(), analysis,
                        List.of(emissions.get(i)));
            }
            assertEquals(emissions.size(), trace.records());
        }
        return ElliottResearchTrace.read(file)
                .records()
                .stream()
                .map(record -> record.getAsJsonArray("candidates")
                        .get(0)
                        .getAsJsonObject()
                        .get("version")
                        .getAsString())
                .toList();
    }

    @Test
    void appendedBarsDoNotChangeTheTraceOfAnEarlierPrefix() throws Exception {
        final Path prefix = directory.resolve("prefix.jsonl");
        final Path appended = directory.resolve("appended.jsonl");
        traceEvaluation(prefix, buildSeries(24), 0, 19);
        traceEvaluation(appended, buildSeries(40), 0, 19);

        final List<String> prefixLines = Files.readAllLines(prefix, StandardCharsets.UTF_8);
        assertTrue(prefixLines.size() > 3);
        assertEquals(prefixLines, Files.readAllLines(appended, StandardCharsets.UTF_8));
    }

    @Test
    void replayedNullMemberTraceRecordsOnlyTheChosenMember() throws Exception {
        final StudyRunner.Partitions partitions = new StudyRunner.Partitions(
                List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 1)),
                        new StudyRunner.Partition("validation", LocalDate.of(2018, 1, 2), LocalDate.of(2018, 1, 12)),
                        new StudyRunner.Partition("holdout", LocalDate.of(2018, 1, 13), LocalDate.of(2018, 2, 28))),
                LocalDate.of(2024, 1, 1));
        final StudyRunner runner = runner(partitions, 3);
        final Path file = directory.resolve("BTC.null-b2-m1.jsonl");
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev-1", "fp-1", "abc123",
                ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER, 2, 1)) {
            runner.replayNullMember(buildSeries(24), 0, 23, 2, 1, trace);
            assertTrue(trace.records() > 0);
        }

        final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file);
        assertTrue(parsed.complete());
        assertEquals("selected-null-member", parsed.header().get("traceMode").getAsString());
        assertEquals("rev-1", parsed.header().get("revision").getAsString());
        assertEquals("fp-1", parsed.header().get("fingerprint").getAsString());
        assertEquals("abc123", parsed.header().get("sourceSha256").getAsString());
        assertEquals(2, parsed.header().get("nullBlockLength").getAsInt());
        assertEquals(1, parsed.header().get("nullMemberIndex").getAsInt());
        assertFalse(parsed.records().isEmpty());
        for (final JsonObject record : parsed.records()) {
            assertEquals("null", record.get("section").getAsString());
            assertEquals(2, record.get("nullBlockLength").getAsInt());
            assertEquals(1, record.get("nullMemberIndex").getAsInt());
            final JsonObject bar = record.getAsJsonObject("bar");
            for (final String field : List.of("begin", "end", "open", "high", "low", "close", "volume")) {
                assertTrue(bar.has(field) && !bar.get(field).getAsString().isEmpty(), field);
            }
            assertTrue(Instant.parse(bar.get("begin").getAsString())
                    .isBefore(Instant.parse(bar.get("end").getAsString())));
        }
    }

    @Test
    void traceRejectsObservationsFromAnotherMemberOrMode() throws Exception {
        final Path file = directory.resolve("guard.jsonl");
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            final StudyObserver.Scope nullScope = new StudyObserver.Scope("null", "topology-only", "MOTIVE_5",
                    List.of(), "d", 2, 0);
            assertThrows(IllegalArgumentException.class, () -> trace.alternative(nullScope, "calibration", 1,
                    Instant.parse("2018-01-02T00:00:00Z"), List.of(), "no-match", Set.of("no-match")));
            assertEquals(0L, trace.records());
        }
        assertThrows(IllegalArgumentException.class, () -> ElliottResearchTrace.open(directory.resolve("bad.jsonl"),
                "BTC", "rev", "fp", null, "all-null", -1, -1));
    }

    @Test
    void truncatedFileReadsAsIncompleteAndCorruptionIsRejectedWithLocation() throws Exception {
        final Path file = directory.resolve("BTC.real.jsonl");
        traceEvaluation(file, buildSeries(24), 0, 19);
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final int recordCount = lines.size() - 2;

        final Path noFooter = directory.resolve("no-footer.jsonl");
        Files.write(noFooter, lines.subList(0, lines.size() - 1), StandardCharsets.UTF_8);
        final ElliottResearchTrace.TraceFile truncated = ElliottResearchTrace.read(noFooter);
        assertFalse(truncated.complete());
        assertEquals(recordCount, truncated.records().size());

        final Path partialLine = directory.resolve("partial-line.jsonl");
        final String lastRecord = lines.get(lines.size() - 2);
        final List<String> partialLines = new ArrayList<>(lines.subList(0, lines.size() - 2));
        Files.writeString(partialLine,
                String.join("\n", partialLines) + "\n" + lastRecord.substring(0, lastRecord.length() / 2),
                StandardCharsets.UTF_8);
        final ElliottResearchTrace.TraceFile partial = ElliottResearchTrace.read(partialLine);
        assertFalse(partial.complete());
        assertEquals(recordCount - 1, partial.records().size());

        // Only an unterminated final line counts as a partial write; a terminated
        // corrupt line is corruption.
        final Path terminatedTail = directory.resolve("terminated-tail.jsonl");
        Files.writeString(terminatedTail, String.join("\n", partialLines) + "\n{\"dataset\":\n",
                StandardCharsets.UTF_8);
        final IllegalArgumentException terminatedError = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(terminatedTail));
        assertTrue(terminatedError.getMessage().contains("line " + (partialLines.size() + 1)),
                terminatedError.getMessage());

        // The retention filter bounds kept records without changing validation or the
        // footer count.
        final ElliottResearchTrace.TraceFile firstOnly = ElliottResearchTrace.read(file,
                record -> record.get("asOfIndex").getAsInt() == 0);
        assertTrue(firstOnly.complete());
        assertEquals(recordCount, firstOnly.recordCount());
        assertTrue(firstOnly.records().size() < recordCount, firstOnly.records().size() + " of " + recordCount);
        assertTrue(firstOnly.records().stream().allMatch(record -> record.get("asOfIndex").getAsInt() == 0));

        final Path corrupt = directory.resolve("corrupt.jsonl");
        final List<String> corruptLines = new ArrayList<>(lines);
        corruptLines.set(2, "{\"dataset\":");
        Files.write(corrupt, corruptLines, StandardCharsets.UTF_8);
        final IllegalArgumentException corruptError = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(corrupt));
        assertTrue(corruptError.getMessage().contains(corrupt.toString()), corruptError.getMessage());
        assertTrue(corruptError.getMessage().contains("line 3"), corruptError.getMessage());

        final Path wrongSchema = directory.resolve("wrong-schema.jsonl");
        final List<String> schemaLines = new ArrayList<>(lines);
        schemaLines.set(0, schemaLines.get(0).replace(ElliottResearchTrace.SCHEMA, "elliott-research-trace/2"));
        Files.write(wrongSchema, schemaLines, StandardCharsets.UTF_8);
        final IllegalArgumentException schemaError = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(wrongSchema));
        assertTrue(schemaError.getMessage().contains(wrongSchema.toString()), schemaError.getMessage());
        assertTrue(schemaError.getMessage().contains("line 1"), schemaError.getMessage());

        final Path wrongCount = directory.resolve("wrong-count.jsonl");
        final List<String> countLines = new ArrayList<>(lines);
        countLines.set(countLines.size() - 1, "{\"complete\":true,\"records\":" + (recordCount + 1) + "}");
        Files.write(wrongCount, countLines, StandardCharsets.UTF_8);
        final IllegalArgumentException countError = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(wrongCount));
        assertTrue(countError.getMessage().contains("line " + lines.size()), countError.getMessage());

        // A partial write is tolerated only before the footer; after it the
        // trace is corrupt, not complete.
        final Path tailAfterFooter = directory.resolve("tail-after-footer.jsonl");
        Files.writeString(tailAfterFooter, String.join("\n", lines) + "\n{\"dataset\":", StandardCharsets.UTF_8);
        final IllegalArgumentException tailError = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(tailAfterFooter));
        assertTrue(tailError.getMessage().contains("line " + (lines.size() + 1)), tailError.getMessage());
    }

    @Test
    public void everyRecordIsBoundToHeaderCoordinatesBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = coordinateTrace("coordinates-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final ElliottResearchTrace.TraceFile valid = ElliottResearchTrace.read(file);
            assertTrue(valid.complete());
            assertEquals(lines.size() - 2, valid.recordCount());
            final ElliottResearchTrace.TraceFile excluded = ElliottResearchTrace.read(file, record -> false);
            assertTrue(excluded.complete());
            assertEquals(valid.recordCount(), excluded.recordCount());
            assertTrue(excluded.records().isEmpty());

            for (int index = 1; index < lines.size() - 1; index++) {
                final JsonObject original = JsonParser.parseString(lines.get(index)).getAsJsonObject();
                for (final String field : List.of("dataset", "nullBlockLength", "nullMemberIndex")) {
                    final JsonObject foreign = original.deepCopy();
                    if ("dataset".equals(field)) {
                        foreign.addProperty(field, "ETH");
                    } else {
                        foreign.addProperty(field, original.get(field).getAsLong() + 1);
                    }
                    final IllegalArgumentException error = assertMalformedAtLine(lines, foreign, index + 1, field);
                    assertTrue(error.getMessage().contains(field + "=" + foreign.get(field)), error.getMessage());
                    assertTrue(error.getMessage().contains("do not match header dataset=BTC, nullBlockLength="),
                            error.getMessage());
                    if (!"dataset".equals(field)) {
                        // An out-of-range integer must not wrap back to the header's int.
                        foreign.addProperty(field, original.get(field).getAsLong() + (1L << 32));
                        final IllegalArgumentException overflow = assertMalformedAtLine(lines, foreign, index + 1,
                                field);
                        assertTrue(overflow.getMessage().contains(foreign.get(field).toString()),
                                overflow.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void headerCoordinatesMustHaveTheWritersShapeAndMode() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = coordinateTrace("header-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // Header validation also applies when there are no observations to retain.
            final List<String> emptyLines = List.of(lines.get(0), "{\"complete\":true,\"records\":0}");
            final JsonObject original = JsonParser.parseString(lines.get(0)).getAsJsonObject();
            for (final String field : List.of("dataset", "traceMode", "nullBlockLength", "nullMemberIndex")) {
                final JsonObject missing = original.deepCopy();
                missing.remove(field);
                assertMalformedAtLine(emptyLines, missing, 1, field);
                final JsonObject nullValue = original.deepCopy();
                nullValue.add(field, null);
                assertMalformedAtLine(emptyLines, nullValue, 1, field);
                final JsonObject wrongType = original.deepCopy();
                wrongType.add(field, "dataset".equals(field) || "traceMode".equals(field) ? new JsonPrimitive(1)
                        : new JsonPrimitive("1"));
                assertMalformedAtLine(emptyLines, wrongType, 1, field);
            }
            for (final String field : List.of("nullBlockLength", "nullMemberIndex")) {
                final JsonObject fractional = original.deepCopy();
                fractional.addProperty(field, 1.5);
                assertMalformedAtLine(emptyLines, fractional, 1, field);
                final JsonObject overflow = original.deepCopy();
                overflow.addProperty(field, original.get(field).getAsLong() + (1L << 32));
                assertMalformedAtLine(emptyLines, overflow, 1, field);
                for (final String number : List.of("1e2147483648", "100e2147483647")) {
                    final JsonObject oversized = original.deepCopy();
                    oversized.add(field, JsonParser.parseString(number));
                    final IllegalArgumentException error = assertMalformedAtLine(emptyLines, oversized, 1, field);
                    assertTrue(error.getMessage().contains(number), error.getMessage());
                }
                final JsonObject invalid = original.deepCopy();
                invalid.addProperty(field, selectedNull && "nullMemberIndex".equals(field) ? -1 : 0);
                assertMalformedAtLine(emptyLines, invalid, 1, field);
            }
            final JsonObject unsupportedMode = original.deepCopy();
            unsupportedMode.addProperty("traceMode", "all-null");
            assertMalformedAtLine(emptyLines, unsupportedMode, 1, "traceMode");
            final JsonObject otherMode = original.deepCopy();
            otherMode.addProperty("traceMode",
                    selectedNull ? ElliottResearchTrace.MODE_REAL : ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER);
            assertMalformedAtLine(emptyLines, otherMode, 1, "traceMode");
        }
    }

    @Test
    public void recordSectionMustAgreeWithTheHeaderTraceModeBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = coordinateTrace("section-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final JsonObject foreign = JsonParser.parseString(lines.get(1)).getAsJsonObject();
            foreign.addProperty("section", selectedNull ? "h1" : "null");
            final IllegalArgumentException error = assertMalformedAtLine(lines, foreign, 2, "section");
            assertTrue(error.getMessage().contains(foreign.get("section").toString()), error.getMessage());
            assertTrue(error.getMessage()
                    .contains("traceMode=" + (selectedNull ? ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER
                            : ElliottResearchTrace.MODE_REAL)),
                    error.getMessage());
        }
    }

    @Test
    public void recordStatusesMustMatchTheirKindBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = coordinateTrace("statuses-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 1; index < lines.size() - 1; index++) {
                final JsonObject original = JsonParser.parseString(lines.get(index)).getAsJsonObject();
                final boolean topology = "topology".equals(original.get("kind").getAsString());
                for (final String status : List.of("bogus", "", topology ? "complete" : "COMPLETE")) {
                    final JsonObject damaged = original.deepCopy();
                    damaged.addProperty("status", status);
                    final IllegalArgumentException error = assertMalformedAtLine(lines, damaged, index + 1, "status");
                    assertTrue(
                            error.getMessage()
                                    .contains(status.isBlank() ? "status is blank" : damaged.get("status").toString()),
                            error.getMessage());
                }
            }
        }
    }

    @Test
    public void asOfIndicesMustFitTheWriterIntegerRangeBeforeRetention() throws Exception {
        final Path file = coordinateTrace("as-of-range.jsonl", false);
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final String number : List.of("-2147483649", "2147483648", "4294967296", "1.0000000000000001",
                "1e2147483648", "100e2147483647")) {
            final JsonObject damaged = original.deepCopy();
            damaged.add("asOfIndex", JsonParser.parseString(number));
            assertMalformedAtLine(lines, damaged, 2, "asOfIndex");
        }
        for (final int boundary : List.of(Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE)) {
            final JsonObject supported = original.deepCopy();
            supported.addProperty("asOfIndex", boundary);
            final List<String> supportedLines = new ArrayList<>(lines);
            supportedLines.set(1, supported.toString());
            Files.write(file, supportedLines, StandardCharsets.UTF_8);
            final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file);
            assertTrue(parsed.complete());
            assertEquals(boundary, parsed.records().getFirst().get("asOfIndex").getAsInt());
        }
    }

    @Test
    public void footerCountMustExactlyMatchWithoutLongNarrowing() throws Exception {
        final Path file = coordinateTrace("footer-count-range.jsonl", false);
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size() - 2);
        final JsonObject original = JsonParser.parseString(lines.getLast()).getAsJsonObject();
        for (final String number : List.of("2.5", "18446744073709551618", "1e2147483648", "100e2147483647")) {
            final JsonObject damaged = original.deepCopy();
            damaged.add("records", JsonParser.parseString(number));
            assertMalformedAtLine(lines, damaged, lines.size(), "record");
        }
    }

    private Path coordinateTrace(final String name, final boolean selectedNull) throws IOException {
        final Path file = directory.resolve(name);
        final int blockLength = selectedNull ? 2 : -1;
        final int memberIndex = selectedNull ? 1 : -1;
        final StudyObserver.Scope scope = new StudyObserver.Scope(selectedNull ? "null" : "h1",
                selectedNull ? "MOTIVE_5" : "topology-only", "MOTIVE_5", List.of(), "d", blockLength, memberIndex);
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                selectedNull ? ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER : ElliottResearchTrace.MODE_REAL,
                blockLength, memberIndex)) {
            trace.topology(scope, "calibration", 0, Instant.parse("2018-01-01T00:00:00Z"), List.of(),
                    TopologyAnalysis.insufficientHistory("single observation"), List.of());
            if (!selectedNull) {
                trace.alternative(
                        StudyObserver.Scope.real("competing", "competing-change-point-baseline",
                                "change-point-baseline", List.of(), "d"),
                        "calibration", 0, Instant.parse("2018-01-01T00:00:00Z"), List.of(), "insufficient-history",
                        Set.of("insufficient-history"));
            }
        }
        return file;
    }

    @Test
    public void blankRecordIdentityFieldsAreRejectedBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = coordinateTrace("blank-identities-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 1; index < lines.size() - 1; index++) {
                final JsonObject original = JsonParser.parseString(lines.get(index)).getAsJsonObject();
                for (final String field : List.of("dataset", "section", "mode", "grammar", "detector", "partition",
                        "asOfTime", "status")) {
                    for (final String blank : List.of("", " ", "\t\r\n")) {
                        final JsonObject damaged = original.deepCopy();
                        damaged.addProperty(field, blank);
                        final IllegalArgumentException error = assertMalformedAtLine(lines, damaged, index + 1, field);
                        assertTrue(error.getMessage().contains(field + " is blank"), error.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void recordLinesMissingWrittenFieldsAreRejectedWithLocation() throws Exception {
        // Regression: any object with a "kind" member counted as an observation,
        // so a hand-edited line matched the footer count and fed inspection.
        final Path file = directory.resolve("BTC.real.jsonl");
        traceEvaluation(file, buildSeries(24), 0, 19);
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        final List<String> damages = List.of("dataset", "asOfIndex", "activeRules", "candidates", "status",
                "formingStartBarIndex", "formingEndBarIndex", "explanation");
        for (final String field : damages) {
            final JsonObject damaged = original.deepCopy();
            damaged.remove(field);
            assertMalformedAtLine2(lines, damaged, field);
        }
        final JsonObject wrongKind = original.deepCopy();
        wrongKind.addProperty("kind", "summary");
        assertMalformedAtLine2(lines, wrongKind, "kind");
        final JsonObject fractionalIndex = original.deepCopy();
        fractionalIndex.addProperty("asOfIndex", 1.5);
        assertMalformedAtLine2(lines, fractionalIndex, "asOfIndex");
        final JsonObject alternative = original.deepCopy();
        alternative.addProperty("kind", "alternative");
        alternative.addProperty("status", "insufficient-history");
        alternative.remove("labels");
        assertMalformedAtLine2(lines, alternative, "labels");

        // Regression: nested entries were unchecked, so a malformed candidate or rule
        // crashed inspection instead of reporting the corrupt line.
        final JsonObject nested = lines.stream()
                .skip(1)
                .map(line -> JsonParser.parseString(line).getAsJsonObject())
                .filter(record -> record.has("candidates") && !record.getAsJsonArray("candidates").isEmpty()
                        && !record.getAsJsonArray("pivots").isEmpty()
                        && !firstCandidate(record).getAsJsonArray("rules").isEmpty()
                        && !firstCandidate(record).getAsJsonArray("placement").isEmpty())
                .findFirst()
                .orElseThrow();
        for (final String number : List.of("-2147483649", "2147483648", "4294967296", "1.0000000000000001",
                "1e2147483648", "100e2147483647")) {
            for (final String field : List.of("index", "confirmationIndex")) {
                final JsonObject damaged = nested.deepCopy();
                damaged.getAsJsonArray("pivots").get(0).getAsJsonObject().add(field, JsonParser.parseString(number));
                assertMalformedAtLine2(lines, damaged, "pivots[0]." + field);
            }
            final JsonObject damaged = nested.deepCopy();
            firstCandidate(damaged).getAsJsonArray("placement")
                    .get(0)
                    .getAsJsonObject()
                    .add("index", JsonParser.parseString(number));
            assertMalformedAtLine2(lines, damaged, "candidates[0].placement[0].index");
        }
        final JsonObject emptyCandidate = nested.deepCopy();
        emptyCandidate.getAsJsonArray("candidates").set(0, new JsonObject());
        assertMalformedAtLine2(lines, emptyCandidate, "candidates[0].candidateKey");
        final JsonObject scalarRules = nested.deepCopy();
        firstCandidate(scalarRules).addProperty("rules", "none");
        assertMalformedAtLine2(lines, scalarRules, "candidates[0].rules");
        final JsonObject stateless = nested.deepCopy();
        firstCandidate(stateless).getAsJsonArray("rules").get(0).getAsJsonObject().remove("state");
        assertMalformedAtLine2(lines, stateless, "candidates[0].rules[0].state");
        for (final String state : List.of("bogus", "pass", "", "PASS ")) {
            final JsonObject damaged = nested.deepCopy();
            firstCandidate(damaged).getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("state", state);
            assertMalformedAtLine2(lines, damaged, "candidates[0].rules[0].state");
        }
        final int nestedLine = lines.indexOf(nested.toString());
        assertTrue(nestedLine > 0);
        for (final EvidenceState state : EvidenceState.values()) {
            final JsonObject supported = nested.deepCopy();
            firstCandidate(supported).getAsJsonArray("rules")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("state", state.name());
            refreshVersion(firstCandidate(supported));
            final List<String> supportedLines = new ArrayList<>(lines);
            supportedLines.set(nestedLine, supported.toString());
            final Path supportedFile = directory.resolve("supported-rule-state.jsonl");
            Files.write(supportedFile, supportedLines, StandardCharsets.UTF_8);
            assertTrue(ElliottResearchTrace.read(supportedFile).complete());
        }
        final JsonObject textScore = nested.deepCopy();
        firstCandidate(textScore).getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("score", "high");
        assertMalformedAtLine2(lines, textScore, "candidates[0].rules[0].score");
        final JsonObject scalarPlacement = nested.deepCopy();
        firstCandidate(scalarPlacement).getAsJsonArray("placement").set(0, new JsonPrimitive(3));
        assertMalformedAtLine2(lines, scalarPlacement, "candidates[0].placement[0]");
        final JsonObject pivotWithoutIndex = nested.deepCopy();
        pivotWithoutIndex.getAsJsonArray("pivots").get(0).getAsJsonObject().remove("index");
        assertMalformedAtLine2(lines, pivotWithoutIndex, "pivots[0].index");
        final JsonObject numericRule = nested.deepCopy();
        numericRule.getAsJsonArray("activeRules").set(0, new JsonPrimitive(1));
        assertMalformedAtLine2(lines, numericRule, "activeRules[0]");
        final JsonObject arrayDirection = nested.deepCopy();
        arrayDirection.add("direction", new JsonArray());
        assertMalformedAtLine2(lines, arrayDirection, "direction");
        final JsonObject objectLabel = original.deepCopy();
        objectLabel.addProperty("kind", "alternative");
        objectLabel.addProperty("status", "insufficient-history");
        final JsonArray labels = new JsonArray();
        labels.add(new JsonObject());
        objectLabel.add("labels", labels);
        assertMalformedAtLine2(lines, objectLabel, "labels[0]");
    }

    @Test
    public void candidateIdentityAndEvidenceVersionMustMatchRecordedContents() throws Exception {
        final Path file = directory.resolve("candidate-identity.jsonl");
        traceEvaluation(file, buildSeries(24), 0, 19);
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final JsonObject original = lines.stream()
                .skip(1)
                .map(line -> JsonParser.parseString(line).getAsJsonObject())
                .filter(record -> record.has("candidates") && !record.getAsJsonArray("candidates").isEmpty()
                        && firstCandidate(record).getAsJsonArray("rules").size() > 1)
                .findFirst()
                .orElseThrow();
        for (final String field : List.of("candidateKey", "version", "grammar", "direction", "placement.index",
                "placement.type", "placement.price", "placement.order", "placement.removed", "rules.id", "rules.state",
                "rules.score", "rules.observations", "rules.explanation", "rules.order", "rules.removed")) {
            final JsonObject damaged = original.deepCopy();
            final JsonObject candidate = firstCandidate(damaged);
            final JsonArray placement = candidate.getAsJsonArray("placement");
            final JsonObject pivot = placement.get(0).getAsJsonObject();
            final JsonArray rules = candidate.getAsJsonArray("rules");
            final JsonObject rule = rules.get(0).getAsJsonObject();
            switch (field) {
            case "candidateKey", "version" -> candidate.addProperty(field, "fabricated");
            case "grammar" -> damaged.addProperty("grammar", "FOREIGN");
            case "direction" -> candidate.addProperty("direction",
                    "BULLISH".equals(candidate.get("direction").getAsString()) ? "BEARISH" : "BULLISH");
            case "placement.index" -> pivot.addProperty("index", pivot.get("index").getAsInt() + 1);
            case "placement.type" ->
                pivot.addProperty("type", "LOW".equals(pivot.get("type").getAsString()) ? "HIGH" : "LOW");
            case "placement.price" -> pivot.addProperty("price", pivot.get("price").getAsString() + "0");
            case "placement.order" -> {
                final JsonElement first = placement.get(0);
                placement.set(0, placement.get(1));
                placement.set(1, first);
            }
            case "placement.removed" -> placement.remove(0);
            case "rules.id" -> rule.addProperty("id", "edited");
            case "rules.state" -> rule.addProperty("state", "FAIL");
            case "rules.score" -> rule.addProperty("score", 0.123);
            case "rules.observations" -> rule.getAsJsonArray("observations").add("edited");
            case "rules.explanation" -> rule.addProperty("explanation", "edited");
            case "rules.order" -> {
                final JsonElement first = rules.get(0);
                rules.set(0, rules.get(1));
                rules.set(1, first);
            }
            case "rules.removed" -> rules.remove(0);
            default -> throw new AssertionError(field);
            }
            assertMalformedAtLine2(lines, damaged, "candidates[0]."
                    + (field.startsWith("rules.") || "version".equals(field) ? "version" : "candidateKey"));
        }
    }

    @Test
    public void domainInvariantsAreCheckedBeforeRetentionWithSelfConsistentHashes() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("domain-invariants.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final String mutation : List.of("complete-empty", "complete-multiple", "ambiguous-empty",
                "ambiguous-single", "candidate-bound", "rules-omitted", "rules-duplicated", "rules-renamed",
                "rules-reordered", "rules-extra", "forming-null-direction", "record-unknown-direction",
                "candidate-unknown-direction", "unknown-grammar", "placement-count", "placement-index-order",
                "placement-type-order", "placement-negative-index", "placement-invalid-type",
                "placement-nonfinite-price", "pivot-negative-index", "pivot-confirmation-before-index",
                "pivot-invalid-type", "pivot-nonfinite-price", "score-on-fail", "score-below-zero", "score-above-one",
                "score-extreme", "score-epsilon-below-zero", "score-epsilon-above-one", "score-unsupported-exponent")) {
            final JsonObject damaged = original.deepCopy();
            final JsonArray candidates = damaged.getAsJsonArray("candidates");
            final JsonObject candidate = candidates.get(0).getAsJsonObject();
            final JsonArray placement = candidate.getAsJsonArray("placement");
            final JsonObject point = placement.get(0).getAsJsonObject();
            final JsonObject pivot = damaged.getAsJsonArray("pivots").get(0).getAsJsonObject();
            final JsonArray rules = candidate.getAsJsonArray("rules");
            final JsonObject rule = rules.get(0).getAsJsonObject();
            final String field;
            switch (mutation) {
            case "complete-empty", "ambiguous-empty" -> {
                damaged.addProperty("status", mutation.startsWith("ambiguous") ? "AMBIGUOUS" : "COMPLETE");
                damaged.add("candidates", new JsonArray());
                field = "candidates";
            }
            case "complete-multiple", "candidate-bound" -> {
                damaged.addProperty("status", "candidate-bound".equals(mutation) ? "AMBIGUOUS" : "COMPLETE");
                while (candidates.size() < ("candidate-bound".equals(mutation) ? 65 : 2)) {
                    candidates.add(candidate.deepCopy());
                }
                field = "candidates";
            }
            case "ambiguous-single" -> {
                damaged.addProperty("status", "AMBIGUOUS");
                field = "candidates";
            }
            case "rules-omitted", "rules-extra", "rules-duplicated", "rules-renamed", "rules-reordered" -> {
                switch (mutation) {
                case "rules-omitted" -> rules.remove(0);
                case "rules-extra" -> rules.add(rule.deepCopy());
                case "rules-duplicated" -> rules.set(1, rule.deepCopy());
                case "rules-renamed" -> rule.addProperty("id", "foreign-rule");
                case "rules-reordered" -> {
                    rules.set(0, rules.get(1));
                    rules.set(1, rule);
                }
                default -> throw new AssertionError(mutation);
                }
                field = "rules";
            }
            case "forming-null-direction", "record-unknown-direction" -> {
                if ("forming-null-direction".equals(mutation)) {
                    damaged.addProperty("status", "FORMING");
                    damaged.add("direction", JsonNull.INSTANCE);
                } else {
                    damaged.addProperty("direction", "FLAT");
                }
                field = "direction";
            }
            case "candidate-unknown-direction" -> {
                candidate.addProperty("direction", "FLAT");
                field = "direction";
            }
            case "unknown-grammar" -> {
                damaged.addProperty("grammar", "FOREIGN");
                field = "grammar";
            }
            case "placement-count", "placement-index-order", "placement-type-order", "placement-negative-index",
                    "placement-invalid-type", "placement-nonfinite-price" -> {
                switch (mutation) {
                case "placement-count" -> placement.remove(placement.size() - 1);
                case "placement-index-order" -> placement.get(1).getAsJsonObject().add("index", point.get("index"));
                case "placement-type-order" -> placement.get(1).getAsJsonObject().add("type", point.get("type"));
                case "placement-negative-index" -> point.addProperty("index", -1);
                case "placement-invalid-type" -> point.addProperty("type", "MID");
                case "placement-nonfinite-price" -> point.addProperty("price", "NaN");
                default -> throw new AssertionError(mutation);
                }
                field = "placement";
            }
            case "pivot-negative-index", "pivot-confirmation-before-index", "pivot-invalid-type",
                    "pivot-nonfinite-price" -> {
                switch (mutation) {
                case "pivot-negative-index" -> pivot.addProperty("index", -1);
                case "pivot-confirmation-before-index" ->
                    pivot.addProperty("confirmationIndex", pivot.get("index").getAsInt() - 1);
                case "pivot-invalid-type" -> pivot.addProperty("type", "MID");
                case "pivot-nonfinite-price" -> pivot.addProperty("price", "Infinity");
                default -> throw new AssertionError(mutation);
                }
                field = "pivots[0]";
            }
            case "score-unsupported-exponent" -> {
                rule.add("score", JsonParser.parseString("1e2147483648"));
                field = ".score";
            }
            case "score-on-fail", "score-below-zero", "score-above-one", "score-extreme", "score-epsilon-below-zero",
                    "score-epsilon-above-one" -> {
                rule.addProperty("state", "score-on-fail".equals(mutation) ? "FAIL" : "PASS");
                rule.addProperty("score", new BigDecimal(switch (mutation) {
                case "score-on-fail" -> "0.5";
                case "score-below-zero" -> "-0.5";
                case "score-above-one" -> "1.5";
                case "score-extreme" -> "1e1000";
                case "score-epsilon-below-zero" -> "-1e-10";
                case "score-epsilon-above-one" -> "1.0000000001";
                default -> throw new AssertionError(mutation);
                }));
                field = ".score";
            }
            default -> throw new AssertionError(mutation);
            }
            refreshIdentity(damaged);
            assertMalformedAtLine2(lines, damaged, field);
        }
    }

    @Test
    public void validDomainBoundariesKeepTheirRecordedValues() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("domain-boundaries.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final TopologyGrammar grammar : TopologyGrammar.values()) {
            for (final WaveDirection direction : WaveDirection.values()) {
                for (final int count : List.of(1, 2, 64)) {
                    final JsonObject record = original.deepCopy();
                    orientGrammarShape(record, grammar, direction);
                    record.add("direction", count == 1 ? new JsonPrimitive(direction.name()) : JsonNull.INSTANCE);
                    record.addProperty("status", count == 1 ? "COMPLETE" : "AMBIGUOUS");
                    final JsonObject candidate = firstCandidate(record);
                    candidate.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("score", 0);
                    candidate.getAsJsonArray("rules").get(1).getAsJsonObject().addProperty("score", 1);
                    retainDistinctCandidates(record, count);
                    refreshIdentity(record);
                    final List<String> changed = new ArrayList<>(lines);
                    changed.set(1, record.toString());
                    final Path copy = directory.resolve("valid-" + grammar + "-" + direction + "-" + count + ".jsonl");
                    Files.write(copy, changed, StandardCharsets.UTF_8);
                    assertEquals(record, ElliottResearchTrace.read(copy).records().get(0));
                    assertTrue(ElliottResearchTrace.read(copy, ignored -> false).records().isEmpty());
                }
            }
        }
    }

    @Test
    public void persistedTopologyStatesMatchTheModelBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("topology-states.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        final TopologyCandidate modelCandidate = new TopologyCandidate(TopologyGrammar.CYCLE_5_3, WaveDirection.BULLISH,
                alternating(10, 20, 14, 26, 18, 32, 24, 30, 20));
        final WaveDirection[] directions = { null, WaveDirection.BULLISH, WaveDirection.BEARISH };
        for (final TopologyStatus status : TopologyStatus.values()) {
            for (final int count : List.of(0, 1, 2, 64, 65)) {
                for (final WaveDirection direction : directions) {
                    final boolean validCount = switch (status) {
                    case COMPLETE -> count == 1;
                    case AMBIGUOUS -> count >= 2 && count <= 64;
                    default -> count == 0;
                    };
                    final boolean validDirection = switch (status) {
                    case COMPLETE -> direction == WaveDirection.BULLISH;
                    case FORMING -> direction != null;
                    default -> direction == null;
                    };
                    final List<TopologyCandidate> modelCandidates = Collections.nCopies(count, modelCandidate);
                    final int start = status == TopologyStatus.FORMING ? 0 : -1;
                    final int end = status == TopologyStatus.FORMING ? 2 : -1;
                    final JsonObject record = original.deepCopy();
                    record.addProperty("status", status.name());
                    record.add("direction",
                            direction == null ? JsonNull.INSTANCE : new JsonPrimitive(direction.name()));
                    if (status == TopologyStatus.FORMING && direction == WaveDirection.BEARISH) {
                        orientGrammarShape(record, TopologyGrammar.CYCLE_5_3, direction);
                    }
                    if (count > 0) {
                        retainDistinctCandidates(record, count);
                        refreshIdentity(record);
                    } else {
                        record.add("candidates", new JsonArray());
                    }
                    final JsonArray pivots = record.getAsJsonArray("pivots");
                    final int visibleCount = status == TopologyStatus.INSUFFICIENT_HISTORY ? 1
                            : status == TopologyStatus.FORMING ? 3 : pivots.size();
                    while (pivots.size() > visibleCount) {
                        pivots.remove(pivots.size() - 1);
                    }
                    record.addProperty("formingStartBarIndex", start);
                    record.addProperty("formingEndBarIndex", end);
                    record.addProperty("explanation", "");
                    if (validCount && validDirection) {
                        final TopologyAnalysis analysis = new TopologyAnalysis(status, direction, modelCandidates, "",
                                start, end);
                        assertEquals(direction, analysis.direction());
                        if (count == 0) {
                            record.add("labels",
                                    labelArray(StudyRunner.topologyStabilityLabels(analysis).toArray(String[]::new)));
                        }
                        final List<String> changed = new ArrayList<>(lines);
                        changed.set(1, record.toString());
                        final Path copy = directory
                                .resolve("state-" + status + "-" + count + "-" + direction + ".jsonl");
                        Files.write(copy, changed, StandardCharsets.UTF_8);
                        assertEquals(record, ElliottResearchTrace.read(copy).records().get(0));
                        assertTrue(ElliottResearchTrace.read(copy, ignored -> false).records().isEmpty());
                    } else {
                        final Class<? extends RuntimeException> error = validCount && status == TopologyStatus.FORMING
                                && direction == null ? NullPointerException.class : IllegalArgumentException.class;
                        assertThrows(error,
                                () -> new TopologyAnalysis(status, direction, modelCandidates, "", start, end));
                        final String field = status == TopologyStatus.FORMING && direction == null || validCount
                                ? "direction"
                                : "candidates";
                        assertMalformedAtLine2(lines, record, field);
                    }
                }
            }
        }
    }

    @Test
    public void placementsMustBelongToTheConfirmedAsOfPivotViewBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("causal-pivots.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final String mutation : List.of("missing-visible-pivot", "different-visible-index",
                "different-visible-price", "different-visible-type", "future-confirmation", "duplicate-visible-index",
                "unsorted-visible-indices", "price-beyond-default-precision")) {
            final JsonObject damaged = original.deepCopy();
            final JsonArray pivots = damaged.getAsJsonArray("pivots");
            final JsonObject visible = pivots.get(0).getAsJsonObject();
            final JsonObject placed = firstCandidate(damaged).getAsJsonArray("placement").get(0).getAsJsonObject();
            final String field;
            switch (mutation) {
            case "missing-visible-pivot" -> {
                pivots.remove(0);
                field = "placement[0]";
            }
            case "different-visible-index" -> {
                firstCandidate(damaged).getAsJsonArray("placement").get(8).getAsJsonObject().addProperty("index", 9);
                field = "placement[8]";
            }
            case "different-visible-price" -> {
                placed.addProperty("price", "11");
                field = "placement[0]";
            }
            case "different-visible-type" -> {
                visible.addProperty("type", "HIGH");
                field = "pivots[1].type";
            }
            case "future-confirmation" -> {
                visible.addProperty("confirmationIndex", damaged.get("asOfIndex").getAsInt() + 1);
                field = "pivots[0].confirmationIndex";
            }
            case "duplicate-visible-index" -> {
                pivots.get(1).getAsJsonObject().addProperty("index", 0);
                field = "pivots[1].index";
            }
            case "unsorted-visible-indices" -> {
                pivots.set(0, pivots.get(1));
                pivots.set(1, visible);
                field = "pivots[1].index";
            }
            case "price-beyond-default-precision" -> {
                visible.addProperty("price", "10.0000000000000000000000000000000000000000");
                placed.addProperty("price", "10.0000000000000000000000000000000000000001");
                field = "placement[0]";
            }
            default -> throw new AssertionError(mutation);
            }
            refreshIdentity(damaged);
            assertMalformedAtLine2(lines, damaged, field);
        }
        final JsonObject alternative = original.deepCopy();
        alternative.addProperty("kind", "alternative");
        alternative.addProperty("status", "complete");
        alternative.add("direction", JsonNull.INSTANCE);
        alternative.add("candidates", new JsonArray());
        alternative.add("labels", new JsonArray());
        alternative.getAsJsonArray("pivots").get(0).getAsJsonObject().addProperty("confirmationIndex", 9);
        assertMalformedAtLine2(lines, alternative, "pivots[0].confirmationIndex");

        final JsonObject equivalentPrices = original.deepCopy();
        equivalentPrices.getAsJsonArray("pivots").get(0).getAsJsonObject().addProperty("price", "1e1");
        final List<String> equivalentLines = new ArrayList<>(lines);
        equivalentLines.set(1, equivalentPrices.toString());
        final Path equivalent = directory.resolve("equivalent-pivot-prices.jsonl");
        Files.write(equivalent, equivalentLines, StandardCharsets.UTF_8);
        assertEquals(equivalentPrices, ElliottResearchTrace.read(equivalent).records().get(0));
    }

    @Test
    public void recordKindMustMatchItsGrammarBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final List<String> lines = Files.readAllLines(
                    coordinateTrace("kind-grammar-" + selectedNull + ".jsonl", selectedNull), StandardCharsets.UTF_8);
            final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
            for (final TopologyGrammar grammar : TopologyGrammar.values()) {
                for (final String status : List.of("insufficient-history", "no-match", "forming", "complete",
                        "ambiguous")) {
                    final JsonObject alternative = original.deepCopy();
                    alternative.addProperty("kind", "alternative");
                    alternative.addProperty("grammar", grammar.name());
                    alternative.addProperty("status", status);
                    alternative.add("labels", new JsonArray());
                    assertMalformedAtLine2(lines, alternative, "grammar");
                }
            }
            for (final String grammar : List.of("3+3", "5+5", "change-point-baseline")) {
                final JsonObject alternative = original.deepCopy();
                alternative.addProperty("kind", "alternative");
                alternative.addProperty("grammar", grammar);
                alternative.addProperty("status", "insufficient-history");
                alternative.add("labels", labelArray("insufficient-history"));
                if (!selectedNull) {
                    alternative.addProperty("section", "competing");
                    alternative.addProperty("mode", "competing-" + grammar);
                    final List<String> changed = List.of(lines.getFirst(), alternative.toString(),
                            "{\"complete\":true,\"records\":1}");
                    final Path copy = directory.resolve("valid-alternative-" + grammar + ".jsonl");
                    Files.write(copy, changed, StandardCharsets.UTF_8);
                    assertEquals(alternative, ElliottResearchTrace.read(copy).records().get(0));
                    assertTrue(ElliottResearchTrace.read(copy, ignored -> false).records().isEmpty());
                }
                final JsonObject topology = alternative.deepCopy();
                topology.addProperty("kind", "topology");
                topology.addProperty("status", "INSUFFICIENT_HISTORY");
                assertMalformedAtLine2(lines, topology, "grammar");
            }
        }
    }

    @Test
    public void grammarDirectionOriginAndStrictLegShapeAreCheckedBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("grammar-shape.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final TopologyGrammar grammar : TopologyGrammar.values()) {
            for (final WaveDirection direction : WaveDirection.values()) {
                final JsonObject valid = original.deepCopy();
                orientGrammarShape(valid, grammar, direction);
                valid.addProperty("direction", direction.name());
                final JsonObject wrongDirection = valid.deepCopy();
                final String opposite = direction == WaveDirection.BULLISH ? "BEARISH" : "BULLISH";
                wrongDirection.addProperty("direction", opposite);
                firstCandidate(wrongDirection).addProperty("direction", opposite);
                refreshIdentity(wrongDirection);
                assertMalformedAtLine2(lines, wrongDirection, "placement");

                final JsonObject wrongOrigin = valid.deepCopy();
                for (final JsonElement pivot : wrongOrigin.getAsJsonArray("pivots")) {
                    final JsonObject point = pivot.getAsJsonObject();
                    point.addProperty("type", "LOW".equals(point.get("type").getAsString()) ? "HIGH" : "LOW");
                }
                for (final JsonElement pivot : firstCandidate(wrongOrigin).getAsJsonArray("placement")) {
                    final JsonObject point = pivot.getAsJsonObject();
                    point.addProperty("type", "LOW".equals(point.get("type").getAsString()) ? "HIGH" : "LOW");
                }
                refreshIdentity(wrongOrigin);
                assertMalformedAtLine2(lines, wrongOrigin, "placement");

                for (int leg = 0; leg < grammar.legCount(); leg++) {
                    for (final boolean reversed : List.of(false, true)) {
                        final JsonObject damaged = valid.deepCopy();
                        final JsonArray placement = firstCandidate(damaged).getAsJsonArray("placement");
                        final String start = placement.get(leg).getAsJsonObject().get("price").getAsString();
                        final String end = reversed ? start.startsWith("-") ? "-2e400" : "2e400" : start;
                        placement.get(leg + 1).getAsJsonObject().addProperty("price", end);
                        damaged.getAsJsonArray("pivots").get(leg + 1).getAsJsonObject().addProperty("price", end);
                        refreshIdentity(damaged);
                        assertMalformedAtLine2(lines, damaged, "placement");
                    }
                }
            }
        }
    }

    @Test
    public void observationsAreUniqueAndStrictlyIncreasingWithinEachScopeBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("observation-order.jsonl"), StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        final JsonObject footer = JsonParser.parseString(lines.get(2)).getAsJsonObject();
        footer.addProperty("records", 3);
        for (final int firstIndex : List.of(8, 10)) {
            final JsonObject first = original.deepCopy();
            first.addProperty("asOfIndex", firstIndex);
            final JsonObject interleaved = original.deepCopy();
            interleaved.addProperty("mode", "another-mode");
            final List<String> changed = List.of(lines.get(0), first.toString(), interleaved.toString(),
                    original.toString(), footer.toString());
            assertMalformedAtLine(changed, original, 4, "asOfIndex");
        }
        final JsonObject later = original.deepCopy();
        later.addProperty("asOfIndex", 9);
        for (final String otherScope : List.of("section", "mode", "detector", "partition")) {
            final JsonObject interleaved = original.deepCopy();
            interleaved.addProperty(otherScope, "another-" + otherScope);
            final Path valid = directory.resolve("ordered-" + otherScope + ".jsonl");
            Files.write(valid, List.of(lines.get(0), original.toString(), interleaved.toString(), later.toString(),
                    footer.toString()), StandardCharsets.UTF_8);
            assertEquals(3, ElliottResearchTrace.read(valid).recordCount());
            assertTrue(ElliottResearchTrace.read(valid, ignored -> false).records().isEmpty());
        }
    }

    @Test
    public void formingRangeModelBoundaryIsAcceptedAtTheAsOfIndex() throws Exception {
        final Path file = directory.resolve("forming-range-boundary.jsonl");
        final List<ConfirmedPivot> visible = alternating(10, 20, 14);
        final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5, visible);
        assertEquals(TopologyStatus.FORMING, analysis.status());
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            trace.topology(StudyObserver.Scope.real("h1", "topology-only", "MOTIVE_5", List.of(), "d"), "calibration",
                    2, Instant.parse("2018-01-03T00:00:00Z"), visible, analysis, List.of());
        }
        final JsonObject record = ElliottResearchTrace.read(file).records().getFirst();
        assertEquals(0, record.get("formingStartBarIndex").getAsInt());
        assertEquals(2, record.get("formingEndBarIndex").getAsInt());
        assertEquals(labelArray("forming:BULLISH:0-2"), record.get("labels"));
    }

    @Test
    public void topologyLabelsAreCanonicalForEveryStatusAndCaptureModeBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = topologyLabelTrace("topology-labels-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final List<JsonObject> records = ElliottResearchTrace.read(file).records();
            final Set<TopologyStatus> seen = new HashSet<>();
            for (int i = 0; i < records.size(); i++) {
                final JsonObject record = records.get(i);
                final TopologyStatus status = TopologyStatus.valueOf(record.get("status").getAsString());
                seen.add(status);
                final JsonArray expected = switch (status) {
                case COMPLETE -> labelArray("BULLISH:0-5");
                case AMBIGUOUS -> labelArray("BULLISH:0-5", "BULLISH:2-7");
                case FORMING -> labelArray("forming:BULLISH:0-2");
                case INVALIDATED -> labelArray("invalidated:" + record.get("explanation").getAsString());
                case INSUFFICIENT_HISTORY -> labelArray("insufficient-history");
                case NO_MATCH -> labelArray();
                };
                assertEquals(expected, record.getAsJsonArray("labels"), status.name());
                assertEquals(status == TopologyStatus.FORMING ? 0 : -1, record.get("formingStartBarIndex").getAsInt());
                assertEquals(status == TopologyStatus.FORMING ? 2 : -1, record.get("formingEndBarIndex").getAsInt());
                assertTrue(record.get("explanation").getAsJsonPrimitive().isString());
                final JsonObject forged = record.deepCopy();
                forged.add("labels", labelArray("arbitrary-label"));
                assertMalformedAtLine(lines, forged, i + 2, "labels");
                if (!expected.isEmpty()) {
                    final JsonObject empty = record.deepCopy();
                    empty.add("labels", labelArray());
                    assertMalformedAtLine(lines, empty, i + 2, "labels");
                    final JsonObject duplicate = record.deepCopy();
                    duplicate.getAsJsonArray("labels").add(expected.get(0));
                    final IllegalArgumentException error = assertMalformedAtLine(lines, duplicate, i + 2, "labels");
                    assertTrue(error.getMessage().contains("duplicates a label"), error.getMessage());
                }
                if (status == TopologyStatus.AMBIGUOUS) {
                    final JsonObject reordered = record.deepCopy();
                    reordered.add("labels", labelArray("BULLISH:2-7", "BULLISH:0-5"));
                    final List<String> changed = new ArrayList<>(lines);
                    changed.set(i + 1, reordered.toString());
                    final Path copy = directory.resolve("unordered-label-set-" + selectedNull + ".jsonl");
                    Files.write(copy, changed, StandardCharsets.UTF_8);
                    assertEquals(reordered, ElliottResearchTrace.read(copy).records().get(i));
                }
            }
            assertEquals(Set.of(TopologyStatus.values()), seen);
        }
    }

    @Test
    public void topologyFormingBoundsAndExplanationAreRequiredAndCausalBeforeRetention() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final List<String> lines = Files.readAllLines(
                    topologyLabelTrace("topology-fields-" + selectedNull + ".jsonl", selectedNull),
                    StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size() - 1; i++) {
                final JsonObject original = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                for (final String field : List.of("formingStartBarIndex", "formingEndBarIndex", "explanation")) {
                    final JsonObject missing = original.deepCopy();
                    missing.remove(field);
                    assertMalformedAtLine(lines, missing, i + 1, field);
                    final JsonObject nullValue = original.deepCopy();
                    nullValue.add(field, JsonNull.INSTANCE);
                    assertMalformedAtLine(lines, nullValue, i + 1, field);
                    final JsonObject wrongType = original.deepCopy();
                    wrongType.add(field, "explanation".equals(field) ? new JsonPrimitive(1) : new JsonPrimitive("0"));
                    assertMalformedAtLine(lines, wrongType, i + 1, field);
                }
                if (!"FORMING".equals(original.get("status").getAsString())) {
                    for (final String field : List.of("formingStartBarIndex", "formingEndBarIndex")) {
                        final JsonObject nonFormingRange = original.deepCopy();
                        nonFormingRange.addProperty(field, 0);
                        assertMalformedAtLine(lines, nonFormingRange, i + 1, field);
                    }
                    continue;
                }
                for (final String field : List.of("formingStartBarIndex", "formingEndBarIndex")) {
                    for (final String number : List.of("1.5", "2147483648", "-2147483649", "1e2147483648")) {
                        final JsonObject unsupported = original.deepCopy();
                        unsupported.add(field, JsonParser.parseString(number));
                        assertMalformedAtLine(lines, unsupported, i + 1, field);
                    }
                }
                for (final String mutation : List.of("negative-start", "negative-end", "reversed", "future-end",
                        "missing-start-pivot", "missing-end-pivot")) {
                    final JsonObject damaged = original.deepCopy();
                    switch (mutation) {
                    case "negative-start" -> damaged.addProperty("formingStartBarIndex", -1);
                    case "negative-end" -> damaged.addProperty("formingEndBarIndex", -1);
                    case "reversed" -> damaged.addProperty("formingStartBarIndex", 3);
                    case "future-end" ->
                        damaged.addProperty("formingEndBarIndex", damaged.get("asOfIndex").getAsInt() + 1);
                    case "missing-start-pivot" -> damaged.getAsJsonArray("pivots").remove(0);
                    case "missing-end-pivot" -> damaged.getAsJsonArray("pivots").remove(2);
                    default -> throw new AssertionError(mutation);
                    }
                    // Keep the label self-consistent: bounds must be valid independently.
                    damaged.add("labels", labelArray("forming:BULLISH:" + damaged.get("formingStartBarIndex").getAsInt()
                            + "-" + damaged.get("formingEndBarIndex").getAsInt()));
                    assertMalformedAtLine(lines, damaged, i + 1, "forming");
                }
            }
            final JsonObject invalidated = lines.stream()
                    .map(JsonParser::parseString)
                    .map(JsonElement::getAsJsonObject)
                    .filter(record -> record.has("status") && "INVALIDATED".equals(record.get("status").getAsString()))
                    .findFirst()
                    .orElseThrow();
            final int line = lines.indexOf(invalidated.toString()) + 1;
            final JsonObject differentExplanation = invalidated.deepCopy();
            differentExplanation.addProperty("explanation", "different captured reason");
            assertMalformedAtLine(lines, differentExplanation, line, "labels");
            // The model requires non-null text, not a new prose grammar or nonblankness.
            final JsonObject emptyExplanation = invalidated.deepCopy();
            emptyExplanation.addProperty("explanation", "");
            emptyExplanation.add("labels", labelArray("invalidated:"));
            final List<String> changed = new ArrayList<>(lines);
            changed.set(line - 1, emptyExplanation.toString());
            final Path supported = directory.resolve("empty-explanation-" + selectedNull + ".jsonl");
            Files.write(supported, changed, StandardCharsets.UTF_8);
            assertEquals(emptyExplanation, ElliottResearchTrace.read(supported).records().get(line - 2));
        }
    }

    @Test
    public void alternativeOutcomesAndLabelsComeFromTheCapturedPivotGrammarBeforeRetention() throws Exception {
        for (final String grammar : List.of("3+3", "5+5")) {
            final int leadingLegs = "3+3".equals(grammar) ? 3 : 5;
            final double[][] histories = "3+3".equals(grammar)
                    ? new double[][] { {}, { 0, 10 }, { 0, 10, 5, 20 }, { 0, 10, 5, 20, 8, 25 },
                            { 0, 10, 5, 20, 8, 15, 10 }, { 0, 10, 5, 20, 8, 25, 12, 22, 14 }, { 0, 10, 5, 20, 20 },
                            { 0, 10, 5, 10 } }
                    : new double[][] { {}, { 0, 10 }, { 0, 10, 5, 20, 8, 30 }, { 0, 10, 5, 20, 8, 30, 12, 40 },
                            { 0, 10, 5, 20, 8, 30, 12, 25, 14, 22, 16 },
                            { 0, 10, 5, 20, 8, 30, 12, 40, 14, 35, 16, 32, 18 }, { 0, 10, 5, 20, 8, 30, 30 },
                            { 0, 10, 5, 20, 8, 15 } };
            final List<String> outcomes = List.of("insufficient-history", "no-match", "forming", "ambiguous",
                    "complete", "ambiguous", "forming", "no-match");
            final List<Set<String>> labels = List.of(Set.of("insufficient-history"), Set.of("no-match"),
                    Set.of("BULLISH:0-" + leadingLegs),
                    Set.of("BULLISH:0-" + (leadingLegs + 2), "BULLISH:2-" + (leadingLegs + 2)),
                    Set.of("0-" + (2 * leadingLegs)), Set.of("0-" + (2 * leadingLegs), "2-" + (2 * leadingLegs + 2)),
                    Set.of("BULLISH:0-" + (leadingLegs + 1)), Set.of("no-match"));
            for (int i = 0; i < histories.length; i++) {
                final Path file = directory.resolve("alternative-" + grammar + "-" + i + ".jsonl");
                try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                        ElliottResearchTrace.MODE_REAL, -1, -1)) {
                    trace.alternative(
                            StudyObserver.Scope.real("competing", "competing-" + grammar, grammar, List.of(), "d"),
                            "calibration", 20, Instant.parse("2018-01-21T00:00:00Z"), alternating(histories[i]),
                            outcomes.get(i), labels.get(i));
                }
                final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                final JsonObject record = ElliottResearchTrace.read(file).records().getFirst();
                assertEquals(outcomes.get(i), record.get("status").getAsString());
                assertFalse(record.has("formingStartBarIndex"));
                assertFalse(record.has("formingEndBarIndex"));
                assertFalse(record.has("explanation"));
                final JsonObject directed = record.deepCopy();
                directed.addProperty("direction", "BULLISH");
                assertMalformedAtLine2(lines, directed, "direction");
                for (final String outcome : Set.of("insufficient-history", "no-match", "forming", "complete",
                        "ambiguous")) {
                    if (!outcome.equals(outcomes.get(i))) {
                        final JsonObject wrongOutcome = record.deepCopy();
                        wrongOutcome.addProperty("status", outcome);
                        assertMalformedAtLine2(lines, wrongOutcome, "status");
                    }
                }
                final JsonObject empty = record.deepCopy();
                empty.add("labels", labelArray());
                assertMalformedAtLine2(lines, empty, "labels");
                final JsonObject forged = record.deepCopy();
                forged.add("labels", labelArray("arbitrary-placement"));
                assertMalformedAtLine2(lines, forged, "labels");
                final JsonObject duplicate = record.deepCopy();
                duplicate.getAsJsonArray("labels").add(record.getAsJsonArray("labels").get(0));
                assertMalformedAtLine2(lines, duplicate, "duplicates a label");
                if ("complete".equals(outcomes.get(i))) {
                    final JsonObject contradictedPivots = record.deepCopy();
                    contradictedPivots.getAsJsonArray("pivots").get(1).getAsJsonObject().addProperty("price", "0");
                    assertMalformedAtLine2(lines, contradictedPivots, "status");
                }
            }
        }
    }

    @Test
    public void changePointLabelsBindOnlyTheRecordedOutcomeAndAsOfIndexBeforeRetention() throws Exception {
        for (final int asOfIndex : List.of(17, Integer.MAX_VALUE)) {
            for (final String outcome : List.of("insufficient-history", "no-match", "complete")) {
                final String label = switch (outcome) {
                case "insufficient-history" -> "insufficient-history";
                case "no-match" -> "stable";
                default -> "change@" + asOfIndex;
                };
                final Path file = directory.resolve("change-point-" + outcome + "-" + asOfIndex + ".jsonl");
                try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                        ElliottResearchTrace.MODE_REAL, -1, -1)) {
                    trace.alternative(
                            StudyObserver.Scope.real("competing", "competing-change-point-baseline",
                                    "change-point-baseline", List.of(), "d"),
                            "calibration", asOfIndex, Instant.parse("2018-01-01T00:00:00Z"), List.of(), outcome,
                            Set.of(label));
                }
                final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                final JsonObject record = ElliottResearchTrace.read(file).records().getFirst();
                assertEquals(labelArray(label), record.get("labels"));
                final JsonObject nonemptyPivots = record.deepCopy();
                final JsonObject pivot = new JsonObject();
                pivot.addProperty("index", 0);
                pivot.addProperty("confirmationIndex", 0);
                pivot.addProperty("type", "LOW");
                pivot.addProperty("price", "0");
                nonemptyPivots.getAsJsonArray("pivots").add(pivot);
                assertMalformedAtLine2(lines, nonemptyPivots, "pivots");
                for (final String forgedLabel : List.of("arbitrary", "change@" + (asOfIndex - 1))) {
                    final JsonObject forged = record.deepCopy();
                    forged.add("labels", labelArray(forgedLabel));
                    assertMalformedAtLine2(lines, forged, "labels");
                }
                final JsonObject empty = record.deepCopy();
                empty.add("labels", labelArray());
                assertMalformedAtLine2(lines, empty, "labels");
                final JsonObject duplicate = record.deepCopy();
                duplicate.add("labels", labelArray(label, label));
                assertMalformedAtLine2(lines, duplicate, "duplicates a label");
                for (final String invalidOutcome : List.of("forming", "ambiguous")) {
                    final JsonObject impossible = record.deepCopy();
                    impossible.addProperty("status", invalidOutcome);
                    assertMalformedAtLine2(lines, impossible, "status");
                }
                final JsonObject unknownGrammar = record.deepCopy();
                unknownGrammar.addProperty("grammar", "CHANGE_POINT");
                assertMalformedAtLine2(lines, unknownGrammar, "grammar");
            }
        }
    }

    @Test
    public void aggregatePreservingLabelRemapsCannotMasqueradeAsStabilityEvidence() throws Exception {
        for (final boolean selectedNull : List.of(false, true)) {
            final Path file = topologyLabelTrace("bijective-labels-" + selectedNull + ".jsonl", selectedNull);
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final List<JsonObject> originals = ElliottResearchTrace.read(file).records();
            final List<JsonObject> forged = new ArrayList<>();
            final List<String> changed = new ArrayList<>(lines);
            boolean partialOverlap = false;
            for (int i = 0; i < originals.size(); i++) {
                final JsonObject record = originals.get(i).deepCopy();
                final JsonArray labels = new JsonArray();
                for (final JsonElement label : record.getAsJsonArray("labels")) {
                    labels.add("forged:" + label.getAsString());
                }
                record.add("labels", labels);
                assertEquals(originals.get(i).get("status"), record.get("status"));
                assertEquals(originals.get(i).get("candidates"), record.get("candidates"));
                forged.add(record);
                changed.set(i + 1, record.toString());
                if (i > 0) {
                    final List<JsonElement> previous = originals.get(i - 1).getAsJsonArray("labels").asList();
                    final List<JsonElement> current = originals.get(i).getAsJsonArray("labels").asList();
                    final List<JsonElement> forgedPrevious = forged.get(i - 1).getAsJsonArray("labels").asList();
                    final List<JsonElement> forgedCurrent = labels.asList();
                    final long intersection = previous.stream().filter(current::contains).count();
                    final long forgedIntersection = forgedPrevious.stream().filter(forgedCurrent::contains).count();
                    final long union = previous.size() + current.size() - intersection;
                    assertEquals(intersection, forgedIntersection);
                    assertEquals(union, forgedPrevious.size() + forgedCurrent.size() - forgedIntersection);
                    partialOverlap |= intersection > 0 && intersection < union;
                }
            }
            assertTrue(partialOverlap, "the stream includes a nontrivial COMPLETE-to-AMBIGUOUS overlap");
            // Every Jaccard numerator/denominator survived the bijection. The reader
            // must reject the stream nonetheless, even when retaining no records.
            assertMalformedAtLine(changed, forged.getFirst(), 2, "labels");
        }
    }

    private Path topologyLabelTrace(final String name, final boolean selectedNull) throws IOException {
        final List<List<ConfirmedPivot>> histories = List.of(List.of(), alternating(10, 20), alternating(10, 20, 14),
                alternating(10, 20, 14, 26, 18, 32), alternating(10, 20, 14, 26, 18, 32, 24, 40),
                alternating(10, 20, 14, 26, 18, 32, 35, 9));
        final List<TopologyStatus> statuses = List.of(TopologyStatus.INSUFFICIENT_HISTORY, TopologyStatus.NO_MATCH,
                TopologyStatus.FORMING, TopologyStatus.COMPLETE, TopologyStatus.AMBIGUOUS, TopologyStatus.INVALIDATED);
        final Path file = directory.resolve(name);
        final StudyObserver.Scope scope = new StudyObserver.Scope(selectedNull ? "null" : "h1",
                selectedNull ? "MOTIVE_5" : "topology-only", "MOTIVE_5", List.of(), "d", selectedNull ? 2 : -1,
                selectedNull ? 1 : -1);
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                selectedNull ? ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER : ElliottResearchTrace.MODE_REAL,
                scope.nullBlockLength(), scope.nullMemberIndex())) {
            for (int i = 0; i < histories.size(); i++) {
                final List<ConfirmedPivot> visible = histories.get(i);
                final TopologyAnalysis analysis = new TopologyAnalyzer().analyze(TopologyGrammar.MOTIVE_5, visible);
                assertEquals(statuses.get(i), analysis.status());
                trace.topology(scope, "calibration", 20 + i,
                        Instant.parse("2018-01-21T00:00:00Z").plusSeconds(86_400L * i), visible, analysis,
                        Collections.nCopies(analysis.candidates().size(), List.of()));
            }
        }
        return file;
    }

    @Test
    public void formingSuffixesMatchTheirGrammarAndEndAtTheNewestVisiblePivotBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("forming-suffix-contract.jsonl"),
                StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final TopologyGrammar grammar : TopologyGrammar.values()) {
            for (final WaveDirection direction : WaveDirection.values()) {
                for (int length = 3; length < grammar.requiredPivots(); length++) {
                    final JsonObject record = original.deepCopy();
                    orientGrammarShape(record, grammar, direction);
                    retainDistinctCandidates(record, 1);
                    final JsonArray completePivots = record.getAsJsonArray("pivots").deepCopy();
                    final JsonArray pivots = record.getAsJsonArray("pivots");
                    while (pivots.size() > length) {
                        pivots.remove(pivots.size() - 1);
                    }
                    // Confirmation order is not pivot order; every pivot is causal.
                    pivots.get(0)
                            .getAsJsonObject()
                            .addProperty("confirmationIndex", record.get("asOfIndex").getAsInt());
                    record.add("candidates", new JsonArray());
                    record.addProperty("status", "FORMING");
                    record.addProperty("direction", direction.name());
                    record.addProperty("formingStartBarIndex", 0);
                    record.addProperty("formingEndBarIndex", length - 1);
                    record.add("labels", labelArray("forming:" + direction + ":0-" + (length - 1)));
                    final List<String> valid = new ArrayList<>(lines);
                    valid.set(1, record.toString());
                    final Path trace = directory
                            .resolve("forming-" + grammar + "-" + direction + "-" + length + ".jsonl");
                    Files.write(trace, valid, StandardCharsets.UTF_8);
                    assertEquals(record, ElliottResearchTrace.read(trace).records().getFirst());
                    assertTrue(ElliottResearchTrace.read(trace, ignored -> false).records().isEmpty());

                    for (final String mutation : List.of("not-newest", "two-pivot-suffix", "wrong-direction",
                            "flat-leg", "complete-length")) {
                        final JsonObject damaged = record.deepCopy();
                        switch (mutation) {
                        case "not-newest" -> damaged.addProperty("formingEndBarIndex", length - 2);
                        case "two-pivot-suffix" -> damaged.addProperty("formingStartBarIndex", length - 2);
                        case "wrong-direction" -> damaged.addProperty("direction",
                                direction == WaveDirection.BULLISH ? "BEARISH" : "BULLISH");
                        case "flat-leg" -> damaged.getAsJsonArray("pivots")
                                .get(length - 1)
                                .getAsJsonObject()
                                .add("price", pivots.get(length - 2).getAsJsonObject().get("price").deepCopy());
                        case "complete-length" -> {
                            damaged.add("pivots", completePivots.deepCopy());
                            damaged.addProperty("formingEndBarIndex", grammar.requiredPivots() - 1);
                        }
                        default -> throw new AssertionError(mutation);
                        }
                        damaged.add("labels",
                                labelArray("forming:" + damaged.get("direction").getAsString() + ":"
                                        + damaged.get("formingStartBarIndex").getAsInt() + "-"
                                        + damaged.get("formingEndBarIndex").getAsInt()));
                        assertMalformedAtLine2(valid, damaged, "forming");
                    }
                }
            }
        }
    }

    @Test
    public void disjointCompletionBoundariesMaySelectAShorterValidFormingSuffix() throws Exception {
        for (final TopologyGrammar grammar : TopologyGrammar.values()) {
            for (final WaveDirection direction : WaveDirection.values()) {
                final List<ConfirmedPivot> visible = new ArrayList<>();
                final boolean startsLow = (direction == WaveDirection.BULLISH) != (grammar == TopologyGrammar.CORRECTIVE_3);
                for (int p = 0; p < grammar.requiredPivots() + 3; p++) {
                    final boolean low = (p % 2 == 0) == startsLow;
                    visible.add(new ConfirmedPivot(p, p, DoubleNum.valueOf(low ? 10 : 20),
                            low ? SwingPivotType.LOW : SwingPivotType.HIGH));
                }
                final TopologyAnalysis analysis = new TopologyAnalyzer().detectForming(grammar, visible,
                        grammar.requiredPivots() - 1);
                assertEquals(TopologyStatus.FORMING, analysis.status());
                assertEquals(grammar.requiredPivots(), analysis.formingStartBarIndex());
                assertEquals(visible.size() - 1, analysis.formingEndBarIndex());
                final Path file = directory.resolve("disjoint-forming-" + grammar + "-" + direction + ".jsonl");
                try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                        ElliottResearchTrace.MODE_REAL, -1, -1)) {
                    trace.topology(StudyObserver.Scope.real("h1", "topology-only", grammar.name(), List.of(), "d"),
                            "calibration", visible.size() - 1, Instant.parse("2018-02-01T00:00:00Z"), visible, analysis,
                            List.of());
                }
                assertEquals(analysis.formingStartBarIndex(),
                        ElliottResearchTrace.read(file).records().getFirst().get("formingStartBarIndex").getAsInt());
                assertTrue(ElliottResearchTrace.read(file, ignored -> false).records().isEmpty());
            }
        }
    }

    @Test
    public void capturedPivotViewsAndRetainedCandidatesCannotContradictTheirStatesBeforeRetention() throws Exception {
        final List<String> lines = Files.readAllLines(domainTrace("captured-state-contract.jsonl"),
                StandardCharsets.UTF_8);
        final JsonObject original = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        for (final String mutation : List.of("nonalternating", "insufficient-with-visible-history",
                "no-match-with-one-pivot", "invalidated-with-one-pivot", "duplicate-candidate", "gapped-candidate")) {
            final JsonObject damaged = original.deepCopy();
            final JsonArray pivots = damaged.getAsJsonArray("pivots");
            final String field;
            switch (mutation) {
            case "nonalternating" -> {
                pivots.get(1).getAsJsonObject().add("type", pivots.get(0).getAsJsonObject().get("type").deepCopy());
                field = "pivots[1].type";
            }
            case "insufficient-with-visible-history", "no-match-with-one-pivot", "invalidated-with-one-pivot" -> {
                final String status = "insufficient-with-visible-history".equals(mutation) ? "INSUFFICIENT_HISTORY"
                        : "no-match-with-one-pivot".equals(mutation) ? "NO_MATCH" : "INVALIDATED";
                damaged.addProperty("status", status);
                damaged.add("direction", JsonNull.INSTANCE);
                damaged.add("candidates", new JsonArray());
                if (!"INSUFFICIENT_HISTORY".equals(status)) {
                    while (pivots.size() > 1) {
                        pivots.remove(pivots.size() - 1);
                    }
                }
                damaged.add("labels", "INSUFFICIENT_HISTORY".equals(status) ? labelArray("insufficient-history")
                        : "INVALIDATED".equals(status) ? labelArray("invalidated:complete") : labelArray());
                field = "status";
            }
            case "duplicate-candidate" -> {
                damaged.addProperty("status", "AMBIGUOUS");
                damaged.add("direction", JsonNull.INSTANCE);
                damaged.getAsJsonArray("candidates").add(firstCandidate(damaged).deepCopy());
                field = "candidateKey";
            }
            case "gapped-candidate" -> {
                final JsonArray expanded = new JsonArray();
                for (int p = 0; p < pivots.size(); p++) {
                    if (p == 1) {
                        for (int inserted = 1; inserted <= 2; inserted++) {
                            final JsonObject extra = pivots.get(inserted % 2).getAsJsonObject().deepCopy();
                            extra.addProperty("index", inserted);
                            extra.addProperty("confirmationIndex", inserted);
                            expanded.add(extra);
                        }
                    }
                    final JsonObject pivot = pivots.get(p).getAsJsonObject().deepCopy();
                    pivot.addProperty("index", p == 0 ? 0 : p + 2);
                    pivot.addProperty("confirmationIndex", p == 0 ? 0 : p + 2);
                    expanded.add(pivot);
                }
                damaged.add("pivots", expanded);
                final JsonObject candidate = firstCandidate(damaged);
                final JsonArray placement = candidate.getAsJsonArray("placement");
                for (int p = 1; p < placement.size(); p++) {
                    placement.get(p).getAsJsonObject().addProperty("index", p + 2);
                }
                candidate.addProperty("endBarIndex", 10);
                damaged.addProperty("asOfIndex", 10);
                refreshIdentity(damaged);
                field = "placement[1]";
            }
            default -> throw new AssertionError(mutation);
            }
            assertMalformedAtLine2(lines, damaged, field);
        }
    }

    private static void orientGrammarShape(final JsonObject record, final TopologyGrammar grammar,
            final WaveDirection direction) {
        record.addProperty("grammar", grammar.name());
        final JsonObject candidate = firstCandidate(record);
        candidate.addProperty("direction", direction.name());
        final JsonArray placement = candidate.getAsJsonArray("placement");
        while (placement.size() > grammar.requiredPivots()) {
            placement.remove(placement.size() - 1);
        }
        final boolean startsLow = (direction == WaveDirection.BULLISH) == (grammar != TopologyGrammar.CORRECTIVE_3);
        final JsonArray pivots = record.getAsJsonArray("pivots");
        for (int i = 0; i < pivots.size(); i++) {
            final boolean low = (i % 2 == 0) == startsLow;
            final JsonObject pivot = pivots.get(i).getAsJsonObject();
            pivot.addProperty("type", low ? "LOW" : "HIGH");
            pivot.addProperty("price", low ? "-1e400" : "1e400");
            if (i < placement.size()) {
                final JsonObject placed = placement.get(i).getAsJsonObject();
                placed.addProperty("type", low ? "LOW" : "HIGH");
                placed.addProperty("price", low ? "-1e400" : "1e400");
            }
        }
    }

    private static void retainDistinctCandidates(final JsonObject record, final int count) {
        final JsonObject template = firstCandidate(record).deepCopy();
        final JsonArray original = template.getAsJsonArray("placement");
        final int required = original.size();
        final JsonArray pivots = new JsonArray();
        for (int p = 0; p < required + 2 * (count - 1); p++) {
            final JsonObject pivot = original.get(p % 2).getAsJsonObject().deepCopy();
            pivot.addProperty("index", p);
            pivot.addProperty("confirmationIndex", p);
            pivots.add(pivot);
        }
        final JsonArray candidates = new JsonArray();
        final JsonArray labels = new JsonArray();
        for (int i = 0; i < count; i++) {
            final int start = 2 * i;
            final JsonArray placement = new JsonArray();
            for (int p = start; p < start + required; p++) {
                final JsonObject pivot = pivots.get(p).getAsJsonObject().deepCopy();
                pivot.remove("confirmationIndex");
                placement.add(pivot);
            }
            final JsonObject candidate = template.deepCopy();
            candidate.add("placement", placement);
            candidate.addProperty("startBarIndex", start);
            candidate.addProperty("endBarIndex", start + required - 1);
            candidates.add(candidate);
            labels.add(candidate.get("direction").getAsString() + ":" + start + "-" + (start + required - 1));
        }
        record.add("pivots", pivots);
        record.add("candidates", candidates);
        record.add("labels", labels);
        record.addProperty("asOfIndex", Math.max(record.get("asOfIndex").getAsInt(), pivots.size() - 1));
    }

    private Path domainTrace(final String name) throws IOException {
        final Path file = directory.resolve(name);
        final TopologyCandidate candidate = new TopologyCandidate(TopologyGrammar.CYCLE_5_3, WaveDirection.BULLISH,
                alternating(10, 20, 14, 26, 18, 32, 24, 30, 20));
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            trace.topology(StudyObserver.Scope.real("h2", "all-rules", "CYCLE_5_3", List.of("first", "second"), "d"),
                    "calibration", 8, Instant.parse("2018-01-09T00:00:00Z"), candidate.pivots(),
                    new TopologyAnalysis(TopologyStatus.COMPLETE, WaveDirection.BULLISH, List.of(candidate), "complete",
                            -1, -1),
                    List.of(List.of(RuleEvidence.scored("first", 0.5, List.of(), ""),
                            RuleEvidence.pass("second", List.of(), ""))));
        }
        return file;
    }

    private static void refreshIdentity(final JsonObject record) throws NoSuchAlgorithmException {
        for (final JsonElement element : record.getAsJsonArray("candidates")) {
            final JsonObject candidate = element.getAsJsonObject();
            final StringBuilder identity = new StringBuilder(record.get("grammar").getAsString()).append('|')
                    .append(candidate.get("direction").getAsString());
            for (final JsonElement point : candidate.getAsJsonArray("placement")) {
                final JsonObject pivot = point.getAsJsonObject();
                identity.append('|')
                        .append(pivot.get("index").getAsInt())
                        .append(':')
                        .append(pivot.get("type").getAsString())
                        .append(':')
                        .append(pivot.get("price").getAsString());
            }
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8));
            candidate.addProperty("candidateKey", HexFormat.of().formatHex(digest, 0, 16));
            refreshVersion(candidate);
        }
    }

    private static void refreshVersion(final JsonObject candidate) throws NoSuchAlgorithmException {
        final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(candidate.getAsJsonArray("rules").toString().getBytes(StandardCharsets.UTF_8));
        candidate.addProperty("version",
                candidate.get("candidateKey").getAsString() + "@" + HexFormat.of().formatHex(digest, 0, 16));
    }

    private static JsonArray labelArray(final String... labels) {
        final JsonArray array = new JsonArray();
        for (final String label : labels) {
            array.add(label);
        }
        return array;
    }

    private static JsonObject firstCandidate(final JsonObject record) {
        return record.getAsJsonArray("candidates").get(0).getAsJsonObject();
    }

    private void assertMalformedAtLine2(final List<String> lines, final JsonObject damaged, final String field)
            throws IOException {
        assertMalformedAtLine(lines, damaged, 2, field);
    }

    private IllegalArgumentException assertMalformedAtLine(final List<String> lines, final JsonObject damaged,
            final int lineNumber, final String field) throws IOException {
        final Path copy = directory.resolve("damaged-" + lineNumber + "-" + field + ".jsonl");
        final List<String> damagedLines = new ArrayList<>(lines);
        damagedLines.set(lineNumber - 1, damaged.toString());
        Files.write(copy, damagedLines, StandardCharsets.UTF_8);
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(copy), field);
        assertTrue(error.getMessage().startsWith(copy.toString()), error.getMessage());
        final String reason = error.getMessage().substring(copy.toString().length());
        assertTrue(reason.contains("line " + lineNumber), error.getMessage());
        assertTrue(reason.contains(field), error.getMessage());
        final IllegalArgumentException filtered = assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.read(copy, record -> false), field);
        assertEquals(error.getMessage(), filtered.getMessage());
        return error;
    }

    private void traceEvaluation(final Path file, final BarSeries series, final int from, final int to)
            throws IOException {
        final StudyRunner runner = runner(StudyRunner.Partitions.lockedDefault(), 2);
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", "rev", "fp", null,
                ElliottResearchTrace.MODE_REAL, -1, -1)) {
            runner.evaluate("BTC", series, from, to, trace);
        }
    }

    private static StudyRunner runner(final StudyRunner.Partitions partitions, final int ensembleSize) {
        return new StudyRunner(ElliottResearchTraceTest::detectorFactory,
                List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CYCLE_5_3),
                List.of(rule("first"), rule("second")), configuration(partitions, ensembleSize));
    }

    private static RelationshipRule rule(final String id) {
        return new RelationshipRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvidence evaluate(final TopologyCandidate candidate) {
                return RuleEvidence.pass(id, List.of("synthetic"), "synthetic pass");
            }
        };
    }

    private static StudyRunner.Configuration configuration(final StudyRunner.Partitions partitions,
            final int ensembleSize) {
        return new StudyRunner.Configuration(partitions,
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", SEED, List.of(2), ensembleSize,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic",
                        ElliottResearchTraceTest::detectorFactory)),
                "synthetic-primary", null);
    }

    private static SwingDetector detectorFactory() {
        return (series, index, degree) -> {
            final int[] pivotIndices = { 1, 3, 5, 7, 9, 11, 13, 15, 17 };
            final List<SwingPivot> pivots = new ArrayList<>();
            for (final int pivotIndex : pivotIndices) {
                if (pivotIndex >= series.getBeginIndex() && pivotIndex <= index && pivotIndex <= series.getEndIndex()) {
                    final SwingPivotType type = pivotIndex % 4 == 1 ? SwingPivotType.LOW : SwingPivotType.HIGH;
                    pivots.add(new SwingPivot(pivotIndex, series.getBar(pivotIndex).getClosePrice(), type));
                }
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }

    private static BarSeries buildSeries(final int count) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double close = syntheticClose(index);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static double syntheticClose(final int index) {
        return switch (index) {
        case 1 -> 100;
        case 3 -> 120;
        case 5 -> 110;
        case 7 -> 140;
        case 9 -> 130;
        case 11 -> 160;
        case 13 -> 150;
        case 15 -> 180;
        case 17 -> 170;
        default -> 100 + index;
        };
    }

    private static List<ConfirmedPivot> alternating(final double... prices) {
        final List<ConfirmedPivot> pivots = new ArrayList<>(prices.length);
        for (int i = 0; i < prices.length; i++) {
            pivots.add(new ConfirmedPivot(i, i, DoubleNum.valueOf(prices[i]),
                    i % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH));
        }
        return pivots;
    }

    private static TopologyCandidate motive(final double... prices) {
        return new TopologyCandidate(TopologyGrammar.MOTIVE_5, WaveDirection.BULLISH, alternating(prices));
    }

    /** Status and evidence tallies rebuilt from trace records. */
    private static final class Counts {
        private long evaluations;
        private long complete;
        private long forming;
        private long ambiguous;
        private long noMatch;
        private long invalidated;
        private long insufficientHistory;
        private long evidencePass;
        private long evidenceFail;
        private long evidencePending;
        private long evidenceUnavailable;
        private long evidenceNotApplicable;

        static Counts of(final StudyReport.PartitionMetrics metrics) {
            final Counts counts = new Counts();
            counts.evaluations = metrics.evaluationCount();
            counts.complete = metrics.completeCount();
            counts.forming = metrics.formingCount();
            counts.ambiguous = metrics.ambiguousCount();
            counts.noMatch = metrics.noMatchCount();
            counts.invalidated = metrics.invalidatedCount();
            counts.insufficientHistory = metrics.insufficientHistoryCount();
            counts.evidencePass = metrics.evidencePassCount();
            counts.evidenceFail = metrics.evidenceFailCount();
            counts.evidencePending = metrics.evidencePendingCount();
            counts.evidenceUnavailable = metrics.evidenceUnavailableCount();
            counts.evidenceNotApplicable = metrics.evidenceNotApplicableCount();
            return counts;
        }

        void add(final JsonObject record) {
            evaluations++;
            switch (record.get("status").getAsString()) {
            case "COMPLETE" -> {
                complete++;
                for (final JsonElement rule : record.getAsJsonArray("candidates")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("rules")) {
                    switch (rule.getAsJsonObject().get("state").getAsString()) {
                    case "PASS" -> evidencePass++;
                    case "FAIL" -> evidenceFail++;
                    case "PENDING" -> evidencePending++;
                    case "UNAVAILABLE" -> evidenceUnavailable++;
                    case "NOT_APPLICABLE" -> evidenceNotApplicable++;
                    default -> throw new IllegalStateException("unknown evidence state");
                    }
                }
            }
            case "FORMING" -> forming++;
            case "AMBIGUOUS" -> ambiguous++;
            case "NO_MATCH" -> noMatch++;
            case "INVALIDATED" -> invalidated++;
            case "INSUFFICIENT_HISTORY" -> insufficientHistory++;
            default -> throw new IllegalStateException("unknown status");
            }
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Counts counts && toString().equals(counts.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public String toString() {
            return List
                    .of(evaluations, complete, forming, ambiguous, noMatch, invalidated, insufficientHistory,
                            evidencePass, evidenceFail, evidencePending, evidenceUnavailable, evidenceNotApplicable)
                    .toString();
        }
    }
}
