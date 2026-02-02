/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.num.DecimalNumFactory;

public class VolumeBarBuilderTest {

    @Test
    public void add() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(4)).build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1: aggregated volume = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3).add();

        // add bar 2: aggregated volume = 1 + 1 = 2
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();

        // add bar 3: aggregated volume = 1 + 1 + 1 = 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(7)
                .add();

        // add bar 4: aggregated volume = 1 + 1 + 1 + 2= 5
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(4)
                .volume(2) // sum is 5 and 1 moved to next bar (= remainder)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume());
        assertNumEquals(1, bar1.getOpenPrice());
        assertNumEquals(4, bar1.getClosePrice());
        assertNumEquals(5, bar1.getHighPrice());
        assertNumEquals(1, bar1.getLowPrice());
        assertEquals(oneDay.multipliedBy(4), bar1.getTimePeriod());
        final var beginTime0 = now.minus(oneDay);
        final var endTime4 = now.plus(Duration.ofDays(3));
        assertEquals(beginTime0, bar1.getBeginTime());
        assertEquals(endTime4, bar1.getEndTime());
        final var numFactory = DecimalNumFactory.getInstance();
        assertEquals(numFactory.numOf(16), bar1.getAmount()); // 1 * 1 + 1 * 2 + 1 * 5 + 2 * 4 = 16
        assertEquals(10, bar1.getTrades());

        // add bar 5: aggregated volume = 1 + 1= 2
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(2)
                .volume(1)
                .amount(12)
                .add();

        // add bar 6: aggregated volume = 1 + 1 + 1= 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .trades(5)
                .add();

        // add bar 7: aggregated volume = 1 + 1 + 1+ 1 = 4
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(6).volume(1).add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(4, bar2.getVolume());
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount());
        assertEquals(5, bar2.getTrades());
    }

    @Test
    public void addTradeBuildsVolumeBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder().addTrade(start, numFactory.numOf(1), numFactory.numOf(10));
        series.barBuilder().addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(12));
        assertEquals(0, series.getBarCount());

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(11));
        assertEquals(1, series.getBarCount());

        final var bar = series.getBar(0);
        assertEquals(start, bar.getBeginTime());
        assertEquals(start.plusSeconds(20), bar.getEndTime());
        assertNumEquals(3, bar.getVolume());
        assertNumEquals(10, bar.getOpenPrice());
        assertNumEquals(12, bar.getHighPrice());
        assertNumEquals(10, bar.getLowPrice());
        assertNumEquals(11, bar.getClosePrice());
        assertEquals(numFactory.numOf(33), bar.getAmount());
        assertEquals(3, bar.getTrades());
    }

    @Test
    public void addTradeBuildsRealtimeVolumeBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(10), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(12), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(11), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);

        assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        assertTrue(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertNumEquals(2, bar.getBuyVolume());
        assertNumEquals(1, bar.getSellVolume());
        assertNumEquals(21, bar.getBuyAmount());
        assertNumEquals(12, bar.getSellAmount());
        assertEquals(2, bar.getBuyTrades());
        assertEquals(1, bar.getSellTrades());
        assertNumEquals(2, bar.getMakerVolume());
        assertNumEquals(1, bar.getTakerVolume());
        assertNumEquals(21, bar.getMakerAmount());
        assertNumEquals(12, bar.getTakerAmount());
        assertEquals(2, bar.getMakerTrades());
        assertEquals(1, bar.getTakerTrades());
    }

    @Test
    public void addTradeRejectsSideDataWhenRealtimeDisabled() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(10), RealtimeBar.Side.BUY, null));
    }

    @Test
    public void addTradeResetsSideAndLiquidityAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(10), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(12), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(11), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);

        series.barBuilder().addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(13), null, null);
        series.barBuilder().addTrade(start.plusSeconds(40), numFactory.numOf(1), numFactory.numOf(14), null, null);
        series.barBuilder().addTrade(start.plusSeconds(50), numFactory.numOf(1), numFactory.numOf(15), null, null);

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
        assertEquals(3, second.getTrades());
    }

    @Test
    public void addTradeCarriesVolumeRemainderAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(10), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(10), null, null);

        assertEquals(2, series.getBarCount());
        final var second = (RealtimeBar) series.getBar(1);
        assertFalse(second.hasSideData());
        assertFalse(second.hasLiquidityData());
        assertNumEquals(3, second.getVolume());
        assertEquals(2, second.getTrades());
    }

    @Test
    public void addTradeCarriesSideAndLiquidityProportionally() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(new VolumeBarBuilderFactory(3, true, RemainderCarryOverPolicy.PROPORTIONAL))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(10), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(10), null, null);

        assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        assertNumEquals(2, first.getBuyVolume());
        assertNumEquals(1, first.getSellVolume());
        assertNumEquals(20, first.getBuyAmount());
        assertNumEquals(10, first.getSellAmount());
        assertNumEquals(2, first.getMakerVolume());
        assertNumEquals(1, first.getTakerVolume());
        assertNumEquals(20, first.getMakerAmount());
        assertNumEquals(10, first.getTakerAmount());

        final var second = (RealtimeBar) series.getBar(1);
        assertTrue(second.hasSideData());
        assertTrue(second.hasLiquidityData());
        assertNumEquals(0, second.getBuyVolume());
        assertNumEquals(1, second.getSellVolume());
        assertNumEquals(0, second.getBuyAmount());
        assertNumEquals(10, second.getSellAmount());
        assertNumEquals(0, second.getMakerVolume());
        assertNumEquals(1, second.getTakerVolume());
        assertNumEquals(0, second.getMakerAmount());
        assertNumEquals(10, second.getTakerAmount());
        assertNumEquals(3, second.getVolume());
        assertEquals(2, second.getTrades());
    }

    @Test
    public void addTradeCarriesTradeCountsProportionally() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(
                        new VolumeBarBuilderFactory(3, true, RemainderCarryOverPolicy.PROPORTIONAL_WITH_TRADE_COUNT))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(2), numFactory.numOf(10), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(10), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(10), null, null);

        assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        assertEquals(1, first.getTrades());
        assertEquals(1, first.getBuyTrades());
        assertEquals(0, first.getSellTrades());
        assertEquals(1, first.getMakerTrades());
        assertEquals(0, first.getTakerTrades());

        final var second = (RealtimeBar) series.getBar(1);
        assertEquals(3, second.getTrades());
        assertEquals(0, second.getBuyTrades());
        assertEquals(1, second.getSellTrades());
        assertEquals(0, second.getMakerTrades());
        assertEquals(1, second.getTakerTrades());
    }

    @Test
    public void addTradeRejectsOutOfOrderTimestamp() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new VolumeBarBuilderFactory(3)).build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:10Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.numOf(1), numFactory.numOf(10));

        assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(1), numFactory.numOf(1), numFactory.numOf(10)));
    }
}
