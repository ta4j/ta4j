/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

public class ThreeBlackCrowsIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeBlackCrowsIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(19).closePrice(19).highPrice(22).lowPrice(15.0).add();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(8.0).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17.0).add();
        series.barBuilder().openPrice(19).closePrice(17).highPrice(20).lowPrice(16.9).add();
        series.barBuilder().openPrice(17.5).closePrice(14).highPrice(18).lowPrice(13.9).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(11.0).add();
        series.barBuilder().openPrice(12).closePrice(14).highPrice(15).lowPrice(8.0).add();
        series.barBuilder().openPrice(13).closePrice(16).highPrice(16).lowPrice(11.0).add();
    }

    @Test
    public void getValue() {
        var tbc = new ThreeBlackCrowsIndicator(series, 3, 0.1);
        assertFalse(tbc.getValue(0));
        assertFalse(tbc.getValue(1));
        assertFalse(tbc.getValue(2));
        assertFalse(tbc.getValue(3));
        assertFalse(tbc.getValue(4));
        assertTrue(tbc.getValue(5));
        assertFalse(tbc.getValue(6));
        assertFalse(tbc.getValue(7));
    }
}
