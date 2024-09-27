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
package org.ta4j.core.indicators.donchian;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DonchianChannelMiddleIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;

    public DonchianChannelMiddleIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withName("DonchianChannelMiddleIndicatorTestSeries")
                .withNumFactory(numFactory)
                .build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(120).highPrice(125).lowPrice(115).closePrice(120).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(100).highPrice(105).lowPrice(95).closePrice(100).add();

    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetValue() {
        var subject = new DonchianChannelMiddleIndicator(series, 3);

        assertEquals(numOf(100), subject.getValue(0));
        assertEquals(numOf(102.5), subject.getValue(1));
        assertEquals(numOf(105), subject.getValue(2));
        assertEquals(numOf(110), subject.getValue(3));
        assertEquals(numOf(115), subject.getValue(4));
        assertEquals(numOf(117.5), subject.getValue(5));
        assertEquals(numOf(115), subject.getValue(6));
        assertEquals(numOf(110), subject.getValue(7));
        assertEquals(numOf(105), subject.getValue(8));

    }
}
