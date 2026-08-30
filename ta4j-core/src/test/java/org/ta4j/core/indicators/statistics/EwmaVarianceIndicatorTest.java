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

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        return List.of(serializationFixture(series, new EwmaVarianceIndicator(close, 8, 0.94), stableIndexes(series)));
    }
}
