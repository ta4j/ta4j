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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ATRIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

    private final ExternalIndicatorTest xls;

    public ATRIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new ATRIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "ATR.xls", 7, numFactory);
    }

    @Test
    public void testDummy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var now = Instant.now();
        series.addBar(series.barBuilder()
                .endTime(now.minusSeconds(5))
                .openPrice(0)
                .closePrice(12)
                .highPrice(15)
                .lowPrice(8)
                .amount(0)
                .volume(0)
                .build());
        series.barBuilder()
                .endTime(now.minusSeconds(4))
                .openPrice(0)
                .closePrice(8)
                .highPrice(11)
                .lowPrice(6)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(3))
                .openPrice(0)
                .closePrice(15)
                .highPrice(17)
                .lowPrice(14)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(2))
                .openPrice(0)
                .closePrice(15)
                .highPrice(17)
                .lowPrice(14)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        series.barBuilder()
                .endTime(now.minusSeconds(1))
                .openPrice(0)
                .closePrice(0)
                .highPrice(0)
                .lowPrice(2)
                .volume(0)
                .amount(0)
                .trades(0)
                .add();
        Indicator<Num> indicator = getIndicator(series, 3);

        assertEquals(7d, indicator.getValue(0).doubleValue(), TestUtils.GENERAL_OFFSET);
        assertEquals(6d / 3 + (1 - 1d / 3) * indicator.getValue(0).doubleValue(), indicator.getValue(1).doubleValue(),
                TestUtils.GENERAL_OFFSET);
        assertEquals(9d / 3 + (1 - 1d / 3) * indicator.getValue(1).doubleValue(), indicator.getValue(2).doubleValue(),
                TestUtils.GENERAL_OFFSET);
        assertEquals(3d / 3 + (1 - 1d / 3) * indicator.getValue(2).doubleValue(), indicator.getValue(3).doubleValue(),
                TestUtils.GENERAL_OFFSET);
        assertEquals(15d / 3 + (1 - 1d / 3) * indicator.getValue(3).doubleValue(), indicator.getValue(4).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testXls() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> indicator;

        indicator = getIndicator(xlsSeries, 1);
        assertIndicatorEquals(xls.getIndicator(1), indicator);
        assertEquals(4.8, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 3);
        assertIndicatorEquals(xls.getIndicator(3), indicator);
        assertEquals(7.4225, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(xlsSeries, 13);
        assertIndicatorEquals(xls.getIndicator(13), indicator);
        assertEquals(8.8082, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
