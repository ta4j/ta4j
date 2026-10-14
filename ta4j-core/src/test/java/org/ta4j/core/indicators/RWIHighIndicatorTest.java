/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Testing the RWIHighIndicator
 */
public class RWIHighIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    /**
     * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel
     * formula needed
     */
    private final ExternalIndicatorTest xls;

    public RWIHighIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new RWIHighIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 8, numFactory);
    }

    @Test
    public void cachedHistoricalValuesReanchorAfterPruning() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        double[] prices = { 10, 12, 11, 15, 13, 16, 12, 17, 14, 18 };
        for (int i = 0; i < prices.length; i++) {
            double price = prices[i];
            series.barBuilder()
                    .openPrice(price)
                    .highPrice(price + i % 3 + 1)
                    .lowPrice(price - i % 2 - 1)
                    .closePrice(price)
                    .volume(100 + i)
                    .add();
        }
        series.setMaximumBarCount(10);
        Indicator<Num> reused = new RWIHighIndicator(series, 3);
        for (int i = 0; i < series.getEndIndex(); i++) {
            reused.getValue(i);
        }
        series.barBuilder().openPrice(9).highPrice(11).lowPrice(8).closePrice(9).volume(110).add();
        Indicator<Num> fresh = new RWIHighIndicator(series, 3);
        for (int i = series.getBeginIndex(); i < series.getEndIndex(); i++) {
            assertNumEquals(fresh.getValue(i), reused.getValue(i));
        }
    }

    @Test
    public void randomWalkIndexHigh() throws Exception {
        Indicator<Num> expected = xls.getIndicator(20);
        RWIHighIndicator rwih = (RWIHighIndicator) getIndicator(expected.getBarSeries(), 20);
        assertEquals(20, rwih.getCountOfUnstableBars());
        assertIndicatorEquals(expected, rwih);
    }
}
