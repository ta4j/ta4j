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

public class MeanDeviationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public MeanDeviationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9).build();
    }

    @Test
    public void meanDeviationUsingBarCount5UsingClosePrice() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(2.44444444444444, meanDeviation.getValue(2));
        assertNumEquals(2.5, meanDeviation.getValue(3));
        assertNumEquals(2.16, meanDeviation.getValue(7));
        assertNumEquals(2.32, meanDeviation.getValue(8));
        assertNumEquals(2.72, meanDeviation.getValue(9));
    }

    @Test
    public void firstValueShouldBeZero() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0, meanDeviation.getValue(0));
    }

    @Test
    public void meanDeviationShouldBeZeroWhenBarCountIs1() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, meanDeviation.getValue(2));
        assertNumEquals(0, meanDeviation.getValue(7));
    }
}
