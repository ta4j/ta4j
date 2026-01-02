/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ThreeInsideDownIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    private BarSeries series;

    public ThreeInsideDownIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withBars(generateUptrend()).build();
        series.barBuilder().openPrice(17).closePrice(25).highPrice(25).lowPrice(17).add();
        series.barBuilder().openPrice(18).closePrice(26).highPrice(28).lowPrice(17).add();
        series.barBuilder().openPrice(22).closePrice(19).highPrice(22).lowPrice(18).add();
        series.barBuilder().openPrice(19).closePrice(14).highPrice(19).lowPrice(12).add();
        series.barBuilder().openPrice(11).closePrice(10).highPrice(12).lowPrice(10).add();
    }

    private List<Bar> generateUptrend() {
        List<Bar> bars = new ArrayList<Bar>(30);
        for (int i = 0; i < 17; ++i) {
            bars.add(
                    new MockBarBuilder(numFactory).openPrice(i).closePrice(i + 6).highPrice(i + 8).lowPrice(i).build());
        }

        return bars;
    }

    @Test
    public void getValue() {
        var tid = new ThreeInsideDownIndicator(series);
        assertFalse(tid.getValue(17));
        assertFalse(tid.getValue(18));
        assertFalse(tid.getValue(19));
        assertTrue(tid.getValue(20));
        assertFalse(tid.getValue(21));
    }
}
