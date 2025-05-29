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
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarPool;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Tests for the integration between {@link TimeBarBuilder} and {@link BarPool}.
 */
public class TimeBarBuilderPoolTest {

    private BarPool barPool;
    private TimeBarBuilder builder;
    private Duration timePeriod;
    private Instant endTime;
    private Num openPrice, highPrice, lowPrice, closePrice, volume, amount;
    private long trades;

    @Before
    public void setUp() {
        barPool = BarPool.getInstance();
        builder = new TimeBarBuilder(DoubleNumFactory.getInstance());
        timePeriod = Duration.ofMinutes(1);
        endTime = Instant.parse("2023-01-01T12:00:00Z");

        // Initialize with DoubleNum values
        openPrice = DoubleNumFactory.getInstance().numOf(100);
        highPrice = DoubleNumFactory.getInstance().numOf(110);
        lowPrice = DoubleNumFactory.getInstance().numOf(90);
        closePrice = DoubleNumFactory.getInstance().numOf(105);
        volume = DoubleNumFactory.getInstance().numOf(1000);
        amount = DoubleNumFactory.getInstance().numOf(105000);
        trades = 10;
    }

    @After
    public void tearDown() {
        // Clear the pool after each test to ensure tests don't affect each other
        barPool.clear();
    }

    @Test
    public void testTimeBarBuilderUsesBarPool() {
        // Configure the builder
        builder.timePeriod(timePeriod)
               .endTime(endTime)
               .openPrice(openPrice)
               .highPrice(highPrice)
               .lowPrice(lowPrice)
               .closePrice(closePrice)
               .volume(volume)
               .amount(amount)
               .trades(trades);

        // Build a bar
        Bar bar1 = builder.build();

        // Verify the bar has the correct properties
        assertNotNull("Bar should not be null", bar1);
        assertEquals("Bar should have correct time period", timePeriod, bar1.getTimePeriod());
        assertEquals("Bar should have correct end time", endTime, bar1.getEndTime());
        assertEquals("Bar should have correct open price", openPrice, bar1.getOpenPrice());
        assertEquals("Bar should have correct high price", highPrice, bar1.getHighPrice());
        assertEquals("Bar should have correct low price", lowPrice, bar1.getLowPrice());
        assertEquals("Bar should have correct close price", closePrice, bar1.getClosePrice());
        assertEquals("Bar should have correct volume", volume, bar1.getVolume());
        assertEquals("Bar should have correct amount", amount, bar1.getAmount());
        assertEquals("Bar should have correct trades", trades, bar1.getTrades());

        // Return the bar to the pool
        barPool.returnBar(bar1);

        // Build another bar with the same time period
        builder.endTime(endTime.plusSeconds(60));
        Bar bar2 = builder.build();

        // The second bar should be the same instance as the first one, but with updated values
        assertSame("TimeBarBuilder should reuse bar from pool", bar1, bar2);
        assertEquals("Bar should have updated end time", endTime.plusSeconds(60), bar2.getEndTime());
    }

    @Test
    public void testTimeBarBuilderWithBarSeries() throws Exception {
        // Create a bar series with maximum bar count of 1
        BaseBarSeries series = new BaseBarSeriesBuilder()
                .withName("test series")
                .withNumFactory(DoubleNumFactory.getInstance())
                .withMaxBarCount(1)
                .build();

        // Access the private pools field using reflection to check the pool state
        Field poolsField = BarPool.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Duration, ConcurrentLinkedQueue<BaseBar>> pools = 
            (Map<Duration, ConcurrentLinkedQueue<BaseBar>>) poolsField.get(barPool);

        // Initially, the pool should be empty
        ConcurrentLinkedQueue<BaseBar> pool = pools.get(timePeriod);
        assertEquals("Pool should be empty initially", 0, pool != null ? pool.size() : 0);

        // Bind the builder to the series
        builder.bindTo(series)
               .timePeriod(timePeriod)
               .endTime(endTime)
               .openPrice(openPrice)
               .highPrice(highPrice)
               .lowPrice(lowPrice)
               .closePrice(closePrice)
               .volume(volume)
               .amount(amount)
               .trades(trades);

        // Add a bar to the series
        builder.add();

        // Verify the bar was added to the series
        assertEquals("Series should have 1 bar", 1, series.getBarCount());
        Bar firstBar = series.getBar(0);
        assertEquals("Bar should have correct end time", endTime, firstBar.getEndTime());

        // Add a second bar with a later time - this should cause the first bar to be removed and returned to the pool
        Instant laterTime = endTime.plusSeconds(60);
        builder.endTime(laterTime).add();

        // Verify the series still has 1 bar (the second one)
        assertEquals("Series should still have 1 bar", 1, series.getBarCount());
        Bar secondBar = series.getBar(0);
        assertEquals("Bar should have updated end time", laterTime, secondBar.getEndTime());

        // Now the pool should have the first bar
        pool = pools.get(timePeriod);
        assertEquals("Pool should have 1 bar", 1, pool.size());

        // Add a third bar with an even later time - this should reuse the bar from the pool
        Instant evenLaterTime = laterTime.plusSeconds(60);
        builder.endTime(evenLaterTime).add();

        // Verify the series still has 1 bar (the third one)
        assertEquals("Series should still have 1 bar", 1, series.getBarCount());
        Bar thirdBar = series.getBar(0);
        assertEquals("Bar should have updated end time", evenLaterTime, thirdBar.getEndTime());

        // The pool should still have 1 bar (the second one)
        assertEquals("Pool should still have 1 bar", 1, pool.size());
    }
}
