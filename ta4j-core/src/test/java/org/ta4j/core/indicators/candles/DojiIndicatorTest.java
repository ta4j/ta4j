/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DojiIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public DojiIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().openPrice(19).closePrice(19).highPrice(22).lowPrice(16).add();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(10).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17).add();
        series.barBuilder().openPrice(15).closePrice(15.1).highPrice(16).lowPrice(14).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
    }

    @Test
    public void getValueAtIndex0() {
        var doji = new DojiIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0d).build(), 10,
                0.03);
        assertTrue(doji.getValue(0));

        doji = new DojiIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d).build(), 10, 0.03);
        assertFalse(doji.getValue(0));
    }

    @Test
    public void getValue() {
        DojiIndicator doji = new DojiIndicator(series, 3, 0.1);
        assertTrue(doji.getValue(0));
        assertFalse(doji.getValue(1));
        assertFalse(doji.getValue(2));
        assertTrue(doji.getValue(3));
        assertFalse(doji.getValue(4));
        assertFalse(doji.getValue(5));
    }
}
