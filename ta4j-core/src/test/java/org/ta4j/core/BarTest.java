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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.BaseBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BarTest extends AbstractIndicatorTest<BarSeries, Num> {

    private Bar bar;

    private Instant beginTime;

    private Instant endTime;

    public BarTest(final NumFactory numFactory) {
        super(null, numFactory);
    }

    @Before
    public void setUp() {
        this.beginTime = Instant.parse("2014-06-25T00:00:00Z");
        this.endTime = Instant.parse("2014-06-25T01:00:00Z");
        this.bar = new BaseBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .volume(0)
                .amount(0)
                .build();
    }

    @Test
    public void addTrades() {

        this.bar.addTrade(numOf(3.0), numOf(200.0));
        this.bar.addTrade(numOf(4.0), numOf(201.0));
        this.bar.addTrade(numOf(2.0), numOf(198.0));

        assertEquals(3, this.bar.getTrades());
        assertEquals(numOf(3 * 200 + 4 * 201 + 2 * 198), this.bar.getAmount());
        assertEquals(numOf(200), this.bar.getOpenPrice());
        assertEquals(numOf(198), this.bar.getClosePrice());
        assertEquals(numOf(198), this.bar.getLowPrice());
        assertEquals(numOf(201), this.bar.getHighPrice());
        assertEquals(numOf(9), this.bar.getVolume());
    }

    @Test
    public void getTimePeriod() {
        assertEquals(this.beginTime, this.bar.getEndTime().minus(this.bar.getTimePeriod()));
    }

    @Test
    public void getBeginTime() {
        assertEquals(this.beginTime, this.bar.getBeginTime());
    }

    @Test
    public void getDateName() {
        assertNotNull(bar.getDateName());
    }

    @Test
    public void getSimpleDateName() {
        assertNotNull(bar.getSimpleDateName());
    }

    @Test
    public void inPeriod() {
        assertFalse(this.bar.inPeriod(null));

        ZonedDateTime zonedBeginTime = beginTime.atZone(ZoneOffset.UTC);
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(24).toInstant()));
        assertFalse(bar.inPeriod(zonedBeginTime.withDayOfMonth(26).toInstant()));
        assertTrue(bar.inPeriod(zonedBeginTime.withMinute(30).toInstant()));

        assertTrue(this.bar.inPeriod(this.beginTime));
        assertFalse(this.bar.inPeriod(this.endTime));
    }

    @Test
    public void doesNotThrowNullPointerException() {
        var bar = new BaseBarBuilder().timePeriod(Duration.ofHours(1)).endTime(endTime).build();
        // TODO use Junit5: org.junit.jupiter.api.Assertions.assertDoesNotThrow instead:
        assertNotNull(bar.toString());
    }

    @Test
    public void equals() {
        final Bar bar1 = new BaseBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new BaseBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1, bar2);
        assertNotSame(bar1, bar2);
    }

    @Test
    public void hashCode2() {
        final Bar bar1 = new BaseBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();
        final Bar bar2 = new BaseBarBuilder(this.numFactory).timePeriod(Duration.ofHours(1))
                .endTime(this.endTime)
                .build();

        assertEquals(bar1.hashCode(), bar2.hashCode());
    }
}
