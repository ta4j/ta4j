/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ta4jexamples.charting.replay.ReplayFrame.Candidate;
import ta4jexamples.charting.replay.ReplaySession.TimelineEntry;

class ReplaySessionTest {

    @TempDir
    Path temp;

    private ReplaySession open(final Path directory, final String key, final int window, final int cap) {
        return ReplaySession.open(ReplayArtifact.open(directory), key, ReplayArtifact.TRACE_MODE_REAL, window, cap);
    }

    private ReplaySession open(final Path directory) {
        return open(directory, ReplayFixture.RULES_KEY, 120, 8);
    }

    @Test
    void defaultCursorIsFirstAmbiguousRecord() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        assertEquals(33, session.cursor());
        assertEquals("AMBIGUOUS", session.frame().status());
        assertEquals(2, session.frame().candidates().size());
        assertEquals(ReplayFixture.FIRST_AS_OF, session.firstAsOf());
        assertEquals(ReplayFixture.BARS - 1, session.lastAsOf());
    }

    @Test
    void defaultCursorRecognizesTheProducersLowercaseAmbiguousAlternativeStatus() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")), ReplayFixture.COMPETING_KEY, 120,
                8);

        assertEquals(33, session.cursor(), "the first ambiguous alternative record, not the first record");
        assertEquals("alternative", session.frame().kind());
        assertEquals("ambiguous", session.frame().status());
        assertTrue(session.frame().candidates().isEmpty(), "alternative records carry labels, not candidates");
        assertEquals(List.of("BEARISH:5-30", "BULLISH:2-30"), session.frame().labels());
    }

    @Test
    void firstRecordIsAnInitialStateNotAStateChange() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final ReplayFrame first = session.seek(ReplayFixture.FIRST_AS_OF);

        assertTrue(first.initial());
        assertFalse(first.transition());
        final String text = ReplayEvidenceText.render(first);
        assertTrue(text.contains("initial recorded state"), text);
        assertFalse(text.contains("state changed at this bar"), text);
        assertFalse(text.contains("unchanged since the previous"), text);
        final JsonObject semantic = JsonParser.parseString(first.toSemanticJson()).getAsJsonObject();
        assertTrue(semantic.get("initial").getAsBoolean());
        assertFalse(semantic.get("transition").getAsBoolean());

        final ReplayFrame second = session.stepBar(1);
        assertFalse(second.initial());
        assertFalse(second.transition());
        assertTrue(ReplayEvidenceText.render(second).contains("unchanged since the previous recorded bar"));
        final ReplayFrame changed = session.seek(15);
        assertFalse(changed.initial());
        assertTrue(changed.transition());
        assertTrue(ReplayEvidenceText.render(changed).contains("state changed at this bar"));
    }

    @Test
    void previousTransitionNeverStopsAtTheInitialRecord() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        assertEquals(15, session.seek(15).cursor());
        assertEquals(15, session.stepTransition(-1).cursor(),
                "records 10..14 are identical, so there is no earlier state change");
        session.seek(20);
        assertEquals(15, session.stepTransition(-1).cursor());
        session.seek(ReplayFixture.FIRST_AS_OF);
        assertEquals(ReplayFixture.FIRST_AS_OF, session.stepTransition(-1).cursor());
    }

    @Test
    void frameIsUnchangedWhenOnlyFutureBarsDiffer() {
        final Path original = ReplayFixture.write(temp.resolve("original"));
        final Path mutated = ReplayFixture.write(temp.resolve("mutated"), options -> {
            options.mutateBarsFrom = 36;
            return options;
        });

        final ReplayFrame before = open(original).seek(35);
        final ReplaySession mutatedSession = open(mutated);
        final ReplayFrame after = mutatedSession.seek(35);

        assertEquals(before.toSemanticJson(), after.toSemanticJson());
        assertEquals(before.digest(), after.digest());
        final ReplayChartModel model = ReplayChartModel.of(after, mutatedSession.bars());
        assertEquals(35, model.windowStart() + model.series().getEndIndex());
        assertNotEquals(before.digest(), open(mutated).seek(45).digest());
        assertNotEquals(open(original).seek(45).digest(), open(mutated).seek(45).digest());
    }

    @Test
    void pivotAppearsOnlyFromItsConfirmationBar() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final ReplayFrame beforeConfirmation = session.seek(32);
        assertTrue(beforeConfirmation.pivots().stream().noneMatch(pivot -> pivot.index() == 30));
        final ReplayFrame atConfirmation = session.seek(33);
        final ReplayFrame.Pivot pivot = atConfirmation.pivots()
                .stream()
                .filter(candidate -> candidate.index() == 30)
                .findFirst()
                .orElseThrow();
        assertEquals(33, pivot.confirmationIndex());
        assertTrue(pivot.newlyConfirmed());
        assertFalse(session.seek(34)
                .pivots()
                .stream()
                .filter(p -> p.index() == 30)
                .findFirst()
                .orElseThrow()
                .newlyConfirmed());
        for (int cursor = ReplayFixture.FIRST_AS_OF; cursor < ReplayFixture.BARS; cursor++) {
            final ReplayFrame frame = session.seek(cursor);
            assertTrue(frame.pivots().stream().allMatch(p -> p.confirmationIndex() <= frame.cursor()));
            assertEquals(0, frame.suppressedFuture());
        }
    }

    @Test
    void guardDropsPivotsConfirmedAfterTheCursor() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run"), options -> {
            options.leakFuturePivot = true;
            return options;
        }));

        final ReplayFrame frame = session.seek(20);

        assertEquals(1, frame.suppressedFuture());
        assertTrue(frame.pivots().stream().noneMatch(pivot -> pivot.index() == 30));
        assertTrue(ReplayEvidenceText.render(frame).contains("causality guard removed 1"));
    }

    @Test
    public void guardAlsoDropsCandidatePlacementsConfirmedAfterTheCursor() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("candidate-leak"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
        for (int line = 1; line < lines.size() - 1; line++) {
            final JsonObject record = JsonParser.parseString(lines.get(line)).getAsJsonObject();
            if ("h2".equals(record.get("section").getAsString()) && record.get("asOfIndex").getAsInt() == 23) {
                record.getAsJsonArray("pivots").get(0).getAsJsonObject().addProperty("confirmationIndex", 24);
                lines.set(line, record.toString());
            }
        }
        Files.write(trace, lines, StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");

        final ReplayFrame frame = open(run).seek(23);

        assertEquals(2, frame.suppressedFuture());
        assertTrue(frame.pivots().stream().noneMatch(pivot -> pivot.index() == 0));
        assertEquals(1, frame.candidates().size());
        assertEquals(8, frame.candidates().get(0).placement().size());
        assertTrue(frame.candidates().get(0).placement().stream().noneMatch(point -> point.index() == 0));
        assertTrue(ReplayEvidenceText.render(frame).contains("causality guard removed 2"));
    }

    @Test
    void seekAndStepConvergeOnTheSameFrame() {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final ReplayFrame direct = open(run).seek(40);

        final ReplaySession forward = open(run);
        forward.seek(ReplayFixture.FIRST_AS_OF);
        ReplayFrame stepped = forward.frame();
        while (stepped.cursor() < 40) {
            stepped = forward.stepBar(1);
        }
        final ReplaySession backward = open(run);
        backward.seek(ReplayFixture.BARS - 1);
        ReplayFrame back = backward.frame();
        while (back.cursor() > 40) {
            back = backward.stepBar(-1);
        }

        assertEquals(direct.digest(), stepped.digest());
        assertEquals(direct.digest(), back.digest());
    }

    @Test
    void stepBarStopsAtEitherEnd() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        assertEquals(ReplayFixture.FIRST_AS_OF, session.seek(ReplayFixture.FIRST_AS_OF).cursor());
        assertEquals(ReplayFixture.FIRST_AS_OF, session.stepBar(-1).cursor());
        assertEquals(ReplayFixture.BARS - 1, session.seek(ReplayFixture.BARS - 1).cursor());
        assertEquals(ReplayFixture.BARS - 1, session.stepBar(1).cursor());
    }

    @Test
    void transitionStepsSkipUnchangedRecords() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));
        session.seek(ReplayFixture.FIRST_AS_OF);

        final int[] expected = { 15, 23, 33, 41, 43 };
        for (final int cursor : expected) {
            final ReplayFrame frame = session.stepTransition(1);
            assertEquals(cursor, frame.cursor());
            assertTrue(frame.transition());
        }
        assertEquals(43, session.stepTransition(1).cursor());
        assertEquals(41, session.stepTransition(-1).cursor());
        assertEquals(33, session.stepTransition(-1).cursor());
    }

    @Test
    void seekToInstantUsesBarsThatHadEnded() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final Instant endOfBar25 = ReplayFixture.START.plusSeconds(26L * 86_400);
        assertEquals(25, session.seek(endOfBar25).cursor());
        assertEquals(24, session.seek(endOfBar25.minusSeconds(1)).cursor());
        assertThrows(ReplayArtifactException.class, () -> session.seek(ReplayFixture.START.minusSeconds(1)));
        assertThrows(ReplayArtifactException.class, () -> session.seek(3));
    }

    @Test
    void revisedCandidateKeepsEarlierVersionsReadable() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        assertEquals(ReplayFixture.A_VERSION_1,
                session.seek(30).candidate(ReplayFixture.CANDIDATE_A).orElseThrow().version());
        assertEquals(ReplayFixture.A_VERSION_2,
                session.seek(36).candidate(ReplayFixture.CANDIDATE_A).orElseThrow().version());
        assertEquals(ReplayFixture.A_VERSION_1,
                session.seek(30).candidate(ReplayFixture.CANDIDATE_A).orElseThrow().version());
        session.seek(45);
        final List<TimelineEntry> aTimeline = session.candidateTimeline(ReplayFixture.CANDIDATE_A);
        assertEquals(List.of(new TimelineEntry(23, ReplayFixture.A_VERSION_1, true),
                new TimelineEntry(33, ReplayFixture.A_VERSION_2, true)), aTimeline);
        final List<TimelineEntry> bTimeline = session.candidateTimeline(ReplayFixture.CANDIDATE_B);
        assertEquals(List.of(new TimelineEntry(33, ReplayFixture.B_VERSION_1, true), new TimelineEntry(41, "", false)),
                bTimeline);
        assertTrue(session.candidateTimeline("missing").isEmpty());
    }

    @Test
    void sameLengthTraceEditAfterIndexingIsRejectedInsteadOfProjected() throws java.io.IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final ReplaySession session = open(run);
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final String original = java.nio.file.Files.readString(trace);
        final String edited = original.replace(ReplayFixture.A_VERSION_2,
                "0".repeat(ReplayFixture.A_VERSION_2.length()));
        assertNotEquals(original, edited);
        assertEquals(original.length(), edited.length());
        java.nio.file.Files.writeString(trace, edited, java.nio.charset.StandardCharsets.UTF_8);

        final String failure = assertThrows(ReplayArtifactException.class, () -> session.seek(43)).getMessage();
        assertTrue(failure.contains("changed on disk after it was indexed"), failure);
    }

    @Test
    void asOfTimeEditAfterIndexingIsRejectedInsteadOfProjected() throws java.io.IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final ReplaySession session = open(run);
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final String original = java.nio.file.Files.readString(trace);
        final String edited = original.replace("\"asOfTime\":\"2020-02-14T00:00:00Z\"",
                "\"asOfTime\":\"2020-02-14T00:00:01Z\"");
        assertNotEquals(original, edited);
        java.nio.file.Files.writeString(trace, edited, java.nio.charset.StandardCharsets.UTF_8);

        final String failure = assertThrows(ReplayArtifactException.class, () -> session.seek(43)).getMessage();
        assertTrue(failure.contains("changed on disk after it was indexed"), failure);
    }

    @Test
    void selectionIsKeyedAndSurvivesNavigation() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));
        session.seek(36);

        assertEquals(ReplayFixture.CANDIDATE_B, session.select("2").selectedCandidate());
        assertTrue(session.frame().candidate(ReplayFixture.CANDIDATE_B).orElseThrow().selected());
        assertEquals(ReplayFixture.CANDIDATE_B, session.seek(50).selectedCandidate(),
                "key is kept even when absent at the new cursor");
        assertTrue(session.frame().candidates().stream().noneMatch(Candidate::selected));
        assertEquals(ReplayFixture.CANDIDATE_B, session.seek(35).selectedCandidate());
        assertTrue(session.frame().candidate(ReplayFixture.CANDIDATE_B).orElseThrow().selected());
        assertEquals("", session.clearSelection().selectedCandidate());
        assertThrows(ReplayArtifactException.class, () -> session.select("9"));
        assertThrows(ReplayArtifactException.class, () -> session.select("nope"));
        assertEquals("nope", session.follow("nope").selectedCandidate());
    }

    @Test
    void overlayCapDrawsSelectedFirstAndCountsTheRest() {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final ReplaySession session = open(run, ReplayFixture.RULES_KEY, 120, 1);
        session.seek(36);

        final ReplayFrame capped = session.frame();
        assertEquals(1, capped.overlayed());
        assertEquals(1, capped.truncated());
        assertEquals(2, capped.candidates().size(), "every retained candidate stays inspectable");
        assertTrue(capped.candidates().get(0).overlayed());
        assertFalse(capped.candidates().get(1).overlayed());
        assertTrue(ReplayEvidenceText.render(capped).contains("NOT DRAWN: 1"));

        final ReplayFrame selected = session.select(ReplayFixture.CANDIDATE_B);
        assertFalse(selected.candidates().get(0).overlayed());
        assertTrue(selected.candidates().get(1).overlayed());
        assertEquals(1,
                ReplayChartModel.of(selected, session.bars())
                        .layers()
                        .stream()
                        .filter(layer -> layer.candidate() >= 0)
                        .count());
    }

    @Test
    void windowClipsOverlayPointsAndNarrowsTheAxis() {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final ReplaySession session = open(run, ReplayFixture.RULES_KEY, 8, 8);

        final ReplayFrame frame = session.seek(36);

        assertEquals(29, frame.windowStart());
        assertTrue(frame.clippedPoints() > 0);
        assertTrue(frame.candidates()
                .stream()
                .flatMap(candidate -> candidate.placement().stream())
                .anyMatch(point -> !point.inWindow()));
        final ReplayChartModel model = ReplayChartModel.of(frame, session.bars());
        assertEquals(8, model.series().getBarCount());
        assertTrue(model.axisLow() < model.axisHigh());
    }

    @Test
    void topologyStreamPointsToTheRuleEvidenceRow() {
        final ReplaySession topology = open(ReplayFixture.write(temp.resolve("run")), ReplayFixture.TOPOLOGY_KEY, 120,
                8);

        final ReplayFrame frame = topology.seek(36);

        assertTrue(frame.candidates().stream().allMatch(candidate -> candidate.rules().isEmpty()));
        assertTrue(frame.ruleEvidenceHint().contains(ReplayFixture.RULES_KEY));
        final String text = ReplayEvidenceText.render(frame);
        assertTrue(text.contains("not evaluated"));
        assertTrue(text.contains(ReplayFixture.RULES_KEY));
    }

    @Test
    void rulesStreamCarriesRecordedEvidence() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final String text = ReplayEvidenceText.render(session.seek(36));

        assertTrue(text.contains("wave2-origin"));
        assertTrue(text.contains("PASS"));
        assertTrue(text.contains("wave 2 stayed above the origin"));
        assertTrue(text.contains("wave 2 may not retrace the whole of wave 1 (A)"));
    }

    @Test
    void emptyStatesAreReportedNotHidden() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final ReplayFrame frame = session.seek(ReplayFixture.FIRST_AS_OF);

        assertEquals("NO_MATCH", frame.status());
        assertTrue(frame.candidates().isEmpty());
        assertTrue(ReplayEvidenceText.render(frame).contains("candidates retained: 0"));
        assertEquals(1,
                ReplayChartModel.of(frame, session.bars())
                        .layers()
                        .stream()
                        .filter(layer -> layer.name().equals("As-of bar"))
                        .count());
    }

    private static ReplaySession openNull(final Path directory, final String key) {
        return ReplaySession.open(ReplayArtifact.open(directory), key, ReplayArtifact.TRACE_MODE_NULL_MEMBER, 120, 8);
    }

    private Path nullRun(final String name) {
        return ReplayFixture.write(temp.resolve(name), options -> {
            options.nullTrace = true;
            return options;
        });
    }

    @Test
    void nullMemberReplayMapsRowsToTheProducersNullSectionAndUsesRecordedPrices() {
        final Path run = nullRun("run");

        for (final String key : List.of(ReplayFixture.RULES_KEY, ReplayFixture.TOPOLOGY_KEY)) {
            final ReplaySession session = openNull(run, key);
            assertEquals("null", session.family().section());
            assertEquals(33, session.cursor());
            assertEquals(2, session.frame().candidates().size());
            assertEquals(Integer.toString(ReplayFixture.NULL_BAR_OFFSET + ReplayFixture.close(40)),
                    session.bars().get(40).close());
            assertNotEquals(open(run, key, 120, 8).bars().get(40).close(), session.bars().get(40).close());
            final ReplayChartModel model = ReplayChartModel.of(session.seek(40), session.bars());
            assertTrue(model.series().getLastBar().getClosePrice().doubleValue() > ReplayFixture.NULL_BAR_OFFSET);
        }
        assertEquals("MOTIVE_5", openNull(run, ReplayFixture.TOPOLOGY_KEY).family().mode());
        assertEquals("all-rules", openNull(run, ReplayFixture.RULES_KEY).family().mode());
    }

    @Test
    void nullMemberReplayRejectsUnsupportedRowsAndMismatchedRecordings() throws java.io.IOException {
        final Path run = nullRun("run");
        final String competing = assertThrows(ReplayArtifactException.class,
                () -> openNull(run, ReplayFixture.COMPETING_KEY)).getMessage();
        assertTrue(competing.contains("records only h1 and h2 rows"), competing);

        final Path block = ReplayFixture.write(temp.resolve("block"), options -> {
            options.nullTrace = true;
            options.nullBlockLength = 30;
            return options;
        });
        final String blockFailure = assertThrows(ReplayArtifactException.class,
                () -> openNull(block, ReplayFixture.RULES_KEY)).getMessage();
        assertTrue(blockFailure.contains("null block length 20"), blockFailure);
        assertTrue(blockFailure.contains("records block length 30"), blockFailure);

        final Path noBars = ReplayFixture.write(temp.resolve("nobars"), options -> {
            options.nullTrace = true;
            options.nullTraceWithoutBars = true;
            return options;
        });
        assertTrue(assertThrows(ReplayArtifactException.class, () -> openNull(noBars, ReplayFixture.RULES_KEY))
                .getMessage()
                .contains("does not record a price bar"));
    }

    @Test
    void truncatedTimelineSeedsFromThePrecedingRecordAndReportsTheCut() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));
        session.seek(40);

        assertEquals(
                List.of(new TimelineEntry(23, ReplayFixture.A_VERSION_1, true),
                        new TimelineEntry(33, ReplayFixture.A_VERSION_2, true)),
                session.candidateTimeline(ReplayFixture.CANDIDATE_A));
        assertEquals(-1, session.timelineStartAsOf());
        assertEquals(List.of(), session.candidateTimeline(ReplayFixture.CANDIDATE_A, 3));
        assertEquals(38, session.timelineStartAsOf(3));
        assertEquals(List.of(new TimelineEntry(33, ReplayFixture.A_VERSION_2, true)),
                session.candidateTimeline(ReplayFixture.CANDIDATE_A, 8));
    }

    @Test
    public void comparisonHeadersRejectDuplicateNamesEvenWithMatchingCells() throws IOException {
        for (final String column : List.of("observed", "validNullMembers")) {
            for (final boolean conflicting : List.of(false, true)) {
                final Path run = ReplayFixture.write(temp.resolve("duplicate-header-" + column + "-" + conflicting));
                final Path csv = run.resolve("comparisons.csv");
                final List<List<String>> table = ReplayArtifact.parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
                final int offset = table.getFirst().indexOf(column);
                final StringBuilder rewritten = new StringBuilder();
                for (int i = 0; i < table.size(); i++) {
                    final List<String> row = new ArrayList<>(table.get(i));
                    final String repeated = i > 0 && conflicting ? "-2" : row.get(offset);
                    row.add(offset, repeated);
                    rewritten.append(String.join(",", row)).append('\n');
                }
                Files.writeString(csv, rewritten, StandardCharsets.UTF_8);
                ReplayFixture.refreshComparisonsDigest(run);
                final String rejected = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                        .getMessage();
                assertTrue(rejected.contains("header has duplicate column '" + column + "'")
                        && rejected.contains("Regenerate"), rejected);
            }
        }
    }

    @Test
    public void comparisonRowsRequireTheHeadersExactCellCount() throws IOException {
        for (final boolean extra : List.of(false, true)) {
            final Path run = ReplayFixture.write(temp.resolve("row-width-" + extra));
            final Path csv = run.resolve("comparisons.csv");
            final List<List<String>> table = ReplayArtifact.parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
            final StringBuilder rewritten = new StringBuilder();
            for (int i = 0; i < table.size(); i++) {
                final List<String> row = new ArrayList<>(table.get(i));
                if (i == 1) {
                    if (extra) {
                        row.add("unrecorded");
                    } else {
                        row.removeLast();
                    }
                }
                rewritten.append(String.join(",", row)).append('\n');
            }
            Files.writeString(csv, rewritten, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);
            final String rejected = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(rejected.contains("row 2 has") && rejected.contains("cells; expected 24")
                    && rejected.contains("Regenerate"), rejected);
        }
    }

    @Test
    public void comparisonRowsRejectImpossibleNullReferenceSummariesBeforeSelection() throws IOException {
        final List<Map<String, String>> corruptions = List.of(Map.of("validNullMembers", "-1"),
                Map.of("requestedNullMembers", "-1"), Map.of("unavailableNullMembers", "-1"),
                Map.of("requestedNullMembers", "6"), Map.of("validNullMembers", "0", "unavailableNullMembers", "5"),
                Map.of("nullLow", "0.9", "nullMedian", "0.5", "nullHigh", "0.1"), Map.of("nullMedian", ""),
                Map.of("nullHigh", "Infinity"), Map.of("observed", "Infinity"), Map.of("empiricalReferenceRank", "1.5"),
                Map.of("empiricalReferenceRank", "-0.1"), Map.of("empiricalReferenceRank", "0"),
                Map.of("empiricalReferenceRank", "0.123456789"), Map.of("empiricalReferenceRank", "0.5"),
                Map.of("observedMinusNullMedian", "0.123456789"), Map.of("availability", "unavailable"),
                Map.of("availability", "not-comparable"), Map.of("nullBlockLength", ""), Map.of("nullBlockLength", "0"),
                Map.of("nullBlockLength", "-1"),
                Map.of("validNullMembers", "1", "unavailableNullMembers", "4", "nullLow", "0.1", "nullHigh", "0.3",
                        "empiricalReferenceRank", "0.5"),
                Map.of("validNullMembers", "2", "unavailableNullMembers", "3", "nullLow", "0.1", "nullHigh", "0.3",
                        "empiricalReferenceRank", "0.3333333333333333"));
        for (int i = 0; i < corruptions.size(); i++) {
            final Path run = ReplayFixture.write(temp.resolve("null-reference-invalid-" + i));
            comparisonFields(run, ReplayFixture.RULES_KEY, corruptions.get(i));
            final ReplayArtifactException rejected = assertThrows(ReplayArtifactException.class,
                    () -> ReplayArtifact.open(run), corruptions.get(i).toString());
            assertTrue(rejected.getMessage().contains("comparisons.csv row 2")
                    && (rejected.getMessage().contains("inconsistent null reference")
                            || rejected.getMessage().contains("observed must equal"))
                    && rejected.getMessage().contains("Regenerate"), rejected.getMessage());
        }
    }

    @Test
    public void comparisonRowsPreserveValidNullReferenceBoundaryCases() throws IOException {
        for (final int requested : List.of(1, 2, Integer.MAX_VALUE)) {
            final Path run = ReplayFixture.write(temp.resolve("requested-" + requested));
            configureNullEnsemble(run, requested);
            if (requested == 2) {
                comparisonFields(run, ReplayFixture.RULES_KEY,
                        Map.of("validNullMembers", "2", "unavailableNullMembers", "0"));
            }
            assertDoesNotThrow(() -> ReplayArtifact.open(run));
        }
        final Path noValid = ReplayFixture.write(temp.resolve("no-valid-null-members"));
        comparisonFields(noValid, ReplayFixture.RULES_KEY,
                Map.of("validNullMembers", "0", "unavailableNullMembers", "5", "nullMedian", "", "nullLow", "",
                        "nullHigh", "", "observedMinusNullMedian", "", "empiricalReferenceRank", "", "availability",
                        "unavailable"));
        assertDoesNotThrow(() -> ReplayArtifact.open(noValid));
        final Path undefined = ReplayFixture.write(temp.resolve("undefined-observed"));
        comparisonFields(undefined, ReplayFixture.RULES_KEY.replace("ambiguousRate", "confirmationLagBars"),
                Map.of("observed", "", "observedMinusNullMedian", "", "empiricalReferenceRank", "", "availability",
                        "unavailable"));
        assertDoesNotThrow(() -> ReplayArtifact.open(undefined));
        final ReplayArtifact blockless = ReplayArtifact.open(ReplayFixture.write(temp.resolve("no-null-reference")));
        assertEquals(0, blockless.comparison(ReplayFixture.COMPETING_KEY).nullBlockLength());
        assertTrue(Double
                .isNaN(blockless.comparison(ReplayFixture.COMPETING_KEY.replace("ambiguousRate", "confirmationLagBars"))
                        .observed()));
    }

    @Test
    public void comparisonRatesRejectIncoherentSupportBeforeAnyTraceIsSelected() throws IOException {
        final List<Map<String, String>> corruptions = List.of(Map.of("numerator", ""), Map.of("denominator", ""),
                Map.of("numerator", "-1"), Map.of("denominator", "-1"), Map.of("numerator", "2", "denominator", "1"),
                Map.of("numerator", "1", "denominator", "0"),
                Map.of("numerator", "0", "denominator", "0", "observed", "0"), Map.of("observed", ""),
                Map.of("observed", "Infinity"));
        for (final String metric : List.of("completeOccupancyRate", "ambiguousRate", "noMatchRate", "evidencePassRate",
                "jointPassRate")) {
            for (int index = 0; index <= corruptions.size(); index++) {
                final Path run = ReplayFixture.write(temp.resolve(metric + "-support-" + index));
                final Map<String, String> fields = index < corruptions.size() ? corruptions.get(index)
                        : Map.of("observed", Double.toString(Math.nextUp(expectedValue(metric))));
                comparisonFields(run, ReplayFixture.RULES_KEY.replace("ambiguousRate", metric), fields);
                final String failure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                        .getMessage();
                assertTrue(failure.contains("metric " + metric) && failure.contains("Regenerate"), failure);
            }
        }
    }

    @Test
    public void comparisonRatesPreserveExactLongAndUndefinedBoundaries() throws IOException {
        for (final String metric : List.of("completeOccupancyRate", "ambiguousRate", "noMatchRate", "evidencePassRate",
                "jointPassRate")) {
            final long[][] supports = { { Long.MAX_VALUE, Long.MAX_VALUE }, { 1, Long.MAX_VALUE },
                    { 0, Long.MAX_VALUE }, { 0, 0 } };
            for (int index = 0; index < supports.length; index++) {
                final Path run = ReplayFixture.write(temp.resolve(metric + "-boundary-" + index));
                final long numerator = supports[index][0];
                final long denominator = supports[index][1];
                comparisonMetric(run, ReplayFixture.RULES_KEY, metric,
                        denominator == 0 ? Double.NaN : numerator / (double) denominator, numerator, denominator);
                assertDoesNotThrow(() -> ReplayArtifact.open(run));
            }
        }
    }

    @Test
    public void comparisonRowsRejectUnknownMetricsAndNonRateSupport() throws IOException {
        final Path unknown = ReplayFixture.write(temp.resolve("unknown-metric"));
        comparisonFields(unknown, ReplayFixture.RULES_KEY, Map.of("metric", "unrecordedRate"));
        final String unknownFailure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(unknown))
                .getMessage();
        assertTrue(unknownFailure.contains("unknown comparison metric"), unknownFailure);
        for (final String metric : List.of("confirmationLagBars", "labelStabilityJaccard")) {
            for (final String field : List.of("numerator", "denominator")) {
                final Path run = ReplayFixture.write(temp.resolve(metric + "-" + field));
                comparisonFields(run, ReplayFixture.RULES_KEY.replace("ambiguousRate", metric), Map.of(field, "0"));
                final String failure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                        .getMessage();
                assertTrue(failure.contains("non-rate metrics cannot carry"), failure);
            }
        }
    }

    @Test
    public void comparisonInventoryRejectsMissingRowsAtEveryConfiguredScope() throws IOException {
        final List<String> omissions = List.of(ReplayFixture.RULES_KEY, "|all-rules|", "|h2|", "|evidencePassRate|",
                "d1|");
        for (int index = 0; index < omissions.size(); index++) {
            final Path run = ReplayFixture.write(temp.resolve("missing-scope-" + index));
            final Path csv = run.resolve("comparisons.csv");
            final List<String> rows = Files.readAllLines(csv, StandardCharsets.UTF_8);
            final String marker = omissions.get(index);
            final StringBuilder retained = new StringBuilder(rows.getFirst()).append('\n');
            for (int row = 1; row < rows.size(); row++) {
                if (!rows.get(row).contains(marker)) {
                    retained.append(rows.get(row)).append('\n');
                }
            }
            Files.writeString(csv, retained, StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(run);
            final String failure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(failure.contains("row-key inventory") && failure.contains("missing "), failure);
        }
    }

    @Test
    public void comparisonInventoryRejectsInventedCoordinatesAndRecordedFamilyMismatches() throws IOException {
        final List<Map<String, String>> changes = List.of(Map.of("dataset", "foreign"), Map.of("section", "foreign"),
                Map.of("mode", "foreign"), Map.of("detector", "foreign"), Map.of("partition", "foreign"),
                Map.of("nullBlockLength", "30"), Map.of("asset", "foreign"), Map.of("grammar", "MOTIVE_5"),
                Map.of("activeRules", "wave3-not-shortest;wave2-origin"), Map.of("policy", "kernel-topology"),
                Map.of("requestedNullMembers", "6", "unavailableNullMembers", "1"));
        for (int index = 0; index < changes.size(); index++) {
            final Path run = ReplayFixture.write(temp.resolve("foreign-family-" + index));
            comparisonFields(run, ReplayFixture.RULES_KEY, changes.get(index));
            final String failure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(run))
                    .getMessage();
            assertTrue(failure.contains("run.json") && failure.contains("Regenerate"), failure);
        }
        final Path added = ReplayFixture.write(temp.resolve("added-dataset"));
        final Path csv = added.resolve("comparisons.csv");
        final List<String> rows = Files.readAllLines(csv, StandardCharsets.UTF_8);
        final String foreign = rows.get(1).replace("d1|h2|", "foreign|h2|").replace(",d1,", ",foreign,");
        Files.writeString(csv, String.join("\n", rows) + "\n" + foreign + "\n", StandardCharsets.UTF_8);
        ReplayFixture.refreshComparisonsDigest(added);
        final String failure = assertThrows(ReplayArtifactException.class, () -> ReplayArtifact.open(added))
                .getMessage();
        assertTrue(failure.contains("row-key inventory") && failure.contains("unexpected 1"), failure);
    }

    @Test
    public void comparisonInventoryPreservesConfiguredAndFrozenClassicalRuleOrder() throws IOException {
        final List<String> classical = List.of("wave2-origin", "wave3-not-shortest", "wave4-nonoverlap",
                "wave5-divergence");
        final List<String> configured = classical.reversed();
        for (final String recipe : List.of("explore", "frozen-cf525")) {
            final Path directory = ReplayFixture.write(temp.resolve("rule-order-" + recipe));
            final Path metadata = directory.resolve("run.json");
            final JsonObject run = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
            run.getAsJsonObject("recipe").addProperty("name", recipe);
            final JsonArray active = new JsonArray();
            configured.forEach(active::add);
            run.getAsJsonObject("configuration").add("activeRules", active);
            Files.writeString(metadata, run.toString(), StandardCharsets.UTF_8);
            final List<String> expected = "frozen-cf525".equals(recipe) ? classical : configured;
            final String mode = "frozen-cf525".equals(recipe) ? "classical-all" : "all-rules";
            final Path csv = directory.resolve("comparisons.csv");
            final List<List<String>> table = ReplayArtifact.parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
            final List<String> header = table.getFirst();
            final StringBuilder rewritten = new StringBuilder(String.join(",", header)).append('\n');
            for (int index = 1; index < table.size(); index++) {
                final List<String> row = new ArrayList<>(table.get(index));
                final String originalMode = row.get(header.indexOf("mode"));
                if ("all-rules".equals(originalMode)) {
                    row.set(header.indexOf("key"),
                            row.get(header.indexOf("key")).replace("|all-rules|", "|" + mode + "|"));
                    row.set(header.indexOf("mode"), mode);
                    row.set(header.indexOf("activeRules"), String.join(";", expected));
                    if ("evidencePassRate".equals(row.get(header.indexOf("metric")))) {
                        row.set(header.indexOf("denominator"), "116");
                        for (final String field : List.of("observed", "nullMedian", "nullLow", "nullHigh")) {
                            row.set(header.indexOf(field), "0.25");
                        }
                    }
                }
                rewritten.append(String.join(",", row)).append('\n');
                if ("+wave2-origin".equals(originalMode)) {
                    for (final String rule : List.of("wave4-nonoverlap", "wave5-divergence")) {
                        rewritten.append(String.join(",", row)
                                .replace("+wave2-origin", "+" + rule)
                                .replace(",wave2-origin,", "," + rule + ",")).append('\n');
                    }
                }
            }
            Files.writeString(csv, rewritten.toString(), StandardCharsets.UTF_8);
            ReplayFixture.refreshComparisonsDigest(directory);

            final ReplayArtifact artifact = assertDoesNotThrow(() -> ReplayArtifact.open(directory));
            assertEquals(50, artifact.comparisons().size());
            final String rulesKey = ReplayFixture.RULES_KEY.replace("|all-rules|", "|" + mode + "|");
            assertEquals(expected, artifact.comparison(rulesKey).activeRules());
            assertEquals(rulesKey,
                    artifact.ruleEvidenceRow(artifact.comparison(ReplayFixture.TOPOLOGY_KEY)).orElseThrow().key());
        }
    }

    @Test
    public void everyRealComparisonMetricMustMatchCapturedContributions() throws IOException {
        for (final String metric : List.of("ambiguousRate", "completeOccupancyRate", "noMatchRate", "evidencePassRate",
                "jointPassRate", "confirmationLagBars", "labelStabilityJaccard")) {
            final Path run = ReplayFixture.write(temp.resolve(metric));
            final String key = comparisonMetric(run, ReplayFixture.RULES_KEY, metric, expectedValue(metric),
                    expectedNumerator(metric), expectedDenominator(metric));
            final String evidence = open(run, key, 12, 1).comparisonEvidence();
            assertTrue(evidence.contains("-> match"), evidence);
            assertTrue(evidence.contains(metric), evidence);
            final Long numerator = expectedNumerator(metric);
            final Long denominator = expectedDenominator(metric);
            final Long changedNumerator = numerator == null ? null : numerator + (numerator < denominator ? 1 : -1);
            final double changed = numerator == null ? expectedValue(metric) + 0.125d
                    : changedNumerator / (double) denominator;
            comparisonMetric(run, key, metric, changed, changedNumerator, denominator);
            final String failure = assertThrows(ReplayArtifactException.class, () -> open(run, key, 12, 1))
                    .getMessage();
            assertTrue(failure.contains(metric) && failure.contains("contributions")
                    && failure.contains("comparison row") && failure.contains("regenerate"), failure);
        }
    }

    @Test
    public void ratioNumeratorsAndDenominatorsMustMatchEvenWhenTheObservedValueDoes() throws IOException {
        for (final String metric : List.of("ambiguousRate", "completeOccupancyRate", "noMatchRate", "evidencePassRate",
                "jointPassRate")) {
            for (final boolean changeNumerator : List.of(false, true)) {
                final Path run = ReplayFixture.write(temp.resolve(metric + changeNumerator));
                final long factor = changeNumerator ? 2 : 3;
                final String key = comparisonMetric(run, ReplayFixture.RULES_KEY, metric, expectedValue(metric),
                        expectedNumerator(metric) * factor, expectedDenominator(metric) * factor);
                final String failure = assertThrows(ReplayArtifactException.class, () -> open(run, key, 120, 8))
                        .getMessage();
                assertTrue(failure.contains(metric) && failure.contains("contributions"), failure);
            }
        }
    }

    @Test
    public void selectedNullMetricsDescribeOnlyTheirOwnMemberNeverTheRealObservation() throws IOException {
        for (final String metric : List.of("ambiguousRate", "completeOccupancyRate", "noMatchRate", "evidencePassRate",
                "jointPassRate", "confirmationLagBars", "labelStabilityJaccard")) {
            final Path run = nullRun(metric);
            final Long numerator = expectedNumerator(metric);
            final Long denominator = expectedDenominator(metric);
            final Long changedNumerator = numerator == null ? null : numerator + (numerator < denominator ? 1 : -1);
            final String key = comparisonMetric(run, ReplayFixture.RULES_KEY, metric,
                    numerator == null ? expectedValue(metric) + 0.125d : changedNumerator / (double) denominator,
                    changedNumerator, denominator);
            final String evidence = openNull(run, key).comparisonEvidence();
            assertTrue(evidence.contains("Selected null member " + metric), evidence);
            assertTrue(evidence.contains(Double.toString(expectedValue(metric))), evidence);
            assertTrue(evidence.contains("not compared with the real comparison row"), evidence);
            assertFalse(evidence.contains("-> match"), evidence);
        }
    }

    @Test
    public void unavailableMetricContributionsAreNotReportedAsMatches() throws IOException {
        for (final String metric : List.of("evidencePassRate", "jointPassRate", "confirmationLagBars")) {
            final Path run = ReplayFixture.write(temp.resolve(metric));
            final Path trace = run.resolve("traces/d1-real.jsonl");
            final List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
            Files.write(trace, List.of(lines.getFirst(), lines.get(1), "{\"complete\":true,\"records\":1}"),
                    StandardCharsets.UTF_8);
            ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
            final boolean lag = "confirmationLagBars".equals(metric);
            final String key = comparisonMetric(run, ReplayFixture.RULES_KEY, metric, Double.NaN, lag ? null : 0L,
                    lag ? null : 0L);
            final String evidence = open(run, key, 120, 8).comparisonEvidence();
            assertTrue(evidence.contains("unavailable (no contributions)"), evidence);
            assertFalse(evidence.contains("-> match"), evidence);
        }
        final Path run = ReplayFixture.write(temp.resolve("one-label-observation"));
        final Path trace = run.resolve("traces/d1-real.jsonl");
        final List<String> lines = Files.readAllLines(trace, StandardCharsets.UTF_8);
        Files.write(trace, List.of(lines.getFirst(), lines.get(1), "{\"complete\":true,\"records\":1}"),
                StandardCharsets.UTF_8);
        ReplayFixture.refreshTraceDigest(run, "traces/d1-real.jsonl");
        final String key = comparisonMetric(run, ReplayFixture.RULES_KEY, "labelStabilityJaccard", Double.NaN, null,
                null);
        final String evidence = open(run, key, 120, 8).comparisonEvidence();
        assertTrue(evidence.contains("unavailable (no contributions)"), evidence);
        assertFalse(evidence.contains("-> match"), evidence);
    }

    private static double expectedValue(final String metric) {
        return switch (metric) {
        case "ambiguousRate" -> 8.0d / 50;
        case "completeOccupancyRate" -> 29.0d / 50;
        case "noMatchRate" -> 13.0d / 50;
        case "evidencePassRate" -> 29.0d / 58;
        case "jointPassRate" -> 0.0d;
        case "confirmationLagBars" -> {
            double sum = 0;
            for (int i = 0; i < 29; i++) {
                sum += 12.0d / 9;
            }
            yield sum / 29;
        }
        case "labelStabilityJaccard" -> 47.0d / 49;
        default -> throw new AssertionError(metric);
        };
    }

    private static Long expectedNumerator(final String metric) {
        return switch (metric) {
        case "ambiguousRate" -> 8L;
        case "completeOccupancyRate", "evidencePassRate" -> 29L;
        case "noMatchRate" -> 13L;
        case "jointPassRate" -> 0L;
        default -> null;
        };
    }

    private static Long expectedDenominator(final String metric) {
        return switch (metric) {
        case "ambiguousRate", "completeOccupancyRate", "noMatchRate" -> 50L;
        case "evidencePassRate" -> 58L;
        case "jointPassRate" -> 29L;
        default -> null;
        };
    }

    private static String comparisonMetric(final Path run, final String originalKey, final String metric,
            final double observed, final Long numerator, final Long denominator) throws IOException {
        final String value = Double.isNaN(observed) ? "" : Double.toString(observed);
        final String[] parts = originalKey.split("\\|", -1);
        parts[5] = metric;
        final String key = String.join("|", parts);
        final boolean hasNull = !"b-".equals(parts[6]);
        return comparisonFields(run, key,
                Map.of("observed", value, "numerator", numerator == null ? "" : numerator.toString(), "denominator",
                        denominator == null ? "" : denominator.toString(), "nullMedian",
                        hasNull ? Double.isFinite(observed) ? value : "0" : "", "nullLow",
                        hasNull ? Double.isFinite(observed) ? value : "0" : "", "nullHigh",
                        hasNull ? Double.isFinite(observed) ? value : "0" : "", "observedMinusNullMedian",
                        hasNull && Double.isFinite(observed) ? "0" : "", "empiricalReferenceRank",
                        hasNull && Double.isFinite(observed) ? "1" : "", "availability",
                        Double.isFinite(observed) ? hasNull ? "available" : "not-comparable" : "unavailable"));
    }

    private static String comparisonFields(final Path run, final String originalKey, final Map<String, String> fields)
            throws IOException {
        final Path csv = run.resolve("comparisons.csv");
        final List<List<String>> rows = ReplayArtifact.parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
        final List<String> header = rows.getFirst();
        final StringBuilder rewritten = new StringBuilder(String.join(",", header)).append('\n');
        String selected = null;
        for (int i = 1; i < rows.size(); i++) {
            final List<String> row = new ArrayList<>(rows.get(i));
            if (originalKey.equals(row.getFirst())) {
                fields.forEach((field, value) -> row.set(header.indexOf(field), value));
                final String block = row.get(header.indexOf("nullBlockLength"));
                selected = String.join("|", row.get(header.indexOf("dataset")), row.get(header.indexOf("section")),
                        row.get(header.indexOf("mode")), row.get(header.indexOf("detector")),
                        row.get(header.indexOf("partition")), row.get(header.indexOf("metric")),
                        block.isEmpty() || Integer.parseInt(block) <= 0 ? "b-" : "b" + block);
                row.set(0, selected);
            }
            rewritten.append(String.join(",", row)).append('\n');
        }
        assertTrue(selected != null, originalKey);
        Files.writeString(csv, rewritten, StandardCharsets.UTF_8);
        ReplayFixture.refreshComparisonsDigest(run);
        return selected;
    }

    private static void configureNullEnsemble(final Path directory, final int requested) throws IOException {
        final Path metadata = directory.resolve("run.json");
        final JsonObject run = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        run.getAsJsonObject("configuration").getAsJsonObject("null").addProperty("ensembleSize", requested);
        Files.writeString(metadata, run.toString(), StandardCharsets.UTF_8);
        final Path csv = directory.resolve("comparisons.csv");
        final List<List<String>> rows = ReplayArtifact.parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
        final List<String> header = rows.getFirst();
        final StringBuilder rewritten = new StringBuilder(String.join(",", header)).append('\n');
        for (int index = 1; index < rows.size(); index++) {
            final List<String> row = new ArrayList<>(rows.get(index));
            if (!row.get(header.indexOf("nullBlockLength")).isEmpty()) {
                final String value = row.get(header.indexOf("observed"));
                final boolean available = !value.isEmpty();
                row.set(header.indexOf("validNullMembers"), available ? "1" : "0");
                row.set(header.indexOf("requestedNullMembers"), Integer.toString(requested));
                row.set(header.indexOf("unavailableNullMembers"),
                        Integer.toString(available ? requested - 1 : requested));
                for (final String quantile : List.of("nullMedian", "nullLow", "nullHigh")) {
                    row.set(header.indexOf(quantile), value);
                }
                row.set(header.indexOf("observedMinusNullMedian"), available ? "0" : "");
                row.set(header.indexOf("empiricalReferenceRank"), available ? "1" : "");
                row.set(header.indexOf("availability"), available ? "available" : "unavailable");
            }
            rewritten.append(String.join(",", row)).append('\n');
        }
        Files.writeString(csv, rewritten, StandardCharsets.UTF_8);
        ReplayFixture.refreshComparisonsDigest(directory);
    }
}
