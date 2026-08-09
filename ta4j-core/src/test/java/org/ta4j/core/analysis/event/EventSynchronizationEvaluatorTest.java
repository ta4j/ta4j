/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.DoubleStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.EmptyEventPolicy;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EventSynchronizationEvaluatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int SERIES_BARS = 40;

    public EventSynchronizationEvaluatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries series() {
        return series(SERIES_BARS);
    }

    private BarSeries series(int barCount) {
        double[] prices = DoubleStream.iterate(1.0, d -> d + 1.0).limit(barCount).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
    }

    private EventSignal events(BarSeries series, int unstableBars, int... indexes) {
        boolean[] mask = new boolean[series.getBarCount() == 0 ? 0 : series.getEndIndex() + 1];
        for (int index : indexes) {
            mask[index] = true;
        }
        return EventSignals.fromPredicate(series, unstableBars, i -> mask[i]);
    }

    private EventSynchronizationResult evaluate(EventSignal predicted, EventSignal reference, int maxLeadBars,
            int maxLagBars, HistoryPolicy historyPolicy, EmptyEventPolicy emptyEventPolicy, int start, int end) {
        return new EventSynchronizationEvaluator().evaluate(predicted, reference, start, end,
                new EventSynchronizationConfig(maxLeadBars, maxLagBars, historyPolicy, emptyEventPolicy));
    }

    @Test
    public void exactCoincidenceProducesPerfectMetrics() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 5, 10, 15), events(series, 0, 5, 10, 15), 0, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(3, result.predictedCount());
        assertEquals(3, result.referenceCount());
        assertEquals(3, result.matchedCount());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());
        assertEquals(3, result.exactMatchCount());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
        assertEquals(List.of(new EventMatch(5, 5, 0), new EventMatch(10, 10, 0), new EventMatch(15, 15, 0)),
                result.matches());
        assertNumEquals(0.0, result.meanSignedOffset());
        assertNumEquals(0.0, result.meanAbsoluteOffset());
        assertNumEquals(0.0, result.medianSignedOffset());
        assertNumEquals(0.0, result.minSignedOffset());
        assertNumEquals(0.0, result.maxSignedOffset());
    }

    @Test
    public void leadingPredictionsMatchWithinMaxLeadBars() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 9, 14), events(series, 0, 5, 10, 15), 1, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(3, result.matchedCount());
        assertEquals(List.of(new EventMatch(4, 5, 1), new EventMatch(9, 10, 1), new EventMatch(14, 15, 1)),
                result.matches());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
    }

    @Test
    public void laggingPredictionsMatchOnlyWithinMaxLagBars() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 6, 11, 16), events(series, 0, 5, 10, 15), 0, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(3, result.matchedCount());
        assertEquals(List.of(new EventMatch(6, 5, -1), new EventMatch(11, 10, -1), new EventMatch(16, 15, -1)),
                result.matches());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
    }

    @Test
    public void asymmetricLeadLagWindowsAreHonored() {
        BarSeries series = series();
        // p=3 leads r=4 by 1 and r=7 by 4; p=8 lags r=7 by 1 and leads r=10 by 2.
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 8), events(series, 0, 4, 7, 10), 2, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(new EventMatch(3, 4, 1), new EventMatch(8, 7, -1)), result.matches());
        assertNumEquals(1.0, result.meanAbsoluteOffset());
    }

    @Test
    public void eventsJustOutsideWindowRemainUnmatched() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 9), events(series, 0, 5), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(0, result.matchedCount());
        assertEquals(2, result.falsePositives());
        assertEquals(1, result.falseNegatives());
        assertEquals(List.of(3, 9), result.unmatchedPredictedIndexes());
        assertEquals(List.of(5), result.unmatchedReferenceIndexes());
        assertNumEquals(0.0, result.precision());
        assertNumEquals(0.0, result.recall());
        assertNumEquals(0.0, result.f1Score());
    }

    @Test
    public void twoPredictionsCannotBothConsumeOneReferenceEvent() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(new EventMatch(4, 5, 1)), result.matches());
        assertEquals(1, result.falsePositives());
        assertEquals(0, result.falseNegatives());
    }

    @Test
    public void onePredictionCannotConsumeTwoReferenceEvents() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 5), events(series, 0, 4, 6), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(new EventMatch(5, 4, -1)), result.matches());
        assertEquals(0, result.falsePositives());
        assertEquals(1, result.falseNegatives());
    }

    @Test
    public void competingAssignmentsSelectMaximumCardinality() {
        BarSeries series = series();
        // Two disjoint pairs are available; a naive nearest-neighbor approach would
        // match only one.
        EventSynchronizationResult result = evaluate(events(series, 0, 2, 5), events(series, 0, 3, 4), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(new EventMatch(2, 3, 1), new EventMatch(5, 4, -1)), result.matches());
    }

    @Test
    public void equalCardinalityAssignmentsMinimizeTotalAbsoluteOffset() {
        BarSeries series = series();
        // Two two-pair assignments exist: (0,1)+(3,2) with total |offset| 2, and
        // (0,2)+(3,1) with total 4.
        EventSynchronizationResult result = evaluate(events(series, 0, 0, 3), events(series, 0, 1, 2), 2, 2,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(new EventMatch(0, 1, 1), new EventMatch(3, 2, -1)), result.matches());
        assertNumEquals(1.0, result.meanAbsoluteOffset());
    }

    @Test
    public void finalTieIsDeterministicAndIndexOrdered() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(List.of(new EventMatch(4, 5, 1)), result.matches());

        // Repeated evaluation returns structurally equal results.
        EventSynchronizationResult again = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(result, again);
    }

    @Test
    public void lexicographicTieBreakSurvivesDominantPrefixOffsets() {
        BarSeries series = series();
        // Two 3-pair assignments tie on (pairs=3, totalAbs=4, worst=2):
        // X=[(3,1,-2),(16,18,2),(19,19,0)] and Y=[(3,1,-2),(19,18,-1),(20,19,-1)].
        // The second pair's predicted index must win lexicographically.
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 16, 19, 20), events(series, 0, 1, 18, 19, 25),
                2, 3, HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 25);
        assertEquals(List.of(new EventMatch(3, 1, -2), new EventMatch(16, 18, 2), new EventMatch(19, 19, 0)),
                result.matches());
        assertEquals(1, result.exactMatchCount());
        assertEquals(List.of(20), result.unmatchedPredictedIndexes());
        assertNumEquals(0.0, result.meanSignedOffset());
    }

    @Test
    public void lexicographicallyEarliestSequenceAmongTiedOptima() {
        BarSeries series = series();
        // A: (4,6),(8,10),(11,11),(17,16) and B: (4,6),(11,10),(12,11),(17,16)
        // both achieve (pairs=4, totalAbs=5, worst=2); A is lexicographically
        // earlier at the second pair.
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 8, 11, 12, 17),
                events(series, 0, 6, 10, 11, 16), 2, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(List.of(new EventMatch(4, 6, 2), new EventMatch(8, 10, 2), new EventMatch(11, 11, 0),
                new EventMatch(17, 16, -1)), result.matches());
        assertEquals(1, result.exactMatchCount());
        assertEquals(List.of(12), result.unmatchedPredictedIndexes());
    }

    @Test
    public void lexicographicTieBreakKeepsEarliestPredictedMatched() {
        BarSeries series = series();
        // Two 3-pair assignments tie on (pairs=3, totalAbs=8, worst=4):
        // [(0,4),(14,17),(19,18)] is the canonical choice over
        // [(0,4),(19,17),(20,18)].
        EventSynchronizationResult result = evaluate(events(series, 0, 0, 14, 19, 20), events(series, 0, 4, 17, 18), 7,
                7, HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 20);
        assertEquals(List.of(new EventMatch(0, 4, 4), new EventMatch(14, 17, 3), new EventMatch(19, 18, -1)),
                result.matches());
        assertEquals(List.of(20), result.unmatchedPredictedIndexes());
    }

    @Test
    public void denseEventStreamsBeyondMemorySafeCapacityFailWithDocumentedException() {
        BarSeries series = series(5000);
        EventSignal dense = events(series, 0, allIndexes(5000));
        assertThrows(IllegalArgumentException.class, () -> evaluate(dense, dense, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 4999));
    }

    @Test
    public void benchmarkEnvelopeRemainsComputable() {
        BarSeries series = series(1000);
        EventSynchronizationResult result = evaluate(events(series, 0, allIndexes(1000)),
                events(series, 0, allIndexes(1000)), 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 999);
        assertEquals(1000, result.matchedCount());
    }

    @Test
    public void matchesBruteForceOracleOnLargerRandomCases() {
        Random random = new Random(20260809L);
        for (int trial = 0; trial < 2000; trial++) {
            int predictedSize = random.nextInt(9);
            int referenceSize = random.nextInt(9);
            int[] predicted = sortedDistinct(random, predictedSize, 30);
            int[] reference = sortedDistinct(random, referenceSize, 30);
            int maxLead = random.nextInt(10);
            int maxLag = random.nextInt(10);

            BarSeries series = series(30);
            EventSynchronizationResult actual = evaluate(events(series, 0, predicted), events(series, 0, reference),
                    maxLead, maxLag, HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 29);
            BruteForceResult expected = bruteForce(predicted, reference, maxLead, maxLag);

            assertEquals("trial " + trial + " p=" + Arrays.toString(predicted) + " r=" + Arrays.toString(reference)
                    + " lead=" + maxLead + " lag=" + maxLag, expected.matches.size(), actual.matchedCount());
            assertEquals(expected.totalAbsoluteOffset,
                    actual.matches().stream().mapToLong(EventMatch::offsetBars).map(Math::abs).sum());
            for (int i = 0; i < expected.matches.size(); i++) {
                assertEquals(expected.matches.get(i).predictedIndex(), actual.matches().get(i).predictedIndex());
                assertEquals(expected.matches.get(i).referenceIndex(), actual.matches().get(i).referenceIndex());
            }
        }
    }

    private static int[] allIndexes(int barCount) {
        int[] indexes = new int[barCount];
        for (int i = 0; i < barCount; i++) {
            indexes[i] = i;
        }
        return indexes;
    }

    @Test
    public void emptyEventPoliciesAreExplicitAndRecorded() {
        BarSeries series = series();
        EventSignal empty = events(series, 0);
        EventSignal nonEmpty = events(series, 0, 5);

        EventSynchronizationResult undefinedBoth = evaluate(empty, empty, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(Double.NaN, undefinedBoth.precision());
        assertNumEquals(Double.NaN, undefinedBoth.recall());
        assertNumEquals(Double.NaN, undefinedBoth.f1Score());
        assertEquals(EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, undefinedBoth.emptyEventPolicy());

        EventSynchronizationResult zeroBoth = evaluate(empty, empty, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.ZERO_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(0.0, zeroBoth.precision());
        assertNumEquals(0.0, zeroBoth.recall());
        assertNumEquals(0.0, zeroBoth.f1Score());
        assertEquals(EmptyEventPolicy.ZERO_WHEN_BOTH_EMPTY, zeroBoth.emptyEventPolicy());

        EventSynchronizationResult oneBoth = evaluate(empty, empty, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.ONE_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(1.0, oneBoth.precision());
        assertNumEquals(1.0, oneBoth.recall());
        assertNumEquals(1.0, oneBoth.f1Score());

        EventSynchronizationResult emptyPredictions = evaluate(empty, nonEmpty, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(Double.NaN, emptyPredictions.precision());
        assertNumEquals(0.0, emptyPredictions.recall());
        assertNumEquals(0.0, emptyPredictions.f1Score());

        EventSynchronizationResult emptyReferences = evaluate(nonEmpty, empty, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(0.0, emptyReferences.precision());
        assertNumEquals(Double.NaN, emptyReferences.recall());
        assertNumEquals(0.0, emptyReferences.f1Score());

        EventSynchronizationResult noMatch = evaluate(events(series, 0, 3), events(series, 0, 9), 1, 1,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(0.0, noMatch.precision());
        assertNumEquals(0.0, noMatch.recall());
        assertNumEquals(0.0, noMatch.f1Score());
    }

    @Test
    public void unstableBarsAreExcludedFromEvaluation() {
        BarSeries series = series();
        EventSignal predicted = EventSignals.fromPredicate(series, 4, i -> {
            if (i < 4) {
                throw new AssertionError("evaluator must not read below the unstable boundary");
            }
            return i == 5 || i == 8;
        });
        EventSignal reference = events(series, 0, 5);
        // Silent exclusion below the unstable boundary is CLAMP's documented
        // behavior; STRICT fails fast for such requests instead.
        EventSynchronizationResult result = evaluate(predicted, reference, 0, 0, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertEquals(4, result.effectiveStartIndex());
        assertEquals(2, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(8), result.unmatchedPredictedIndexes());
    }

    @Test
    public void strictPolicyFailsFastWhenRequestedRangeIncludesUnstableBars() {
        BarSeries series = series();
        EventSignal predicted = EventSignals.fromPredicate(series, 10, i -> i == 12);
        EventSignal reference = events(series, 0, 12);
        // STRICT treats the unstable-bar boundary as unavailable history: a
        // request starting below it fails fast instead of silently truncating.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19));
        assertThrows(IllegalArgumentException.class, () -> evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 9));
        // A request starting at or after the boundary succeeds under STRICT.
        EventSynchronizationResult valid = evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 10, 19);
        assertEquals(10, valid.effectiveStartIndex());
        assertEquals(1, valid.matchedCount());
    }

    @Test
    public void clampPolicyNeverReportsGappedEffectiveRange() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        EventSignal predicted = events(series, 0, 12);
        EventSignal reference = events(series, 0, 12);
        // A request fully before the available history clamps to the canonical
        // empty inclusive range (start == end + 1), never an inverted one.
        EventSynchronizationResult beforeHistory = evaluate(predicted, reference, 0, 0, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 8);
        assertEquals(beforeHistory.effectiveEndIndex() + 1, beforeHistory.effectiveStartIndex());
        assertEquals(0, beforeHistory.predictedCount());

        EventSynchronizationResult afterEnd = evaluate(predicted, reference, 0, 0, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 45, 50);
        assertEquals(afterEnd.effectiveEndIndex() + 1, afterEnd.effectiveStartIndex());
        assertEquals(0, afterEnd.predictedCount());

        EventSynchronizationResult unstableGap = evaluate(EventSignals.fromPredicate(series, 40, i -> false),
                events(series, 0, 12), 0, 0, HistoryPolicy.CLAMP, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 30);
        assertEquals(unstableGap.effectiveEndIndex() + 1, unstableGap.effectiveStartIndex());
        assertEquals(0, unstableGap.predictedCount());
    }

    @Test
    public void strictPolicyRejectsUnavailableHistory() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        assertEquals(10, series.getBeginIndex());
        assertEquals(39, series.getEndIndex());

        EventSignal predicted = events(series, 0, 12);
        EventSignal reference = events(series, 0, 12);
        assertThrows(IllegalArgumentException.class, () -> evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19));
        assertThrows(IllegalArgumentException.class, () -> evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 10, 45));
        // A fully available request passes under STRICT.
        EventSynchronizationResult valid = evaluate(predicted, reference, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 12, 20);
        assertEquals(1, valid.matchedCount());
    }

    @Test
    public void clampPolicyIntersectsWithAvailableHistory() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        EventSignal predicted = events(series, 0, 12, 20);
        EventSignal reference = events(series, 0, 12);

        EventSynchronizationResult result = evaluate(predicted, reference, 0, 0, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 45);
        assertEquals(10, result.effectiveStartIndex());
        assertEquals(39, result.effectiveEndIndex());
        assertEquals(2, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
    }

    @Test
    public void differentSeriesInputsAreRejected() {
        BarSeries first = series();
        BarSeries second = series();
        assertThrows(IllegalArgumentException.class, () -> evaluate(events(first, 0, 5), events(second, 0, 5), 0, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19));
        assertThrows(NullPointerException.class, () -> evaluate(null, events(first, 0, 5), 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19));
        assertThrows(NullPointerException.class, () -> evaluate(events(first, 0, 5), null, 0, 0, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19));
        assertThrows(NullPointerException.class, () -> new EventSynchronizationEvaluator().evaluate(events(first, 0, 5),
                events(first, 0, 5), 0, 19, null));
    }

    @Test
    public void invertedRequestedRangeIsRejected() {
        BarSeries series = series();
        assertThrows(IllegalArgumentException.class, () -> evaluate(events(series, 0, 5), events(series, 0, 5), 0, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 10, 5));
    }

    @Test
    public void negativeTolerancesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationConfig(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EventSynchronizationConfig(0, -1));
        assertThrows(NullPointerException.class,
                () -> new EventSynchronizationConfig(0, 0, null, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY));
        assertThrows(NullPointerException.class,
                () -> new EventSynchronizationConfig(0, 0, HistoryPolicy.STRICT, null));
    }

    @Test
    public void extremeToleranceWindowsAreOverflowSafe() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 0, 19), events(series, 0, 5, 10),
                Integer.MAX_VALUE, Integer.MAX_VALUE, HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY,
                0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(new EventMatch(0, 5, 5), new EventMatch(19, 10, -9)), result.matches());
    }

    @Test
    public void resultListsAreImmutable() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 5), events(series, 0, 5), 0, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertThrows(UnsupportedOperationException.class, () -> result.matches().add(new EventMatch(0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> result.unmatchedPredictedIndexes().add(1));
        assertThrows(UnsupportedOperationException.class, () -> result.unmatchedReferenceIndexes().add(1));
    }

    @Test
    public void doesNotMatchAcrossRequestedRangeBoundary() {
        BarSeries series = series();
        EventSignal predicted = events(series, 0, 9);
        EventSignal reference = events(series, 0, 11);
        // The reference event at 11 sits outside [0, 10] and must never satisfy the
        // predicted event at 9, even though a 5-bar window would reach it.
        EventSynchronizationResult result = evaluate(predicted, reference, 5, 5, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 10);
        assertEquals(0, result.referenceCount());
        assertEquals(0, result.matchedCount());

        EventSynchronizationResult widened = evaluate(predicted, reference, 5, 5, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 11);
        assertEquals(1, widened.matchedCount());
    }

    @Test
    public void emptySeriesEvaluationIsSafe() {
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EventSignal signal = EventSignals.fromPredicate(empty, 0, i -> false);
        EventSynchronizationResult clamped = evaluate(signal, signal, 1, 1, HistoryPolicy.CLAMP,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 9);
        assertEquals(0, clamped.predictedCount());
        assertEquals(0, clamped.referenceCount());
        assertEquals(clamped.effectiveEndIndex() + 1, clamped.effectiveStartIndex());
        assertNumEquals(Double.NaN, clamped.f1Score());
        assertThrows(IllegalArgumentException.class, () -> evaluate(signal, signal, 1, 1, HistoryPolicy.STRICT,
                EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 9));
    }

    @Test
    public void matchesBruteForceOracleOnSmallRandomCases() {
        Random random = new Random(453);
        for (int trial = 0; trial < 250; trial++) {
            int predictedSize = random.nextInt(6);
            int referenceSize = random.nextInt(6);
            int[] predicted = sortedDistinct(random, predictedSize, 16);
            int[] reference = sortedDistinct(random, referenceSize, 16);
            int maxLead = random.nextInt(4);
            int maxLag = random.nextInt(4);

            BarSeries series = series(20);
            EventSynchronizationResult actual = evaluate(events(series, 0, predicted), events(series, 0, reference),
                    maxLead, maxLag, HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
            BruteForceResult expected = bruteForce(predicted, reference, maxLead, maxLag);

            assertEquals("trial " + trial + " p=" + Arrays.toString(predicted) + " r=" + Arrays.toString(reference)
                    + " lead=" + maxLead + " lag=" + maxLag, expected.matches.size(), actual.matchedCount());
            assertEquals(expected.totalAbsoluteOffset,
                    actual.matches().stream().mapToLong(EventMatch::offsetBars).map(Math::abs).sum());
            assertEquals(expected.worstAbsoluteOffset,
                    actual.matches().stream().mapToLong(EventMatch::offsetBars).map(Math::abs).max().orElse(-1));
            for (int i = 0; i < expected.matches.size(); i++) {
                assertEquals(expected.matches.get(i).predictedIndex(), actual.matches().get(i).predictedIndex());
                assertEquals(expected.matches.get(i).referenceIndex(), actual.matches().get(i).referenceIndex());
            }
        }
    }

    @Test
    public void sanityCheckOnBothFactories() {
        // Parameterized execution already runs every test under both Num factories;
        // this guards the mean/median diagnostics across factories.
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 9, 14), events(series, 0, 5, 10, 15), 1, 0,
                HistoryPolicy.STRICT, EmptyEventPolicy.UNDEFINED_WHEN_BOTH_EMPTY, 0, 19);
        assertNumEquals(1.0, result.meanSignedOffset());
        assertNumEquals(1.0, result.meanAbsoluteOffset());
        assertNumEquals(1.0, result.medianSignedOffset());
        assertNumEquals(1.0, result.minSignedOffset());
        assertNumEquals(1.0, result.maxSignedOffset());
    }

    private static int[] sortedDistinct(Random random, int size, int bound) {
        int[] values = new int[size];
        int cursor = 0;
        while (cursor < size) {
            int candidate = random.nextInt(bound);
            boolean duplicate = false;
            for (int i = 0; i < cursor; i++) {
                if (values[i] == candidate) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                values[cursor++] = candidate;
            }
        }
        Arrays.sort(values);
        return values;
    }

    private static final class BruteForceResult {
        final List<EventMatch> matches;
        final long totalAbsoluteOffset;
        final long worstAbsoluteOffset;

        BruteForceResult(List<EventMatch> matches, long totalAbsoluteOffset, long worstAbsoluteOffset) {
            this.matches = matches;
            this.totalAbsoluteOffset = totalAbsoluteOffset;
            this.worstAbsoluteOffset = worstAbsoluteOffset;
        }
    }

    private static BruteForceResult bruteForce(int[] predicted, int[] reference, int maxLead, int maxLag) {
        List<EventMatch> best = new ArrayList<>();
        search(predicted, reference, 0, 0, maxLead, maxLag, new ArrayList<>(), best);
        long total = 0;
        long worst = -1;
        for (EventMatch match : best) {
            long absolute = Math.abs((long) match.offsetBars());
            total += absolute;
            worst = Math.max(worst, absolute);
        }
        return new BruteForceResult(best, total, worst);
    }

    private static void search(int[] predicted, int[] reference, int predictedPosition, int referencePosition,
            int maxLead, int maxLag, List<EventMatch> current, List<EventMatch> best) {
        if (predictedPosition == predicted.length) {
            if (isBetter(current, best)) {
                best.clear();
                best.addAll(current);
            }
            return;
        }
        // Skip the current predicted event.
        search(predicted, reference, predictedPosition + 1, referencePosition, maxLead, maxLag, current, best);
        // Match it with any eligible reference event after the previous match.
        for (int l = referencePosition; l < reference.length; l++) {
            long offset = (long) reference[l] - predicted[predictedPosition];
            if (offset < -maxLag) {
                continue;
            }
            if (offset > maxLead) {
                break;
            }
            current.add(new EventMatch(predicted[predictedPosition], reference[l], (int) offset));
            search(predicted, reference, predictedPosition + 1, l + 1, maxLead, maxLag, current, best);
            current.remove(current.size() - 1);
        }
    }

    private static boolean isBetter(List<EventMatch> candidate, List<EventMatch> incumbent) {
        if (candidate.size() != incumbent.size()) {
            return candidate.size() > incumbent.size();
        }
        long candidateTotal = candidate.stream().mapToLong(m -> Math.abs((long) m.offsetBars())).sum();
        long incumbentTotal = incumbent.stream().mapToLong(m -> Math.abs((long) m.offsetBars())).sum();
        if (candidateTotal != incumbentTotal) {
            return candidateTotal < incumbentTotal;
        }
        long candidateWorst = candidate.stream().mapToLong(m -> Math.abs((long) m.offsetBars())).max().orElse(-1);
        long incumbentWorst = incumbent.stream().mapToLong(m -> Math.abs((long) m.offsetBars())).max().orElse(-1);
        if (candidateWorst != incumbentWorst) {
            return candidateWorst < incumbentWorst;
        }
        for (int i = 0; i < candidate.size(); i++) {
            EventMatch a = candidate.get(i);
            EventMatch b = incumbent.get(i);
            if (a.predictedIndex() != b.predictedIndex()) {
                return a.predictedIndex() < b.predictedIndex();
            }
            if (a.referenceIndex() != b.referenceIndex()) {
                return a.referenceIndex() < b.referenceIndex();
            }
        }
        return false;
    }
}
