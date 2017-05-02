/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TickTest {

    private Tick tick;

    private DateTime beginTime;

    private DateTime endTime;

    @Before
    public void setUp() {
        beginTime = new DateTime(2014, 6, 25, 0, 0);
        endTime = new DateTime(2014, 6, 25, 1, 0);
        tick = new Tick(Period.hours(1), endTime);
    }

    @Test
    public void addTrades() {

        tick.addTrade(3.0, 200.0);
        tick.addTrade(4.0, 201.0);
        tick.addTrade(2.0, 198.0);

        assertEquals(3, tick.getTrades());
        assertDecimalEquals(tick.getAmount(), 9);
        assertDecimalEquals(tick.getOpenPrice(), 200);
        assertDecimalEquals(tick.getClosePrice(), 198);
        assertDecimalEquals(tick.getMinPrice(), 198);
        assertDecimalEquals(tick.getMaxPrice(), 201);
        assertDecimalEquals(tick.getVolume(), 3 * 200 + 4 * 201 + 2 * 198);
    }

    @Test
    public void getTimePeriod() {
        assertEquals(beginTime, tick.getEndTime().minus(tick.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(beginTime, tick.getBeginTime());
    }

    @Test
    public void inPeriod() {
        assertFalse(tick.inPeriod(null));

        assertFalse(tick.inPeriod(beginTime.withDayOfMonth(24)));
        assertFalse(tick.inPeriod(beginTime.withDayOfMonth(26)));
        assertTrue(tick.inPeriod(beginTime.withMinuteOfHour(30)));

        assertTrue(tick.inPeriod(beginTime));
        assertFalse(tick.inPeriod(endTime));
    }
}
