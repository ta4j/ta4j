/*
 * SPDX-License-Identifier: MIT
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

    @Test(expected = NullPointerException.class)
    public void testConstructorRejectsNullNumFactory() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0, null, null, null,
                null, 0, 0, null, null, null, null, 0, 0, false, false, null);
    }

    @Test
    public void testConstructorWithAllParameters() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var openPrice = numOf(100);
        final var highPrice = numOf(110);
        final var lowPrice = numOf(90);
        final var closePrice = numOf(105);
        final var volume = numOf(1000);
        final var amount = numOf(105000);
        final var buyVolume = numOf(600);
        final var sellVolume = numOf(400);
        final var buyAmount = numOf(63000);
        final var sellAmount = numOf(42000);
        final var makerVolume = numOf(700);
        final var takerVolume = numOf(300);
        final var makerAmount = numOf(73500);
        final var takerAmount = numOf(31500);

        final var bar = new BaseRealtimeBar(period, start, start.plus(period), openPrice, highPrice, lowPrice,
                closePrice, volume, amount, 100, buyVolume, sellVolume, buyAmount, sellAmount, 60, 40, makerVolume,
                takerVolume, makerAmount, takerAmount, 70, 30, true, true, numFactory);

        assertEquals(openPrice, bar.getOpenPrice());
        assertEquals(highPrice, bar.getHighPrice());
        assertEquals(lowPrice, bar.getLowPrice());
        assertEquals(closePrice, bar.getClosePrice());
        assertEquals(volume, bar.getVolume());
        assertEquals(amount, bar.getAmount());
        assertEquals(100, bar.getTrades());
        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(buyVolume, bar.getBuyVolume());
        assertEquals(sellVolume, bar.getSellVolume());
        assertEquals(buyAmount, bar.getBuyAmount());
        assertEquals(sellAmount, bar.getSellAmount());
        assertEquals(60, bar.getBuyTrades());
        assertEquals(40, bar.getSellTrades());
        assertEquals(makerVolume, bar.getMakerVolume());
        assertEquals(takerVolume, bar.getTakerVolume());
        assertEquals(makerAmount, bar.getMakerAmount());
        assertEquals(takerAmount, bar.getTakerAmount());
        assertEquals(70, bar.getMakerTrades());
        assertEquals(30, bar.getTakerTrades());
    }

    @Test
    public void testGettersReturnZeroWhenFieldsAreNull() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        assertEquals(zero, bar.getBuyVolume());
        assertEquals(zero, bar.getSellVolume());
        assertEquals(zero, bar.getBuyAmount());
        assertEquals(zero, bar.getSellAmount());
        assertEquals(zero, bar.getMakerVolume());
        assertEquals(zero, bar.getTakerVolume());
        assertEquals(zero, bar.getMakerAmount());
        assertEquals(zero, bar.getTakerAmount());
    }

    @Test
    public void testGettersReturnZeroWhenHasSideDataIsFalse() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        assertFalse(bar.hasSideData());
        assertEquals(zero, bar.getBuyVolume());
        assertEquals(zero, bar.getSellVolume());
        assertEquals(zero, bar.getBuyAmount());
        assertEquals(zero, bar.getSellAmount());
        assertEquals(0, bar.getBuyTrades());
        assertEquals(0, bar.getSellTrades());
    }

    @Test
    public void testGettersReturnZeroWhenHasLiquidityDataIsFalse() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        assertFalse(bar.hasLiquidityData());
        assertEquals(zero, bar.getMakerVolume());
        assertEquals(zero, bar.getTakerVolume());
        assertEquals(zero, bar.getMakerAmount());
        assertEquals(zero, bar.getTakerAmount());
        assertEquals(0, bar.getMakerTrades());
        assertEquals(0, bar.getTakerTrades());
    }

    @Test
    public void testHasSideDataInitialState() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        assertFalse(bar.hasSideData());
    }

    @Test
    public void testHasSideDataAfterAddingTradeWithSide() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, null);
        assertTrue(bar.hasSideData());
    }

    @Test
    public void testHasLiquidityDataInitialState() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        assertFalse(bar.hasLiquidityData());
    }

    @Test
    public void testHasLiquidityDataAfterAddingTradeWithLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), null, RealtimeBar.Liquidity.MAKER);
        assertTrue(bar.hasLiquidityData());
    }

    @Test
    public void testHasSideDataWithPrePopulatedData() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var buyVolume = numOf(100);
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                buyVolume, null, null, null, 1, 0, null, null, null, null, 0, 0, true, false, numFactory);

        assertTrue(bar.hasSideData());
    }

    @Test
    public void testHasLiquidityDataWithPrePopulatedData() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var makerVolume = numOf(100);
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, makerVolume, null, null, null, 1, 0, false, true, numFactory);

        assertTrue(bar.hasLiquidityData());
    }

    @Test
    public void testAddTradeAccumulatesMultipleBuyTrades() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, null);
        bar.addTrade(numOf(2), numOf(110), RealtimeBar.Side.BUY, null);
        bar.addTrade(numOf(3), numOf(120), RealtimeBar.Side.BUY, null);

        assertEquals(numOf(6), bar.getBuyVolume());
        assertEquals(numOf(680), bar.getBuyAmount()); // 100 + 220 + 360
        assertEquals(3, bar.getBuyTrades());
        assertEquals(numOf(0), bar.getSellVolume());
        assertEquals(0, bar.getSellTrades());
    }

    @Test
    public void testAddTradeAccumulatesMultipleSellTrades() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.SELL, null);
        bar.addTrade(numOf(2), numOf(110), RealtimeBar.Side.SELL, null);
        bar.addTrade(numOf(3), numOf(120), RealtimeBar.Side.SELL, null);

        assertEquals(numOf(6), bar.getSellVolume());
        assertEquals(numOf(680), bar.getSellAmount()); // 100 + 220 + 360
        assertEquals(3, bar.getSellTrades());
        assertEquals(numOf(0), bar.getBuyVolume());
        assertEquals(0, bar.getBuyTrades());
    }

    @Test
    public void testAddTradeAccumulatesMultipleMakerTrades() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), null, RealtimeBar.Liquidity.MAKER);
        bar.addTrade(numOf(2), numOf(110), null, RealtimeBar.Liquidity.MAKER);
        bar.addTrade(numOf(3), numOf(120), null, RealtimeBar.Liquidity.MAKER);

        assertEquals(numOf(6), bar.getMakerVolume());
        assertEquals(numOf(680), bar.getMakerAmount()); // 100 + 220 + 360
        assertEquals(3, bar.getMakerTrades());
        assertEquals(numOf(0), bar.getTakerVolume());
        assertEquals(0, bar.getTakerTrades());
    }

    @Test
    public void testAddTradeAccumulatesMultipleTakerTrades() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(1), numOf(100), null, RealtimeBar.Liquidity.TAKER);
        bar.addTrade(numOf(2), numOf(110), null, RealtimeBar.Liquidity.TAKER);
        bar.addTrade(numOf(3), numOf(120), null, RealtimeBar.Liquidity.TAKER);

        assertEquals(numOf(6), bar.getTakerVolume());
        assertEquals(numOf(680), bar.getTakerAmount()); // 100 + 220 + 360
        assertEquals(3, bar.getTakerTrades());
        assertEquals(numOf(0), bar.getMakerVolume());
        assertEquals(0, bar.getMakerTrades());
    }

    @Test
    public void testAddTradeAccumulatesMixedSideAndLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        // Buy + Maker
        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        // Buy + Taker
        bar.addTrade(numOf(2), numOf(110), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.TAKER);
        // Sell + Maker
        bar.addTrade(numOf(3), numOf(120), RealtimeBar.Side.SELL, RealtimeBar.Liquidity.MAKER);
        // Sell + Taker
        bar.addTrade(numOf(4), numOf(130), RealtimeBar.Side.SELL, RealtimeBar.Liquidity.TAKER);

        assertEquals(numOf(3), bar.getBuyVolume()); // 1 + 2
        assertEquals(numOf(320), bar.getBuyAmount()); // 100 + 220
        assertEquals(2, bar.getBuyTrades());
        assertEquals(numOf(7), bar.getSellVolume()); // 3 + 4
        assertEquals(numOf(880), bar.getSellAmount()); // 360 + 520
        assertEquals(2, bar.getSellTrades());
        assertEquals(numOf(4), bar.getMakerVolume()); // 1 + 3
        assertEquals(numOf(460), bar.getMakerAmount()); // 100 + 360
        assertEquals(2, bar.getMakerTrades());
        assertEquals(numOf(6), bar.getTakerVolume()); // 2 + 4
        assertEquals(numOf(740), bar.getTakerAmount()); // 220 + 520
        assertEquals(2, bar.getTakerTrades());
    }

    @Test
    public void testAddTradeUpdatesBaseBarVolume() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(5), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);

        assertEquals(numOf(5), bar.getVolume());
        assertEquals(numOf(500), bar.getAmount());
        assertEquals(1, bar.getTrades());
    }

    @Test
    public void testAddTradeAccumulatesBaseBarVolume() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(2), numOf(100), RealtimeBar.Side.BUY, null);
        bar.addTrade(numOf(3), numOf(110), RealtimeBar.Side.SELL, null);
        bar.addTrade(numOf(1), numOf(120), null, RealtimeBar.Liquidity.MAKER);

        assertEquals(numOf(6), bar.getVolume());
        assertEquals(numOf(650), bar.getAmount()); // 200 + 330 + 120
        assertEquals(3, bar.getTrades());
    }

    @Test
    public void testAddTradeWithZeroVolume() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(zero, numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);

        assertEquals(zero, bar.getVolume());
        assertEquals(zero, bar.getAmount());
        assertEquals(1, bar.getTrades());
        assertEquals(zero, bar.getBuyVolume());
        assertEquals(zero, bar.getBuyAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(zero, bar.getMakerVolume());
        assertEquals(zero, bar.getMakerAmount());
        assertEquals(1, bar.getMakerTrades());
    }

    @Test
    public void testAddTradeWithZeroPrice() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(5), zero, RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);

        assertEquals(numOf(5), bar.getVolume());
        assertEquals(zero, bar.getAmount());
        assertEquals(1, bar.getTrades());
        assertEquals(numOf(5), bar.getBuyVolume());
        assertEquals(zero, bar.getBuyAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(numOf(5), bar.getMakerVolume());
        assertEquals(zero, bar.getMakerAmount());
        assertEquals(1, bar.getMakerTrades());
    }

    @Test
    public void testAddTradeWithLargeTradeCounts() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        // Add many trades
        for (int i = 0; i < 1000; i++) {
            bar.addTrade(numOf(1), numOf(100 + i), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        }

        assertEquals(numOf(1000), bar.getBuyVolume());
        assertEquals(1000, bar.getBuyTrades());
        assertEquals(1000, bar.getMakerTrades());
        assertEquals(1000, bar.getTrades());
    }

    @Test
    public void testAddTradeOnlySideNoLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(5), numOf(100), RealtimeBar.Side.BUY, null);

        assertTrue(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(numOf(5), bar.getBuyVolume());
        assertEquals(numOf(500), bar.getBuyAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(zero, bar.getMakerVolume());
        assertEquals(zero, bar.getTakerVolume());
    }

    @Test
    public void testAddTradeOnlyLiquidityNoSide() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(5), numOf(100), null, RealtimeBar.Liquidity.MAKER);

        assertFalse(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(zero, bar.getBuyVolume());
        assertEquals(zero, bar.getSellVolume());
        assertEquals(numOf(5), bar.getMakerVolume());
        assertEquals(numOf(500), bar.getMakerAmount());
        assertEquals(1, bar.getMakerTrades());
    }

    @Test
    public void testAddTradeNeitherSideNorLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        bar.addTrade(numOf(5), numOf(100), null, null);

        assertFalse(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(zero, bar.getBuyVolume());
        assertEquals(zero, bar.getSellVolume());
        assertEquals(zero, bar.getMakerVolume());
        assertEquals(zero, bar.getTakerVolume());
        // Base bar should still be updated
        assertEquals(numOf(5), bar.getVolume());
        assertEquals(numOf(500), bar.getAmount());
        assertEquals(1, bar.getTrades());
    }

    @Test
    public void testConstructorWithPrePopulatedSideData() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var buyVolume = numOf(100);
        final var sellVolume = numOf(200);
        final var buyAmount = numOf(10000);
        final var sellAmount = numOf(22000);

        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                buyVolume, sellVolume, buyAmount, sellAmount, 10, 20, null, null, null, null, 0, 0, true, false,
                numFactory);

        assertTrue(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(buyVolume, bar.getBuyVolume());
        assertEquals(sellVolume, bar.getSellVolume());
        assertEquals(buyAmount, bar.getBuyAmount());
        assertEquals(sellAmount, bar.getSellAmount());
        assertEquals(10, bar.getBuyTrades());
        assertEquals(20, bar.getSellTrades());
    }

    @Test
    public void testConstructorWithPrePopulatedLiquidityData() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var makerVolume = numOf(150);
        final var takerVolume = numOf(250);
        final var makerAmount = numOf(15000);
        final var takerAmount = numOf(27500);

        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, makerVolume, takerVolume, makerAmount, takerAmount, 15, 25, false, true,
                numFactory);

        assertFalse(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(makerVolume, bar.getMakerVolume());
        assertEquals(takerVolume, bar.getTakerVolume());
        assertEquals(makerAmount, bar.getMakerAmount());
        assertEquals(takerAmount, bar.getTakerAmount());
        assertEquals(15, bar.getMakerTrades());
        assertEquals(25, bar.getTakerTrades());
    }

    @Test
    public void testConstructorWithAllPrePopulatedData() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var buyVolume = numOf(100);
        final var sellVolume = numOf(200);
        final var buyAmount = numOf(10000);
        final var sellAmount = numOf(22000);
        final var makerVolume = numOf(150);
        final var takerVolume = numOf(250);
        final var makerAmount = numOf(15000);
        final var takerAmount = numOf(27500);

        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                buyVolume, sellVolume, buyAmount, sellAmount, 10, 20, makerVolume, takerVolume, makerAmount,
                takerAmount, 15, 25, true, true, numFactory);

        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(buyVolume, bar.getBuyVolume());
        assertEquals(sellVolume, bar.getSellVolume());
        assertEquals(makerVolume, bar.getMakerVolume());
        assertEquals(takerVolume, bar.getTakerVolume());
    }

    @Test
    public void testTradeCountsAccumulateCorrectly() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        // Add 5 buy trades
        for (int i = 0; i < 5; i++) {
            bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, null);
        }

        // Add 3 sell trades
        for (int i = 0; i < 3; i++) {
            bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.SELL, null);
        }

        // Add 4 maker trades (these are separate trades, so they add to total count)
        for (int i = 0; i < 4; i++) {
            bar.addTrade(numOf(1), numOf(100), null, RealtimeBar.Liquidity.MAKER);
        }

        // Add 2 taker trades (these are separate trades, so they add to total count)
        for (int i = 0; i < 2; i++) {
            bar.addTrade(numOf(1), numOf(100), null, RealtimeBar.Liquidity.TAKER);
        }

        // Each addTrade() increments the total trade count
        assertEquals(14, bar.getTrades()); // 5 + 3 + 4 + 2
        assertEquals(5, bar.getBuyTrades());
        assertEquals(3, bar.getSellTrades());
        assertEquals(4, bar.getMakerTrades());
        assertEquals(2, bar.getTakerTrades());
    }

    @Test
    public void testTradeCountsWithOverlappingSideAndLiquidity() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        // Add trades with both side and liquidity (each trade counts once)
        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.TAKER);
        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.SELL, RealtimeBar.Liquidity.MAKER);
        bar.addTrade(numOf(1), numOf(100), RealtimeBar.Side.SELL, RealtimeBar.Liquidity.TAKER);

        assertEquals(4, bar.getTrades()); // 4 total trades
        assertEquals(2, bar.getBuyTrades());
        assertEquals(2, bar.getSellTrades());
        assertEquals(2, bar.getMakerTrades());
        assertEquals(2, bar.getTakerTrades());
    }

    @Test
    public void testAmountCalculationIsCorrect() {
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var period = Duration.ofMinutes(1);
        final var zero = numFactory.zero();
        final var bar = new BaseRealtimeBar(period, start, start.plus(period), null, null, null, null, zero, zero, 0,
                null, null, null, null, 0, 0, null, null, null, null, 0, 0, false, false, numFactory);

        // Trade: volume=2, price=100 -> amount=200
        bar.addTrade(numOf(2), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        assertEquals(numOf(200), bar.getBuyAmount());
        assertEquals(numOf(200), bar.getMakerAmount());

        // Trade: volume=3, price=110 -> amount=330
        bar.addTrade(numOf(3), numOf(110), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        assertEquals(numOf(530), bar.getBuyAmount()); // 200 + 330
        assertEquals(numOf(530), bar.getMakerAmount()); // 200 + 330
    }
}
