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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.backtest.BacktestBarBuilder;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BacktestBarTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BacktestBar bar;

    private ZonedDateTime beginTime;

    private ZonedDateTime endTime;
  private BacktestBarSeries series;


  public BacktestBarTest(final NumFactory numFactory) {
        super(null, numFactory);
    }

    @Before
    public void setUp() {
      this.beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
      this.endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
      this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
      this.bar = new BacktestBarBuilder(this.series).timePeriod(Duration.ofHours(1))
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
        assertEquals(numOf(200), this.bar.openPrice());
        assertEquals(numOf(198), this.bar.closePrice());
        assertEquals(numOf(198), this.bar.lowPrice());
        assertEquals(numOf(201), this.bar.highPrice());
        assertEquals(numOf(9), this.bar.volume());
    }

    @Test
    public void timePeriod() {
        assertEquals(this.beginTime, this.bar.endTime().minus(this.bar.timePeriod()));
    }

    @Test
    public void beginTime() {
        assertEquals(this.beginTime, this.bar.beginTime());
    }

    @Test
    public void inPeriod() {
        assertFalse(this.bar.inPeriod(null));

        assertFalse(this.bar.inPeriod(this.beginTime.withDayOfMonth(24)));
        assertFalse(this.bar.inPeriod(this.beginTime.withDayOfMonth(26)));
        assertTrue(this.bar.inPeriod(this.beginTime.withMinute(30)));

        assertTrue(this.bar.inPeriod(this.beginTime));
        assertFalse(this.bar.inPeriod(this.endTime));
    }

    @Test
    public void equals() {
        final Bar bar1 = this.series.barBuilder().timePeriod(Duration.ofHours(1)).endTime(this.endTime).build();
        final Bar bar2 = this.series.barBuilder().timePeriod(Duration.ofHours(1)).endTime(this.endTime).build();

        assertEquals(bar1, bar2);
      assertNotSame(bar1, bar2);
    }

    @Test
    public void hashCode2() {
        final Bar bar1 = this.series.barBuilder().timePeriod(Duration.ofHours(1)).endTime(this.endTime).build();
        final Bar bar2 = this.series.barBuilder().timePeriod(Duration.ofHours(1)).endTime(this.endTime).build();

        assertEquals(bar1.hashCode(), bar2.hashCode());
    }
}
