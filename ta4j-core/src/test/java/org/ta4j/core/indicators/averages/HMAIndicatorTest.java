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

public class HMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public HMAIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
                        82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87)
                .build();
    }

    @Test
    public void hmaUsingBarCount9UsingClosePrice() {
        // Example from
        // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        var hma = new HMAIndicator(new ClosePriceIndicator(data), 9);
        assertNumEquals(86.3204, hma.getValue(10));
        assertNumEquals(85.3705, hma.getValue(11));
        assertNumEquals(84.1044, hma.getValue(12));
        assertNumEquals(83.0197, hma.getValue(13));
        assertNumEquals(81.3913, hma.getValue(14));
        assertNumEquals(79.6511, hma.getValue(15));
        assertNumEquals(78.0443, hma.getValue(16));
        assertNumEquals(76.8832, hma.getValue(17));
        assertNumEquals(75.5363, hma.getValue(18));
        assertNumEquals(75.1713, hma.getValue(19));
        assertNumEquals(75.3597, hma.getValue(20));
    }

}
