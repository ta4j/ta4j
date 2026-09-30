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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", ElliottResearchTrace.MODE_REAL, -1,
                -1)) {
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
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", ElliottResearchTrace.MODE_REAL, -1,
                -1)) {
            trace.topology(StudyObserver.Scope.real("h2", "all-rules", "MOTIVE_5", List.of("first", "second"), "d"),
                    "calibration", 11, Instant.parse("2018-01-12T00:00:00Z"), alternating(10, 20, 14), analysis,
                    evidence);
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
            assertEquals(candidate.get("candidateKey").getAsString() + "@v1", candidate.get("version").getAsString());
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
    void candidateIdentityCoversTheFullPlacementAndVersionsTrackEvidence() throws Exception {
        final TopologyCandidate first = motive(10, 20, 14, 26, 18, 32);
        final TopologyCandidate sameEndpoints = motive(10, 20, 15, 26, 18, 32);
        assertEquals(first.startBarIndex(), sameEndpoints.startBarIndex());
        assertEquals(first.endBarIndex(), sameEndpoints.endBarIndex());
        assertNotEquals(ElliottResearchTrace.candidateKey(first), ElliottResearchTrace.candidateKey(sameEndpoints));
        assertEquals(ElliottResearchTrace.candidateKey(first),
                ElliottResearchTrace.candidateKey(motive(10, 20, 14, 26, 18, 32)));

        final TopologyAnalysis analysis = new TopologyAnalysis(TopologyStatus.COMPLETE, WaveDirection.BULLISH,
                List.of(first), "complete", -1, -1);
        final StudyObserver.Scope scope = StudyObserver.Scope.real("h2", "all-rules", "MOTIVE_5", List.of("first"),
                "d");
        final List<RuleEvidence> passing = List.of(RuleEvidence.pass("first", List.of("a"), "pass"));
        final List<RuleEvidence> failing = List.of(RuleEvidence.fail("first", List.of("a"), "fail"));
        final Path file = directory.resolve("versions.jsonl");
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", ElliottResearchTrace.MODE_REAL, -1,
                -1)) {
            for (final List<RuleEvidence> rules : List.of(passing, passing, failing)) {
                trace.topology(scope, "calibration", 5, Instant.parse("2018-01-06T00:00:00Z"), first.pivots(), analysis,
                        List.of(rules));
            }
            assertEquals(3L, trace.records());
        }

        final String key = ElliottResearchTrace.candidateKey(first);
        final List<String> versions = ElliottResearchTrace.read(file)
                .records()
                .stream()
                .map(record -> record.getAsJsonArray("candidates")
                        .get(0)
                        .getAsJsonObject()
                        .get("version")
                        .getAsString())
                .toList();
        assertEquals(List.of(key + "@v1", key + "@v1", key + "@v2"), versions);
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
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC",
                ElliottResearchTrace.MODE_SELECTED_NULL_MEMBER, 2, 1)) {
            runner.replayNullMember(buildSeries(24), 0, 23, 2, 1, trace);
            assertTrue(trace.records() > 0);
        }

        final ElliottResearchTrace.TraceFile parsed = ElliottResearchTrace.read(file);
        assertTrue(parsed.complete());
        assertEquals("selected-null-member", parsed.header().get("traceMode").getAsString());
        assertEquals(2, parsed.header().get("nullBlockLength").getAsInt());
        assertEquals(1, parsed.header().get("nullMemberIndex").getAsInt());
        assertFalse(parsed.records().isEmpty());
        for (final JsonObject record : parsed.records()) {
            assertEquals("null", record.get("section").getAsString());
            assertEquals(2, record.get("nullBlockLength").getAsInt());
            assertEquals(1, record.get("nullMemberIndex").getAsInt());
        }
    }

    @Test
    void traceRejectsObservationsFromAnotherMemberOrMode() throws Exception {
        final Path file = directory.resolve("guard.jsonl");
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", ElliottResearchTrace.MODE_REAL, -1,
                -1)) {
            final StudyObserver.Scope nullScope = new StudyObserver.Scope("null", "topology-only", "MOTIVE_5",
                    List.of(), "d", 2, 0);
            assertThrows(IllegalArgumentException.class, () -> trace.alternative(nullScope, "calibration", 1,
                    Instant.parse("2018-01-02T00:00:00Z"), List.of(), "no-match", Set.of("no-match")));
            assertEquals(0L, trace.records());
        }
        assertThrows(IllegalArgumentException.class,
                () -> ElliottResearchTrace.open(directory.resolve("bad.jsonl"), "BTC", "all-null", -1, -1));
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

    private void traceEvaluation(final Path file, final BarSeries series, final int from, final int to)
            throws IOException {
        final StudyRunner runner = runner(StudyRunner.Partitions.lockedDefault(), 2);
        try (ElliottResearchTrace trace = ElliottResearchTrace.open(file, "BTC", ElliottResearchTrace.MODE_REAL, -1,
                -1)) {
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
