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
        // The complement (1 - decay) must be derived from the raw decay
        // factor, mirroring EWMAIndicator: at DecimalNum precision 1 both
        // 0.9999 and its complement round to 1 and 0.0001 respectively, and
        // deriving the complement as one().minus(decay) collapses it to zero.
        NumFactory rounded = DecimalNumFactory.getInstance(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(rounded).withData(1, 2).build();
        EwmaVarianceIndicator roundedWeights = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, rounded.numOf(1), rounded.numOf(2)), 1, 0.9999);

        // sigma^2_1 = 0.9999 * 0 + 0.0001 * (2 - 1)^2 = 0.0001.
        assertNumEquals(rounded.numOf(0.0001), roundedWeights.getValue(1));
    }

    @Test
    public void reseedsOnlyFromFiniteSeedVariance() {
        // A seed window whose own population variance overflows (DoubleNum)
        // must publish NaN instead of a non-finite seed.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(-100, -100, -100, 2e154, 2e154)
                .build();
        EwmaVarianceIndicator extreme = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(-100), numOf(-100), numOf(-100), numOf(2e154), numOf(2e154)), 3,
                0.5);

        Num reseeded = extreme.getValue(4);
        if (numFactory instanceof DoubleNumFactory) {
            assertTrue(reseeded.isNaN());
        } else {
            assertTrue(Num.isFinite(reseeded));
        }
    }

    @Test
    public void reanchorsAfterRetainedHeadPrunes() {
        // Retained-head pruning invalidates the caches and rebuilds the
        // control mean: values computed against the discarded prefix must not
        // survive.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        EwmaVarianceIndicator pruned = new EwmaVarianceIndicator(
                new MockIndicator(series, 0, numOf(1), numOf(2), numOf(3), numOf(4), numOf(5)), 2, 0.5);

        // Fill the caches across the whole series.
        pruned.getValue(4);

        series.setMaximumBarCount(2);

        // beginIndex = 2. The new head is inside the warm-up window: NaN.
        assertTrue(pruned.getValue(2).isNaN());
        // index 3 reseeds from the rolling variance of [3, 4] = 0.25.
        assertNumEquals(0.25, pruned.getValue(3));
        // index 4: rebuilt control mean is 4 (EMA resets to the current value
        // after a NaN), so sigma^2_4 = 0.5 * 0.25 + 0.5 * (5 - 4)^2 = 0.625.
        // A stale cached mean (3.125) would yield 1.8828125 instead.
        assertNumEquals(0.625, pruned.getValue(4));
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new EwmaVarianceIndicator(close, 8, 0.94), stableIndexes(series)));
    }
}
