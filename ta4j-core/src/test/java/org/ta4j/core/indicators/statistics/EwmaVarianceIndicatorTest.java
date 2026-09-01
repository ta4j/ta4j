/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EwmaVarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;
    private EwmaVarianceIndicator ewmaVariance;

    public EwmaVarianceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        ewmaVariance = new EwmaVarianceIndicator(new MockIndicator(data, 0, numOf(1), numOf(2), numOf(3), numOf(4)), 3,
                0.5);
    }

    @Test
    public void seedsWithRollingPopulationVarianceAfterWarmUp() {
        assertTrue(ewmaVariance.getValue(0).isNaN());
        assertTrue(ewmaVariance.getValue(1).isNaN());
        // Population variance of [1, 2, 3] = 2/3.
        assertNumEquals(2.0 / 3.0, ewmaVariance.getValue(2));
    }

    @Test
    public void appliesDecayAfterSeed() {
        // sigma^2_3 = 0.5 * (2/3) + 0.5 * (4 - 2)^2 = 7/3.
        assertNumEquals(7.0 / 3.0, ewmaVariance.getValue(3));
    }

    @Test
    public void rejectsBarCountThatOverflowsUnstableCount() {
        // getCountOfUnstableBars() adds barCount - 1 to the source's unstable-bar
        // count in int arithmetic; reject combinations that would overflow it.
        Indicator<Num> unstable = new MockIndicator(data, 2, numOf(1), numOf(2), numOf(3));

        assertThrows(IllegalArgumentException.class,
                () -> new EwmaVarianceIndicator(unstable, Integer.MAX_VALUE, 0.94));
    }

    @Test
    public void rejectsDecayFactorOutsideOpenUnitInterval() {
        ClosePriceIndicator close = new ClosePriceIndicator(data);

        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(close, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(close, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(close, 3, Double.NaN));
    }

    @Test
    public void nonFiniteBarReseedsOnceGapLeavesSeedWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6, 7).build();
        EwmaVarianceIndicator gapped = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3), NaN.NaN, numOf(4), numOf(5), numOf(6)), 3,
                0.5);

        assertTrue(gapped.getValue(3).isNaN());
        assertTrue(gapped.getValue(4).isNaN());
        // The gap has left the seed window: population variance of [4, 5, 6] = 2/3.
        assertNumEquals(2.0 / 3.0, gapped.getValue(6));
    }

    @Test
    public void propagatesSourceUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        EwmaVarianceIndicator unstable = new EwmaVarianceIndicator(
                new MockIndicator(series, 2, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5)), 3, 0.5);

        assertEquals(4, unstable.getCountOfUnstableBars());
        assertTrue(unstable.getValue(3).isNaN());
    }

    @Test
    public void supportsControlLimitComposition() {
        Num controlLimit = NumericIndicator.of(ewmaVariance).sqrt().multipliedBy(2).getValue(3);

        assertNumEquals(Math.sqrt(7.0 / 3.0) * 2, controlLimit);
    }

    @Test
    public void rejectsInvalidParameters() {
        MockIndicator source = new MockIndicator(data, 0, numOf(1), numOf(2), numOf(3), numOf(4));

        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(source, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(source, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(source, 3, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new EwmaVarianceIndicator(source, 3, Double.NaN));
    }

    @Test
    public void extremeRegimeChangeYieldsNaNInsteadOfInfinity() {
        // The jump to 2e154 squares a deviation of ~2e154: DoubleNum overflows
        // the squared deviation to infinity and must yield NaN (reseeding on
        // the next finite bar); DecimalNum carries the value exactly.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-100, -100, -100, 2e154)
                .build();
        EwmaVarianceIndicator extreme = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(-100), numOf(-100), numOf(-100), numOf(2e154)), 3, 0.5);

        Num variance = extreme.getValue(3);
        assertTrue(variance.isNaN() || Num.isFinite(variance));
    }

    @Test
    public void nonFiniteFirstBarDoesNotSeedZeroVariance() {
        // With barCount = 1 the seed window is the first bar alone: a
        // non-finite anchor must not publish a stable zero variance.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1).build();
        EwmaVarianceIndicator singleBar = new EwmaVarianceIndicator(new MockIndicator(series, 0, NaN.NaN), 1, 0.5);

        assertTrue(singleBar.getValue(0).isNaN());
    }

    @Test
    public void seedsVarianceConsistentlyWithRoundedWeights() {
        // The complement is derived as the exact BigDecimal difference
        // 1 - decay and the decay is derived from it, mirroring
        // CusumIndicator's scale-decay conversion: at DecimalNum precision 1
        // both 0.9999 and its complement round to 1 and 0.0001 respectively,
        // and deriving the complement as one().minus(decay) collapses it to
        // zero.
        NumFactory rounded = DecimalNumFactory.getInstance(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(rounded).withData(1, 2).build();
        EwmaVarianceIndicator roundedWeights = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, rounded.numOf(1), rounded.numOf(2)), 1, 0.9999);

        // sigma^2_1 = 0.9999 * 0 + 0.0001 * (2 - 1)^2 = 0.0001.
        assertNumEquals(rounded.numOf(0.0001), roundedWeights.getValue(1));
    }

    @Test
    public void reseedsOnlyFromFiniteSeedVariance() {
        // A non-finite bar collapses both legs of the recursion: the EWMA
        // mean goes NaN and so does the variance. The indicator then falls
        // back to the rolling window variance seed, and a non-finite seed
        // (the window still spans the NaN bar) must publish NaN instead of
        // leaking a non-finite or stale value.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1).build();
        EwmaVarianceIndicator collapsed = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(1), NaN.NaN, numOf(1), numOf(1)), 3, 0.5);

        Num reseeded = collapsed.getValue(4);
        assertTrue(reseeded.isNaN());
    }

    @Test
    public void seedsInitialWindowWithoutSquaringOverflow() {
        // The seed window [0, 2e154, 0] has population variance 8e308/9,
        // which is representable, but the naive sum-of-squares seeding first
        // squares 2e154 to 4e308 and overflows DoubleNum to a non-finite
        // seed. The compensated window mean with per-term scaled squared
        // deviations must publish the finite population variance instead.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 2e154, 0).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(2e154), numOf(0)), 3, 0.5);

        Num value = variance.getValue(2);

        assertTrue(Num.isFinite(value));
        assertTrue(value.isPositive());
        assertNumEquals(numOf(8.888888888888889e307), value, 8.888888888888889e307 * 1e-9);
    }

    @Test
    public void meanAndVarianceShareTheExactDecimalComplement() {
        // EWMAIndicator derives its complement in primitive double, so at a
        // decay of Math.nextDown(1d) its mean weight (~1.110223e-16) differs
        // from the exact decimal complement 1e-16 the variance leg applies;
        // the shared mean recursion must apply the identical weight so both
        // legs of the estimator stay consistent under a near-one decay.
        NumFactory decimal = DecimalNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(decimal).withData(1, 2).build();
        EwmaVarianceIndicator shared = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, decimal.numOf(1), decimal.numOf(2)), 1, Math.nextDown(1d));

        // mean_1 = 1 + 1e-16 * (2 - 1); sigma^2_1 = 1e-16 * (2 - 1)^2.
        assertNumEquals("1.0000000000000001", shared.getMeanIndicator().getValue(1));
        assertNumEquals("1E-16", shared.getValue(1));
    }

    @Test
    public void seedWindowOfMaximumBarsPublishesFiniteZeroVariance() {
        // Three Double.MAX_VALUE bars: each scaled quotient is finite, but
        // the naive window sum rounds the mean off MAX and the resulting
        // deviations square to a non-finite seed even though the true
        // population variance is zero. The compensated mean with its
        // max-absolute re-scaling recovers the exact mean, so the seed
        // publishes the finite zero variance and the shared mean reproduces
        // the bar value exactly (asserted as Num equality, since the
        // DecimalNum representation of Double.MAX_VALUE exceeds the double
        // range and its doubleValue() would overflow).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
                .build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(new MockIndicator(series, 0, numOf(Double.MAX_VALUE),
                numOf(Double.MAX_VALUE), numOf(Double.MAX_VALUE), numOf(Double.MAX_VALUE)), 3, 0.5);

        Num seed = variance.getValue(2);
        assertTrue(Num.isFinite(seed));
        assertNumEquals(numOf(0), seed, 0);
        assertTrue(Num.isFinite(variance.getValue(3)));
        assertNumEquals(numOf(0), variance.getValue(3), 0);
        assertNumEquals(numOf(Double.MAX_VALUE), variance.getMeanIndicator().getValue(2));
    }

    @Test
    public void seedWindowOfSubnormalBarsPublishesPopulationVarianceInsteadOfZero() {
        // The seed window [0, 2^-536] has population variance 2^-1074
        // (Double.MIN_VALUE), but per-term division halves each deviation to
        // 2^-538 and the product rounds to zero, collapsing the seed. The
        // shared-scale accumulation keeps every contribution representable and
        // publishes the subnormal population variance instead.
        double subnormalBar = Math.scalb(1.0, -536);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, subnormalBar).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(subnormalBar)), 2, 0.5);

        Num value = variance.getValue(1);

        assertTrue(Num.isFinite(value));
        assertTrue(value.isPositive());
        assertNumEquals(numOf(Double.MIN_VALUE), value, 0);
    }

    @Test
    public void seedWindowOfLargeCenteredBarsPublishesFiniteVarianceInsteadOfOverflowing() {
        // The seed window [0, 2.8e154, 0] has population variance 2d^2/3 with
        // d = 2.8e154/3 (about 1.74e308), but squaring the largest centered
        // deviation (2d/3, about 1.87e154) overflows DoubleNum before the
        // window average can shrink it back. Multiplying the normalized sum
        // between the two scale factors keeps every intermediate finite and
        // publishes the representable variance.
        double largeBar = 2.8e154;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, largeBar, 0).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(largeBar), numOf(0)), 3, 0.5);

        Num value = variance.getValue(2);

        assertTrue(Num.isFinite(value));
        assertTrue(value.isPositive());
        double expected = 1.7422222222222222e308;
        assertTrue(Math.abs(value.doubleValue() - expected) / expected < 1e-9);
    }

    @Test
    public void oppositeSignExtremesKeepPublishedMeanFinite() {
        // Consecutive finite bars of opposite extreme signs overflow their
        // raw difference under DoubleNum (1e308 - (-1e308) = infinity), but
        // the EWMA update is a convex combination of the two finite values:
        // weighting each operand before combining keeps the second mean at
        // 0.9 * (-1e308) + 0.1 * 1e308 = -8e307 instead of infinity.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(-1e308, 1e308).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(-1e308), numOf(1e308)), 1, 0.9);

        Num mean = variance.getMeanIndicator().getValue(1);

        assertTrue(Num.isFinite(mean));
        assertTrue(Math.abs(mean.doubleValue() - -8e307) / 8e307 < 1e-9);
    }

    @Test
    public void sharedMeanKeepsConstantSubnormalSource() {
        // A constant Double.MIN_VALUE source must keep the shared mean at
        // MIN_VALUE: at decay 0.5 both convex operands (previousMean * decay
        // and current * (1 - decay)) round to zero although their exact sum is
        // the representable MIN_VALUE.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
                .build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(Double.MIN_VALUE), numOf(Double.MIN_VALUE), numOf(Double.MIN_VALUE)),
                1, 0.5);

        assertNumEquals(numOf(Double.MIN_VALUE), variance.getMeanIndicator().getValue(2));
    }

    @Test
    public void subnormalVarianceSumCombinesBeforeRounding() {
        // barCount 1, decay 0.5, source [0, a, a / 2 + b] with
        // a = sqrt(2 * MIN_VALUE) and b = sqrt(MIN_VALUE): index 1 publishes
        // variance MIN_VALUE and index 2 deviates by essentially b, so the
        // exact update 0.5 * MIN_VALUE + 0.5 * b^2 is MIN_VALUE while each
        // product (previousVariance * 0.5 and b * (b * 0.5)) rounds to zero
        // in double. The exact binary combination must round once.
        double a = Math.sqrt(2 * Double.MIN_VALUE);
        double b = Math.sqrt(Double.MIN_VALUE);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, a, a / 2 + b).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(a), numOf(a / 2 + b)), 1, 0.5);

        Num value = variance.getValue(2);

        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(Double.MIN_VALUE), value);
        } else {
            // DecimalNum keeps both contributions at full precision and never
            // collapses; only positivity and the magnitude bound are
            // meaningful.
            assertTrue(Num.isFinite(value));
            assertTrue(value.isPositive());
            assertTrue(value.isLessThanOrEqual(numOf(2 * Double.MIN_VALUE)));
        }
    }

    @Test
    public void increasingSubnormalSourceKeepsMeanGrowing() {
        // previousMean MIN_VALUE with current 2 * MIN_VALUE at decay 0.5 has
        // exact mean 1.5 * MIN_VALUE, which rounds to 2 * MIN_VALUE: the
        // difference form stalls at MIN_VALUE (the weighted half-min delta
        // underflows) and a double convex fallback returns MIN_VALUE (both
        // products round down), so the published mean never grows. The exact
        // binary combination must round once to 2 * MIN_VALUE.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Double.MIN_VALUE, 2 * Double.MIN_VALUE)
                .build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(Double.MIN_VALUE), numOf(2 * Double.MIN_VALUE)), 1, 0.5);

        Num mean = variance.getMeanIndicator().getValue(1);

        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(2 * Double.MIN_VALUE), mean);
        } else {
            // DecimalNum keeps the exact convex mean 1.5 * MIN_VALUE without
            // ever stalling; only growth and positivity are meaningful.
            assertTrue(Num.isFinite(mean));
            assertTrue(mean.isPositive());
            assertTrue(mean.isGreaterThan(numOf(Double.MIN_VALUE)));
        }
    }

    @Test
    public void nonStallingSubnormalMeanRoundsOnce() {
        // With previous mean MIN_VALUE and current 4 * MIN_VALUE at decay 0.5,
        // the difference form adds fl(1.5 * MIN_VALUE) = 2 * MIN_VALUE to the
        // previous mean and publishes 3 * MIN_VALUE. The exact convex mean is
        // 2.5 * MIN_VALUE, which rounds once to 2 * MIN_VALUE for DoubleNum.
        // Use the exact binary expansion as DecimalNum's source so both
        // factory-specific expected values retain the same mathematical inputs.
        Num minimum = numOf(new BigDecimal(Double.MIN_VALUE));
        Num fourMinimum = minimum.multipliedBy(numOf(4));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(new MockIndicator(series, 0, minimum, fourMinimum),
                1, 0.5);

        Num expected = minimum.multipliedBy(numOf(2.5));

        assertNumEquals(expected, variance.getMeanIndicator().getValue(1));
    }

    @Test
    public void subnormalVarianceRoundsOnceWithNonzeroTerms() {
        // With barCount 1 and decay 0.5, source [-s / 2, s / 2, s] for
        // s = sqrt(2 * MIN_VALUE) has variance MIN_VALUE at index 1 and
        // deviation s at index 2. The exact index-2 update is
        // 0.5 * MIN_VALUE + 0.5 * s^2 = 1.5 * MIN_VALUE, which rounds once
        // to 2 * MIN_VALUE. Separately rounded double products publish
        // MIN_VALUE instead.
        double scale = Math.sqrt(2 * Double.MIN_VALUE);
        Num exactScale = numOf(new BigDecimal(scale));
        Num halfScale = exactScale.multipliedBy(numOf(0.5));
        Num negativeHalfScale = halfScale.multipliedBy(numFactory.minusOne());
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-0.5 * scale, 0.5 * scale, scale)
                .build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, negativeHalfScale, halfScale, exactScale), 1, 0.5);

        Num value = variance.getValue(2);

        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(2 * Double.MIN_VALUE), value);
        } else {
            // DecimalNum preserves a factory-specific 16-digit sequence.
            // Compute it directly from the exact delegates: index 1 narrows
            // 0.5 * (s / 2 - (-s / 2))^2 once, then index 2 narrows the
            // exact weighted sum of that stored state and s^2 once.
            BigDecimal half = new BigDecimal(0.5);
            Num firstDeviation = halfScale.minus(negativeHalfScale);
            Num firstVariance = numFactory.numOf(firstDeviation.bigDecimalValue().pow(2).multiply(half));
            Num expected = numFactory.numOf(firstVariance.bigDecimalValue()
                    .multiply(half)
                    .add(exactScale.bigDecimalValue().pow(2).multiply(half)));

            assertNumEquals(expected, value);
        }
    }

    @Test
    public void decimalOperandsBeyondDoubleRangeCombineExactly() {
        // A precision-1 DecimalNum mean of 1E1000 with current 2E1000 at
        // decay 0.9 stalls the difference form (the 0.1-weighted delta 1E999
        // rounds the sum back to 1E1000) and falls back to the exact convex
        // sum 1.1E1000, which rounds to 1E1000. The operands' doubleValue()
        // is infinite, so the fallback must use the exact decimal expansion
        // instead of the binary one (BigDecimal rejects non-finite
        // conversions).
        NumFactory precisionOne = DecimalNumFactory.getInstance(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionOne).build();
        series.barBuilder().closePrice(precisionOne.numOf(new BigDecimal("1E1000"))).add();
        series.barBuilder().closePrice(precisionOne.numOf(new BigDecimal("2E1000"))).add();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(new MockIndicator(series, 0,
                precisionOne.numOf(new BigDecimal("1E1000")), precisionOne.numOf(new BigDecimal("2E1000"))), 1, 0.9);

        Num mean = variance.getMeanIndicator().getValue(1);

        assertNumEquals(precisionOne.numOf(new BigDecimal("1E1000")), mean);
    }

    @Test
    public void reanchorsAfterRetainedHeadPrunes() {
        // Retained-head pruning invalidates the caches and rebuilds the
        // control mean: values computed against the discarded prefix must not
        // survive. The recursion re-anchors only once the full seed window is
        // available at the retained head: earlier indices stay NaN so no
        // future bar leaks into a historical value.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        EwmaVarianceIndicator pruned = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5)), 2, 0.5);

        // Fill the caches across the whole series.
        pruned.getValue(4);

        series.setMaximumBarCount(3);

        // beginIndex = 2. The full seed window [2, 3] is not available at the
        // new head: NaN.
        assertTrue(pruned.getValue(2).isNaN());
        // index 3 re-seeds from the rolling variance of [2, 3] = 0.25.
        assertNumEquals(0.25, pruned.getValue(3));
        // index 4: the rebuilt control mean at 3 is the configured seed
        // SMA(3, 4) = 3.5, so sigma^2_4 = 0.5 * 0.25 + 0.5 * (5 - 3.5)^2 =
        // 1.25. A stale cached value (2.8359375 from the discarded prefix)
        // would surface otherwise.
        assertNumEquals(1.25, pruned.getValue(4));
    }

    @Test
    public void sharedMeanReadAtRemovedIndexAnchorsAtRetainedHead() {
        // A consumer reading the shared mean at a removed index must receive
        // the first retained bar's seed, not NaN from the warm-up guard
        // evaluating a discarded prefix index. With barCount 1 the retained
        // head is its own seed window.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        EwmaVarianceIndicator pruned = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5)), 1, 0.5);

        pruned.getValue(4);

        series.setMaximumBarCount(3);

        assertNumEquals(numOf(3), pruned.getMeanIndicator().getValue(0));
    }

    @Test
    public void reanchoredMeanUsesConfiguredSeedWindow() {
        // After pruning, the rebuilt control mean keeps the configured
        // barCount so its seed at the re-anchoring index is the SMA of the
        // retained seed window, not a single-window EWMA decayed across it.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8)
                .build();
        EwmaVarianceIndicator pruned = new EwmaVarianceIndicator(new MockIndicator(series, 0, numOf(1), numOf(2),
                numOf(3), numOf(4), numOf(5), numOf(6), numOf(7), numOf(8)), 3, 0.5);

        pruned.getValue(7);

        series.setMaximumBarCount(4);

        // beginIndex = 4; the combined warm-up (2 bars) covers 4 and 5.
        assertTrue(pruned.getValue(4).isNaN());
        assertTrue(pruned.getValue(5).isNaN());
        // index 6 re-seeds from the rolling variance of [4, 6] = [5, 6, 7] =
        // 2/3.
        assertNumEquals(2.0 / 3.0, pruned.getValue(6));
        // index 7: the rebuilt control mean at 6 is the configured SMA seed
        // SMA(5, 6, 7) = 6, so sigma^2_7 = 0.5 * 2/3 + 0.5 * (8 - 6)^2 =
        // 7/3. A window-1 rebuild would decay the mean to 6.25 and yield
        // 1.8645833333... instead.
        assertNumEquals(7.0 / 3.0, pruned.getValue(7));
    }

    @Test
    public void reseedWaitsForSourceWarmUpAfterPrunes() {
        // A source with its own unstable bars must not have unstable values
        // pulled into the re-anchoring window: indices inside the combined
        // warm-up stay NaN, and the first source-stable index re-seeds from
        // the first full window of source-stable values.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        EwmaVarianceIndicator pruned = new EwmaVarianceIndicator(new MockIndicator(series, 2, numOf(1), numOf(2),
                numOf(3), numOf(4), numOf(5), numOf(6), numOf(7), numOf(8), numOf(9), numOf(10)), 3, 0.5);

        pruned.getValue(9);

        series.setMaximumBarCount(5);

        // beginIndex = 5; combined warm-up = 2 + 2 = 4 bars, so 5, 6, 7 and 8
        // publish NaN even though reseedIndex = 7 holds a full window.
        assertTrue(pruned.getValue(5).isNaN());
        assertTrue(pruned.getValue(6).isNaN());
        assertTrue(pruned.getValue(7).isNaN());
        assertTrue(pruned.getValue(8).isNaN());
        // index 9 is the first source-stable index: the full window [7, 9] of
        // stable values [8, 9, 10] seeds 2/3.
        assertNumEquals(2.0 / 3.0, pruned.getValue(9));
    }

    @Test
    public void weightedDeviationKeepsFiniteVarianceNearOverflow() {
        // A deviation of 1e160 has a square of 1e320, which overflows
        // DoubleNum even though the decay-weighted contribution
        // (1 - decay) * deviation^2 with a decay one ulp below one (about
        // 1e304) is representable; scaling one deviation factor by the
        // complement weight before completing the product must keep the
        // variance finite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 1e160).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(new MockIndicator(series, 0, numOf(0), numOf(1e160)),
                1, Math.nextDown(1d));

        Num value = variance.getValue(1);

        assertTrue(Num.isFinite(value));
        if (numFactory instanceof DoubleNumFactory) {
            // Replicate the exact multiplication order for a bit-identical
            // comparison: deviation * (deviation * oneMinusDecay), where
            // oneMinusDecay converts the exact BigDecimal complement
            // BigDecimal.ONE - BigDecimal.valueOf(decayFactor) (1E-16 for a
            // decay one ulp below one).
            assertNumEquals(1e160 * (1e160 * 1e-16), value);
        }
    }

    @Test
    public void recoveryReanchorsAroundRetainedMean() {
        // Three -100 bars warm up; the 2e154 bar then produces a deviation
        // whose square (4e308) overflows DoubleNum and collapses the
        // variance leg to NaN while the EWMA mean stays finite. The next
        // bar re-anchors the variance on the seed window measured around
        // the retained EWMA mean (1e154 at index 4), publishing about
        // 1e308 at index 4 instead of the rolling window variance
        // (non-finite, because the window spans -100 and 2e154). Index 5
        // then continues the normal recursion from the recovered variance:
        // 0.5 * 1e308 + 0.5 * (2e154 - 1.5e154)^2 = 6.25e307.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-100, -100, -100, 2e154, 2e154, 2e154)
                .build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(new MockIndicator(series, 0, numOf(-100),
                numOf(-100), numOf(-100), numOf(2e154), numOf(2e154), numOf(2e154)), 3, 0.5);

        Num recovered = variance.getValue(4);

        assertTrue(Num.isFinite(recovered));
        assertTrue(recovered.isPositive());
        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(1e308), recovered, 1e308 * 1e-9);
        }

        Num continued = variance.getValue(5);

        assertTrue(Num.isFinite(continued));
        assertTrue(continued.isPositive());
        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(6.25e307), continued, 6.25e307 * 1e-9);
        }
    }

    @Test
    public void recoveryScalesTermsBeforeSquaring() {
        // Zero warm-up; the 2e154 bar collapses the variance leg (its
        // deviation squared, 4e308, overflows DoubleNum) while the EWMA
        // mean stays finite (1e154). The following zero bar re-anchors on
        // the seed window [0, 2e154, 0] around the retained mean: each
        // squared deviation is 1e308 and their sum overflows, but the
        // per-term scaled accumulation keeps the averaged population
        // variance 1e308 finite.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0, 0, 2e154, 0).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(0), numOf(0), numOf(0), numOf(2e154), numOf(0)), 3, 0.5);

        Num value = variance.getValue(4);

        assertTrue(Num.isFinite(value));
        assertTrue(value.isPositive());
        if (numFactory instanceof DoubleNumFactory) {
            assertNumEquals(numOf(1e308), value, 1e308 * 1e-9);
        }
    }

    @Test
    public void subnormalMeanDecaysThroughZero() {
        // A mean seeded at Double.MIN_VALUE followed by a zero bar must decay
        // toward zero: the difference-form update stalls at MIN_VALUE when the
        // weighted delta underflows, and the stall check reroutes through the
        // convex combination, which rounds correctly for both factories.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(Double.MIN_VALUE, 0).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(Double.MIN_VALUE), numOf(0)), 1, 0.5);

        Num mean = variance.getMeanIndicator().getValue(1);

        assertNumEquals(numOf(Double.MIN_VALUE).multipliedBy(numOf(0.5)), mean);
    }

    @Test
    public void removedIndexReadAnchorsAtRetainedHead() {
        // Reading a pruned index maps to the synthetic zero evaluation; it must
        // anchor at the retained head instead of returning NaN for the removed
        // index.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3), numOf(4)), 1, 0.94);
        variance.getValue(3);
        series.setMaximumBarCount(1);

        // The anchored read returns the variance of the single retained bar
        // (0) instead of NaN for the removed index.
        assertNumEquals(numOf(0), variance.getValue(0));
    }

    @Test
    public void barSeriesConstructorMonitorsClosePrice() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        EwmaVarianceIndicator fromSeries = new EwmaVarianceIndicator(series, 2, 0.5);
        EwmaVarianceIndicator fromIndicator = new EwmaVarianceIndicator(new ClosePriceIndicator(series), 2, 0.5);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num expected = fromIndicator.getValue(i);
            Num actual = fromSeries.getValue(i);
            if (expected.isNaN()) {
                assertTrue(actual.isNaN());
            } else {
                assertNumEquals(expected, actual);
            }
        }
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new EwmaVarianceIndicator(close, 8, 0.94), stableIndexes(series)));
    }
}
