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

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    @Test
    public void addTrades() {

        bar.addTrade(numOf(3.0), numOf(200.0));
        bar.addTrade(numOf(4.0), numOf(201.0));
        bar.addTrade(numOf(2.0), numOf(198.0));

        assertEquals(3, bar.getTrades());
        assertEquals(numOf(3 * 200 + 4 * 201 + 2 * 198), bar.getAmount());
        assertEquals(numOf(200), bar.getOpenPrice());
        assertEquals(numOf(198), bar.getClosePrice());
        assertEquals(numOf(198), bar.getLowPrice());
        assertEquals(numOf(201), bar.getHighPrice());
        assertEquals(numOf(9), bar.getVolume());
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
