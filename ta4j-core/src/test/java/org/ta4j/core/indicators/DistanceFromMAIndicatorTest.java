/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DistanceFromMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public DistanceFromMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 15, 20, 18, 17, 18, 15, 12, 10, 8, 5, 2)
                .build();
    }

    @Test
    public void DistanceFromMovingAverageTest() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        DistanceFromMAIndicator distanceFromMAIndicator = new DistanceFromMAIndicator(data, sma);
        assertNumEquals(0.3333, distanceFromMAIndicator.getValue(2));
        assertNumEquals(0.01886792452830182, distanceFromMAIndicator.getValue(5));
        assertNumEquals(-0.1, distanceFromMAIndicator.getValue(6));
    }

    @Test(expected = IllegalArgumentException.class)
    public void DistanceFromIllegalMovingAverage() {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(data);
        new DistanceFromMAIndicator(data, closePriceIndicator);
    }
}
