/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseRealtimeBar;
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
                .lowPrice(numFactory.hundred())
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
        assertEquals(numFactory.hundred(), bar.getLowPrice());
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
                .lowPrice(numFactory.hundred())
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
        assertEquals(numFactory.hundred(), bar.getLowPrice());
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
                .lowPrice(numFactory.hundred())
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
        assertEquals(numFactory.hundred(), bar.getLowPrice());
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
        builder.addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertTrue(bar instanceof RealtimeBar);
        final var realtimeBar = (RealtimeBar) bar;
        assertTrue(realtimeBar.hasSideData());
        assertTrue(realtimeBar.hasLiquidityData());
        assertEquals(numFactory.one(), realtimeBar.getBuyVolume());
        assertEquals(numFactory.two(), realtimeBar.getSellVolume());
        assertEquals(numFactory.hundred(), realtimeBar.getBuyAmount());
        assertEquals(numOf(220), realtimeBar.getSellAmount());
        assertEquals(1, realtimeBar.getBuyTrades());
        assertEquals(1, realtimeBar.getSellTrades());
        assertEquals(numFactory.one(), realtimeBar.getMakerVolume());
        assertEquals(numFactory.two(), realtimeBar.getTakerVolume());
        assertEquals(numFactory.hundred(), realtimeBar.getMakerAmount());
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

        assertThrows(IllegalStateException.class, () -> series.barBuilder()
                .addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY, null));
    }

    @Test
    public void timePeriodFromFactoryIsApplied() {
        final var period = Duration.ofMinutes(5);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var tradeTime = Instant.parse("2024-01-01T00:07:30Z");
        final var alignedStart = Instant.parse("2024-01-01T00:05:00Z");

        series.barBuilder().addTrade(tradeTime, numFactory.one(), numFactory.hundred(), null, null);

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertTrue(bar instanceof RealtimeBar);
        assertEquals(period, bar.getTimePeriod());
        assertEquals(alignedStart, bar.getBeginTime());
        assertEquals(alignedStart.plus(period), bar.getEndTime());
    }

    @Test
    public void addTradeSkipsEmptyTimePeriods() {
        final var period = Duration.ofHours(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var firstTrade = Instant.parse("2024-01-01T10:05:00Z");
        final var laterTrade = Instant.parse("2024-01-01T12:00:00Z");

        builder.addTrade(firstTrade, numFactory.one(), numFactory.hundred(), null, null);
        builder.addTrade(laterTrade, numFactory.one(), numOf(110), null, null);

        assertEquals(2, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), series.getBar(0).getEndTime());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), series.getBar(1).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T13:00:00Z"), series.getBar(1).getEndTime());
    }

    @Test
    public void addTradeSkipsLargeGap() {
        final var period = Duration.ofMinutes(1);
        final var gapPeriods = 1000L;
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var later = start.plus(period.multipliedBy(gapPeriods));

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        builder.addTrade(later, numFactory.one(), numOf(110));

        assertEquals(2, series.getBarCount());
        assertEquals(later, series.getBar(1).getBeginTime());
        assertEquals(later.plus(period), series.getBar(1).getEndTime());
    }

    /**
     * Regression: TimeBarBuilder preserves chronological gap placement without
     * backfilling. Missing periods remain missing; bars appear at correct positions
     * in the series.
     */
    @Test
    public void addTradePreservesChronologicalGapPlacementWithoutBackfilling() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var firstTrade = Instant.parse("2024-01-01T00:00:30Z");
        final var laterTrade = Instant.parse("2024-01-01T00:04:30Z"); // skips periods 1, 2, 3

        builder.addTrade(firstTrade, numFactory.one(), numFactory.hundred());
        builder.addTrade(laterTrade, numFactory.one(), numOf(110));

        // No backfill: exactly 2 bars, not 5
        assertEquals(2, series.getBarCount());

        // Chronological order: bar i end < bar i+1 begin
        assertTrue(series.getBar(0).getEndTime().isBefore(series.getBar(1).getBeginTime()));

        // Gap placement: second bar begins exactly where skipped periods would end
        final var expectedSecondBegin = Instant.parse("2024-01-01T00:04:00Z");
        assertEquals(expectedSecondBegin, series.getBar(1).getBeginTime());
        assertEquals(expectedSecondBegin.plus(period), series.getBar(1).getEndTime());

        // First bar aligned to period 0
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T00:01:00Z"), series.getBar(0).getEndTime());
    }

    @Test
    public void addTradeFlushesSeededBarBeforeSkippingPeriods() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var seededEnd = start.plus(period);
        final var later = start.plus(period.multipliedBy(3));

        builder.beginTime(start)
                .endTime(seededEnd)
                .openPrice(numFactory.hundred())
                .highPrice(numOf(110))
                .lowPrice(numOf(90))
                .closePrice(numOf(105))
                .volume(numOf(10))
                .amount(numOf(1050))
                .trades(1);

        builder.addTrade(later, numFactory.one(), numOf(120));

        assertEquals(2, series.getBarCount());
        final var seededBar = series.getBar(0);
        assertEquals(start, seededBar.getBeginTime());
        assertEquals(seededEnd, seededBar.getEndTime());
        assertEquals(numFactory.hundred(), seededBar.getOpenPrice());
        assertEquals(numOf(110), seededBar.getHighPrice());
        assertEquals(numOf(90), seededBar.getLowPrice());
        assertEquals(numOf(105), seededBar.getClosePrice());
        assertEquals(numOf(10), seededBar.getVolume());
        assertEquals(numOf(1050), seededBar.getAmount());
        assertEquals(1, seededBar.getTrades());
        assertEquals(later, series.getBar(1).getBeginTime());
        assertEquals(later.plus(period), series.getBar(1).getEndTime());
    }

    @Test
    public void addTradeLogsDiscontinuityWarning() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .withName("TestSeries")
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");
        final var later = start.plus(period.multipliedBy(3));

        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(TimeBarBuilder.class.getName());
        Level originalLevel = loggerConfig.getLevel();

        StringWriter logOutput = new StringWriter();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%msg%n").build();
        Appender appender = WriterAppender.newBuilder()
                .setTarget(logOutput)
                .setLayout(layout)
                .setName("TimeBarBuilderAppender")
                .build();
        appender.start();

        loggerConfig.addAppender(appender, Level.WARN, null);
        loggerConfig.setLevel(Level.WARN);
        loggerContext.updateLoggers();

        try {
            builder.addTrade(start, numFactory.one(), numFactory.hundred());
            builder.addTrade(later, numFactory.one(), numOf(110));
        } finally {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            loggerConfig.setLevel(originalLevel);
            loggerContext.updateLoggers();
        }

        String logContent = logOutput.toString();
        assertTrue(logContent.contains("Detected 2 missing bar period(s)"));
        assertTrue(logContent.contains("for series TestSeries"));
    }

    @Test
    public void addTradeRejectsOutOfOrderTimestamp() {
        final var period = Duration.ofMinutes(5);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var start = Instant.parse("2024-01-01T00:07:30Z");
        final var builder = series.barBuilder();

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, null);

        assertThrows(IllegalArgumentException.class,
                () -> builder.addTrade(start.minusSeconds(200), numFactory.one(), numFactory.hundred(), null, null));
    }

    @Test
    public void testDefaultConstructor() {
        TimeBarBuilder builder = new TimeBarBuilder();
        assertNotNull(builder);
        // Default uses DoubleNumFactory and realtimeBars=false
    }

    @Test
    public void testConstructorWithNumFactory() {
        TimeBarBuilder builder = new TimeBarBuilder(numFactory);
        assertNotNull(builder);
    }

    @Test
    public void testConstructorWithNumFactoryAndRealtimeBars() {
        TimeBarBuilder builder = new TimeBarBuilder(numFactory, true);
        assertNotNull(builder);
    }

    @Test
    public void testConstructorWithRealtimeBarsFalse() {
        TimeBarBuilder builder = new TimeBarBuilder(numFactory, false);
        assertNotNull(builder);
    }

    @Test
    public void testBindTo() {
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        final var builder = new TimeBarBuilder(numFactory);

        TimeBarBuilder result = (TimeBarBuilder) builder.bindTo(series);
        assertSame(builder, result);
    }

    @Test
    public void testBindToRejectsNull() {
        final var builder = new TimeBarBuilder(numFactory);
        assertThrows(NullPointerException.class, () -> builder.bindTo(null));
    }

    @Test
    public void testBuilderMethodChaining() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var builder = new TimeBarBuilder(numFactory);

        TimeBarBuilder result = (TimeBarBuilder) builder.timePeriod(duration)
                .beginTime(beginTime)
                .endTime(endTime)
                .openPrice(numFactory.hundred())
                .highPrice(numOf(110))
                .lowPrice(numOf(90))
                .closePrice(numOf(105))
                .volume(numOf(1000))
                .amount(numOf(105000))
                .trades(100);

        assertSame(builder, result);
    }

    @Test
    public void testAddTradeRejectsNullTime() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();

        assertThrows(NullPointerException.class, () -> builder.addTrade(null, numFactory.one(), numFactory.hundred()));
    }

    @Test
    public void testAddTradeRejectsNullTradeVolume() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var time = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(NullPointerException.class, () -> builder.addTrade(time, null, numFactory.hundred()));
    }

    @Test
    public void testAddTradeRejectsNullTradePrice() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var time = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(NullPointerException.class, () -> builder.addTrade(time, numFactory.one(), null));
    }

    @Test
    public void testAddTradeRejectsMissingTimePeriod() {
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();
        final var builder = series.barBuilder();
        final var time = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> builder.addTrade(time, numFactory.one(), numFactory.hundred()));
    }

    @Test
    public void testAddTradeRejectsMissingBaseBarSeries() {
        final var period = Duration.ofMinutes(1);
        final var builder = new TimeBarBuilder(numFactory);
        builder.timePeriod(period);
        final var time = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(NullPointerException.class, () -> builder.addTrade(time, numFactory.one(), numFactory.hundred()));
    }

    @Test
    public void testAddTradeAtEndTimeBoundary() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        // Trade exactly at endTime should start a new bar
        builder.addTrade(start.plus(period), numFactory.one(), numOf(110));

        assertEquals(2, series.getBarCount());
        assertEquals(start.plus(period), series.getBar(1).getBeginTime());
    }

    @Test
    public void testAddTradeAtBeginTimeBoundary() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        // Trade exactly at beginTime of next period should be in that period
        builder.addTrade(start.plus(period), numFactory.one(), numOf(110));

        assertEquals(2, series.getBarCount());
    }

    @Test
    public void testAddTradeMultipleTradesInSamePeriod() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110));
        builder.addTrade(start.plusSeconds(20), numOf(3), numOf(120));

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertEquals(numOf(6), bar.getVolume()); // 1 + 2 + 3
        assertEquals(numOf(680), bar.getAmount()); // 100 + 220 + 360
        assertEquals(3, bar.getTrades());
        assertEquals(numFactory.hundred(), bar.getOpenPrice());
        assertEquals(numOf(120), bar.getHighPrice());
        assertEquals(numFactory.hundred(), bar.getLowPrice());
        assertEquals(numOf(120), bar.getClosePrice());
    }

    @Test
    public void testAddTradeMultipleTradesAcrossPeriods() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        builder.addTrade(start.plusSeconds(30), numFactory.two(), numOf(110));
        builder.addTrade(start.plus(period), numOf(3), numOf(120));
        builder.addTrade(start.plus(period).plusSeconds(30), numOf(4), numOf(130));

        assertEquals(2, series.getBarCount());
        assertEquals(numOf(3), series.getBar(0).getVolume());
        assertEquals(numOf(320), series.getBar(0).getAmount()); // 100 + 220
        assertEquals(2, series.getBar(0).getTrades());
        assertEquals(numOf(7), series.getBar(1).getVolume()); // 3 + 4
        assertEquals(numOf(880), series.getBar(1).getAmount()); // 360 + 520
        assertEquals(2, series.getBar(1).getTrades());
    }

    @Test
    public void testAddTradeWithOnlySideNoLiquidity() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY, null);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110), RealtimeBar.Side.SELL, null);

        assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        assertTrue(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
        assertEquals(numFactory.one(), bar.getBuyVolume());
        assertEquals(numFactory.two(), bar.getSellVolume());
    }

    @Test
    public void testAddTradeWithOnlyLiquidityNoSide() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110), null, RealtimeBar.Liquidity.TAKER);

        assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        assertFalse(bar.hasSideData());
        assertTrue(bar.hasLiquidityData());
        assertEquals(numFactory.one(), bar.getMakerVolume());
        assertEquals(numFactory.two(), bar.getTakerVolume());
    }

    @Test
    public void testAddTradeWithNeitherSideNorLiquidity() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, null);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110), null, null);

        assertEquals(1, series.getBarCount());
        final var bar = (RealtimeBar) series.getBar(0);
        assertFalse(bar.hasSideData());
        assertFalse(bar.hasLiquidityData());
    }

    @Test
    public void testAddTradeBuildsRegularBarWhenRealtimeDisabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, false))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, null);

        assertEquals(1, series.getBarCount());
        final var bar = series.getBar(0);
        assertTrue(bar instanceof BaseBar);
        assertFalse(bar instanceof RealtimeBar);
    }

    @Test
    public void testAddTradeRejectsLiquidityWhenRealtimeDisabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, false))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        assertThrows(IllegalStateException.class, () -> builder.addTrade(start, numFactory.one(), numFactory.hundred(),
                null, RealtimeBar.Liquidity.MAKER));
    }

    @Test
    public void testBuildReturnsBaseBarWhenRealtimeDisabled() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var builder = new TimeBarBuilder(numFactory, false);

        final Bar bar = builder.timePeriod(duration)
                .beginTime(beginTime)
                .endTime(endTime)
                .openPrice(numFactory.hundred())
                .highPrice(numOf(110))
                .lowPrice(numOf(90))
                .closePrice(numOf(105))
                .volume(numOf(1000))
                .amount(numOf(105000))
                .trades(100)
                .build();

        assertTrue(bar instanceof BaseBar);
        assertFalse(bar instanceof RealtimeBar);
    }

    @Test
    public void testBuildReturnsBaseRealtimeBarWhenRealtimeEnabled() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var builder = new TimeBarBuilder(numFactory, true);

        final Bar bar = builder.timePeriod(duration)
                .beginTime(beginTime)
                .endTime(endTime)
                .openPrice(numFactory.hundred())
                .highPrice(numOf(110))
                .lowPrice(numOf(90))
                .closePrice(numOf(105))
                .volume(numOf(1000))
                .amount(numOf(105000))
                .trades(100)
                .build();

        assertTrue(bar instanceof BaseRealtimeBar);
        assertTrue(bar instanceof RealtimeBar);
    }

    @Test
    public void testAddCalculatesAmountWhenMissing() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder()
                .timePeriod(duration)
                .beginTime(beginTime)
                .endTime(endTime)
                .closePrice(numOf(10))
                .volume(numOf(20))
                .add();

        assertEquals(numOf(200), series.getBar(0).getAmount()); // 10 * 20
    }

    @Test
    public void testAddDoesNotOverrideAmountWhenPresent() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder()
                .timePeriod(duration)
                .beginTime(beginTime)
                .endTime(endTime)
                .closePrice(numOf(10))
                .volume(numOf(20))
                .amount(numOf(150))
                .add();

        assertEquals(numOf(150), series.getBar(0).getAmount());
    }

    @Test
    public void testAddDoesNotCalculateAmountWhenClosePriceMissing() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder().timePeriod(duration).beginTime(beginTime).endTime(endTime).volume(numOf(20)).add();

        assertNull(series.getBar(0).getAmount());
    }

    @Test
    public void testAddDoesNotCalculateAmountWhenVolumeMissing() {
        final var beginTime = Instant.parse("2014-06-25T00:00:00Z");
        final var endTime = Instant.parse("2014-06-25T01:00:00Z");
        final var duration = Duration.between(beginTime, endTime);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory())
                .build();

        series.barBuilder().timePeriod(duration).beginTime(beginTime).endTime(endTime).closePrice(numOf(10)).add();

        assertNull(series.getBar(0).getAmount());
    }

    @Test
    public void testAlignToTimePeriodStartWithSeconds() {
        final var period = Duration.ofSeconds(30);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-01-01T00:00:45Z"); // 45 seconds past minute

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T00:00:30Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T00:01:00Z"), series.getBar(0).getEndTime());
    }

    @Test
    public void testAlignToTimePeriodStartWithHours() {
        final var period = Duration.ofHours(2);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-01-01T03:30:00Z"); // 3:30 AM

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T02:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T04:00:00Z"), series.getBar(0).getEndTime());
    }

    @Test
    public void testAlignToTimePeriodStartWithDays() {
        final var period = Duration.ofDays(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-01-01T12:30:00Z"); // Noon on Jan 1

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), series.getBar(0).getEndTime());
    }

    @Test
    public void testAlignToTimePeriodStartAtEpochBoundary() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-01-01T00:00:00Z"); // Exactly on minute boundary

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(tradeTime, series.getBar(0).getBeginTime());
        assertEquals(tradeTime.plus(period), series.getBar(0).getEndTime());
    }

    @Test
    public void testRecordTradeFirstTradeSetsOpenHighLowClose() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());

        final var bar = series.getBar(0);
        assertEquals(numFactory.hundred(), bar.getOpenPrice());
        assertEquals(numFactory.hundred(), bar.getHighPrice());
        assertEquals(numFactory.hundred(), bar.getLowPrice());
        assertEquals(numFactory.hundred(), bar.getClosePrice());
    }

    @Test
    public void testRecordTradeSubsequentTradesUpdateHighLowClose() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        builder.addTrade(start.plusSeconds(10), numFactory.one(), numOf(120)); // Higher
        builder.addTrade(start.plusSeconds(20), numFactory.one(), numOf(90)); // Lower
        builder.addTrade(start.plusSeconds(30), numFactory.one(), numOf(110)); // Final

        final var bar = series.getBar(0);
        assertEquals(numFactory.hundred(), bar.getOpenPrice());
        assertEquals(numOf(120), bar.getHighPrice());
        assertEquals(numOf(90), bar.getLowPrice());
        assertEquals(numOf(110), bar.getClosePrice());
    }

    @Test
    public void testRecordTradeAccumulatesVolumeAndAmount() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.two(), numFactory.hundred());
        builder.addTrade(start.plusSeconds(10), numOf(3), numOf(110));
        builder.addTrade(start.plusSeconds(20), numFactory.one(), numOf(120));

        final var bar = series.getBar(0);
        assertEquals(numOf(6), bar.getVolume()); // 2 + 3 + 1
        assertEquals(numOf(650), bar.getAmount()); // 200 + 330 + 120
        assertEquals(3, bar.getTrades());
    }

    @Test
    public void testResetTradeStateAfterPeriodRollover() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        // First period
        builder.addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110), RealtimeBar.Side.SELL,
                RealtimeBar.Liquidity.TAKER);

        // Second period (should reset state)
        builder.addTrade(start.plus(period), numOf(3), numOf(120), RealtimeBar.Side.BUY, RealtimeBar.Liquidity.MAKER);

        assertEquals(2, series.getBarCount());
        final var bar1 = (RealtimeBar) series.getBar(0);
        final var bar2 = (RealtimeBar) series.getBar(1);

        // First bar should have accumulated data
        assertEquals(numOf(3), bar1.getVolume());
        assertEquals(2, bar1.getTrades());
        assertEquals(numFactory.one(), bar1.getBuyVolume());
        assertEquals(numFactory.two(), bar1.getSellVolume());

        // Second bar should have fresh state
        assertEquals(numOf(3), bar2.getVolume());
        assertEquals(1, bar2.getTrades());
        assertEquals(numOf(3), bar2.getBuyVolume());
        assertEquals(numFactory.zero(), bar2.getSellVolume());
    }

    @Test
    public void testEnsureRealtimeTrackingAcceptsNullWhenRealtimeDisabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, false))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        // Should not throw when both side and liquidity are null
        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, null);
        assertEquals(1, series.getBarCount());
    }

    @Test
    public void testEnsureRealtimeTrackingAcceptsSideWhenRealtimeEnabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY, null);
        assertEquals(1, series.getBarCount());
    }

    @Test
    public void testEnsureRealtimeTrackingAcceptsLiquidityWhenRealtimeEnabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), null, RealtimeBar.Liquidity.MAKER);
        assertEquals(1, series.getBarCount());
    }

    @Test
    public void testEnsureRealtimeTrackingAcceptsBothWhenRealtimeEnabled() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period, true))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred(), RealtimeBar.Side.BUY,
                RealtimeBar.Liquidity.MAKER);
        assertEquals(1, series.getBarCount());
    }

    @Test
    public void testAddTradeReplacesBarWhenOpenPriceExists() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        assertEquals(1, series.getBarCount());
        assertEquals(numFactory.one(), series.getBar(0).getVolume());

        // Second trade in same period should replace the bar
        builder.addTrade(start.plusSeconds(10), numFactory.two(), numOf(110));
        assertEquals(1, series.getBarCount()); // Still 1 bar (replaced)
        assertEquals(numOf(3), series.getBar(0).getVolume()); // Accumulated: 1 + 2
    }

    @Test
    public void testAddTradeCreatesNewBarWhenOpenPriceIsNull() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var start = Instant.parse("2024-01-01T00:00:00Z");

        builder.addTrade(start, numFactory.one(), numFactory.hundred());
        assertEquals(1, series.getBarCount());

        // After period rollover, openPrice is null, so new bar is created
        builder.addTrade(start.plus(period), numFactory.two(), numOf(110));
        assertEquals(2, series.getBarCount());
    }

    @Test
    public void testAlignToTimePeriodStartWithNanos() {
        final var period = Duration.ofNanos(1_000_000_000L); // 1 second in nanos
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-01-01T00:00:00.5Z"); // 0.5 seconds

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), series.getBar(0).getBeginTime());
        assertEquals(Instant.parse("2024-01-01T00:00:01Z"), series.getBar(0).getEndTime());
    }

    @Test
    public void testAlignToTimePeriodStartWithVeryLargePeriod() {
        final var period = Duration.ofDays(365); // 1 year
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = series.barBuilder();
        final var tradeTime = Instant.parse("2024-06-15T12:00:00Z"); // Mid-year

        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        // Alignment is based on epoch, not calendar year boundaries
        // Calculate expected alignment: epoch + floor((tradeTime - epoch) / period) *
        // period
        final var epoch = Instant.EPOCH;
        final var nanosSinceEpoch = Duration.between(epoch, tradeTime).toNanos();
        final var periodNanos = period.toNanos();
        final var alignedNanos = (nanosSinceEpoch / periodNanos) * periodNanos;
        final var expectedBegin = epoch.plusNanos(alignedNanos);
        assertEquals(expectedBegin, series.getBar(0).getBeginTime());
        assertEquals(expectedBegin.plus(period), series.getBar(0).getEndTime());
    }

    @Test
    public void testEnsureTimeRangeCalculatesBeginTimeFromEndTime() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = (TimeBarBuilder) series.barBuilder();
        final var endTime = Instant.parse("2024-01-01T00:01:00Z");
        final var tradeTime = Instant.parse("2024-01-01T00:00:30Z");

        // Manually set endTime (simulating a scenario)
        builder.endTime(endTime);
        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(endTime.minus(period), series.getBar(0).getBeginTime());
        assertEquals(endTime, series.getBar(0).getEndTime());
    }

    @Test
    public void testEnsureTimeRangeCalculatesEndTimeFromBeginTime() {
        final var period = Duration.ofMinutes(1);
        final var series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new TimeBarBuilderFactory(period))
                .build();
        final var builder = (TimeBarBuilder) series.barBuilder();
        final var beginTime = Instant.parse("2024-01-01T00:00:00Z");
        final var tradeTime = Instant.parse("2024-01-01T00:00:30Z");

        // Manually set beginTime
        builder.beginTime(beginTime);
        builder.addTrade(tradeTime, numFactory.one(), numFactory.hundred());

        assertEquals(1, series.getBarCount());
        assertEquals(beginTime, series.getBar(0).getBeginTime());
        assertEquals(beginTime.plus(period), series.getBar(0).getEndTime());
    }
}
