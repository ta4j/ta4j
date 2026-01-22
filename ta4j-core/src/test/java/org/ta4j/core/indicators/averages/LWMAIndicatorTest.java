/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

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

public class LWMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public LWMAIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83)
                .build();
    }

    @Test
    public void lwmaUsingBarCount5UsingClosePrice() {
        var lwma = new LWMAIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0.0, lwma.getValue(0));
        assertNumEquals(0.0, lwma.getValue(1));
        assertNumEquals(0.0, lwma.getValue(2));
        assertNumEquals(0.0, lwma.getValue(3));
        assertNumEquals(36.0506, lwma.getValue(4));
        assertNumEquals(35.9673, lwma.getValue(5));
        assertNumEquals(36.0766, lwma.getValue(6));
        assertNumEquals(36.6253, lwma.getValue(7));
        assertNumEquals(37.1833, lwma.getValue(8));
        assertNumEquals(37.5240, lwma.getValue(9));
        assertNumEquals(37.4060, lwma.getValue(10));
    }
}
