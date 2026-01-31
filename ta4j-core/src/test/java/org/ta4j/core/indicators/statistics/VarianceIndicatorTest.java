/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VarianceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public VarianceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9).build();
    }

    @Test
    public void varianceUsingBarCount4UsingClosePrice() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 4);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.25, variance.getValue(1));
        assertNumEquals(2.0 / 3, variance.getValue(2));
        assertNumEquals(1.25, variance.getValue(3));
        assertNumEquals(0.5, variance.getValue(4));
        assertNumEquals(0.25, variance.getValue(5));
        assertNumEquals(0.5, variance.getValue(6));
        assertNumEquals(0.5, variance.getValue(7));
        assertNumEquals(0.5, variance.getValue(8));
        assertNumEquals(3.5, variance.getValue(9));
        assertNumEquals(10.5, variance.getValue(10));
    }

    @Test
    public void firstValueShouldBeZero() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 4);
        assertNumEquals(0, variance.getValue(0));
    }

    @Test
    public void varianceShouldBeZeroWhenBarCountIs1() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, variance.getValue(3));
        assertNumEquals(0, variance.getValue(8));
    }

    @Test
    public void varianceUsingBarCount2UsingClosePrice() {
        var variance = new VarianceIndicator(new ClosePriceIndicator(data), 2);

        assertNumEquals(0, variance.getValue(0));
        assertNumEquals(0.25, variance.getValue(1));
        assertNumEquals(0.25, variance.getValue(2));
        assertNumEquals(0.25, variance.getValue(3));
        assertNumEquals(2.25, variance.getValue(9));
        assertNumEquals(20.25, variance.getValue(10));
    }
}
