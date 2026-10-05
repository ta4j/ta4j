/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Event;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Stream;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Tape;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Structural;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DecimalNum;

/**
 * Tests for the {@link ElliottResearchEvents} recorder: enrollment, stream
 * identity, horizon tracking, and tape addressing.
 */
final class ElliottResearchEventsTest {

    private static final Instant AS_OF = Instant.parse("2018-01-01T00:00:00Z");

    @Test
    void tapeOffsetAddressesBarsInSourceCoordinates() {
        final BarSeries shifted = ElliottResearchOutcomesTest.series(i -> i < 4 ? 100 : 110, 1, 1);
        // Source index 10 is member index 0; the first member bar closes 100.
        final Tape tape = new Tape(shifted, 10);
        assertEquals(10, tape.first());
        assertEquals(10 + shifted.getEndIndex(), tape.last());
        assertEquals(shifted.getBar(3).getClosePrice(), tape.bar(13).getClosePrice());
    }

    @Test
    void recorderEnrollsEachPlacementOnceAtItsFirstFullObservationAndKeepsStreamsApart() {
        final BarSeries series = ElliottResearchOutcomesTest.syntheticSeries(30);
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 10);
        recorder.bindRealTape(series);
        ElliottResearchOutcomesTest.runner().evaluate("syn", series, 0, 29, recorder);

        final List<Event> events = recorder.streams().stream().flatMap(stream -> stream.events().stream()).toList();
        assertFalse(events.isEmpty());
        final Set<String> identities = events.stream()
                .map(event -> event.stream.partition() + "|" + event.candidateKey + "|" + event.version)
                .collect(Collectors.toSet());
        assertEquals(events.size(), identities.size(), "no placement may enroll twice in a stream");
        assertTrue(events.size() >= 2, "distinct placements enroll separately");
        final Set<List<Integer>> placements = events.stream()
                .map(event -> event.pivots.stream().map(ConfirmedPivot::pivotIndex).toList())
                .collect(Collectors.toSet());
        assertEquals(events.size(), placements.size());
        final long observations = recorder.streams()
                .stream()
                .mapToLong(stream -> stream.lastObserved() - stream.firstObserved() + 1)
                .sum();
        assertTrue(observations > events.size(),
                "a placement stays visible over several observations but enrolls once");
        for (final Event event : events) {
            assertEquals(6, event.pivots.size());
            assertNotNull(event.version);
            assertTrue(event.enrollIndex >= event.end().confirmationIndex() || event.carriedIn());
            assertFalse(event.tiedCandidateKeys.contains(event.candidateKey));
        }
        for (final Stream stream : recorder.streams()) {
            assertTrue(stream.key().real());
            assertTrue(stream.events().stream().allMatch(event -> event.enrollIndex <= stream.lastObserved()));
        }
    }

    @Test
    void nullMemberStreamsKeepIdentityAndReplayDeterministically() {
        final BarSeries series = zigzagSeries(160);
        final List<String> first = nullLabels(series, 2, 1);
        final List<String> second = nullLabels(series, 2, 1);

        assertFalse(first.isEmpty(), "null member must enroll events");
        assertEquals(first, second);
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 10);
        ElliottResearchOutcomesTest.runner(ElliottResearchEventsTest::localExtrema)
                .replayNullMember(series, 0, 159, 2, 1, recorder);
        for (final Stream stream : recorder.streams()) {
            assertFalse(stream.key().real());
            assertEquals(2, stream.key().nullBlockLength());
            assertEquals(1, stream.key().nullMemberIndex());
            assertEquals("null-b2-m1", stream.key().label());
            assertNotNull(recorder.tapeOf(stream), "null stream labels read its own member tape");
        }
    }

    @Test
    void horizonArithmeticDoesNotOverflowForAnUnboundedMaxHorizon() {
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", Integer.MAX_VALUE);
        final List<ConfirmedPivot> cycle = bullishCyclePivots();
        observeMotive(recorder, 9, cycle.subList(0, 6));
        observeNoMatch(recorder, 10, cycle.subList(0, 7));
        observeCycle(recorder, 12, cycle);

        final Event event = soleEvent(recorder);
        assertEquals(9, event.enrollIndex);
        assertEquals(12, event.completionIndex,
                "an enrolled event stays tracked when enrollIndex + maxHorizon exceeds int");
    }

    @Test
    void withdrawnPlacementNeverCompletesAtOrAfterWithdrawalButKeepsEarlierCompletion() {
        final List<ConfirmedPivot> cycle = bullishCyclePivots();

        final ElliottResearchEvents readmitted = new ElliottResearchEvents("all-rules", 10);
        observeMotive(readmitted, 9, cycle.subList(0, 6));
        observeNoMatch(readmitted, 10, cycle.subList(0, 5));
        observeNoMatch(readmitted, 11, cycle.subList(0, 7));
        observeCycle(readmitted, 11, cycle);
        final Event withdrawn = soleEvent(readmitted);
        assertEquals(10, withdrawn.withdrawnIndex);
        assertEquals(-1, withdrawn.completionIndex, "a withdrawn placement must not complete after withdrawal");

        final ElliottResearchEvents completedFirst = new ElliottResearchEvents("all-rules", 10);
        observeMotive(completedFirst, 9, cycle.subList(0, 6));
        observeCycle(completedFirst, 10, cycle);
        observeNoMatch(completedFirst, 11, cycle.subList(0, 5));
        observeNoMatch(completedFirst, 12, cycle.subList(0, 7));
        observeCycle(completedFirst, 12, cycle);
        final Event kept = soleEvent(completedFirst);
        assertEquals(10, kept.completionIndex);
        assertEquals(11, kept.withdrawnIndex);
        assertEquals(Structural.CORRECTION_COMPLETED,
                ElliottResearchOutcomes.label(kept, 5, 100, null, ElliottResearchOutcomesTest.SETTINGS, -1)
                        .structural());
    }

    // --------------------------------------------------------------- helpers

    private static List<String> nullLabels(final BarSeries series, final int block, final int member) {
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 10);
        ElliottResearchOutcomesTest.runner(ElliottResearchEventsTest::localExtrema)
                .replayNullMember(series, 0, series.getEndIndex(), block, member, recorder);
        return recorder.streams()
                .stream()
                .flatMap(stream -> stream.events().stream())
                .map(event -> event.stream.label() + "|" + event.stream.partition() + "|" + event.enrollIndex + "|"
                        + event.candidateKey)
                .toList();
    }

    /**
     * Nine bullish pivots at bars 4..12: a motive followed by a corrective block.
     */
    private static List<ConfirmedPivot> bullishCyclePivots() {
        final double[] prices = { 100, 120, 110, 140, 130, 160, 145, 155, 135 };
        final List<ConfirmedPivot> pivots = new ArrayList<>();
        for (int at = 0; at < prices.length; at++) {
            pivots.add(new ConfirmedPivot(4 + at, 4 + at, DecimalNum.valueOf(prices[at]),
                    at % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH));
        }
        return pivots;
    }

    private static void observeMotive(final ElliottResearchEvents recorder, final int index,
            final List<ConfirmedPivot> visible) {
        final TopologyCandidate motive = new TopologyCandidate(TopologyGrammar.MOTIVE_5, WaveDirection.BULLISH,
                visible.subList(0, 6));
        recorder.topology(StudyObserver.Scope.real("h1", "all-rules", "MOTIVE_5", List.of(), "synthetic"),
                "calibration", index, AS_OF, visible,
                new TopologyAnalysis(TopologyStatus.COMPLETE, null, List.of(motive), "motive", -1, -1),
                List.of(List.of()));
    }

    private static void observeNoMatch(final ElliottResearchEvents recorder, final int index,
            final List<ConfirmedPivot> visible) {
        recorder.topology(StudyObserver.Scope.real("h1", "all-rules", "MOTIVE_5", List.of(), "synthetic"),
                "calibration", index, AS_OF, visible, TopologyAnalysis.noMatch("none"), List.of());
    }

    private static void observeCycle(final ElliottResearchEvents recorder, final int index,
            final List<ConfirmedPivot> visible) {
        final TopologyCandidate cycle = new TopologyCandidate(TopologyGrammar.CYCLE_5_3, WaveDirection.BULLISH,
                visible);
        recorder.topology(StudyObserver.Scope.real("h2", "all-rules", "CYCLE_5_3", List.of(), "synthetic"),
                "calibration", index, AS_OF, visible,
                new TopologyAnalysis(TopologyStatus.COMPLETE, null, List.of(cycle), "cycle", -1, -1),
                List.of(List.of()));
    }

    private static Event soleEvent(final ElliottResearchEvents recorder) {
        assertEquals(1, recorder.streams().size());
        assertEquals(1, recorder.streams().get(0).events().size());
        return recorder.streams().get(0).events().get(0);
    }

    private static BarSeries zigzagSeries(final int count) {
        return ElliottResearchOutcomesTest.seriesOf(count,
                index -> 100 + index + (index % 2 == 0 ? 0 : 6) + (index % 7 == 0 ? 3 : 0));
    }

    private static SwingDetector localExtrema() {
        return (series, index, degree) -> {
            final List<SwingPivot> pivots = new ArrayList<>();
            SwingPivotType last = null;
            for (int at = 1; at < index && at < series.getEndIndex(); at++) {
                final double before = series.getBar(at - 1).getClosePrice().doubleValue();
                final double now = series.getBar(at).getClosePrice().doubleValue();
                final double after = series.getBar(at + 1).getClosePrice().doubleValue();
                final SwingPivotType type = now > before && now > after ? SwingPivotType.HIGH
                        : now < before && now < after ? SwingPivotType.LOW : null;
                if (type != null && type != last) {
                    pivots.add(new SwingPivot(at, series.getBar(at).getClosePrice(), type));
                    last = type;
                }
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }
}
