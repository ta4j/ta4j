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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

public class BarTest extends AbstractIndicatorTest<BarSeries, Num> {

    private Bar bar;

    private ZonedDateTime beginTime;

    private ZonedDateTime endTime;

    public BarTest(Function<Number, Num> numFunction) {
        super(null, numFunction);
    }

    @Before
    public void setUp() {
        beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        bar = new BaseBar(Duration.ofHours(1), endTime, numFunction);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void addTrades() {

        bar.addTrade(3.0, 200.0, numFunction);
        bar.addTrade(4.0, 201.0, numFunction);
        bar.addTrade(2.0, 198.0, numFunction);

        assertEquals(3, bar.getTrades());
        assertNumEquals(3 * 200 + 4 * 201 + 2 * 198, bar.getAmount());
        assertNumEquals(200, bar.getOpenPrice());
        assertNumEquals(198, bar.getClosePrice());
        assertNumEquals(198, bar.getLowPrice());
        assertNumEquals(201, bar.getHighPrice());
        assertNumEquals(9, bar.getVolume());
    }

    @Test
    public void addPrice() {
        bar.addPrice(3.0, 2.1, numFunction);

        assertNumEquals(3.0, bar.getClosePrice());
        assertNumEquals(2.1, bar.getSpread());


        // BaseBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
        //            Num closePrice, Num volume, Num amount, long trades, Num spread)
        Bar bar2 = new BaseBar(
                Duration.ZERO.plusMinutes(15),
                endTime,
                numFunction.apply(3.0),
                numFunction.apply(4.0),
                numFunction.apply(2.0),
                numFunction.apply(3.0),
                numFunction.apply(100),
                numFunction.apply(12.3),
                3,
                numFunction.apply(1.2));

        assertNumEquals(3.0, bar2.getOpenPrice());
        assertNumEquals(4.0, bar2.getHighPrice());
        assertNumEquals(2.0, bar2.getLowPrice());
        assertNumEquals(3.0, bar2.getClosePrice());
        assertNumEquals(100, bar2.getVolume());
        assertNumEquals(12.3, bar2.getAmount());
        assertEquals(3, bar2.getTrades());
        assertNumEquals(1.2, bar2.getSpread());

    }

    @Test
    public void getTimePeriod() {
        assertEquals(beginTime, bar.getEndTime().minus(bar.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(beginTime, bar.getBeginTime());
    }

    @Test
    public void inPeriod() {
        assertFalse(bar.inPeriod(null));

        assertFalse(bar.inPeriod(beginTime.withDayOfMonth(24)));
        assertFalse(bar.inPeriod(beginTime.withDayOfMonth(26)));
        assertTrue(bar.inPeriod(beginTime.withMinute(30)));

        assertTrue(bar.inPeriod(beginTime));
        assertFalse(bar.inPeriod(endTime));
    }

    @Test
    public void equals() {
        Bar bar1 = new BaseBar(Duration.ofHours(1), endTime, numFunction);
        Bar bar2 = new BaseBar(Duration.ofHours(1), endTime, numFunction);

        assertEquals(bar1, bar2);
    }

    @Test
    public void hashCode2() {
        Bar bar1 = new BaseBar(Duration.ofHours(1), endTime, numFunction);
        Bar bar2 = new BaseBar(Duration.ofHours(1), endTime, numFunction);

        assertEquals(bar1.hashCode(), bar2.hashCode());
    }
}
