/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Event;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Stream;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.StreamKey;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Tape;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Invalidation;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Label;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Price;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Settings;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Structural;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Tally;
import org.ta4j.core.analysis.elliott.ElliottResearchOutcomes.Touch;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DecimalNum;

/**
 * Tests for causal event enrollment and forward outcome labelling.
 */
class ElliottResearchOutcomesTest {

    private static final StreamKey REAL = new StreamKey(-1, -1, "calibration");
    private static final Settings SETTINGS = new Settings(List.of(5), "all-rules", Invalidation.ORIGIN_PIVOT);

    // ------------------------------------------------------------ structural

    @Test
    void successFailureAndCensoredEventsGiveResolvedRateAndAssumptionFreeBounds() {
        final Event success = bullishEvent(10);
        success.completionIndex = 13;
        final Event failure = bullishEvent(20);
        failure.pivotInvalidationIndex = 22;
        final Event censored = bullishEvent(30);

        final Tally tally = new Tally();
        for (final Event event : List.of(success, failure, censored)) {
            tally.structural(ElliottResearchOutcomes.label(event, 5, event == censored ? 32 : 100, null, SETTINGS, -1)
                    .structural(), false);
        }

        assertEquals(3, tally.prospective());
        assertEquals(1, tally.success);
        assertEquals(1, tally.invalidated);
        assertEquals(1, tally.censored);
        assertEquals(2, tally.resolved());
        assertEquals(0.5d, tally.resolvedRate(), 1e-12);
        assertEquals(1.0d / 3.0d, tally.lowerBound(), 1e-12);
        assertEquals(2.0d / 3.0d, tally.upperBound(), 1e-12);
    }

    @Test
    void horizonBoundaryConfirmationCountsAndOneBarLaterDoesNot() {
        final Event atBoundary = bullishEvent(10);
        atBoundary.completionIndex = 15;
        final Event afterBoundary = bullishEvent(10);
        afterBoundary.completionIndex = 16;

        final Label included = ElliottResearchOutcomes.label(atBoundary, 5, 100, null, SETTINGS, -1);
        final Label excluded = ElliottResearchOutcomes.label(afterBoundary, 5, 100, null, SETTINGS, -1);

        assertEquals(Structural.CORRECTION_COMPLETED, included.structural());
        assertEquals(15, included.resolutionIndex());
        assertEquals(Structural.HORIZON_EXPIRED, excluded.structural());
        assertEquals(-1, excluded.resolutionIndex());
        assertEquals("none", excluded.cause());
    }

    @Test
    void streamEndingBeforeTheHorizonCensorsInsteadOfFailing() {
        final Event event = bullishEvent(10);

        assertEquals(Structural.CENSORED, ElliottResearchOutcomes.label(event, 5, 14, null, SETTINGS, -1).structural());
        assertEquals(Structural.HORIZON_EXPIRED,
                ElliottResearchOutcomes.label(event, 5, 15, null, SETTINGS, -1).structural());
    }

    @Test
    void sameObservationInvalidationBeatsCompletionAndEarlierWinsOtherwise() {
        final Event tie = bullishEvent(10);
        tie.completionIndex = 12;
        tie.pivotInvalidationIndex = 12;
        final Event earlierCompletion = bullishEvent(10);
        earlierCompletion.completionIndex = 11;
        earlierCompletion.withdrawnIndex = 13;
        final Event earlierWithdrawal = bullishEvent(10);
        earlierWithdrawal.withdrawnIndex = 11;
        earlierWithdrawal.completionIndex = 13;

        assertEquals("pivot-invalidation", ElliottResearchOutcomes.label(tie, 5, 100, null, SETTINGS, -1).cause());
        assertEquals(Structural.CORRECTION_COMPLETED,
                ElliottResearchOutcomes.label(earlierCompletion, 5, 100, null, SETTINGS, -1).structural());
        final Label withdrawn = ElliottResearchOutcomes.label(earlierWithdrawal, 5, 100, null, SETTINGS, -1);
        assertEquals(Structural.INVALIDATED_OR_WITHDRAWN, withdrawn.structural());
        assertEquals("withdrawn", withdrawn.cause());
    }

    @Test
    void eventsResolvedAtOrBeforeEnrollmentLeaveTheProspectiveDenominator() {
        final Event completedAtEnrollment = bullishEvent(10);
        completedAtEnrollment.completionIndex = 10;
        final Event invalidatedAtEnrollment = bullishEvent(10);
        invalidatedAtEnrollment.pivotInvalidationIndex = 10;
        final Event open = bullishEvent(10);

        final Tally tally = new Tally();
        for (final Event event : List.of(completedAtEnrollment, invalidatedAtEnrollment, open)) {
            final Label label = ElliottResearchOutcomes.label(event, 5, 100, null, SETTINGS, -1);
            tally.structural(label.structural(), false);
            assertEquals(event == open ? Structural.HORIZON_EXPIRED : Structural.ALREADY_RESOLVED, label.structural());
        }

        assertEquals(3, tally.enrolled);
        assertEquals(2, tally.alreadyResolved);
        assertEquals(1, tally.prospective());
        assertEquals(0, tally.success);
    }

    @Test
    void originPriceInvalidationUsesTheBarBreachInsteadOfTheConfirmedPivot() {
        final Settings priceSettings = new Settings(List.of(5), "all-rules", Invalidation.ORIGIN_PRICE);
        final Event event = bullishEvent(10);
        event.pivotInvalidationIndex = 14;

        final Label byPivot = ElliottResearchOutcomes.label(event, 5, 100, null, SETTINGS, -1);
        final Label byPrice = ElliottResearchOutcomes.label(event, 5, 100, null, priceSettings, 12);

        assertEquals(14, byPivot.resolutionIndex());
        assertEquals("pivot-invalidation", byPivot.cause());
        assertEquals(12, byPrice.resolutionIndex());
        assertEquals("origin-price-invalidation", byPrice.cause());
    }

    @Test
    void originPriceBreachBetweenTheMotiveEndAndLateEnrollmentIsNotAdministrativelyKnownBeforeEnrollment() {
        final Settings priceSettings = new Settings(List.of(5), "all-rules", Invalidation.ORIGIN_PRICE);
        // Pivots sit at bars 4..9 but are only confirmed (and the event only enrolled)
        // at bar 20.
        final List<ConfirmedPivot> pivots = new ArrayList<>();
        final double[] prices = { 100, 120, 110, 140, 130, 160 };
        for (int at = 0; at < prices.length; at++) {
            pivots.add(new ConfirmedPivot(4 + at, 20, DecimalNum.valueOf(prices[at]),
                    at % 2 == 0 ? SwingPivotType.LOW : SwingPivotType.HIGH));
        }
        final Event lateEnrolled = new Event(REAL, "late", "version", WaveDirection.BULLISH, pivots, "late", 20,
                Instant.parse("2018-01-01T00:00:00Z"), TopologyStatus.COMPLETE, List.of());

        final Label label = ElliottResearchOutcomes.label(lateEnrolled, 5, 100, null, priceSettings, 12);

        assertEquals(Structural.ALREADY_RESOLVED, label.structural());
        assertEquals(12, label.resolutionIndex());
        assertEquals(20, label.structuralAvailableIndex());
        assertTrue(ElliottResearchOutcomes.purgeStructural(List.of(label), 19).isEmpty());
        assertEquals(1, ElliottResearchOutcomes.purgeStructural(List.of(label), 20).size());
    }

    // ----------------------------------------------------------------- price

    @Test
    void bullishAndBearishTapesAlignReturnsAndExcursionsWithMotiveDirection() {
        // Bullish event at 10: start 100; bars 11..15 close 102..110, high +1, low -1.
        final BarSeries rising = series(i -> i <= 10 ? 100 : 100 + (i - 10) * 2, 1, 1);
        final Price bull = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, new Tape(rising, 0), SETTINGS, -1)
                .price();
        assertTrue(bull.available());
        assertEquals(0.10d, bull.raw(), 1e-12);
        assertEquals(0.10d, bull.aligned(), 1e-12);
        assertEquals(0.11d, bull.favorable(), 1e-12);
        assertEquals(0.01d, bull.adverse(), 1e-12);

        // The same rising tape is adverse to a bearish motive.
        final Price bear = ElliottResearchOutcomes.label(bearishEvent(10), 5, 15, new Tape(rising, 0), SETTINGS, -1)
                .price();
        assertEquals(0.10d, bear.raw(), 1e-12);
        assertEquals(-0.10d, bear.aligned(), 1e-12);
        assertEquals(-0.01d, bear.favorable(), 1e-12);
        assertEquals(-0.11d, bear.adverse(), 1e-12);
    }

    @Test
    void priceWindowNeverReadsBeyondTheStreamsLastObservedBar() {
        final BarSeries flat = series(i -> 100, 1, 1);

        final Price tooShort = ElliottResearchOutcomes.label(bullishEvent(10), 5, 14, new Tape(flat, 0), SETTINGS, -1)
                .price();
        final Price exact = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, new Tape(flat, 0), SETTINGS, -1)
                .price();
        final Price noTape = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, null, SETTINGS, -1).price();

        assertFalse(tooShort.available());
        assertEquals("HORIZON_BEYOND_PARTITION", tooShort.reason());
        assertTrue(exact.available());
        assertEquals("NO_TAPE", noTape.reason());
    }

    @Test
    void structuralSuccessNearTheEndOfAPartitionIsNotAPriceHorizonLabel() {
        final Event event = bullishEvent(10);
        event.completionIndex = 12;
        final BarSeries flat = series(i -> 100, 1, 1);

        final Label label = ElliottResearchOutcomes.label(event, 5, 13, new Tape(flat, 0), SETTINGS, -1);

        assertEquals(Structural.CORRECTION_COMPLETED, label.structural());
        assertFalse(label.price().available());
        assertEquals("HORIZON_BEYOND_PARTITION", label.price().reason());
        assertEquals(Integer.MAX_VALUE, label.priceAvailableIndex());
    }

    @Test
    void sameBarTargetAndInvalidationTouchIsUnresolvedUnderResearchAndInvalidationFirstUnderLegacy() {
        // Bullish event: target 130 (wave 4), invalidation 160 (wave 5 end).
        // Bar 11 reaches both levels.
        final BarSeries wide = series(i -> 145, i -> i == 11 ? 165 : 146, i -> i == 11 ? 125 : 144);
        final Price touch = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, new Tape(wide, 0), SETTINGS, -1)
                .price();
        assertEquals(Touch.UNRESOLVED_INTRABAR_ORDER, touch.researchTouch());
        assertEquals(Touch.INVALIDATION_FIRST, touch.legacyTouch());

        // Separate bars: target at 11, invalidation at 12.
        final BarSeries ordered = series(i -> 145, i -> i == 12 ? 165 : 146, i -> i == 11 ? 125 : 144);
        final Price sequenced = ElliottResearchOutcomes
                .label(bullishEvent(10), 5, 15, new Tape(ordered, 0), SETTINGS, -1)
                .price();
        assertEquals(Touch.TARGET_FIRST, sequenced.researchTouch());
        assertEquals(Touch.TARGET_FIRST, sequenced.legacyTouch());

        final BarSeries quiet = series(i -> 145, i -> 146, i -> 144);
        final Price none = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, new Tape(quiet, 0), SETTINGS, -1)
                .price();
        assertEquals(Touch.NEITHER, none.researchTouch());
    }

    @Test
    void tapeOffsetAddressesBarsInSourceCoordinates() {
        final BarSeries shifted = series(i -> i < 4 ? 100 : 110, 1, 1);
        // Source index 10 is member index 0; the first member bar closes 100.
        final Tape tape = new Tape(shifted, 10);
        assertEquals(10, tape.first());
        assertEquals(10 + shifted.getEndIndex(), tape.last());
        assertEquals(shifted.getBar(3).getClosePrice(), tape.bar(13).getClosePrice());
    }

    @Test
    void legacyTouchPolicyMatchesTheReleasedOutcomeLabelerAndLeavesItUnchanged() {
        // A bullish motive's correction runs down: target = wave-4 low, invalidation =
        // wave-5 high,
        // which is the released labeler's bearish scenario with the same levels.
        final Curve flatClose = i -> 145;
        final List<BarSeries> tapes = List.of(series(flatClose, i -> i == 11 ? 165 : 146, i -> i == 11 ? 125 : 144),
                series(flatClose, i -> i == 12 ? 165 : 146, i -> i == 11 ? 125 : 144),
                series(flatClose, i -> i == 11 ? 165 : 146, i -> i == 12 ? 125 : 144),
                series(flatClose, i -> 146, i -> 144));
        for (final BarSeries tape : tapes) {
            final Price price = ElliottResearchOutcomes.label(bullishEvent(10), 5, 15, new Tape(tape, 0), SETTINGS, -1)
                    .price();
            final org.ta4j.core.indicators.elliott.ElliottScenario scenario = org.ta4j.core.indicators.elliott.ElliottScenario
                    .builder()
                    .id("legacy")
                    .currentPhase(org.ta4j.core.indicators.elliott.ElliottPhase.WAVE3)
                    .confidence(org.ta4j.core.indicators.elliott.ElliottConfidence.zero(tape.numFactory()))
                    .degree(org.ta4j.core.indicators.elliott.ElliottDegree.MINOR)
                    .primaryTarget(tape.numFactory().numOf(130))
                    .invalidationPrice(tape.numFactory().numOf(160))
                    .type(org.ta4j.core.indicators.elliott.ScenarioType.IMPULSE)
                    .startIndex(tape.getBeginIndex())
                    .bullishDirection(Boolean.FALSE)
                    .build();
            final org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = new org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult.BaseScenarioAssessment(
                    scenario, 0.8, 0.5, 0.7, List.of());
            final org.ta4j.core.walkforward.RankedPrediction<org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction = new org.ta4j.core.walkforward.RankedPrediction<>(
                    scenario.id(), 1, tape.numFactory().numOf(0.7), tape.numFactory().numOf(0.8), assessment);

            final org.ta4j.core.indicators.elliott.walkforward.ElliottWaveOutcome.EventOutcome released = new org.ta4j.core.indicators.elliott.walkforward.ElliottWaveOutcomeLabeler()
                    .label(tape, 10, 5, prediction)
                    .eventOutcome();

            final Touch expected = switch (released) {
            case TARGET_FIRST -> Touch.TARGET_FIRST;
            case INVALIDATION_FIRST -> Touch.INVALIDATION_FIRST;
            default -> Touch.NEITHER;
            };
            assertEquals(expected, price.legacyTouch());
        }
    }

    @Test
    void nonOverlappingCohortKeepsEarliestSkipsRunningHorizonsAndBreaksTiesByKey() {
        final List<Label> labels = new ArrayList<>();
        // Decisions 10, 12 (skipped: 10's horizon runs to 15), 15 (kept: boundary), two
        // at 30 tied by key.
        for (final int decision : new int[] { 15, 12, 10 }) {
            labels.add(ElliottResearchOutcomes.label(keyed(decision, "k" + decision), 5, 100, null, SETTINGS, -1));
        }
        labels.add(ElliottResearchOutcomes.label(keyed(30, "b"), 5, 100, null, SETTINGS, -1));
        labels.add(ElliottResearchOutcomes.label(keyed(30, "a"), 5, 100, null, SETTINGS, -1));
        final Event resolvedEarly = keyed(8, "early");
        resolvedEarly.completionIndex = 8;
        labels.add(ElliottResearchOutcomes.label(resolvedEarly, 5, 100, null, SETTINGS, -1));

        final Tally forward = new Tally();
        ElliottResearchOutcomes.cohort(labels, 5, forward);
        final Tally reversed = new Tally();
        ElliottResearchOutcomes.cohort(new ArrayList<>(labels.reversed()), 5, reversed);

        assertEquals(3, forward.cohortKept);
        assertEquals(2, forward.cohortDiscarded);
        assertEquals(3, forward.cohortExpired);
        assertEquals(forward.cells(), reversed.cells(), "selection must not depend on input order");
    }

    private static Event keyed(final int enrollIndex, final String key) {
        final Event base = bullishEvent(enrollIndex);
        return new Event(REAL, key, base.version, base.direction, base.pivots, key, enrollIndex, base.enrollTime,
                base.status, List.of());
    }

    @Test
    void labelsNeverChangeTheFrozenDecisionTimeEvidence() {
        final Event event = bullishEvent(10);
        final List<ConfirmedPivot> pivots = event.pivots;
        final String version = event.version;
        final String key = event.candidateKey;
        event.completionIndex = 12;

        ElliottResearchOutcomes.label(event, 5, 100, new Tape(series(i -> 100, 1, 1), 0), SETTINGS, -1);

        assertEquals(pivots, event.pivots);
        assertEquals(version, event.version);
        assertEquals(key, event.candidateKey);
        assertEquals(10, event.enrollIndex);
        assertThrows(UnsupportedOperationException.class, () -> event.pivots.add(event.pivots.get(0)));
    }

    // -------------------------------------------------------------- tallying

    @Test
    void tallyKeepsPriceMeansTouchPoliciesAndUnavailableReasonsAdditive() {
        final Tally first = new Tally();
        first.price(new Price(true, null, 0.10d, 0.10d, 0.12d, 0.01d, Touch.TARGET_FIRST, Touch.TARGET_FIRST));
        first.price(new Price(true, null, -0.10d, 0.10d, 0.12d, 0.01d, Touch.UNRESOLVED_INTRABAR_ORDER,
                Touch.INVALIDATION_FIRST));
        first.price(Price.unavailable("HORIZON_BEYOND_PARTITION"));
        final Tally second = new Tally();
        second.price(new Price(true, null, 0.30d, 0.30d, 0.30d, 0.0d, Touch.NEITHER, Touch.NEITHER));
        second.price(Price.unavailable("NO_TAPE"));

        first.add(second);

        assertEquals(3, first.priceAvailable);
        assertEquals(1, first.beyondPartition);
        assertEquals(1, first.noTape);
        assertEquals(2, first.up);
        assertEquals(1, first.researchUnresolved);
        assertEquals(1, first.legacyInvalidation);
        assertEquals(0.10d, first.sumRaw / first.priceAvailable, 1e-12);
    }

    // ------------------------------------------------------------ purge/fit

    @Test
    void fitCutoffPurgeKeepsOnlyLabelsAdministrativelyKnownByTheCutoff() {
        final Event completed = bullishEvent(10);
        completed.completionIndex = 12;
        final Event open = bullishEvent(10);
        final Event late = bullishEvent(10);
        late.pivotInvalidationIndex = 15;
        final BarSeries flat = series(i -> 100, 1, 1);
        final Tape tape = new Tape(flat, 0);

        final List<Label> labels = new ArrayList<>();
        for (final Event event : List.of(completed, open, late)) {
            labels.add(ElliottResearchOutcomes.label(event, 5, 20, tape, SETTINGS, -1));
        }

        // Structural: completed known at 12, late at 15, open (horizon expiry) at 15.
        assertEquals(List.of(completed), events(ElliottResearchOutcomes.purgeStructural(labels, 12)));
        assertEquals(3, ElliottResearchOutcomes.purgeStructural(labels, 15).size());
        assertTrue(ElliottResearchOutcomes.purgeStructural(labels, 9).isEmpty());
        // Price windows all end at decision + horizon = 15, regardless of structural
        // result.
        assertTrue(ElliottResearchOutcomes.purgePrice(labels, 14).isEmpty());
        assertEquals(3, ElliottResearchOutcomes.purgePrice(labels, 15).size());
    }

    private static List<Event> events(final List<Label> labels) {
        return labels.stream().map(Label::event).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- recipe

    @Test
    void settingsRejectUnsortedHorizonsUnknownModesAndUnknownFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Settings(List.of(5, 5), "all-rules", Invalidation.ORIGIN_PIVOT));
        assertThrows(IllegalArgumentException.class,
                () -> new Settings(List.of(0), "all-rules", Invalidation.ORIGIN_PIVOT));
        assertThrows(IllegalArgumentException.class, () -> new Settings(List.of(5), " ", Invalidation.ORIGIN_PIVOT));
        assertThrows(IllegalArgumentException.class, () -> Invalidation.parse("close"));
        final com.google.gson.JsonObject unknown = com.google.gson.JsonParser.parseString("{\"horizon\":[5]}")
                .getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> Settings.parse(unknown));
        final com.google.gson.JsonObject parsed = com.google.gson.JsonParser
                .parseString("{\"horizons\":[3,9],\"invalidation\":\"origin-price\"}")
                .getAsJsonObject();
        assertEquals(List.of(3, 9), Settings.parse(parsed).horizons());
        assertEquals(Invalidation.ORIGIN_PRICE, Settings.parse(parsed).invalidation());
        assertEquals(Settings.defaults(), Settings.parse(null));
    }

    // -------------------------------------------------- recorder over runner

    @Test
    void recorderEnrollsEachPlacementOnceAtItsFirstFullObservationAndKeepsStreamsApart() {
        final BarSeries series = syntheticSeries(30);
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 10);
        recorder.bindRealTape(series);
        runner().evaluate("syn", series, 0, 29, recorder);

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
        runner(ElliottResearchOutcomesTest::localExtrema).replayNullMember(series, 0, 159, 2, 1, recorder);
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
                ElliottResearchOutcomes.label(kept, 5, 100, null, SETTINGS, -1).structural());
    }

    @Test
    void comparatorDatesShareTheEventPriceWindowEligibility() {
        final BarSeries clean = seriesOf(30, index -> 100 + index);
        // A zero low on bar 20 invalidates every five-bar window that reads it.
        final BarSeries zeroLow = seriesOf(30, index -> 100 + index, index -> index == 20 ? 0 : 99 + index);

        final Tally cleanTally = evaluate(clean).all("real", 5).tally();
        final Tally zeroTally = evaluate(zeroLow).all("real", 5).tally();

        assertTrue(cleanTally.unconditionalDates > 5);
        assertEquals(cleanTally.unconditionalDates - 5, zeroTally.unconditionalDates,
                "windows 15..19 read the zero low and must leave the comparator dates");
    }

    @Test
    void evaluationSummaryIsDeterministicAndSumsPartitionsIntoAllRows() {
        final BarSeries series = syntheticSeries(30);
        final ElliottResearchOutcomes.Result one = evaluate(series);
        final ElliottResearchOutcomes.Result two = evaluate(series);

        assertEquals(one.summary().size(), two.summary().size());
        for (int at = 0; at < one.summary().size(); at++) {
            assertEquals(one.summary().get(at).partition(), two.summary().get(at).partition());
            assertEquals(one.summary().get(at).tally().cells(), two.summary().get(at).tally().cells());
        }
        final ElliottResearchOutcomes.SummaryRow all = one.all("real", 5);
        assertNotNull(all);
        long enrolled = 0;
        for (final ElliottResearchOutcomes.SummaryRow row : one.summary()) {
            if ("real".equals(row.stream()) && row.horizon() == 5 && !"ALL".equals(row.partition())) {
                enrolled += row.tally().enrolled;
            }
        }
        assertEquals(enrolled, all.tally().enrolled);
        assertEquals(one.events().size(), all.tally().enrolled);
        for (final ElliottResearchOutcomes.Evaluated evaluated : one.events()) {
            assertEquals(List.of(5, 20), evaluated.labels().stream().map(Label::horizon).toList());
        }
    }

    private static ElliottResearchOutcomes.Result evaluate(final BarSeries series) {
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 20);
        recorder.bindRealTape(series);
        runner().evaluate("syn", series, 0, series.getEndIndex(), recorder);
        return ElliottResearchOutcomes.evaluate("syn", recorder,
                new Settings(List.of(5, 20), "all-rules", Invalidation.ORIGIN_PIVOT));
    }

    private static List<String> nullLabels(final BarSeries series, final int block, final int member) {
        final ElliottResearchEvents recorder = new ElliottResearchEvents("all-rules", 10);
        runner(ElliottResearchOutcomesTest::localExtrema).replayNullMember(series, 0, series.getEndIndex(), block,
                member, recorder);
        return recorder.streams()
                .stream()
                .flatMap(stream -> stream.events().stream())
                .map(event -> event.stream.label() + "|" + event.stream.partition() + "|" + event.enrollIndex + "|"
                        + event.candidateKey)
                .toList();
    }

    // --------------------------------------------------------------- helpers

    private static final Instant AS_OF = Instant.parse("2018-01-01T00:00:00Z");

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

    private static Event bullishEvent(final int enrollIndex) {
        return event(enrollIndex, WaveDirection.BULLISH, new double[] { 100, 120, 110, 140, 130, 160 });
    }

    private static Event bearishEvent(final int enrollIndex) {
        return event(enrollIndex, WaveDirection.BEARISH, new double[] { 160, 140, 150, 120, 130, 100 });
    }

    private static Event event(final int enrollIndex, final WaveDirection direction, final double[] prices) {
        final List<ConfirmedPivot> pivots = new ArrayList<>();
        final boolean bullish = direction == WaveDirection.BULLISH;
        for (int at = 0; at < prices.length; at++) {
            final boolean low = (at % 2 == 0) == bullish;
            pivots.add(new ConfirmedPivot(enrollIndex - 6 + at, enrollIndex - 6 + at, DecimalNum.valueOf(prices[at]),
                    low ? SwingPivotType.LOW : SwingPivotType.HIGH));
        }
        return new Event(REAL, "key-" + enrollIndex + direction, "version", direction, pivots,
                direction + "|" + enrollIndex, enrollIndex, Instant.parse("2018-01-01T00:00:00Z"),
                TopologyStatus.COMPLETE, List.of());
    }

    private interface Curve {
        double at(int index);
    }

    private static BarSeries series(final Curve close, final double highPad, final double lowPad) {
        return series(close, i -> close.at(i) + highPad, i -> close.at(i) - lowPad);
    }

    private static BarSeries series(final Curve close, final Curve high, final Curve low) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("tape").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < 40; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1L)))
                    .openPrice(close.at(index))
                    .highPrice(high.at(index))
                    .lowPrice(low.at(index))
                    .closePrice(close.at(index))
                    .volume(1)
                    .amount(close.at(index))
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries syntheticSeries(final int count) {
        final double[] pivotPrices = { 100, 120, 110, 140, 130, 160, 150, 180, 170, 200, 190, 220, 210, 240, 230 };
        return seriesOf(count,
                index -> index % 2 == 1 && index / 2 < pivotPrices.length ? pivotPrices[index / 2] : 100 + index);
    }

    private static BarSeries seriesOf(final int count, final Curve close) {
        return seriesOf(count, close, index -> close.at(index) - 1);
    }

    private static BarSeries seriesOf(final int count, final Curve close, final Curve low) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            final double value = close.at(index);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1L)))
                    .openPrice(value)
                    .highPrice(value + 1)
                    .lowPrice(low.at(index))
                    .closePrice(value)
                    .volume(1)
                    .amount(value)
                    .trades(1)
                    .add();
        }
        return series;
    }

    private static BarSeries zigzagSeries(final int count) {
        return seriesOf(count, index -> 100 + index + (index % 2 == 0 ? 0 : 6) + (index % 7 == 0 ? 3 : 0));
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

    private static StudyRunner runner() {
        return runner(ElliottResearchOutcomesTest::detector);
    }

    private static StudyRunner runner(final java.util.function.Supplier<SwingDetector> detectorFactory) {
        return new StudyRunner(detectorFactory,
                List.of(TopologyGrammar.MOTIVE_5, TopologyGrammar.CORRECTIVE_3, TopologyGrammar.CYCLE_5_3),
                List.of(rule("first"), rule("second")), configuration());
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

    private static StudyRunner.Configuration configuration() {
        final StudyRunner.Partitions partitions = StudyRunner.Partitions.lockedDefault();
        return new StudyRunner.Configuration(partitions,
                "b92d667cdbf951aac8d0519006a31e097bc88d26e399b04dd9a89e6353729100", 5_252_026L, List.of(2), 2,
                List.of(new DetectorRobustnessMatrix.DetectorSpec("synthetic", ElliottResearchOutcomesTest::detector)),
                "synthetic-primary", null);
    }

    private static SwingDetector detector() {
        return (series, index, degree) -> {
            final List<SwingPivot> pivots = new ArrayList<>();
            for (int pivotIndex = 1; pivotIndex <= index && pivotIndex <= series.getEndIndex(); pivotIndex += 2) {
                final SwingPivotType type = pivotIndex % 4 == 1 ? SwingPivotType.LOW : SwingPivotType.HIGH;
                pivots.add(new SwingPivot(pivotIndex, series.getBar(pivotIndex).getClosePrice(), type));
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }
}
