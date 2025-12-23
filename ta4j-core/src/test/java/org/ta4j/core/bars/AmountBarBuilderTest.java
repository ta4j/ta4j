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

class AmountBarBuilderTest {

    @Test
    void createBarsWithSetAmountByVolume() {

        // setAmountByVolume = true:
        // => AmountBar.amount can only be built from closePrice*volume
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        final var bar = series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3);

        // should throw an exception as the amount cannot be explicitly set due to
        // "setAmountByVolume = true"
        Assertions.assertThrows(IllegalArgumentException.class, () -> bar.amount(1));
    }

    @Test
    void addWithSetAmountByVolume() {
        // setAmountByVolume = true
        // => amount is added by "volume*closePrice"
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, true))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).trades(3).add();

        // add bar 2:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 1 * 1 + 2 * 1 = 3
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(1))).closePrice(2).volume(1).add();

        // add bar 3:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 = 8
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .trades(7)
                .add();

        // add bar 4:
        // aggregated volume = 1 + 1 + 1 + 2 = 5
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 + 4 * 2 = 16
        // => sum of volume is 5 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 16 and 4 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 5 - 4 / 4 = 4
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(3))).closePrice(4).volume(2).add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume()); // adapted aggregated volume = 4
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
        assertEquals(numFactory.numOf(12), bar1.getAmount()); // amountThreshold = 12
        assertEquals(10, bar1.getTrades());

        // add bar 5:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 4 + 2 * 1 = 6
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(4))).closePrice(2).volume(1).add();

        // add bar 6:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 4 + 2 * 1 + 3 * 1 = 9
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .trades(5)
                .add();

        // add bar 7:
        // aggregated volume = 1 + 1 + 1 + 1 = 4
        // aggregated amount = 4 + 2 * 1 + 3 * 1 + 6 * 1 = 15
        // => sum of volume is 4 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 15 and 3 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 4 - 3 / 6 = 3.5
        series.barBuilder().timePeriod(oneDay).endTime(now.plus(Duration.ofDays(6))).closePrice(6).volume(1).add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume()); // adapted aggregated volume = 3.5
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount()); // amountThreshold = 12
        assertEquals(5, bar2.getTrades());
    }

    @Test
    void addWithoutSetAmountByVolume() {
        // setAmountByVolume = false:
        // => amount is added by provided "amount"-field
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(12, false))
                .build();
        final var now = Instant.now();
        final var oneDay = Duration.ofDays(1);

        // add bar 1:
        // aggregated volume = 1
        // aggregated amount = 1 * 1 = 1
        series.barBuilder().timePeriod(oneDay).endTime(now).closePrice(1).volume(1).amount(1).trades(3).add();

        // add bar 2:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 1 * 1 + 2 * 1 = 3
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(1)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();

        // add bar 3:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 = 8
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(2)))
                .closePrice(5)
                .volume(1)
                .amount(5)
                .trades(7)
                .add();

        // add bar 4:
        // aggregated volume = 1 + 1 + 1 + 2 = 5
        // aggregated amount = 1 * 1 + 2 * 1 + 5 * 1 + 4 * 2 = 16
        // => sum of volume is 5 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 16 and 4 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 5 - 4 / 4 = 4
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(3)))
                .closePrice(4)
                .volume(2)
                .amount(8)
                .add();

        assertEquals(1, series.getBarCount());
        final var bar1 = series.getBar(0);
        assertNumEquals(4, bar1.getVolume()); // adapted aggregated volume = 4
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
        assertEquals(numFactory.numOf(12), bar1.getAmount()); // amountThreshold = 12
        assertEquals(10, bar1.getTrades());

        // add bar 5:
        // aggregated volume = 1 + 1 = 2
        // aggregated amount = 4 + 2 * 1 = 6
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(4)))
                .closePrice(2)
                .volume(1)
                .amount(2)
                .add();

        // add bar 6:
        // aggregated volume = 1 + 1 + 1 = 3
        // aggregated amount = 4 + 2 * 1 + 3 * 1 = 9
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(5)))
                .closePrice(3)
                .volume(1)
                .amount(3)
                .trades(5)
                .add();

        // add bar 7:
        // aggregated volume = 1 + 1 + 1 + 1 = 4
        // aggregated amount = 4 + 2 * 1 + 3 * 1 + 6 * 1 = 15
        // => sum of volume is 4 and 1 moved to next bar (= volume remainder)
        // => sum of amount is 15 and 3 moved to next bar (= amount remainder)
        // => adapted aggregated volume = aggregated volume - (amount remainder /
        // closePrice) = 4 - 3 / 6 = 3.5
        series.barBuilder()
                .timePeriod(oneDay)
                .endTime(now.plus(Duration.ofDays(6)))
                .closePrice(6)
                .volume(1)
                .amount(6)
                .add();

        assertEquals(2, series.getBarCount());
        final var bar2 = series.getBar(1);
        assertNumEquals(3.5, bar2.getVolume()); // adapted aggregated volume = 3.5
        assertNumEquals(2, bar2.getOpenPrice());
        assertNumEquals(6, bar2.getClosePrice());
        assertNumEquals(6, bar2.getHighPrice());
        assertNumEquals(2, bar2.getLowPrice());
        assertEquals(oneDay.multipliedBy(3), bar2.getTimePeriod());
        final var beginTime5 = now.plus(Duration.ofDays(4)).minus(oneDay);
        final var endTime7 = now.plus(Duration.ofDays(6));
        assertEquals(beginTime5, bar2.getBeginTime());
        assertEquals(endTime7, bar2.getEndTime());
        assertEquals(numFactory.numOf(12), bar2.getAmount()); // amountThreshold = 12
        assertEquals(5, bar2.getTrades());
    }

    @Test
    void addTradeBuildsAmountBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder().addTrade(start, numFactory.numOf(2), numFactory.numOf(2));
        series.barBuilder().addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(3));
        assertEquals(0, series.getBarCount());

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(3));
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
                .addTrade(start, numFactory.numOf(2), numFactory.numOf(2), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(3), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(3), RealtimeBar.Side.BUY,
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
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(2), RealtimeBar.Side.BUY, null));
    }

    @Test
    void addTradeResetsSideAndLiquidityAcrossBars() {
        final var series = new BaseBarSeriesBuilder().withBarBuilderFactory(new AmountBarBuilderFactory(10, true, true))
                .build();
        final var numFactory = DecimalNumFactory.getInstance();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        series.barBuilder()
                .addTrade(start, numFactory.numOf(1), numFactory.numOf(5), RealtimeBar.Side.BUY,
                        RealtimeBar.Liquidity.MAKER);
        series.barBuilder()
                .addTrade(start.plusSeconds(10), numFactory.numOf(1), numFactory.numOf(5), RealtimeBar.Side.SELL,
                        RealtimeBar.Liquidity.TAKER);

        series.barBuilder().addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(5), null, null);
        series.barBuilder().addTrade(start.plusSeconds(30), numFactory.numOf(1), numFactory.numOf(5), null, null);

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

        builder.addTrade(start, numFactory.numOf(3), numFactory.numOf(2), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(3), numFactory.numOf(2), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(2), numFactory.numOf(2), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.numOf(2), numFactory.numOf(2), null, null);

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

        builder.addTrade(start, numFactory.numOf(3), numFactory.numOf(2), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(3), numFactory.numOf(2), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(2), numFactory.numOf(2), null, null);
        builder.addTrade(start.plusSeconds(30), numFactory.numOf(2), numFactory.numOf(2), null, null);

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

        builder.addTrade(start, numFactory.numOf(2), numFactory.numOf(2), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.numOf(2), numFactory.numOf(2), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        builder.addTrade(start.plusSeconds(20), numFactory.numOf(1), numFactory.numOf(2), null, null);

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

        builder.addTrade(start, numFactory.numOf(1), numFactory.numOf(2));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(1), numFactory.numOf(1), numFactory.numOf(2)));
    }
}
