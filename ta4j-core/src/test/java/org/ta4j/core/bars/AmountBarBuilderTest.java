/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;

class AmountBarBuilderTest {

    @Test
    void createBarsWithSetAmountByVolume() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        final var bar = series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3);
        Assertions.assertThrows(IllegalArgumentException.class, () -> bar.amount(1));
    }

    @Test
    void addWithSetAmountByVolume() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3).add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(7)
                .add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(3))).closePrice(4).volume(2).add();

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
        assertEquals(numFactory.numOf(12), bar1.getAmount());
        assertEquals(10, bar1.getTrades());

        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(4))).closePrice(2).volume(1).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .trades(5)
                .add();
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(6).volume(1).add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume());
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
    void addWithoutSetAmountByVolume() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, false))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).amount(1).trades(3).add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(1)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .amount(5)
                .trades(7)
                .add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(4)
                .volume(2)
                .amount(8)
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
        assertEquals(numFactory.numOf(12), bar1.getAmount());
        assertEquals(10, bar1.getTrades());

        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .amount(3)
                .trades(5)
                .add();
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(6)))
                .closePrice(6)
                .volume(1)
                .amount(6)
                .add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume());
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
    void addTradeBuildsAmountBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder().addTrade(start, numFactory.two(), numFactory.two());
        series.barBuilder().addTrade(start.plusSeconds(10), numFactory.one(), numFactory.three());
        assertEquals(0, series.getBarCount());

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.one(), numFactory.three());
        assertEquals(1, series.getBarCount());

        final var bar = series.getBar(0);
        assertEquals(start, bar.getBeginTime());
        assertEquals(start.plusSeconds(20), bar.getEndTime());
        assertNumEquals(4, bar.getVolume());
        assertNumEquals(2, bar.getOpenPrice());
        assertNumEquals(3, bar.getClosePrice());
        assertNumEquals(3, bar.getHighPrice());
        assertNumEquals(2, bar.getLowPrice());
        assertEquals(numFactory.numOf(10), bar.getAmount());
        assertEquals(3, bar.getTrades());
    }

    @Test
    void addTradeBuildsRealtimeAmountBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.two(), numFactory.two(), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.one(), numFactory.three(), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(20), numFactory.one(), numFactory.three(), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);

        Assertions.assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        Assertions.assertTrue(bar.hasSideData());
        Assertions.assertTrue(bar.hasLiquidityData());
        assertNumEquals(3, bar.getBuyVolume());
        assertNumEquals(1, bar.getSellVolume());
        assertNumEquals(7, bar.getBuyAmount());
        assertNumEquals(3, bar.getSellAmount());
        Assertions.assertEquals(2, bar.getBuyTrades());
        Assertions.assertEquals(1, bar.getSellTrades());
        assertNumEquals(3, bar.getMakerVolume());
        assertNumEquals(1, bar.getTakerVolume());
        assertNumEquals(7, bar.getMakerAmount());
        assertNumEquals(3, bar.getTakerAmount());
        Assertions.assertEquals(2, bar.getMakerTrades());
        Assertions.assertEquals(1, bar.getTakerTrades());
    }

    @Test
    void addTradeRejectsSideDataWhenRealtimeDisabled() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        Assertions.assertThrows(IllegalStateException.class, () -> series.barBuilder()
                .addTrade(start, numFactory.one(), numFactory.two(), RealtimeBar.Side.BUY, null));
    }

    @Test
    void addTradeResetsSideAndLiquidityAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.one(), numFactory.numOf(5), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.one(), numFactory.numOf(5), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.one(), numFactory.numOf(5), null, null);
        series.barBuilder().addTrade(start.plusSeconds(30), numFactory.one(), numFactory.numOf(5), null, null);

        Assertions.assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        Assertions.assertTrue(first.hasSideData());
        Assertions.assertTrue(first.hasLiquidityData());

        final var second = (RealtimeBar) series.getBar(1);
        assertNumEquals(0, second.getBuyVolume());
        assertNumEquals(0, second.getSellVolume());
        assertNumEquals(0, second.getMakerVolume());
        assertNumEquals(0, second.getTakerVolume());
        Assertions.assertEquals(0, second.getBuyTrades());
        Assertions.assertEquals(0, second.getMakerTrades());
        Assertions.assertEquals(2, second.getTrades());
    }

    @Test
    void addTradeCarriesAmountRemainderAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.three(), numFactory.two(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.three(), numFactory.two(), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.two(), numFactory.two(), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.two(), numFactory.two(), null, null);

        Assertions.assertEquals(2, series.getBarCount());
        final var second = (RealtimeBar) series.getBar(1);
        Assertions.assertFalse(second.hasSideData());
        Assertions.assertFalse(second.hasLiquidityData());
        assertNumEquals(5, second.getVolume());
        assertNumEquals(10, second.getAmount());
        Assertions.assertEquals(2, second.getTrades());
    }

    @Test
    void addTradeCarriesSideAndLiquidityProportionally() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(
                        new AmountBarBuilderFactory(10, true, true, RemainderCarryOverPolicy.PROPORTIONAL))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.three(), numFactory.two(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.three(), numFactory.two(), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.two(), numFactory.two(), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.two(), numFactory.two(), null, null);

        Assertions.assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        assertNumEquals(3, first.getBuyVolume());
        assertNumEquals(2, first.getSellVolume());
        assertNumEquals(6, first.getBuyAmount());
        assertNumEquals(4, first.getSellAmount());
        assertNumEquals(3, first.getMakerVolume());
        assertNumEquals(2, first.getTakerVolume());
        assertNumEquals(6, first.getMakerAmount());
        assertNumEquals(4, first.getTakerAmount());

        final var second = (RealtimeBar) series.getBar(1);
        Assertions.assertTrue(second.hasSideData());
        Assertions.assertTrue(second.hasLiquidityData());
        assertNumEquals(0, second.getBuyVolume());
        assertNumEquals(1, second.getSellVolume());
        assertNumEquals(0, second.getBuyAmount());
        assertNumEquals(2, second.getSellAmount());
        assertNumEquals(0, second.getMakerVolume());
        assertNumEquals(1, second.getTakerVolume());
        assertNumEquals(0, second.getMakerAmount());
        assertNumEquals(2, second.getTakerAmount());
        assertNumEquals(5, second.getVolume());
        assertNumEquals(10, second.getAmount());
        Assertions.assertEquals(2, second.getTrades());
    }

    @Test
    void addTradeCarriesTradeCountsProportionally() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(new AmountBarBuilderFactory(5, true, true,
                        RemainderCarryOverPolicy.PROPORTIONAL_WITH_TRADE_COUNT))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.two(), numFactory.two(), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numFactory.two(), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.one(), numFactory.two(), null, null);

        Assertions.assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        Assertions.assertEquals(1, first.getTrades());
        Assertions.assertEquals(1, first.getBuyTrades());
        Assertions.assertEquals(0, first.getSellTrades());
        Assertions.assertEquals(1, first.getMakerTrades());
        Assertions.assertEquals(0, first.getTakerTrades());

        final var second = (RealtimeBar) series.getBar(1);
        Assertions.assertEquals(2, second.getTrades());
        Assertions.assertEquals(0, second.getBuyTrades());
        Assertions.assertEquals(1, second.getSellTrades());
        Assertions.assertEquals(0, second.getMakerTrades());
        Assertions.assertEquals(1, second.getTakerTrades());
    }

    @Test
    void addTradeRejectsOutOfOrderTimestamp() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:10Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.one(), numFactory.two());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(1), numFactory.one(), numFactory.two()));
    }

    @Test
    void addTradeWithZeroPriceUsesLastTradePriceForRemainder() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.addTrade(start, numFactory.three(), numFactory.two());
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numFactory.two());
        assertEquals(1, series.getBarCount());

        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
    }

    @Test
    void addWithZeroPriceWhenThresholdExceededUsesLastTradePrice() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
        assertNumEquals(2, bar1.getClosePrice());
    }

    @Test
    void addTradeThenBuilderWithZeroPriceHandlesRemainderCorrectly() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        final var builder = series.barBuilder();
        builder.addTrade(start, numFactory.three(), numFactory.two());
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numFactory.two());
        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
        assertNumEquals(2, bar1.getClosePrice());
    }

    @Test
    void addTradeRejectsNullTime() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var builder = series.barBuilder();

        Assertions.assertThrows(NullPointerException.class,
                () -> builder.addTrade(null, numFactory.one(), numFactory.two()));
    }

    @Test
    void addTradeRejectsNullVolume() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        Assertions.assertThrows(NullPointerException.class, () -> builder.addTrade(start, null, numFactory.two()));
    }

    @Test
    void addTradeRejectsNullPrice() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        Assertions.assertThrows(NullPointerException.class, () -> builder.addTrade(start, numFactory.one(), null));
    }

    @Test
    void bindToRejectsNullSeries() {
        final var builder = new AmountBarBuilder(10, true);
        Assertions.assertThrows(NullPointerException.class, () -> builder.bindTo(null));
    }

    @Test
    void openPriceSetterThrowsException() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var builder = series.barBuilder();
        final var numFactory = DecimalNumFactory.getInstance();

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.openPrice(numFactory.one()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.openPrice(1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.openPrice("1"));
    }

    @Test
    void highPriceSetterThrowsException() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var builder = series.barBuilder();
        final var numFactory = DecimalNumFactory.getInstance();

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.highPrice(numFactory.one()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.highPrice(1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.highPrice("1"));
    }

    @Test
    void lowPriceSetterThrowsException() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var builder = series.barBuilder();
        final var numFactory = DecimalNumFactory.getInstance();

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.lowPrice(numFactory.one()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.lowPrice(1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.lowPrice("1"));
    }

    @Test
    void amountSetterThrowsWhenSetAmountByVolumeIsTrue() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var builder = series.barBuilder();
        final var numFactory = DecimalNumFactory.getInstance();

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.amount(numFactory.one()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.amount(1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.amount("1"));
    }

    @Test
    void exactThresholdMatchCreatesBar() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(10, bar.getAmount());
        assertNumEquals(5, bar.getVolume());
    }

    @Test
    void priceTrackingHighLow() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(20, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.numOf(5)).volume(numFactory.one()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.numOf(10))
                .volume(numFactory.one())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.numOf(3))
                .volume(numFactory.one())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(30))
                .closePrice(numFactory.numOf(15))
                .volume(numFactory.one())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(5, bar.getOpenPrice());
        assertNumEquals(15, bar.getHighPrice());
        assertNumEquals(3, bar.getLowPrice());
        assertNumEquals(15, bar.getClosePrice());
    }

    @Test
    void timePeriodAccumulation() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(20, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.three())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(30))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertEquals(oneDay.multipliedBy(4), bar.getTimePeriod());
    }

    @Test
    void volumeAccumulationAcrossMultipleAdds() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(20, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.one()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.three())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(30))
                .closePrice(numFactory.two())
                .volume(numFactory.numOf(4))
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(10, bar.getVolume());
    }

    @Test
    void multipleConsecutiveBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        assertEquals(1, series.getBarCount());

        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.three())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(30))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        assertEquals(2, series.getBarCount());

        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(40))
                .closePrice(numFactory.two())
                .volume(numFactory.three())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(50))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        assertEquals(3, series.getBarCount());

        for (int i = 0; i < 3; i++) {
            final var bar = series.getBar(i);
            assertNumEquals(10, bar.getAmount());
            assertNumEquals(5, bar.getVolume());
        }
    }

    @Test
    void remainderCalculationAccuracy() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());

        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(30))
                .closePrice(numFactory.two())
                .volume(numFactory.one())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(40))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(10, bar2.getAmount());
        assertNumEquals(5, bar2.getVolume());
    }

    @Test
    void builderApiWithNumberAndStringOverloads() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, false))
                .build();
        final var oneDay = Duration.ofDays(1);
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(2).volume(3).amount(6).add();
        builder.timePeriod(oneDay).endTime(start.plusSeconds(10)).closePrice("2").volume("2").amount("4").add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(10, bar.getAmount());
        assertNumEquals(5, bar.getVolume());
    }

    @Test
    void tradesStringOverload() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var oneDay = Duration.ofDays(1);
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay)
                .endTime(start)
                .closePrice(numFactory.two())
                .volume(numFactory.three())
                .trades("3")
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .trades("2")
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertEquals(5, bar.getTrades());
    }

    @Test
    void buildMethodReturnsRealtimeBarWhenEnabled() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        final var builder = series.barBuilder();
        builder.addTrade(start, numFactory.one(), numFactory.two(), RealtimeBar.Side.BUY, null);
        builder.addTrade(start.plusSeconds(10), numFactory.one(), numFactory.two(), RealtimeBar.Side.SELL, null);

        final var bar = builder.build();
        Assertions.assertInstanceOf(org.ta4j.core.BaseRealtimeBar.class, bar);
    }

    @Test
    void bindToMethodBindsBuilderToSeries() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var builder = series.barBuilder();
        final var numFactory = DecimalNumFactory.getInstance();
        final var oneDay = Duration.ofDays(1);
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
    }

    @Test
    void zeroPriceTradeWithValidLastTradePrice() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        final var builder = series.barBuilder();
        builder.addTrade(start, numFactory.three(), numFactory.two());
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numFactory.two());
        assertEquals(1, series.getBarCount());

        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
    }

    @Test
    void carryOverPolicyNoneDoesNotCarrySideData() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true, RemainderCarryOverPolicy.NONE))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.three(), numFactory.two(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.three(), numFactory.two(), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);
        builder.addTrade(start.plusSeconds(20), numFactory.two(), numFactory.two(), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.two(), numFactory.two(), null, null);

        assertEquals(2, series.getBarCount());
        final var second = (RealtimeBar) series.getBar(1);
        Assertions.assertFalse(second.hasSideData());
        Assertions.assertFalse(second.hasLiquidityData());
    }

    @Test
    void divisionByZeroFixWithDoubleNumFactory() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final var numFactory = DoubleNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.addTrade(start, numFactory.three(), numFactory.two());
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numFactory.two());
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
    }

    @Test
    void remainderCalculationWithDoubleNumFactory() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final var numFactory = DoubleNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(20))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(10, bar1.getAmount());
        assertNumEquals(5, bar1.getVolume());
    }

    @Test
    void thresholdExceedanceWithDoubleNumFactory() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final var numFactory = DoubleNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var oneDay = Duration.ofDays(1);

        final var builder = series.barBuilder();
        builder.timePeriod(oneDay).endTime(start).closePrice(numFactory.two()).volume(numFactory.three()).add();
        builder.timePeriod(oneDay)
                .endTime(start.plusSeconds(10))
                .closePrice(numFactory.two())
                .volume(numFactory.two())
                .add();

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertNumEquals(10, bar.getAmount());
        assertNumEquals(5, bar.getVolume());
    }

    @Test
    void carryOverProportionalWithDoubleNumFactory() {
        final var series = new BaseBarSeriesBuilder()
                .withBarBuilderFactory(
                        new AmountBarBuilderFactory(10, true, true, RemainderCarryOverPolicy.PROPORTIONAL))
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final var numFactory = DoubleNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.three(), numFactory.two(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.three(), numFactory.two(), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);
        builder.addTrade(start.plusSeconds(20), numFactory.two(), numFactory.two(), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.two(), numFactory.two(), null, null);

        Assertions.assertEquals(2, series.getBarCount());
        final var first = (RealtimeBar) series.getBar(0);
        assertNumEquals(3, first.getBuyVolume());
        assertNumEquals(2, first.getSellVolume());
        assertNumEquals(6, first.getBuyAmount());
        assertNumEquals(4, first.getSellAmount());

        final var second = (RealtimeBar) series.getBar(1);
        Assertions.assertTrue(second.hasSideData());
        Assertions.assertTrue(second.hasLiquidityData());
        assertNumEquals(0, second.getBuyVolume());
        assertNumEquals(1, second.getSellVolume());
        assertNumEquals(0, second.getBuyAmount());
        assertNumEquals(2, second.getSellAmount());
    }

}
