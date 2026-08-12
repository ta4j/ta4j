/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.DoubleStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.EventSynchronizationIndicator.Result.Match;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.NetMomentumIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Deterministic matching and scoring coverage for
 * {@link EventSynchronizationSupport}, the package-private engine behind
 * {@link EventSynchronizationIndicator}.
 */
public class EventSynchronizationSupportTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int SERIES_BARS = 40;

    public EventSynchronizationSupportTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries series() {
        return series(SERIES_BARS);
    }

    private BarSeries series(int barCount) {
        double[] prices = DoubleStream.iterate(1.0, d -> d + 1.0).limit(barCount).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
    }

    private Indicator<Boolean> events(BarSeries series, int unstableBars, int... indexes) {
        boolean[] mask = new boolean[series.getBarCount() == 0 ? 0 : series.getEndIndex() + 1];
        for (int index : indexes) {
            mask[index] = true;
        }
        return new CachedIndicator<Boolean>(series) {
            @Override
            protected Boolean calculate(int index) {
                return mask[index];
            }

            @Override
            public int getCountOfUnstableBars() {
                return unstableBars;
            }
        };
    }

    private EventSynchronizationResult evaluate(Indicator<Boolean> predicted, Indicator<Boolean> reference,
            int maxLeadBars, int maxLagBars, int start, int end) {
        return EventSynchronizationSupport.synchronize(EventSignals.fromIndicator(predicted),
                EventSignals.fromIndicator(reference), start, end, maxLeadBars, maxLagBars);
    }

    private static Match match(int predictedIndex, int referenceIndex) {
        return new Match(predictedIndex, referenceIndex);
    }

    @Test
    public void exactCoincidenceProducesPerfectMetrics() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 5, 10, 15), events(series, 0, 5, 10, 15), 0, 0,
                0, 19);
        assertEquals(3, result.predictedCount());
        assertEquals(3, result.referenceCount());
        assertEquals(3, result.matchedCount());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());
        assertEquals(3, result.exactMatchCount());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
        assertEquals(List.of(match(5, 5), match(10, 10), match(15, 15)), result.matches());
        assertNumEquals(0.0, result.meanSignedOffset());
        assertNumEquals(0.0, result.meanAbsoluteOffset());
        assertNumEquals(0.0, result.medianSignedOffset());
        assertNumEquals(0.0, result.minSignedOffset());
        assertNumEquals(0.0, result.maxSignedOffset());
    }

    @Test
    public void leadingPredictionsMatchWithinMaxLeadBars() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 9, 14), events(series, 0, 5, 10, 15), 1, 0, 0,
                19);
        assertEquals(3, result.matchedCount());
        assertEquals(List.of(match(4, 5), match(9, 10), match(14, 15)), result.matches());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
    }

    @Test
    public void laggingPredictionsMatchOnlyWithinMaxLagBars() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 6, 11, 16), events(series, 0, 5, 10, 15), 0, 1,
                0, 19);
        assertEquals(3, result.matchedCount());
        assertEquals(List.of(match(6, 5), match(11, 10), match(16, 15)), result.matches());
        assertNumEquals(1.0, result.precision());
        assertNumEquals(1.0, result.recall());
        assertNumEquals(1.0, result.f1Score());
    }

    @Test
    public void asymmetricLeadLagWindowsAreHonored() {
        BarSeries series = series();
        // p=3 leads r=4 by 1 and r=7 by 4; p=8 lags r=7 by 1 and leads r=10 by 2.
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 8), events(series, 0, 4, 7, 10), 2, 1, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(match(3, 4), match(8, 7)), result.matches());
        assertNumEquals(1.0, result.meanAbsoluteOffset());
    }

    @Test
    public void eventsJustOutsideWindowRemainUnmatched() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 9), events(series, 0, 5), 1, 1, 0, 19);
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
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1, 0, 19);
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(match(4, 5)), result.matches());
        assertEquals(1, result.falsePositives());
        assertEquals(0, result.falseNegatives());
    }

    @Test
    public void onePredictionCannotConsumeTwoReferenceEvents() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 5), events(series, 0, 4, 6), 1, 1, 0, 19);
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(match(5, 4)), result.matches());
        assertEquals(0, result.falsePositives());
        assertEquals(1, result.falseNegatives());
    }

    @Test
    public void competingAssignmentsSelectMaximumCardinality() {
        BarSeries series = series();
        // Two disjoint pairs are available; a naive nearest-neighbor approach would
        // match only one.
        EventSynchronizationResult result = evaluate(events(series, 0, 2, 5), events(series, 0, 3, 4), 1, 1, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(match(2, 3), match(5, 4)), result.matches());
    }

    @Test
    public void equalCardinalityAssignmentsMinimizeTotalAbsoluteOffset() {
        BarSeries series = series();
        // Two two-pair assignments exist: (0,1)+(3,2) with total |offset| 2, and
        // (0,2)+(3,1) with total 4.
        EventSynchronizationResult result = evaluate(events(series, 0, 0, 3), events(series, 0, 1, 2), 2, 2, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(match(0, 1), match(3, 2)), result.matches());
        assertNumEquals(1.0, result.meanAbsoluteOffset());
    }

    @Test
    public void finalTieIsDeterministicAndIndexOrdered() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1, 0, 19);
        assertEquals(List.of(match(4, 5)), result.matches());

        // Repeated evaluation returns structurally equal results.
        EventSynchronizationResult again = evaluate(events(series, 0, 4, 6), events(series, 0, 5), 1, 1, 0, 19);
        assertEquals(result, again);
    }

    @Test
    public void lexicographicTieBreakSurvivesDominantPrefixOffsets() {
        BarSeries series = series();
        // Two 3-pair assignments tie on (pairs=3, totalAbs=4, worst=2):
        // X=[(3,1,-2),(16,18,2),(19,19,0)] and Y=[(3,1,-2),(19,18,-1),(20,19,-1)].
        // The second pair's predicted index must win lexicographically.
        EventSynchronizationResult result = evaluate(events(series, 0, 3, 16, 19, 20), events(series, 0, 1, 18, 19, 25),
                2, 3, 0, 25);
        assertEquals(List.of(match(3, 1), match(16, 18), match(19, 19)), result.matches());
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
                events(series, 0, 6, 10, 11, 16), 2, 1, 0, 19);
        assertEquals(List.of(match(4, 6), match(8, 10), match(11, 11), match(17, 16)), result.matches());
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
                7, 0, 20);
        assertEquals(List.of(match(0, 4), match(14, 17), match(19, 18)), result.matches());
        assertEquals(List.of(20), result.unmatchedPredictedIndexes());
    }

    @Test
    public void denseEventStreamsBeyondMemorySafeCapacityFailWithDocumentedException() {
        BarSeries series = series(5000);
        Indicator<Boolean> dense = events(series, 0, allIndexes(5000));
        assertThrows(IllegalArgumentException.class, () -> evaluate(dense, dense, 0, 0, 0, 4999));
    }

    @Test
    public void benchmarkEnvelopeRemainsComputable() {
        BarSeries series = series(1000);
        EventSynchronizationResult result = evaluate(events(series, 0, allIndexes(1000)),
                events(series, 0, allIndexes(1000)), 0, 0, 0, 999);
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
                    maxLead, maxLag, 0, 29);
            BruteForceResult expected = bruteForce(predicted, reference, maxLead, maxLag);

            assertEquals("trial " + trial + " p=" + Arrays.toString(predicted) + " r=" + Arrays.toString(reference)
                    + " lead=" + maxLead + " lag=" + maxLag, expected.matches.size(), actual.matchedCount());
            assertEquals(expected.totalAbsoluteOffset,
                    actual.matches().stream().mapToLong(Match::offsetBars).map(Math::abs).sum());
            assertEquals(expected.worstAbsoluteOffset,
                    actual.matches().stream().mapToLong(Match::offsetBars).map(Math::abs).max().orElse(-1));
            for (int i = 0; i < expected.matches.size(); i++) {
                assertEquals(expected.matches.get(i).predictedIndex(), actual.matches().get(i).predictedIndex());
                assertEquals(expected.matches.get(i).referenceIndex(), actual.matches().get(i).referenceIndex());
            }
        }
    }

    @Test
    public void evaluationAtMaximumBarIndexTerminatesWithoutOverflow() {
        // A rolling series may legally reach getEndIndex() == Integer.MAX_VALUE;
        // the extraction loop must process the range and terminate instead of
        // incrementing past the end.
        BarSeries series = series(1);
        BarSeries atMaxSeries = new BaseBarSeries(series.getName(), series.getBarData()) {
            @Override
            public int getBeginIndex() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }
        };
        EventSynchronizationResult result = EventSynchronizationSupport.synchronize(
                EventSignals.fromPredicate(atMaxSeries, 0, i -> i == Integer.MAX_VALUE),
                EventSignals.fromPredicate(atMaxSeries, 0, i -> i == Integer.MAX_VALUE), Integer.MAX_VALUE,
                Integer.MAX_VALUE, 0, 0);
        assertEquals(1, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(match(Integer.MAX_VALUE, Integer.MAX_VALUE)), result.matches());
        assertEquals(0, result.matches().get(0).offsetBars());
    }

    @Test
    public void unstableBoundaryIsRespectedBySupport() {
        BarSeries series = series(10);
        EventSynchronizationResult result = EventSynchronizationSupport.synchronize(
                EventSignals.fromPredicate(series, 6, i -> i == 5 || i == 7),
                EventSignals.fromPredicate(series, 0, i -> i == 7), 0, 9, 0, 0);
        assertEquals(6, result.effectiveStartIndex());
        assertEquals(1, result.predictedCount());
        assertEquals(1, result.matchedCount());
    }

    @Test
    public void crossingIndicatorKeepsItsOwnUnstableBoundary() {
        // CrossIndicator reports max(child unstable bars) + 1: a crossing over a
        // momentum signal with 5 unstable bars is only trustworthy from index 6,
        // and a reference event on the child's boundary bar (5) must never enter
        // the denominator.
        BarSeries series = series(40);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        NetMomentumIndicator momentum = new NetMomentumIndicator(
                NumericIndicator.of(close).minus(new PreviousValueIndicator(close, 1)), 5, 0);
        assertEquals(5, momentum.getCountOfUnstableBars());
        Indicator<Boolean> crossing = new CrossIndicator(momentum,
                new ConstantIndicator<>(series, series.numFactory().zero()));
        assertEquals(6, crossing.getCountOfUnstableBars());

        // A reference event exactly on the child's boundary bar is below the
        // crossing boundary and must be excluded entirely (no false negative).
        EventSynchronizationResult excluded = evaluate(crossing, events(series, 0, 5), 12, 12, 0, 39);
        assertEquals(6, excluded.effectiveStartIndex());
        assertEquals(0, excluded.referenceCount());
        assertEquals(0, excluded.falseNegatives());
        // A reference event at the crossing's own boundary is the first evaluated
        // bar and is counted.
        EventSynchronizationResult counted = evaluate(crossing, events(series, 0, 6), 12, 12, 0, 39);
        assertEquals(6, counted.effectiveStartIndex());
        assertEquals(1, counted.referenceCount());
    }

    private static int[] allIndexes(int barCount) {
        int[] indexes = new int[barCount];
        for (int i = 0; i < barCount; i++) {
            indexes[i] = i;
        }
        return indexes;
    }

    @Test
    public void extractionBoundsBeforeAllocatingOversizedArrays() {
        BarSeries small = series(20);
        BarSeries hugeEnd = (BarSeries) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { BarSeries.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "getBeginIndex":
                        return 0;
                    case "getEndIndex":
                        return 9_000_000;
                    case "getBarCount":
                        return 9_000_001;
                    default:
                        return method.invoke(small, args);
                    }
                });
        // A signal firing on every bar of a 9M-bar range must fail with the
        // documented exception during extraction, before allocating arrays that
        // could exhaust memory.
        assertThrows(IllegalArgumentException.class,
                () -> EventSynchronizationSupport.synchronize(EventSignals.fromPredicate(hugeEnd, 0, i -> true),
                        EventSignals.fromPredicate(hugeEnd, 0, i -> true), 0, 9_000_000, 0, 0));
    }

    @Test
    public void bothEmptyAndPartialEmptySemanticsAreDocumented() {
        BarSeries series = series();
        Indicator<Boolean> empty = events(series, 0);
        Indicator<Boolean> nonEmpty = events(series, 0, 5);

        // Both streams empty: undefined, never a perfect score.
        EventSynchronizationResult bothEmpty = evaluate(empty, empty, 1, 1, 0, 19);
        assertNumEquals(Double.NaN, bothEmpty.precision());
        assertNumEquals(Double.NaN, bothEmpty.recall());
        assertNumEquals(Double.NaN, bothEmpty.f1Score());

        // Exactly one stream empty: the empty side's metric is NaN, F1 is 0.
        EventSynchronizationResult emptyPredictions = evaluate(empty, nonEmpty, 1, 1, 0, 19);
        assertNumEquals(Double.NaN, emptyPredictions.precision());
        assertNumEquals(0.0, emptyPredictions.recall());
        assertNumEquals(0.0, emptyPredictions.f1Score());

        EventSynchronizationResult emptyReferences = evaluate(nonEmpty, empty, 1, 1, 0, 19);
        assertNumEquals(0.0, emptyReferences.precision());
        assertNumEquals(Double.NaN, emptyReferences.recall());
        assertNumEquals(0.0, emptyReferences.f1Score());

        // Both streams non-empty but nothing matches: F1 is 0.
        EventSynchronizationResult noMatch = evaluate(events(series, 0, 3), events(series, 0, 9), 1, 1, 0, 19);
        assertNumEquals(0.0, noMatch.precision());
        assertNumEquals(0.0, noMatch.recall());
        assertNumEquals(0.0, noMatch.f1Score());
    }

    @Test
    public void unstableBarsAreExcludedFromEvaluation() {
        BarSeries series = series();
        Indicator<Boolean> predicted = new AbstractIndicator<Boolean>(series) {
            @Override
            public Boolean getValue(int index) {
                if (index < 4) {
                    throw new AssertionError("evaluator must not read below the unstable boundary");
                }
                return index == 5 || index == 8;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 4;
            }
        };
        Indicator<Boolean> reference = events(series, 0, 5);
        EventSynchronizationResult result = evaluate(predicted, reference, 0, 0, 0, 19);
        assertEquals(4, result.effectiveStartIndex());
        assertEquals(2, result.predictedCount());
        assertEquals(1, result.referenceCount());
        assertEquals(1, result.matchedCount());
        assertEquals(List.of(8), result.unmatchedPredictedIndexes());
    }

    @Test
    public void rangesStartingBelowUnstableBoundaryAreClamped() {
        BarSeries series = series();
        Indicator<Boolean> predicted = events(series, 10, 12);
        Indicator<Boolean> reference = events(series, 0, 12);

        // A request starting below the unstable boundary silently starts at the
        // boundary instead of failing fast.
        EventSynchronizationResult clamped = evaluate(predicted, reference, 0, 0, 0, 19);
        assertEquals(10, clamped.effectiveStartIndex());
        assertEquals(1, clamped.matchedCount());
        // A request fully below the boundary resolves to the canonical empty range.
        EventSynchronizationResult empty = evaluate(predicted, reference, 0, 0, 0, 9);
        assertEquals(empty.effectiveEndIndex() + 1, empty.effectiveStartIndex());
        assertEquals(0, empty.predictedCount());
        assertEquals(0, empty.matchedCount());
        // A request at or after the boundary is evaluated unchanged.
        EventSynchronizationResult valid = evaluate(predicted, reference, 0, 0, 10, 19);
        assertEquals(10, valid.effectiveStartIndex());
        assertEquals(1, valid.matchedCount());
    }

    @Test
    public void unavailableRangesResolveToCanonicalEmptyRange() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        Indicator<Boolean> predicted = events(series, 0, 12);
        Indicator<Boolean> reference = events(series, 0, 12);
        // A request fully before the available history clamps to the canonical
        // empty inclusive range (start == end + 1), never an inverted one.
        EventSynchronizationResult beforeHistory = evaluate(predicted, reference, 0, 0, 0, 8);
        assertEquals(beforeHistory.effectiveEndIndex() + 1, beforeHistory.effectiveStartIndex());
        assertEquals(0, beforeHistory.predictedCount());

        EventSynchronizationResult afterEnd = evaluate(predicted, reference, 0, 0, 45, 50);
        assertEquals(afterEnd.effectiveEndIndex() + 1, afterEnd.effectiveStartIndex());
        assertEquals(0, afterEnd.predictedCount());

        EventSynchronizationResult unstableGap = evaluate(events(series, 40), events(series, 0, 12), 0, 0, 0, 30);
        assertEquals(unstableGap.effectiveEndIndex() + 1, unstableGap.effectiveStartIndex());
        assertEquals(0, unstableGap.predictedCount());
    }

    @Test
    public void rangesAreClampedToAvailableHistory() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        assertEquals(10, series.getBeginIndex());
        assertEquals(39, series.getEndIndex());

        Indicator<Boolean> predicted = events(series, 0, 12);
        Indicator<Boolean> reference = events(series, 0, 12);
        // A request reaching below the available history starts at the begin index.
        EventSynchronizationResult below = evaluate(predicted, reference, 0, 0, 0, 19);
        assertEquals(10, below.effectiveStartIndex());
        assertEquals(1, below.matchedCount());
        // A request reaching past the series end stops at the end index.
        EventSynchronizationResult above = evaluate(predicted, reference, 0, 0, 10, 45);
        assertEquals(39, above.effectiveEndIndex());
        assertEquals(1, above.matchedCount());
        // A fully available request is evaluated unchanged.
        EventSynchronizationResult valid = evaluate(predicted, reference, 0, 0, 12, 20);
        assertEquals(1, valid.matchedCount());
    }

    @Test
    public void rangesIntersectWithAvailableHistory() {
        BarSeries series = series(40);
        series.setMaximumBarCount(30);
        Indicator<Boolean> predicted = events(series, 0, 12, 20);
        Indicator<Boolean> reference = events(series, 0, 12);

        EventSynchronizationResult result = evaluate(predicted, reference, 0, 0, 0, 45);
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
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(events(first, 0, 5), events(second, 0, 5), 0, 0, 0, 19));
        assertThrows(NullPointerException.class, () -> EventSynchronizationSupport.synchronize(null,
                EventSignals.fromIndicator(events(first, 0, 5)), 0, 19, 0, 0));
        assertThrows(NullPointerException.class, () -> EventSynchronizationSupport
                .synchronize(EventSignals.fromIndicator(events(first, 0, 5)), null, 0, 19, 0, 0));
    }

    @Test
    public void invertedRequestedRangeIsRejected() {
        BarSeries series = series();
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(events(series, 0, 5), events(series, 0, 5), 0, 0, 10, 5));
    }

    @Test
    public void negativeTolerancesAreRejected() {
        BarSeries series = series();
        Indicator<Boolean> signal = events(series, 0, 5);
        assertThrows(IllegalArgumentException.class, () -> EventSynchronizationSupport
                .synchronize(EventSignals.fromIndicator(signal), EventSignals.fromIndicator(signal), 0, 19, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> EventSynchronizationSupport
                .synchronize(EventSignals.fromIndicator(signal), EventSignals.fromIndicator(signal), 0, 19, 0, -1));
    }

    @Test
    public void extremeToleranceWindowsAreOverflowSafe() {
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 0, 19), events(series, 0, 5, 10),
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 19);
        assertEquals(2, result.matchedCount());
        assertEquals(List.of(match(0, 5), match(19, 10)), result.matches());
    }

    @Test
    public void emptySeriesEvaluationIsSafe() {
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        Indicator<Boolean> signal = new AbstractIndicator<Boolean>(empty) {
            @Override
            public Boolean getValue(int index) {
                return Boolean.FALSE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EventSynchronizationResult clamped = evaluate(signal, signal, 1, 1, 0, 9);
        assertEquals(0, clamped.predictedCount());
        assertEquals(0, clamped.referenceCount());
        assertEquals(clamped.effectiveEndIndex() + 1, clamped.effectiveStartIndex());
        assertNumEquals(Double.NaN, clamped.f1Score());
    }

    @Test
    public void sanityCheckOnBothFactories() {
        // Parameterized execution already runs every test under both Num factories;
        // this guards the mean/median diagnostics across factories.
        BarSeries series = series();
        EventSynchronizationResult result = evaluate(events(series, 0, 4, 9, 14), events(series, 0, 5, 10, 15), 1, 0, 0,
                19);
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
        final List<Match> matches;
        final long totalAbsoluteOffset;
        final long worstAbsoluteOffset;

        BruteForceResult(List<Match> matches, long totalAbsoluteOffset, long worstAbsoluteOffset) {
            this.matches = matches;
            this.totalAbsoluteOffset = totalAbsoluteOffset;
            this.worstAbsoluteOffset = worstAbsoluteOffset;
        }
    }

    private static BruteForceResult bruteForce(int[] predicted, int[] reference, int maxLead, int maxLag) {
        List<Match> best = new ArrayList<>();
        search(predicted, reference, 0, 0, maxLead, maxLag, new ArrayList<>(), best);
        long total = 0;
        long worst = -1;
        for (Match match : best) {
            long absolute = Math.abs((long) match.offsetBars());
            total += absolute;
            worst = Math.max(worst, absolute);
        }
        return new BruteForceResult(best, total, worst);
    }

    private static void search(int[] predicted, int[] reference, int predictedPosition, int referencePosition,
            int maxLead, int maxLag, List<Match> current, List<Match> best) {
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
            current.add(match(predicted[predictedPosition], reference[l]));
            search(predicted, reference, predictedPosition + 1, l + 1, maxLead, maxLag, current, best);
            current.remove(current.size() - 1);
        }
    }

    private static boolean isBetter(List<Match> candidate, List<Match> incumbent) {
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
            Match a = candidate.get(i);
            Match b = incumbent.get(i);
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
