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
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TimeBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TimeBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testBuildBarWithEndTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testBuildBarWithBeginTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .beginTime(beginTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testBuildBarWithEndTimeAndBeginTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .endTime(endTime)
                .beginTime(beginTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testCalculateAmountIfMissing() {
        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder().timePeriod(duration).endTime(endTime).beginTime(beginTime).closePrice(10).volume(20).add();

        assertEquals(numOf(200), series.getBar(0).getAmount());
    }
}
