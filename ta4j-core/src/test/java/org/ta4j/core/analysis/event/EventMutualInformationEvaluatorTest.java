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
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.statistics.FloatNumFactory;
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
        Indicator<Boolean> target = eventSignal(series, 0, events);

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
        Indicator<Boolean> target = eventSignal(series, 0, index -> (index / 2) % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 5, BinningStrategy.EQUAL_WIDTH));

        assertNumEquals(numFactory.numOf(0), result.mutualInformationNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(LN_2), result.targetEntropyNats(), 1.0e-9);
        assertNumEquals(numFactory.numOf(0), result.normalizedMutualInformation(), 1.0e-9);
        assertEquals(10, result.positiveTargetCount());
    }

    @Test
    public void independentContingencyRoundingDoesNotRejectZeroMutualInformation() {
        // Review regression: with DoubleNum an exactly independent contingency
        // table can sum to a tiny negative mutual information through rounding
        // alone (for example -1.7e-16 for bins of (1, 3) and (4, 12) counts),
        // which the result constructor rejected; roundoff-scale negatives must
        // normalize to zero instead of throwing on valid independent data.
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index < 3 || (index >= 4 && index < 16));

        EventMutualInformationResult result = evaluate(predictor, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertEquals(20, result.sampleCount());
        assertEquals(15, result.positiveTargetCount());
        // The clamp normalizes roundoff-scale negatives to the factory zero,
        // so the result must be exactly zero, not merely within tolerance.
        assertTrue(result.mutualInformationNats().isZero());
        assertTrue(result.normalizedMutualInformation().isZero());
    }

    @Test
    public void futureWindowLabelsOnlySamplesWhoseWindowHoldsAnEvent() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        // Single event at index 5; window [i+1, i+3] contains it exactly for
        // samples i in {2, 3, 4}.
        Indicator<Boolean> target = eventSignal(series, 0, index -> index == 5);

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
        Indicator<Boolean> target = eventSignal(series, 0, events);

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
        Indicator<Boolean> target = eventSignal(series, 0, index -> index == 8);

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
        // 10 samples, 4 requested bins: bins are sized from the remaining
        // samples and remaining bins (3, 3, 2, 2), and tie runs cannot be
        // split, so the applied partition stays {0,0,0}, {1,1,2,2,2}, {3,3}.
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(4, result.requestedBinCount());
        assertEquals(3, result.effectiveBinCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(!result.mutualInformationNats().isNegative());
    }

    @Test
    public void equalFrequencyBinningHonorsRequestedCountWhenSamplesAreNotDivisible() {
        // 5 samples with 4 requested bins: with a fixed desired size of
        // ceil(5/4) = 2 the tail would collapse into 3 bins; sizing each bin
        // from the remaining samples and remaining bins yields sizes 2,1,1,1.
        BarSeries series = series(5);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 4,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(4, result.requestedBinCount());
        assertEquals(4, result.effectiveBinCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(!result.mutualInformationNats().isNegative());
    }

    @Test
    public void equalFrequencyMiMatchesTheReportedPartition() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 1, 1, 2, 2, 2, 3, 3);
        // Ties merge bins and cannot be split, so the applied partition is
        // exactly {0,0,0}, {1,1,2,2,2}, {3,3} (3 bins). The reported
        // effectiveBinCount and the MI must both come from that partition.
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 3 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        assertEquals(3, result.effectiveBinCount());
        assertNumEquals(
                numFactory.numOf(expectedMiForPartition(new int[][] { { 0, 1, 2 }, { 3, 4, 5, 6, 7 }, { 8, 9 } },
                        new boolean[] { true, false, false, true, false, false, true, false, false, true })),
                result.mutualInformationNats(), 1.0e-9);
    }

    @Test
    public void signedZeroPredictorValuesStayInOneEqualFrequencyBin() {
        BarSeries series = series(8);
        List<Num> values = new ArrayList<>();
        // DoubleNum.compareTo ranks -0.0 below +0.0, but they are numerically
        // equal: a constant predictor alternating the two signs must not be
        // split into two perfectly label-correlated bins.
        for (int i = 0; i < 8; i++) {
            values.add(numFactory.numOf(i % 2 == 0 ? 0.0 : -0.0));
        }
        Indicator<Num> predictor = new MockIndicator(series, values);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 7,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));

        // A single bin holds every sample: no predictor uncertainty, so raw MI
        // is zero despite the labels alternating with the sign.
        assertEquals(1, result.effectiveBinCount());
        assertNumEquals(numFactory.numOf(0), result.mutualInformationNats(), 1.0e-9);
    }

    @Test
    public void skewedPredictorUsesMoreEffectiveBinsWithEqualFrequency() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 0, 0, 0, 0, 0, 0, 0, 0, 5, 100);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult equalFrequency = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_FREQUENCY));
        EventMutualInformationResult equalWidth = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_WIDTH));

        // Eight tied zeros collapse into one equal-frequency bin; the two
        // remaining samples are then sized one per remaining bin (1, 1), so
        // each distinct value gets its own bin; equal-width keeps the
        // requested four bins but concentrates samples in the first.
        assertEquals(3, equalFrequency.effectiveBinCount());
        assertEquals(4, equalWidth.effectiveBinCount());
        assertFalse(equalFrequency.mutualInformationNats().isNaN());
        assertFalse(equalFrequency.mutualInformationNats().isNegative());
        assertFalse(equalWidth.mutualInformationNats().isNaN());
        assertFalse(equalWidth.mutualInformationNats().isNegative());
    }

    @Test
    public void equalWidthBinsHonorRequestedCountBeyondSampleCount() {
        BarSeries series = series(3);
        Indicator<Num> predictor = indicator(series, 0.0, 0.1, 1.0);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index == 0);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 2,
                new EventMutualInformationConfig(0, 0, 10, BinningStrategy.EQUAL_WIDTH));

        // Boundaries come from the requested 10 bins (width span / 10), so the
        // samples land in bins 0, 1 and 9; the populated extent spans all ten,
        // even though only three samples exist.
        assertEquals(10, result.requestedBinCount());
        assertEquals(10, result.effectiveBinCount());
        assertFalse(result.mutualInformationNats().isNaN());
    }

    @Test
    public void constantPredictorWithEqualWidthFormsOneBin() {
        BarSeries series = series(10);
        Indicator<Num> predictor = indicator(series, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        // A zero-span predictor would previously divide 0/0 into a NaN bin
        // width and throw inside intValue(); it must instead produce one
        // effective bin and zero mutual information.
        EventMutualInformationResult result = evaluate(predictor, target, 0, 9,
                new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_WIDTH));

        assertEquals(4, result.requestedBinCount());
        assertEquals(1, result.effectiveBinCount());
        assertNumEquals(numFactory.numOf(0), result.mutualInformationNats(), 1.0e-9);
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
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

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
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 3 == 0);
        Indicator<Boolean> unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        // startIndex below the target's unstable boundary.
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, unstableTarget, 0, 19, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
        // endIndex beyond the series end.
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, 0, 20, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
        // The partition cannot hold a single complete target window.
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, 10, 10, new EventMutualInformationConfig(0, 3, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
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
        Indicator<Boolean> unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        EventMutualInformationResult result = evaluate(predictor, unstableTarget, 2, 19,
                new EventMutualInformationConfig(3, 3, 2, BinningStrategy.EQUAL_WIDTH,
                        AnalysisContext.MissingHistoryPolicy.STRICT));

        assertEquals(15, result.sampleCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(!result.mutualInformationNats().isNegative());

        // Samples below index 2 would read unstable target indexes: STRICT
        // rejects them.
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, unstableTarget, 1, 19, new EventMutualInformationConfig(3, 3, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
    }

    @Test
    public void clampPolicyIntersectsTheAvailableRange() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        Indicator<Boolean> unstableTarget = eventSignal(series, 5, index -> index % 3 == 0);

        EventMutualInformationResult clamped = evaluate(predictor, unstableTarget, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH,
                        AnalysisContext.MissingHistoryPolicy.CLAMP));
        assertEquals(15, clamped.sampleCount());
        assertFalse(clamped.mutualInformationNats().isNaN());
        assertTrue(!clamped.mutualInformationNats().isNegative());

        // A range that clamps to empty yields an undefined result, not an error.
        EventMutualInformationResult empty = evaluate(predictor, unstableTarget, 0, 2, new EventMutualInformationConfig(
                0, 5, 2, BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.CLAMP));
        assertEquals(0, empty.sampleCount());
        assertTrue(empty.mutualInformationNats().isNaN());
        assertTrue(empty.positiveTargetRate().isNaN());
    }

    @Test
    public void clampPolicyTreatsPredictorWarmUpAsRelativeToTheSeriesBeginIndex() {
        BarSeries series = series(20);
        // Dropping the head makes the retained begin index 10.
        series.setMaximumBarCount(10);
        // NaN until the 5-bar window completes: stable only from
        // beginIndex + 5 = 15 onward, like a DTW-style indicator.
        List<Num> values = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            values.add(NaN.NaN);
        }
        for (int i = 15; i < 20; i++) {
            values.add(numFactory.numOf(i));
        }
        Indicator<Num> predictor = new MockIndicator(series, 5, values);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        EventMutualInformationResult clamped = evaluate(predictor, target, 10, 19, new EventMutualInformationConfig(0,
                0, 2, BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.CLAMP));

        // Samples 10..14 would read NaN; only 15..19 are usable, so the
        // evaluation starts at the begin-index-relative warm-up boundary.
        assertEquals(5, clamped.sampleCount());
        assertFalse(clamped.mutualInformationNats().isNaN());
        assertTrue(!clamped.mutualInformationNats().isNegative());

        // STRICT shares the same boundary: a partition starting below it is
        // rejected.
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, 12, 19, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
    }

    @Test
    public void clampPolicyTreatsTargetWarmUpAsRelativeToTheSeriesBeginIndex() {
        BarSeries series = series(20);
        // Dropping the head makes the retained begin index 10.
        series.setMaximumBarCount(10);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        // The target is unstable below beginIndex + 2 = 12.
        Indicator<Boolean> unstableTarget = eventSignal(series, 2, index -> index % 2 == 0);

        EventMutualInformationResult clamped = evaluate(predictor, unstableTarget, 10, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH,
                        AnalysisContext.MissingHistoryPolicy.CLAMP));

        // Samples 10 and 11 read target indexes below its stable boundary and
        // must be clamped away; the evaluation covers 12..19.
        assertEquals(8, clamped.sampleCount());
        assertFalse(clamped.mutualInformationNats().isNaN());
        assertTrue(!clamped.mutualInformationNats().isNegative());
    }

    @Test
    public void equalWidthBinningWithOverflowingSpanFormsEndpointBins() {
        // maximum - minimum overflows to infinity for finite samples on
        // opposite ends of the double range; the overflow-safe affine
        // positions must still separate the extremes into their endpoint
        // bins instead of reporting the evaluation undefined. DoubleNum
        // exercises the scaled binning path; DecimalNum keeps the span
        // finite and lands the same bins through the ordinary path.
        BarSeries series = series(2);
        Indicator<Num> predictor = indicator(series, -Double.MAX_VALUE, Double.MAX_VALUE);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index == 1);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 1,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertEquals(2, result.sampleCount());
        assertEquals(1, result.positiveTargetCount());
        assertEquals(2, result.effectiveBinCount());
        // One sample per endpoint bin with opposite labels: the bins explain
        // the target completely, so MI is the target entropy ln(2).
        assertNumEquals(numFactory.numOf(Math.log(2)), result.mutualInformationNats(), 1.0e-12);
    }

    @Test
    public void equalWidthBinningSurvivesUnderflowedBinWidth() {
        // A positive subnormal span (1e-320) combined with a million bins
        // underflows the bin width to zero in double arithmetic; the bin
        // positions must fall back to an underflow-safe ratio form instead of
        // dividing by zero and throwing. Decimal arithmetic keeps the width
        // representable and lands the same bins.
        BarSeries series = series(3);
        Indicator<Num> predictor = indicator(series, 0, 5e-321, 1e-320);
        Indicator<Boolean> target = eventSignal(series, 0, new boolean[] { true, false, true });

        EventMutualInformationResult result = evaluate(predictor, target, 0, 2,
                new EventMutualInformationConfig(0, 0, 1_000_000, BinningStrategy.EQUAL_WIDTH));

        assertEquals(3, result.sampleCount());
        assertEquals(1_000_000, result.effectiveBinCount());
        assertFalse(result.mutualInformationNats().isNaN());
        assertTrue(result.mutualInformationNats().isPositive());
    }

    @Test
    public void equalWidthBinningSurvivesRoundedSubnormalBinWidth() {
        // A subnormal span whose division by the bin count rounds to a wrong
        // nonzero subnormal (5 * Double.MIN_VALUE / 4 rounds from
        // 1.25 * Double.MIN_VALUE to Double.MIN_VALUE) must not shift samples
        // between bins: the same values scaled to the normal range land in
        // bins [0, 1, 2, 3] and perfectly determine the target, so the
        // subnormal-scale evaluation must report the same mutual information
        // and normalized MI of exactly 1.
        boolean[] labels = { false, false, true, false };
        EventMutualInformationConfig config = new EventMutualInformationConfig(0, 0, 4, BinningStrategy.EQUAL_WIDTH);
        BarSeries subnormalSeries = series(4);
        Indicator<Num> subnormalPredictor = indicator(subnormalSeries, 0, 2 * Double.MIN_VALUE, 3 * Double.MIN_VALUE,
                5 * Double.MIN_VALUE);
        Indicator<Boolean> subnormalTarget = eventSignal(subnormalSeries, 0, labels);
        BarSeries scaledSeries = series(4);
        Indicator<Num> scaledPredictor = indicator(scaledSeries, 0, 2e300, 3e300, 5e300);
        Indicator<Boolean> scaledTarget = eventSignal(scaledSeries, 0, labels);

        EventMutualInformationResult subnormal = evaluate(subnormalPredictor, subnormalTarget, 0, 3, config);
        EventMutualInformationResult scaled = evaluate(scaledPredictor, scaledTarget, 0, 3, config);

        assertEquals(4, subnormal.effectiveBinCount());
        assertEquals(4, scaled.effectiveBinCount());
        assertNumEquals(scaled.mutualInformationNats(), subnormal.mutualInformationNats(), 1.0e-9);
        assertNumEquals(numFactory.one(), subnormal.normalizedMutualInformation(), 1.0e-9);
    }

    @Test
    public void unstableBoundaryAboveIntRangeStaysUnavailable() {
        // beginIndex + Integer.MAX_VALUE pushes the first stable index past the
        // int range, so no representable index is stable: STRICT must reject
        // the range and CLAMP must report an empty, undefined evaluation
        // instead of silently admitting Integer.MAX_VALUE.
        BarSeries series = series(20);
        // Dropping the head makes the retained begin index 10.
        series.setMaximumBarCount(10);
        List<Num> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            values.add(numFactory.numOf(0));
        }
        Indicator<Num> predictor = new MockIndicator(series, Integer.MAX_VALUE, values);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 2 == 0);

        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, 10, 19, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));

        EventMutualInformationResult clamped = evaluate(predictor, target, 10, 19, new EventMutualInformationConfig(0,
                0, 2, BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.CLAMP));
        assertEquals(0, clamped.sampleCount());
        assertTrue(clamped.mutualInformationNats().isNaN());
    }

    @Test
    public void proxySeriesEndingAtIntegerMaxValueReturnsUndefinedResult() {
        // A series whose end index is Integer.MAX_VALUE makes the covered
        // target-window span exceed Integer.MAX_VALUE (unrepresentable in
        // memory): the evaluation must be undefined and must terminate instead
        // of wrapping the sample or prefix loops past the int range.
        BaseBarSeries built = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(new double[] { 1, 2, 3 })
                .build();
        BaseBarSeries proxy = new BaseBarSeries(built.getName(), built.getBarData()) {
            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE;
            }
        };
        List<Num> predictorValues = new ArrayList<>();
        predictorValues.add(numFactory.numOf(1));
        Indicator<Num> predictor = new MockIndicator(proxy, predictorValues);
        Indicator<Boolean> target = eventSignal(proxy, 0, index -> false);

        EventMutualInformationResult result = evaluate(predictor, target, 0, Integer.MAX_VALUE,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        assertEquals(0, result.sampleCount());
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void clampPolicyReturnsEmptyUndefinedForDisjointNegativeExtremeRange() {
        BarSeries series = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 3 == 0);

        // A requested partition entirely before the series (negative int
        // extreme) must never wrap the maxSampleIndex subtraction and score
        // available history: the result is empty and undefined.
        EventMutualInformationResult result = evaluate(predictor, target, Integer.MIN_VALUE, Integer.MIN_VALUE,
                new EventMutualInformationConfig(0, 1, 2, BinningStrategy.EQUAL_WIDTH,
                        AnalysisContext.MissingHistoryPolicy.CLAMP));

        assertEquals(0, result.sampleCount());
        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        assertTrue(result.positiveTargetRate().isNaN());
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void rejectsInvalidConfigurationAndSeries() {
        BarSeries series = series(20);
        BarSeries otherSeries = series(20);
        Indicator<Num> predictor = indicator(series, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index % 3 == 0);

        assertThrows(NullPointerException.class, () -> evaluate(null, target, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
        assertThrows(NullPointerException.class, () -> evaluate(predictor, null, 0, 19,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
        assertThrows(NullPointerException.class, () -> evaluate(predictor, target, 0, 19, null));
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, eventSignal(otherSeries, 0, index -> false), 0, 19,
                        new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH)));
    }

    @Test
    public void proxySeriesWithSpanExactlyIntegerMaxValueReturnsUndefinedResult() {
        // A covered target-window span of exactly Integer.MAX_VALUE passes an
        // `> Integer.MAX_VALUE` check and then fails with "Requested array size
        // exceeds VM limit" (the JVM's usable int[] ceiling is a few words
        // below MAX_VALUE): the evaluation must be undefined instead of
        // attempting the allocation.
        BaseBarSeries built = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(new double[] { 1, 2, 3 })
                .build();
        BaseBarSeries proxy = new BaseBarSeries(built.getName(), built.getBarData()) {
            @Override
            public int getEndIndex() {
                return Integer.MAX_VALUE - 2;
            }
        };
        List<Num> predictorValues = new ArrayList<>();
        predictorValues.add(numFactory.numOf(1));
        Indicator<Num> predictor = new MockIndicator(proxy, predictorValues);
        Indicator<Boolean> target = eventSignal(proxy, 0, index -> false);

        EventMutualInformationResult result = evaluate(predictor, target, 0, Integer.MAX_VALUE - 2,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        assertEquals(0, result.sampleCount());
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void floatBackedDeterministicContingencyRoundsToFloatBound() {
        // Review regression: a deterministic contingency table computed in
        // single precision reports normalized MI one ULP above 1
        // (1.0000001192092896 for 10 equal-frequency bins over 16 samples
        // whose first half carries the event); the result constructor
        // previously compared it against a double-sized 1e-12 bound and
        // rejected the evaluation. The bounds must scale with the metric's
        // own precision (FloatNumFactory epsilon is 1e-5).
        NumFactory floatFactory = FloatNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatFactory)
                .withData(new double[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 })
                .build();
        List<Num> values = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            values.add(floatFactory.numOf(i));
        }
        Indicator<Num> predictor = new MockIndicator(series, values);
        Indicator<Boolean> target = eventSignal(series, 0, index -> index < 8);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 15,
                new EventMutualInformationConfig(0, 0, 10, BinningStrategy.EQUAL_FREQUENCY));

        Num normalized = result.normalizedMutualInformation();
        assertFalse(normalized.isNaN());
        // The float ratio legitimately rounds one ULP above the mathematical
        // bound of a perfect deterministic table.
        assertTrue(normalized.isGreaterThan(floatFactory.one()));
        assertTrue(normalized.minus(floatFactory.one()).isLessThanOrEqual(floatFactory.numOf(1.0e-5)));
    }

    @Test
    public void proxySeriesWithImpracticalSpanReturnsUndefinedResult() {
        // A covered target-window span above the practical in-memory bound
        // (10,000,002 ints here, ~40 MB) must be undefined and must terminate
        // without allocating the prefix array or walking the range.
        BaseBarSeries built = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(new double[] { 1, 2, 3 })
                .build();
        BaseBarSeries proxy = new BaseBarSeries(built.getName(), built.getBarData()) {
            @Override
            public int getEndIndex() {
                return 10_000_000 + 2;
            }
        };
        List<Num> predictorValues = new ArrayList<>();
        predictorValues.add(numFactory.numOf(1));
        Indicator<Num> predictor = new MockIndicator(proxy, predictorValues);
        Indicator<Boolean> target = eventSignal(proxy, 0, index -> false);

        EventMutualInformationResult result = evaluate(predictor, target, 0, 10_000_000,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        assertEquals(0, result.sampleCount());
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void emptySeriesNaturalBoundsYieldUndefinedResultUnderClamp() {
        // An empty series has natural bounds (-1, -1), which pass the
        // availability checks and previously crashed reading getValue(-1);
        // the default CLAMP policy must yield the documented undefined result.
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[0]).build();
        Indicator<Num> predictor = new MockIndicator(empty, new ArrayList<>());
        Indicator<Boolean> target = eventSignal(empty, 0, index -> false);

        EventMutualInformationResult result = evaluate(predictor, target, -1, -1,
                new EventMutualInformationConfig(0, 0, 2, BinningStrategy.EQUAL_WIDTH));

        assertTrue(result.mutualInformationNats().isNaN());
        assertTrue(result.targetEntropyNats().isNaN());
        assertTrue(result.normalizedMutualInformation().isNaN());
        assertTrue(result.positiveTargetRate().isNaN());
        assertEquals(0, result.sampleCount());
        assertEquals(0, result.effectiveBinCount());
    }

    @Test
    public void emptySeriesStillRejectsEveryRangeUnderStrict() {
        // STRICT keeps rejecting any requested range on an empty series: it
        // cannot hold a complete target window, so the natural bounds throw
        // like an explicit range instead of reading getValue(-1).
        BarSeries empty = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[0]).build();
        Indicator<Num> predictor = new MockIndicator(empty, new ArrayList<>());
        Indicator<Boolean> target = eventSignal(empty, 0, index -> false);

        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, -1, -1, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
        assertThrows(IllegalArgumentException.class,
                () -> evaluate(predictor, target, 0, 0, new EventMutualInformationConfig(0, 0, 2,
                        BinningStrategy.EQUAL_WIDTH, AnalysisContext.MissingHistoryPolicy.STRICT)));
    }

    private EventMutualInformationResult evaluate(Indicator<Num> predictor, Indicator<Boolean> target, int startIndex,
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

    private static Indicator<Boolean> eventSignal(BarSeries series, int unstableBars,
            java.util.function.IntPredicate predicate) {
        return new CachedIndicator<Boolean>(series) {
            @Override
            protected Boolean calculate(int index) {
                return predicate.test(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return unstableBars;
            }
        };
    }

    private static Indicator<Boolean> eventSignal(BarSeries series, int unstableBars, boolean[] events) {
        return eventSignal(series, unstableBars, index -> index < events.length && events[index]);
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
