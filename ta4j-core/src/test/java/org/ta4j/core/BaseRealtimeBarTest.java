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
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class BaseRealtimeBarTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BaseRealtimeBarTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void addTradeUpdatesSideAndLiquidityBreakdowns() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(2), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        bar.addTrade(numOf(1), numOf(90), RealtimeBar.Side.SELL, RealtimeBar.Liquidity.TAKER);

        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(numOf(3), bar.getVolume());
        assertEquals(numOf(290), bar.getAmount());
        assertEquals(2, bar.getTrades());
        assertEquals(numOf(2), bar.getBuyVolume());
        assertEquals(numOf(1), bar.getSellVolume());
        assertEquals(numOf(200), bar.getBuyAmount());
        assertEquals(numOf(90), bar.getSellAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(1, bar.getSellTrades());
        assertEquals(numOf(2), bar.getMakerVolume());
        assertEquals(numOf(1), bar.getTakerVolume());
        assertEquals(numOf(200), bar.getMakerAmount());
        assertEquals(numOf(90), bar.getTakerAmount());
        assertEquals(1, bar.getMakerTrades());
        assertEquals(1, bar.getTakerTrades());
    }

    @Test
    public void addTradeSupportsOptionalSideAndLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), null, null);

        assertFalse(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(numOf(0), bar.getBuyVolume());
        assertEquals(numOf(0), bar.getSellVolume());
        assertEquals(numOf(0), bar.getMakerVolume());
        assertEquals(numOf(0), bar.getTakerVolume());

        bar.addTrade(numOf(2), numOf(110), RealtimeBar.Side.SELL, null);
        assertTrue(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(numOf(2), bar.getSellVolume());
        assertEquals(numOf(220), bar.getSellAmount());
        assertEquals(1, bar.getSellTrades());

        bar.addTrade(numOf(1), numOf(120), null, RealtimeBar.Liquidity.MAKER);
        assertTrue(bar.hasLiquidityData());
        assertEquals(numOf(1), bar.getMakerVolume());
        assertEquals(numOf(120), bar.getMakerAmount());
        assertEquals(1, bar.getMakerTrades());
    }
}
