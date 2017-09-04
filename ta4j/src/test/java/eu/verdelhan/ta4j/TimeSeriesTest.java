/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.Order.OrderType;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.trading.rules.FixedRule;
import java.util.LinkedList;
import java.util.List;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;

    private TimeSeries emptySeries;

    private TimeSeries seriesForRun;

    private Strategy strategy;

    private List<Tick> ticks;

    private String defaultName;

    private ZonedDateTime date;

    @Before
    public void setUp() {
        date = ZonedDateTime.now();

        ticks = new LinkedList<>();
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()), 3d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 20, 0, 0, 0, 0, ZoneId.systemDefault()), 4d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault()), 5d));
        ticks.add(new MockTick(ZonedDateTime.of(2014, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault()), 6d));

        defaultName = "Series Name";

        defaultSeries = new TimeSeries(defaultName, ticks);
        emptySeries = new TimeSeries();
        final DateTimeFormatter dtf = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        seriesForRun = new MockTimeSeries(
                new double[] { 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d },
                new ZonedDateTime[] {
                    ZonedDateTime.parse("2013-01-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2013-08-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2013-10-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2013-12-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2014-02-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2015-01-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2015-08-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2015-10-01T00:00:00-05:00", dtf),
                    ZonedDateTime.parse("2015-12-01T00:00:00-05:00", dtf)
                });

        strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstablePeriod(2); // Strategy would need a real test class
    }

    @Test
    public void getEndGetBeginGetTickCount() {
        // Default series
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(ticks.size() - 1, defaultSeries.getEndIndex());
        assertEquals(ticks.size(), defaultSeries.getTickCount());
        // Empty series
        assertEquals(-1, emptySeries.getBeginIndex());
        assertEquals(-1, emptySeries.getEndIndex());
        assertEquals(0, emptySeries.getTickCount());
    }

    @Test
    public void getSeriesPeriodDescription() {
        // Default series
        assertTrue(defaultSeries.getSeriesPeriodDescription().endsWith(ticks.get(defaultSeries.getEndIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        assertTrue(defaultSeries.getSeriesPeriodDescription().startsWith(ticks.get(defaultSeries.getBeginIndex()).getEndTime().format(DateTimeFormatter.ISO_DATE_TIME)));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getName() {
        assertEquals(defaultName, defaultSeries.getName());
    }

    @Test
    public void getTickWithRemovedIndexOnMovingSeriesShouldReturnFirstRemainingTick() {
        Tick tick = defaultSeries.getTick(4);
        defaultSeries.setMaximumTickCount(2);
        
        assertSame(tick, defaultSeries.getTick(0));
        assertSame(tick, defaultSeries.getTick(1));
        assertSame(tick, defaultSeries.getTick(2));
        assertSame(tick, defaultSeries.getTick(3));
        assertSame(tick, defaultSeries.getTick(4));
        assertNotSame(tick, defaultSeries.getTick(5));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickOnMovingAndEmptySeriesShouldThrowException() {
        defaultSeries.setMaximumTickCount(2);
        ticks.clear(); // Should not be used like this
        defaultSeries.getTick(1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickWithNegativeIndexShouldThrowException() {
        defaultSeries.getTick(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getTickWithIndexGreaterThanTickCountShouldThrowException() {
        defaultSeries.getTick(10);
    }

    @Test
    public void getTickOnMovingSeries() {
        Tick tick = defaultSeries.getTick(4);
        defaultSeries.setMaximumTickCount(2);
        assertEquals(tick, defaultSeries.getTick(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaximumTickCountShouldThrowException() {
        defaultSeries.setMaximumTickCount(-1);
    }

    @Test
    public void setMaximumTickCount() {
        // Before
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(ticks.size() - 1, defaultSeries.getEndIndex());
        assertEquals(ticks.size(), defaultSeries.getTickCount());

        defaultSeries.setMaximumTickCount(3);

        // After
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(5, defaultSeries.getEndIndex());
        assertEquals(3, defaultSeries.getTickCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullTickShouldThrowException() {
        defaultSeries.addTick(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addTickWithEndTimePriorToSeriesEndTimeShouldThrowException() {
        defaultSeries.addTick(new MockTick(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()), 99d));
    }
    
    @Test
    public void addTick() {
        defaultSeries = new TimeSeries();
        Tick firstTick = new MockTick(ZonedDateTime.of(2014, 6, 13, 0, 0, 0, 0, ZoneId.systemDefault()), 1d);
        Tick secondTick = new MockTick(ZonedDateTime.of(2014, 6, 14, 0, 0, 0, 0, ZoneId.systemDefault()), 2d);

        assertEquals(0, defaultSeries.getTickCount());
        assertEquals(-1, defaultSeries.getBeginIndex());
        assertEquals(-1, defaultSeries.getEndIndex());

        defaultSeries.addTick(firstTick);
        assertEquals(1, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(0, defaultSeries.getEndIndex());

        defaultSeries.addTick(secondTick);
        assertEquals(2, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBeginIndex());
        assertEquals(1, defaultSeries.getEndIndex());
    }

    @Test
    public void runOnWholeSeries() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);

        List<Trade> allTrades = series.run(strategy).getTrades();
        assertEquals(2, allTrades.size());
    }
    
    @Test
    public void runOnWholeSeriesWithAmount() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);

        List<Trade> allTrades = series.run(strategy,OrderType.BUY, Decimal.HUNDRED).getTrades();
        
        assertEquals(2, allTrades.size());
        assertEquals(Decimal.HUNDRED, allTrades.get(0).getEntry().getAmount());
        assertEquals(Decimal.HUNDRED, allTrades.get(1).getEntry().getAmount());

    }

    @Test
    public void runOnSlice() {
        List<TimeSeries> subseries = seriesForRun.split(Duration.ofDays(365 * 2000));
        TimeSeries slice = subseries.get(0);
        List<Trade> trades = slice.run(strategy).getTrades();
        assertEquals(2, trades.size());

        assertEquals(Order.buyAt(2, slice.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, slice.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        assertEquals(Order.buyAt(6, slice.getTick(6).getClosePrice(), Decimal.NaN), trades.get(1).getEntry());
        assertEquals(Order.sellAt(7, slice.getTick(7).getClosePrice(), Decimal.NaN), trades.get(1).getExit());
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        List<TimeSeries> subseries = seriesForRun.split(Duration.ofDays(365));
        TimeSeries slice = subseries.get(0);
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Trade> trades = slice.run(aStrategy).getTrades();
        assertEquals(1, trades.size());

        assertEquals(Order.buyAt(1, slice.getTick(1).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(3, slice.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        List<TimeSeries> subseries = seriesForRun.split(Duration.ofDays(365));
        TimeSeries slice = subseries.get(0);
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Trade> trades = slice.run(aStrategy, OrderType.SELL).getTrades();
        assertEquals(1, trades.size());

        assertEquals(Order.sellAt(1, slice.getTick(1).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.buyAt(3, slice.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void runSplitted() {
        List<TimeSeries> subseries = seriesForRun.split(Duration.ofDays(365));
        TimeSeries slice0 = subseries.get(0);
        TimeSeries slice1 = subseries.get(1);
        TimeSeries slice2 = subseries.get(2);

        List<Trade> trades = slice0.run(strategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(2, slice0.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, slice0.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = slice1.run(strategy).getTrades();
        assertTrue(trades.isEmpty());

        trades = slice2.run(strategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(6, slice2.getTick(6).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(7, slice2.getTick(7).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void splitted(){
        ZonedDateTime dateTime = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        TimeSeries series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d},
                    new ZonedDateTime[]{dateTime.withYear(2000), dateTime.withYear(2000), dateTime.withYear(2001), dateTime.withYear(2001), dateTime.withYear(2002),
                    dateTime.withYear(2002), dateTime.withYear(2002), dateTime.withYear(2003), dateTime.withYear(2004), dateTime.withYear(2005)});

        Strategy aStrategy = new BaseStrategy(new FixedRule(0, 3, 5, 7), new FixedRule(2, 4, 6, 9));

        List<TimeSeries> subseries = series.split(Period.ofYears(1));
        TimeSeries slice0 = subseries.get(0);
        TimeSeries slice1 = subseries.get(1);
        TimeSeries slice2 = subseries.get(2);
        TimeSeries slice3 = subseries.get(3);
        TimeSeries slice4 = subseries.get(4);
        TimeSeries slice5 = subseries.get(5);
        
        List<Trade> trades = slice0.run(aStrategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(0, slice0.getTick(0).getClosePrice(), Decimal.NaN),trades.get(0).getEntry());
        assertEquals(Order.sellAt(2, slice0.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = slice1.run(aStrategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(3, slice1.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, slice1.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = slice2.run(aStrategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(5, slice2.getTick(5).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(6, slice2.getTick(6).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = slice3.run(aStrategy).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(7, slice3.getTick(7).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(9, slice3.getTick(9).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = slice4.run(aStrategy).getTrades();
        assertTrue(trades.isEmpty());

        trades = slice5.run(aStrategy).getTrades();
        assertTrue(trades.isEmpty());
    }
}
