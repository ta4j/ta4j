/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MedianPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private MedianPriceIndicator average;

    BarSeries barSeries;

    public MedianPriceIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        this.barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        barSeries.barBuilder().openPrice(8).closePrice(16).highPrice(16).lowPrice(8).add();
        barSeries.barBuilder().openPrice(6).closePrice(12).highPrice(12).lowPrice(6).add();
        barSeries.barBuilder().openPrice(14).closePrice(18).highPrice(18).lowPrice(14).add();
        barSeries.barBuilder().openPrice(6).closePrice(10).highPrice(10).lowPrice(6).add();
        barSeries.barBuilder().openPrice(6).closePrice(32).highPrice(32).lowPrice(6).add();
        barSeries.barBuilder().openPrice(2).closePrice(2).highPrice(2).lowPrice(2).add();
        barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();
        barSeries.barBuilder().openPrice(1).closePrice(8).highPrice(8).lowPrice(1).add();
        barSeries.barBuilder().openPrice(32).closePrice(83).highPrice(83).lowPrice(32).add();
        barSeries.barBuilder().openPrice(3).closePrice(9).highPrice(9).lowPrice(3).add();

        average = new MedianPriceIndicator(barSeries);
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        Num result;
        for (int i = 0; i < 10; i++) {
            result = barSeries.getBar(i)
                    .getHighPrice()
                    .plus(barSeries.getBar(i).getLowPrice())
                    .dividedBy(numFactory.numOf(2));
            assertEquals(average.getValue(i), result);
        }
    }
}
