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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TimeBarBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TimeBarBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void testBuildBarWithEndTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .endTime(endTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testBuildBarWithBeginTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .beginTime(beginTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testBuildBarWithEndTimeAndBeginTime() {

        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final Bar bar = new TimeBarBuilder(numFactory).timePeriod(duration)
                .endTime(endTime)
                .beginTime(beginTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }

    @Test
    public void testCalculateAmountIfMissing() {
        final Instant beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final Instant endTime = Instant.parse("2014-06-25T01:00:00Z");
        final Duration duration = Duration.between(beginTime, endTime);

        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder().timePeriod(duration).endTime(endTime).beginTime(beginTime).closePrice(10).volume(20).add();

        assertEquals(numOf(200), series.getBar(0).getAmount());
    }

    @Test
    public void addTradeBuildsRealtimeBarWhenEnabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var start = Instant.parse("2024-01-01T00:00:30Z");

        final var builder = series.barBuilder();
        builder.addTrade(start, numOf(1), numOf(100), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numOf(2), numOf(110), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertTrue(bar instanceof RealtimeBar);
        final var realtimeBar = (RealtimeBar) bar;
        assertTrue(realtimeBar.hasSideData());
        assertTrue(realtimeBar.hasLiquidityData());
        assertEquals(numOf(1), realtimeBar.getBuyVolume());
        assertEquals(numOf(2), realtimeBar.getSellVolume());
        assertEquals(numOf(100), realtimeBar.getBuyAmount());
        assertEquals(numOf(220), realtimeBar.getSellAmount());
        assertEquals(1, realtimeBar.getBuyTrades());
        assertEquals(1, realtimeBar.getSellTrades());
        assertEquals(numOf(1), realtimeBar.getMakerVolume());
        assertEquals(numOf(2), realtimeBar.getTakerVolume());
        assertEquals(numOf(100), realtimeBar.getMakerAmount());
        assertEquals(numOf(220), realtimeBar.getTakerAmount());
        assertEquals(1, realtimeBar.getMakerTrades());
        assertEquals(1, realtimeBar.getTakerTrades());
    }

    @Test
    public void addTradeRejectsSideDataWhenRealtimeDisabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var start = Instant.parse("2024-01-01T00:00:30Z");

        assertThrows(IllegalStateException.class,
                () -> series.barBuilder().addTrade(start, numOf(1), numOf(100), RealtimeBar.Side.BUY, null));
    }

    @Test
    public void timePeriodFromFactoryIsApplied() {
        final var period = Duration.ofMinutes(5);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var tradeTime = Instant.parse("2024-01-01T00:07:30Z");
        final var alignedStart = Instant.parse("2024-01-01T00:05:00Z");

        series.barBuilder().addTrade(tradeTime, numOf(1), numOf(100), null, null);

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertTrue(bar instanceof RealtimeBar);
        assertEquals(period, bar.getTimePeriod());
        assertEquals(alignedStart, bar.getBeginTime());
        assertEquals(alignedStart.plus(period), bar.getEndTime());
    }

    @Test
    public void addTradeFillsEmptyTimePeriods() {
        final var period = Duration.ofHours(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var firstTrade = Instant.parse("2024-01-01T10:05:00Z");
        final var laterTrade = Instant.parse("2024-01-01T12:00:00Z");

        builder.addTrade(firstTrade, numOf(1), numOf(100), null, null);
        builder.addTrade(laterTrade, numOf(1), numOf(110), null, null);

        assertEquals(3, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), series.getBar(0).getEndTime());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), series.getBar(1).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), series.getBar(1).getEndTime());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), series.getBar(2).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T13:00:00Z"), series.getBar(2).getEndTime());

        final Bar gapBar = series.getBar(1);
        assertNull(gapBar.getOpenPrice());
        assertNull(gapBar.getHighPrice());
        assertNull(gapBar.getLowPrice());
        assertNull(gapBar.getClosePrice());
        assertNull(gapBar.getVolume());
        assertNull(gapBar.getAmount());
        assertEquals(0, gapBar.getTrades());
    }

    @Test
    public void addTradeHandlesLargeGap() {
        final var period = Duration.ofMinutes(1);
        final var gapPeriods = 1000L;
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var later = start.plus(period.multipliedBy(gapPeriods));

        builder.addTrade(start, numOf(1), numOf(100));
        builder.addTrade(later, numOf(1), numOf(110));

        assertEquals(gapPeriods + 1, series.getBarCount());
        assertNull(series.getBar(1).getClosePrice());
        assertEquals(later, series.getBar((int) gapPeriods).getBeginTime());
        assertEquals(later.plus(period), series.getBar((int) gapPeriods).getEndTime());
    }

    @Test
    public void addTradeRejectsOutOfOrderTimestamp() {
        final var period = Duration.ofMinutes(5);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var start = Instant.parse("2024-01-01T00:07:30Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numOf(1), numOf(100), null, null);

        assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(200), numOf(1), numOf(100), null, null));
    }
}
