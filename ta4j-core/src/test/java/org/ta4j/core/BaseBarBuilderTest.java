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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BaseBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BaseBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testBuildBar() {
        final ZonedDateTime beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        final Duration duration = Duration.between(beginTime, endTime);

        final BaseBar bar = new BaseBarBuilder().timePeriod(duration)
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
    public void testBuildBarHeikinAshi() {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();
        
        new BaseBarBuilder().timePeriod(Duration.ofHours(1))
		        .endTime(ZonedDateTime.of(2024, 10, 04, 0, 0, 0, 0, ZoneId.systemDefault()))
		        .openPrice(numOf(720.00))
		        .highPrice(numOf(908.70))
		        .lowPrice(numOf(711.15))
		        .closePrice(numOf(804.45))
		        .trades(4)
		        .volume(numOf(40))
		        .amount(numOf(4020))
		        .bindTo(series)
		        .toHeikinAshiBar()
	        	.add();
        
        new BaseBarBuilder().timePeriod(Duration.ofHours(1))
                .endTime(ZonedDateTime.of(2024, 10, 04, 1, 0, 0, 0, ZoneId.systemDefault()))
                .openPrice(numOf(807.50))
                .highPrice(numOf(863.00))
                .lowPrice(numOf(696.50))
                .closePrice(numOf(774.20))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .bindTo(series)
                .toHeikinAshiBar()
                .add();
        
        new BaseBarBuilder().timePeriod(Duration.ofHours(1))
                .endTime(ZonedDateTime.of(2024, 10, 04, 2, 0, 0, 0, ZoneId.systemDefault()))
                .openPrice(numOf(777.45))
                .highPrice(numOf(879.00))
                .lowPrice(numOf(770.00))
                .closePrice(numOf(826.70))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .bindTo(series)
                .toHeikinAshiBar()
                .add();

        var bar = series.getLastBar();
        assertEquals("779.73", String.format("%.2f", bar.getOpenPrice().doubleValue()));
        assertEquals("879.00", String.format("%.2f", bar.getHighPrice().doubleValue()));
        assertEquals("770.00", String.format("%.2f", bar.getLowPrice().doubleValue()));
        assertEquals("813.29", String.format("%.2f", bar.getClosePrice().doubleValue()));
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }
}
