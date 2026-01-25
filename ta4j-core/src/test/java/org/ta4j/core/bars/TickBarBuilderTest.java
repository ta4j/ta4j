/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.num.DecimalNumFactory;

public class TickBarBuilderTest {

    @Test
    public void add() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(5)).build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(9)
                .add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(3))).closePrice(1).volume(1).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(4)
                .volume(2)
                .trades(1)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(6, bar1.getVolume());
        assertNumEquals(1, bar1.getOpenPrice());
        assertNumEquals(4, bar1.getClosePrice());
        assertNumEquals(5, bar1.getHighPrice());
        assertNumEquals(1, bar1.getLowPrice());
        assertEquals(oneDay.multipliedBy(5), bar1.getTimePeriod());
        final var beginTime0 = now.minus(oneDay);
        final var endTime4 = now.plus(Duration.ofDays(4));
        assertEquals(beginTime0, bar1.getBeginTime());
        assertEquals(endTime4, bar1.getEndTime());

        final var numFactory = DecimalNumFactory.getInstance();
        assertEquals(numFactory.numOf(24), bar1.getAmount());
        assertEquals(10, bar1.getTrades());

        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(2)
                .volume(1)
                .amount(24)
                .add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(3).volume(1).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(7))).closePrice(6).volume(2).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(8))).closePrice(2).volume(1).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(9)))
                .closePrice(5)
                .volume(2)
                .trades(100)
                .add();
        assertEquals(2, series.getBarCount());

        final var bar2 = series.getBar(1);
        assertNumEquals(7, bar2.getVolume());
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(5, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(5), bar1.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(5)).minus(oneDay);
        final var endTime9 = now.plus(Duration.ofDays(9));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime9, bar2.getEndTime());
        assertEquals(numFactory.numOf(24), bar2.getAmount());
        assertEquals(100, bar2.getTrades());
    }

    @Test
    public void addTradeBuildsTickBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(2)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder().addTrade(start, numFactory.numOf(1), numFactory.numOf(100));
        assertEquals(0, series.getBarCount());

        series.barBuilder().addTrade(start.plusSeconds(30), numFactory.numOf(2), numFactory.numOf(110));
        assertEquals(1, series.getBarCount());

        final var bar = series.getBar(0);
        assertEquals(start, bar.getBeginTime());
        assertEquals(start.plusSeconds(30), bar.getEndTime());
        assertNumEquals(3, bar.getVolume());
        assertNumEquals(100, bar.getOpenPrice());
        assertNumEquals(110, bar.getClosePrice());
        assertEquals(2, bar.getTrades());
    }

    @Test
    public void addTradeBuildsRealtimeTickBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(2, true)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(100), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.TAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(30), numFactory.numOf(2), numFactory.numOf(90), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.MAKER);

        assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertNumEquals(1, bar.getBuyVolume());
        assertNumEquals(2, bar.getSellVolume());
        assertNumEquals(100, bar.getBuyAmount());
        assertNumEquals(180, bar.getSellAmount());
        assertEquals(1, bar.getBuyTrades());
        assertEquals(1, bar.getSellTrades());
        assertNumEquals(2, bar.getMakerVolume());
        assertNumEquals(1, bar.getTakerVolume());
        assertNumEquals(180, bar.getMakerAmount());
        assertNumEquals(100, bar.getTakerAmount());
        assertEquals(1, bar.getMakerTrades());
        assertEquals(1, bar.getTakerTrades());
    }

    @Test
    public void addTradeRejectsSideDataWhenRealtimeDisabled() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(2)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(100), RealtimeBar.Side.BUY, null));
    }

    @Test
    public void addTradeResetsSideAndLiquidityAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(2, true)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(100), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(110), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(120), null, null);
        series.barBuilder().addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(130), null, null);

        assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        assertTrue(first.hasSideData());
        assertTrue(first.hasLiquidityData());

        final var second = (RealtimeBar) series.getBar(1);
        assertEquals(numFactory.zero(), second.getBuyVolume());
        assertEquals(numFactory.zero(), second.getSellVolume());
        assertEquals(numFactory.zero(), second.getMakerVolume());
        assertEquals(numFactory.zero(), second.getTakerVolume());
        assertEquals(0, second.getBuyTrades());
        assertEquals(0, second.getMakerTrades());
        assertEquals(2, second.getTrades());
    }

    @Test
    public void addTradeRejectsOutOfOrderTimestamp() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new TickBarBuilderFactory(2)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:10Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.numOf(1), numFactory.numOf(100));

        assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(1), numFactory.numOf(1), numFactory.numOf(90)));
    }
}
