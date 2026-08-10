/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.event.EventSynchronizationConfig.HistoryPolicy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EventMutualInformationEvaluatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public EventMutualInformationEvaluatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static final double LN_2 = 0.6931471805599453;

    @Test
    public void perfectBinaryDependenceEqualsTargetEntropy() {
        BarSeries series = series(20);
        boolean[] events = alternatingEvents(20, 2);
        Indicator<Num> predictor = indicator(series, binaryValues(events));
        EventSignal target = eventSignal(series, 0, events);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertEquals(20, result.sampleCount());
        assertEquals(10, result.positiveTargetCount());
        assertNumEquals(numFactory.numOf(LN_2), result.targetEntropyNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(LN_2), result.mutualInformationNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(1), result.normalizedMutualInformation(), 1.0e-9);
    }

    @Test
    public void independentPredictorAndTargetProduceZero() {
        BarSeries series = series(20);
        // Predictor is the bar index; the target alternates every two bars, so
        // every equal-width bin of four consecutive bars contains exactly two
        // events: p(event | bin) == p(event) and the MI is exactly zero.
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        EventSignal target = eventSignal(series, 0, index -> (index / 2) % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 5, BinningStrategy.EQUAL_WIDTH));

        assertNumEquals(numFactory.numOf(0), result.mutualInformationNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(LN_2), result.targetEntropyNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(0), result.normalizedMutualInformation(), 1.0e-9);
        assertEquals(10, result.positiveTargetCount());
    }

    @Test
    public void futureWindowLabelsOnlySamplesWhoseWindowHoldsAnEvent() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        // Single event at index 5; window [i+1, i+3] contains it exactly for
        // samples i in {2, 3, 4}.
        EventSignal target = eventSignal(series, 0, index -> index == 5);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(1, 3, 2, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(7, result.sampleCount());
        assertEquals(3, result.positiveTargetCount());
        assertNumEquals(numFactory.numOf(3.0 / 7.0), result.positiveTargetRate(), 1.0e-9);
    }

    @Test
    public void currentBarWindowLabelsTheCurrentBar() {
        BarSeries series = series(20);
        boolean[] events = alternatingEvents(20, 3);
        Indicator<Num> predictor = indicator(series, binaryValues(events));
        EventSignal target = eventSignal(series, 0, events);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertEquals(20, result.sampleCount());
        assertNumEquals(numFactory.numOf(1), result.normalizedMutualInformation(), 1.0e-9);
    }

    @Test
    public void targetWindowsNeverCrossThePartitionBoundary() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        // Event at index 8; with window [i, i+2] the last admissible sample is
        // i = 6, so the event is consumed once and the last two samples are
        // excluded from training.
        EventSignal target = eventSignal(series, 0, index -> index == 8);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 8,
                new EventMutualInformationConfig(0, 2, 2, BinningStrategy.EQUAL_WIDTH));

        assertEquals(7, result.sampleCount());
        assertEquals(1, result.positiveTargetCount());
    }

    @Test
    public void noEventAndAllEventTargetsHaveZeroRawMi() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);

        EventMutualInformationResult noEvents = evaluate(predictor, eventSignal(series, 0, index -> false), 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));
        assertNumEquals(numFactory.numOf(0), noEvents.mutualInformationNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(0), noEvents.targetEntropyNats(), 1.0e-9);
        assertTrue(noEvents.normalizedMutualInformation().isNaN());
        assertEquals(20, noEvents.sampleCount());

        EventMutualInformationResult allEvents = evaluate(predictor, eventSignal(series, 0, index -> true), 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));
        assertNumEquals(numFactory.numOf(0), allEvents.mutualInformationNats(), 1.0e-9);
        assertTrue(allEvents.normalizedMutualInformation().isNaN());
    }

    @Test
    public void equalFrequencyBinningNeverSplitsTiedValues() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 1, 1, 2, 2, 2, 3, 3);
        // 10 samples, 4 requested bins => desired 3 per bin, but the tie run of
        // three 2s cannot be split, so the second bin absorbs it.
        EventSignal target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(4, result.requestedBinCount());
        assertEquals(3, result.effectiveBinCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(!result.mutualInformationNats().isNegative());
    }

    @Test
    public void equalFrequencyMiMatchesTheReportedPartition() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 1, 1, 2, 2, 2, 3, 3);
        // Ties merge bins: requested 4 -> desired ceil(10/4) = 3, and the
        // three 2s cannot be split, so the applied partition is exactly
        // {0,0,0}, {1,1,2,2,2}, {3,3} (3 bins). The reported effectiveBinCount
        // and the MI must both come from that partition.
        EventSignal target = eventSignal(series, 0, index -> index % 3 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(3, result.effectiveBinCount());
        assertNumEquals(
                numFactory.numOf(expectedMiForPartition(new int[][] { { 0, 1, 2 }, { 3, 4, 5, 6, 7 }, { 8, 9 } },
                        new boolean[] { true, false, false, true, false, false, true, false, false, true })),
                result.mutualInformationNats(), 1.0e-9);
    }

    @Test
    public void rejectsExcessivePredictorBinCount() {
        assertThrows(IllegalArgumentException.class, () -> new EventMutualInformationConfig(0, 0,
                EventMutualInformationConfig.MAX_PREDICTOR_BIN_COUNT + 1, BinningStrategy.EQUAL_WIDTH));
        assertThrows(IllegalArgumentException.class,
                () -> new EventMutualInformationConfig(0, 0, Integer.MAX_VALUE, BinningStrategy.EQUAL_WIDTH));
    }

    @Test
    public void skewedPredictorUsesMoreEffectiveBinsWithEqualFrequency() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 0, 0, 0, 0, 0, 5, 100);
        EventSignal target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult equalFrequency = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));
        EventMutualInformationResult equalWidth = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_WIDTH));

        // Eight tied zeros collapse into one equal-frequency bin; equal-width
        // keeps the requested four bins but concentrates samples in the first.
        assertEquals(2, equalFrequency.effectiveBinCount());
        assertEquals(4, equalWidth.effectiveBinCount());
        assertTrue(
                equalFrequency.mutualInformationNats().isNaN() || !equalFrequency.mutualInformationNats().isNegative());
        assertTrue(equalWidth.mutualInformationNats().isNaN() || !equalWidth.mutualInformationNats().isNegative());
    }

    @Test
    public void nonFinitePredictorSampleUndefinesMetricsButKeepsFullDiagnostics() {
        BarSeries series = series(10);
        List<Num> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            values.add(numFactory.numOf(i));
        }
        values.set(5, NaN.NaN);
        Indicator<Num> predictor = new MockIndicator(series, values);
        EventSignal target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        // Diagnostics keep covering every eligible sample, so candidate sample
        // counts cannot drift invisibly.
        assertEquals(10, result.sampleCount());
        assertEquals(5, result.positiveTargetCount());
        assertNumEquals(numFactory.numOf(0.5), result.positiveTargetRate(), 1.0e-9);
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void strictPolicyRejectsUnavailableHistory() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        EventSignal target = eventSignal(series, 0, index -> index % 3 == 0);
        EventSignal unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        // startIndex below the target's unstable boundary.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predictor, unstableTarget, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.STRICT)));
        // endIndex beyond the series end.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predictor, target, 0, 20,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.STRICT)));
        // The partition cannot hold a single complete target window.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predictor, target, 10, 10,
                new EventMutualInformationConfig(0, 3, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.STRICT)));
        // startIndex beyond endIndex.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predictor, target, 5, 4,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
    }

    @Test
    public void strictPolicyAllowsSamplesWhoseTargetWindowStartsAtStableTargetIndexes() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        // Target is unstable below index 5; with a window starting 3 bars
        // ahead, samples from index 2 read only stable target indexes.
        EventSignal unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        EventMutualInformationResult result = evaluate(predictor, unstableTarget, 2, 19,
                new EventMutualInformationConfig(3, 3, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.STRICT));

        assertEquals(15, result.sampleCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(!result.mutualInformationNats().isNegative());

        // Samples below index 2 would read unstable target indexes: STRICT
        // rejects them.
        assertThrows(IllegalArgumentException.class, () -> evaluate(predictor, unstableTarget, 1, 19,
                new EventMutualInformationConfig(3, 3, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.STRICT)));
    }

    @Test
    public void clampPolicyIntersectsTheAvailableRange() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        EventSignal unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        EventMutualInformationResult clamped = evaluate(predictor, unstableTarget, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.CLAMP));
        assertEquals(15, clamped.sampleCount());
        assertFalse(clamped.mutualInformationNats().isNaN());
        assertTrue(!clamped.mutualInformationNats().isNegative());

        // A range that clamps to empty yields an undefined result, not an error.
        EventMutualInformationResult empty = evaluate(predictor, unstableTarget, 0, 2,
                new EventMutualInformationConfig(0, 5, 2, BinningStrategy.EQUAL_WIDTH, HistoryPolicy.CLAMP));
        assertEquals(0, empty.sampleCount());
        assertTrue(empty.mutualInformationNats().isNaN());
        assertTrue(empty.positiveTargetRate().isNaN());
    }

    @Test
    public void rejectsInvalidConfigurationAndSeries() {
        BarSeries series = series(20);
        BarSeries otherSeries = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        EventSignal target = eventSignal(series, 0, index -> index % 3 == 0);

        assertThrows(NullPointerException.class, () -> evaluate(null, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
        assertThrows(NullPointerException.class, () -> evaluate(predictor, null, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
        assertThrows(NullPointerException.class, () -> evaluate(predictor, target, 0, 19, null));
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, eventSignal(otherSeries, 0, index -> false), 0, 19,
                        new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
    }

    private EventMutualInformationResult evaluate(Indicator<Num> predictor, EventSignal target, int startIndex,
            int endIndex, EventMutualInformationConfig config) {
        return new EventMutualInformationEvaluator().evaluate(predictor, target, startIndex, endIndex, config);
    }

    private BarSeries series(int barCount) {
        double[] raw = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            raw[i] = i;
        }
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(raw).build();
    }

    private Indicator<Num> indicator(BarSeries series, double... values) {
        List<Num> nums = new ArrayList<>(values.length);
        for (double value : values) {
            nums.add(numFactory.numOf(value));
        }
        return new MockIndicator(series, nums);
    }

    private static EventSignal eventSignal(BarSeries series, int unstableBars,
            java.util.function.IntPredicate predicate) {
        return EventSignals.fromPredicate(series, unstableBars, predicate);
    }

    private static EventSignal eventSignal(BarSeries series, int unstableBars, boolean[] events) {
        return EventSignals.fromPredicate(series, unstableBars, index -> index < events.length && events[index]);
    }

    /**
     * Computes mutual information over an explicitly given partition of sample
     * indexes, independent of the evaluator's binning implementation.
     */
    private static double expectedMiForPartition(int[][] partition, boolean[] labels) {
        int sampleCount = labels.length;
        int positiveCount = 0;
        for (boolean label : labels) {
            if (label) {
                positiveCount++;
            }
        }
        double positiveRate = (double) positiveCount / sampleCount;
        double negativeRate = 1.0 - positiveRate;
        double mi = 0.0;
        for (int[] bin : partition) {
            int positives = 0;
            for (int index : bin) {
                if (labels[index]) {
                    positives++;
                }
            }
            double binRate = (double) bin.length / sampleCount;
            double positiveInBin = (double) positives / bin.length;
            double negativeInBin = 1.0 - positiveInBin;
            mi += binRate * (positiveInBin * Math.log(positiveInBin / positiveRate)
                    + negativeInBin * Math.log(negativeInBin / negativeRate));
        }
        return mi;
    }

    private static boolean[] alternatingEvents(int barCount, int stride) {
        boolean[] events = new boolean[barCount];
        for (int i = 0; i < barCount; i++) {
            events[i] = i % stride == 0;
        }
        return events;
    }

    private static double[] binaryValues(boolean[] events) {
        double[] values = new double[events.length];
        for (int i = 0; i < events.length; i++) {
            values[i] = events[i] ? 1 : 0;
        }
        return values;
    }
}
