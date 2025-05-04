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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Tests for the {@link BarPool} class.
 */
public class BarPoolTest {

    private BarPool barPool;
    private Duration timePeriod;
    private Instant endTime;
    private Num openPrice, highPrice, lowPrice, closePrice, volume, amount;
    private long trades;

    @Before
    public void setUp() {
        barPool = BarPool.getInstance();
        timePeriod = Duration.ofMinutes(1);
        endTime = Instant.parse("2023-01-01T12:00:00Z");
        
        // Initialize with DoubleNum values
        Num zero = DoubleNumFactory.getInstance().numOf(0);
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
    public void testSingletonPattern() {
        BarPool instance1 = BarPool.getInstance();
        BarPool instance2 = BarPool.getInstance();
        
        assertSame("BarPool should be a singleton", instance1, instance2);
    }

    @Test
    public void testGetBarCreatesNewBarWhenPoolEmpty() {
        Bar bar = barPool.getBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        
        assertNotNull("Should create a new bar when pool is empty", bar);
        assertEquals("Bar should have correct time period", timePeriod, bar.getTimePeriod());
        assertEquals("Bar should have correct end time", endTime, bar.getEndTime());
        assertEquals("Bar should have correct open price", openPrice, bar.getOpenPrice());
        assertEquals("Bar should have correct high price", highPrice, bar.getHighPrice());
        assertEquals("Bar should have correct low price", lowPrice, bar.getLowPrice());
        assertEquals("Bar should have correct close price", closePrice, bar.getClosePrice());
        assertEquals("Bar should have correct volume", volume, bar.getVolume());
        assertEquals("Bar should have correct amount", amount, bar.getAmount());
        assertEquals("Bar should have correct trades", trades, bar.getTrades());
    }

    @Test
    public void testReturnAndReuseBar() {
        // Get a bar from the pool (will create a new one since pool is empty)
        Bar bar1 = barPool.getBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        
        // Return the bar to the pool
        barPool.returnBar(bar1);
        
        // Get another bar with different values
        Instant newEndTime = endTime.plusSeconds(60);
        Num newClosePrice = DoubleNumFactory.getInstance().numOf(106);
        Bar bar2 = barPool.getBar(timePeriod, newEndTime, openPrice, highPrice, lowPrice, newClosePrice, volume, amount, trades);
        
        // The second bar should be the same instance as the first one, but with updated values
        assertSame("Should reuse bar from pool", bar1, bar2);
        assertEquals("Bar should have updated end time", newEndTime, bar2.getEndTime());
        assertEquals("Bar should have updated close price", newClosePrice, bar2.getClosePrice());
    }

    @Test
    public void testDifferentTimePeriods() {
        // Create bars with different time periods
        Duration period1 = Duration.ofMinutes(1);
        Duration period5 = Duration.ofMinutes(5);
        
        Bar bar1min = barPool.getBar(period1, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        Bar bar5min = barPool.getBar(period5, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        
        // Return both bars to the pool
        barPool.returnBar(bar1min);
        barPool.returnBar(bar5min);
        
        // Get bars with the same time periods again
        Bar newBar1min = barPool.getBar(period1, endTime.plusSeconds(60), openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        Bar newBar5min = barPool.getBar(period5, endTime.plusSeconds(300), openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        
        // Each bar should be reused from its respective pool
        assertSame("Should reuse 1-minute bar from pool", bar1min, newBar1min);
        assertSame("Should reuse 5-minute bar from pool", bar5min, newBar5min);
        assertNotSame("Bars with different periods should be different instances", newBar1min, newBar5min);
    }

    @Test
    public void testPoolSizeLimit() throws Exception {
        // Access the private MAX_POOL_SIZE field using reflection
        Field maxPoolSizeField = BarPool.class.getDeclaredField("MAX_POOL_SIZE");
        maxPoolSizeField.setAccessible(true);
        int maxPoolSize = (int) maxPoolSizeField.get(null);
        
        // Access the private pools field using reflection
        Field poolsField = BarPool.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Duration, ConcurrentLinkedQueue<BaseBar>> pools = 
            (Map<Duration, ConcurrentLinkedQueue<BaseBar>>) poolsField.get(barPool);
        
        // Create and return more bars than the maximum pool size
        for (int i = 0; i < maxPoolSize + 10; i++) {
            Bar bar = barPool.getBar(timePeriod, endTime.plusSeconds(i), openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
            barPool.returnBar(bar);
        }
        
        // Check that the pool size doesn't exceed the maximum
        ConcurrentLinkedQueue<BaseBar> pool = pools.get(timePeriod);
        assertTrue("Pool size should not exceed maximum", pool.size() <= maxPoolSize);
    }

    @Test
    public void testNonBaseBarNotAddedToPool() {
        // Create a non-BaseBar implementation
        Bar nonBaseBar = new Bar() {
            @Override
            public Duration getTimePeriod() { return timePeriod; }
            @Override
            public Instant getBeginTime() { return endTime.minus(timePeriod); }
            @Override
            public Instant getEndTime() { return endTime; }
            @Override
            public Num getOpenPrice() { return openPrice; }
            @Override
            public Num getHighPrice() { return highPrice; }
            @Override
            public Num getLowPrice() { return lowPrice; }
            @Override
            public Num getClosePrice() { return closePrice; }
            @Override
            public Num getVolume() { return volume; }
            @Override
            public Num getAmount() { return amount; }
            @Override
            public long getTrades() { return trades; }
            @Override
            public void addTrade(Num tradeVolume, Num tradePrice) { }
            @Override
            public void addPrice(Num price) { }
        };
        
        // Try to return the non-BaseBar to the pool
        barPool.returnBar(nonBaseBar);
        
        // Get a bar from the pool - it should be a new instance, not the one we tried to return
        Bar newBar = barPool.getBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        
        assertNotSame("Non-BaseBar implementation should not be added to pool", nonBaseBar, newBar);
    }

    @Test
    public void testClearPool() throws Exception {
        // Create and return some bars to the pool
        for (int i = 0; i < 5; i++) {
            Bar bar = barPool.getBar(timePeriod, endTime.plusSeconds(i), openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
            barPool.returnBar(bar);
        }
        
        // Access the private pools field using reflection
        Field poolsField = BarPool.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Duration, ConcurrentLinkedQueue<BaseBar>> pools = 
            (Map<Duration, ConcurrentLinkedQueue<BaseBar>>) poolsField.get(barPool);
        
        // Verify that the pool is not empty
        assertTrue("Pool should not be empty before clearing", !pools.isEmpty());
        
        // Clear the pool
        barPool.clear();
        
        // Verify that the pool is now empty
        assertTrue("Pool should be empty after clearing", pools.isEmpty());
    }
}