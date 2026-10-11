/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public WMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculate() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> wmaIndicator = new WMAIndicator(close, 3);

        int unstableBars = wmaIndicator.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(wmaIndicator.getValue(i))).isTrue();
        }
        assertNumEquals(2.3333, wmaIndicator.getValue(2));
        assertNumEquals(3.3333, wmaIndicator.getValue(3));
        assertNumEquals(4.3333, wmaIndicator.getValue(4));
        assertNumEquals(5.3333, wmaIndicator.getValue(5));
    }

    @Test
    public void wmaWithBarCountGreaterThanSeriesSize() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d, 6d).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> wmaIndicator = new WMAIndicator(close, 55);

        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(wmaIndicator.getValue(i))).isTrue();
        }
    }

    @Test
    public void wmaUsingBarCount9UsingClosePrice() {
        // Example from
        // http://traders.com/Documentation/FEEDbk_docs/2010/12/TradingIndexesWithHullMA.xls
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
                        82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87)
                .build();

        WMAIndicator wma = new WMAIndicator(new ClosePriceIndicator(data), 9);
        assertNumEquals(84.4958, wma.getValue(8));
        assertNumEquals(85.0158, wma.getValue(9));
        assertNumEquals(84.6807, wma.getValue(10));
        assertNumEquals(84.5387, wma.getValue(11));
        assertNumEquals(84.4298, wma.getValue(12));
        assertNumEquals(84.1224, wma.getValue(13));
        assertNumEquals(83.1031, wma.getValue(14));
        assertNumEquals(82.1462, wma.getValue(15));
        assertNumEquals(81.1149, wma.getValue(16));
        assertNumEquals(80.0736, wma.getValue(17));
        assertNumEquals(78.6907, wma.getValue(18));
        assertNumEquals(78.1504, wma.getValue(19));
        assertNumEquals(77.6133, wma.getValue(20));
    }
}
