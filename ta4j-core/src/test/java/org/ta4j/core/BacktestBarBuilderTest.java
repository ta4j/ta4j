/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;
import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.backtest.BacktestBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BacktestBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BacktestBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testBuildBar() {
        final ZonedDateTime beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        final Duration duration = Duration.between(beginTime, endTime);

        final BacktestBar bar = new BacktestBarBuilder(new MockBarSeriesBuilder().build()).timePeriod(duration)
                .endTime(endTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.timePeriod());
        assertEquals(beginTime, bar.beginTime());
        assertEquals(endTime, bar.endTime());
        assertEquals(numOf(101), bar.openPrice());
        assertEquals(numOf(103), bar.highPrice());
        assertEquals(numOf(100), bar.lowPrice());
        assertEquals(numOf(102), bar.closePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.volume());
        assertEquals(numOf(4020), bar.getAmount());
    }
}
