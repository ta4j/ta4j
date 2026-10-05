/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

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
        assertFalse(session.seek(34).pivots().stream().filter(p -> p.index() == 30).findFirst().orElseThrow()
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

        assertEquals("v1", session.seek(30).candidate("c-A").orElseThrow().version());
        assertEquals("v2", session.seek(36).candidate("c-A").orElseThrow().version());
        assertEquals("v1", session.seek(30).candidate("c-A").orElseThrow().version());
        session.seek(45);
        final List<TimelineEntry> aTimeline = session.candidateTimeline("c-A");
        assertEquals(List.of(new TimelineEntry(23, "v1", true), new TimelineEntry(33, "v2", true)), aTimeline);
        final List<TimelineEntry> bTimeline = session.candidateTimeline("c-B");
        assertEquals(List.of(new TimelineEntry(33, "v1", true), new TimelineEntry(41, "", false)), bTimeline);
        assertTrue(session.candidateTimeline("missing").isEmpty());
    }

    @Test
    void selectionIsKeyedAndSurvivesNavigation() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));
        session.seek(36);

        assertEquals("c-B", session.select("2").selectedCandidate());
        assertTrue(session.frame().candidate("c-B").orElseThrow().selected());
        assertEquals("c-B", session.seek(50).selectedCandidate(), "key is kept even when absent at the new cursor");
        assertTrue(session.frame().candidates().stream().noneMatch(Candidate::selected));
        assertEquals("c-B", session.seek(35).selectedCandidate());
        assertTrue(session.frame().candidate("c-B").orElseThrow().selected());
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

        final ReplayFrame selected = session.select("c-B");
        assertFalse(selected.candidates().get(0).overlayed());
        assertTrue(selected.candidates().get(1).overlayed());
        assertEquals(1, ReplayChartModel.of(selected, session.bars())
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
        assertTrue(frame.candidates().stream()
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
        assertTrue(text.contains("wave 2 may not retrace the whole of wave 1 (c-A)"));
    }

    @Test
    void emptyStatesAreReportedNotHidden() {
        final ReplaySession session = open(ReplayFixture.write(temp.resolve("run")));

        final ReplayFrame frame = session.seek(ReplayFixture.FIRST_AS_OF);

        assertEquals("NONE", frame.status());
        assertTrue(frame.candidates().isEmpty());
        assertTrue(ReplayEvidenceText.render(frame).contains("candidates retained: 0"));
        assertEquals(1, ReplayChartModel.of(frame, session.bars())
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
        final Path csv = run.resolve("comparisons.csv");
        java.nio.file.Files.writeString(csv,
                java.nio.file.Files.readString(csv) + ReplayFixture.RULES_KEY.replace("|h2|", "|competing|")
                        + ",d1,FIXTURE,competing,all-rules,MOTIVE_5,,fractal-w5,kernel-topology,calibration,ambiguousRate,20,0.5\n",
                java.nio.charset.StandardCharsets.UTF_8);
        final String competing = assertThrows(ReplayArtifactException.class,
                () -> openNull(run, ReplayFixture.RULES_KEY.replace("|h2|", "|competing|"))).getMessage();
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

        assertEquals(List.of(new TimelineEntry(23, "v1", true), new TimelineEntry(33, "v2", true)),
                session.candidateTimeline("c-A"));
        assertEquals(-1, session.timelineStartAsOf());
        assertEquals(List.of(), session.candidateTimeline("c-A", 3));
        assertEquals(38, session.timelineStartAsOf(3));
        assertEquals(List.of(new TimelineEntry(33, "v2", true)), session.candidateTimeline("c-A", 8));
    }
}
