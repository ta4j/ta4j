/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.LinkedList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;

    private TimeSeries subSeries;

    private TimeSeries emptySeries;

    private TimeSeries seriesForRun;

    private Strategy strategy;

    private List<Tick> ticks;

    private String defaultName;

    private DateTime date;

    @Before
    public void setUp() {
        date = new DateTime(0);

        ticks = new LinkedList<Tick>();
        ticks.add(new MockTick(date.withDate(2014, 6, 13), 1d));
        ticks.add(new MockTick(date.withDate(2014, 6, 14), 2d));
        ticks.add(new MockTick(date.withDate(2014, 6, 15), 3d));
        ticks.add(new MockTick(date.withDate(2014, 6, 20), 4d));
        ticks.add(new MockTick(date.withDate(2014, 6, 25), 5d));
        ticks.add(new MockTick(date.withDate(2014, 6, 30), 6d));

        defaultName = "Series Name";

        defaultSeries = new TimeSeries(defaultName, ticks);
        subSeries = defaultSeries.subseries(2, 4);
        emptySeries = new TimeSeries(Period.minutes(15));
        final DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
        seriesForRun = new MockTimeSeries(
                new double[] { 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d },
                new DateTime[] {
                    dtf.parseDateTime("2013-01-01"),
                    dtf.parseDateTime("2013-08-01"),
                    dtf.parseDateTime("2013-10-01"),
                    dtf.parseDateTime("2013-12-01"),
                    dtf.parseDateTime("2014-02-01"),
                    dtf.parseDateTime("2015-01-01"),
                    dtf.parseDateTime("2015-08-01"),
                    dtf.parseDateTime("2015-10-01"),
                    dtf.parseDateTime("2015-12-01")
                });

        strategy = new MockStrategy(
                new Operation[] { null, null, new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY), null, null, new Operation(6, OperationType.BUY), null, null },
                new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null, new Operation(7, OperationType.SELL), new Operation(8, OperationType.SELL) }
                );
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorWithInvalidIndexesShouldThrowException() {
        TimeSeries s = new TimeSeries(null, null, 4, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorWithNullTimePeriodShouldThrowException() {
        TimeSeries s = new TimeSeries((Period) null);
    }

    @Test
    public void getEndGetBeginGetTickCount() {
        // Original series
        assertEquals(0, defaultSeries.getBegin());
        assertEquals(ticks.size() - 1, defaultSeries.getEnd());
        assertEquals(ticks.size(), defaultSeries.getTickCount());
        // Sub-series
        assertEquals(2, subSeries.getBegin());
        assertEquals(4, subSeries.getEnd());
        assertEquals(3, subSeries.getTickCount());
        // Empty series
        assertEquals(-1, emptySeries.getBegin());
        assertEquals(-1, emptySeries.getEnd());
        assertEquals(0, emptySeries.getTickCount());
    }

    @Test
    public void getSeriesPeriodDescription() {
        // Original series
        assertTrue(defaultSeries.getSeriesPeriodDescription().endsWith(ticks.get(defaultSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy")));
        assertTrue(defaultSeries.getSeriesPeriodDescription().startsWith(ticks.get(defaultSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy")));
        // Sub-series
        assertTrue(subSeries.getSeriesPeriodDescription().endsWith(ticks.get(subSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy")));
        assertTrue(subSeries.getSeriesPeriodDescription().startsWith(ticks.get(subSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy")));
        // Empty series
        assertEquals("", emptySeries.getSeriesPeriodDescription());
    }

    @Test
    public void getName() {
        assertEquals(defaultName, defaultSeries.getName());
        assertEquals(defaultName, subSeries.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTickWithRemovedIndexOnMovingSeriesShouldThrowException() {
        defaultSeries.setMaximumTickCount(2);
        defaultSeries.getTick(1);
    }

    @Test
    public void getTickOnMovingSeries() {
        Tick tick = defaultSeries.getTick(4);
        defaultSeries.setMaximumTickCount(2);
        assertEquals(tick, defaultSeries.getTick(4));
    }

    @Test
    public void getTimePeriod() {
        // Original series
        Period origSeriesPeriod = new Period(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis());
        assertEquals(origSeriesPeriod, defaultSeries.getTimePeriod());
        // Sub-series
        Period subSeriesPeriod = new Period(ticks.get(3).getEndTime().getMillis() - ticks.get(2).getEndTime().getMillis());
        assertEquals(subSeriesPeriod, subSeries.getTimePeriod());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeMaximumTickCountShouldThrowException() {
        defaultSeries.setMaximumTickCount(-1);
    }

    @Test
    public void maximumTickCount() {
        // Before
        assertEquals(0, defaultSeries.getBegin());
        assertEquals(ticks.size() - 1, defaultSeries.getEnd());
        assertEquals(ticks.size(), defaultSeries.getTickCount());

        defaultSeries.setMaximumTickCount(3);

        // After
        assertEquals(0, defaultSeries.getBegin());
        assertEquals(2, defaultSeries.getEnd());
        assertEquals(3, defaultSeries.getTickCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addNullTickShouldThrowException() {
        defaultSeries.addTick(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addTickWithEndTimePriorToSeriesEndTimeShouldThrowException() {
        defaultSeries.addTick(new MockTick(date.withDate(2000, 1, 1), 99d));
    }
    
    @Test
    public void addTick() {
        defaultSeries = new TimeSeries(Period.days(1));
        Tick firstTick = new MockTick(date.withDate(2014, 6, 13), 1d);
        Tick secondTick = new MockTick(date.withDate(2014, 6, 14), 2d);

        assertEquals(0, defaultSeries.getTickCount());
        assertEquals(-1, defaultSeries.getBegin());
        assertEquals(-1, defaultSeries.getEnd());

        defaultSeries.addTick(firstTick);
        assertEquals(1, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBegin());
        assertEquals(0, defaultSeries.getEnd());

        defaultSeries.addTick(secondTick);
        assertEquals(2, defaultSeries.getTickCount());
        assertEquals(0, defaultSeries.getBegin());
        assertEquals(1, defaultSeries.getEnd());
    }

    @Test
    public void subseriesWithIndexes() {
        TimeSeries subSeries2 = defaultSeries.subseries(2, 5);
        assertEquals(defaultSeries.getName(), subSeries2.getName());
        assertEquals(2, subSeries2.getBegin());
        assertNotEquals(defaultSeries.getBegin(), subSeries2.getBegin());
        assertEquals(5, subSeries2.getEnd());
        assertEquals(defaultSeries.getEnd(), subSeries2.getEnd());
        assertEquals(4, subSeries2.getTickCount());
        assertNotEquals(defaultSeries.getTimePeriod(), subSeries2.getTimePeriod());
    }

    @Test
    public void subseriesWithDuration() {
        TimeSeries subSeries2 = defaultSeries.subseries(1, Period.weeks(2));
        assertEquals(defaultSeries.getName(), subSeries2.getName());
        assertEquals(1, subSeries2.getBegin());
        assertNotEquals(defaultSeries.getBegin(), subSeries2.getBegin());
        assertEquals(4, subSeries2.getEnd());
        assertNotEquals(defaultSeries.getEnd(), subSeries2.getEnd());
        assertEquals(4, subSeries2.getTickCount());
        assertEquals(defaultSeries.getTimePeriod(), subSeries2.getTimePeriod());
    }

    @Test
    public void splitEvery3Ticks() {
        TimeSeries series = new MockTimeSeries(
                date.withYear(2010),
                date.withYear(2011),
                date.withYear(2012),
                date.withYear(2015),
                date.withYear(2016),
                date.withYear(2017),
                date.withYear(2018),
                date.withYear(2019));

        List<TimeSeries> subseries = series.split(3);

        assertEquals(3, subSeries.getTickCount());

        assertEquals(0, subseries.get(0).getBegin());
        assertEquals(2, subseries.get(0).getEnd());

        assertEquals(3, subseries.get(1).getBegin());
        assertEquals(5, subseries.get(1).getEnd());

        assertEquals(6, subseries.get(2).getBegin());
        assertEquals(7, subseries.get(2).getEnd());
    }

    @Test
    public void splitByYearForTwoYearsSubseries() {

        TimeSeries series = new MockTimeSeries(
                date.withYear(2010),
                date.withYear(2011),
                date.withYear(2012),
                date.withYear(2015),
                date.withYear(2016));

        List<TimeSeries> subseries = series.split(Period.years(1), Period.years(2));

        assertEquals(5, subseries.size());

        assertEquals(0, subseries.get(0).getBegin());
        assertEquals(1, subseries.get(0).getEnd());

        assertEquals(1, subseries.get(1).getBegin());
        assertEquals(2, subseries.get(1).getEnd());

        assertEquals(2, subseries.get(2).getBegin());
        assertEquals(2, subseries.get(2).getEnd());

        assertEquals(4, subseries.get(4).getBegin());
        assertEquals(4, subseries.get(4).getEnd());
    }

    @Test
    public void splitByMonthForOneWeekSubseries() {

        TimeSeries series = new MockTimeSeries(
                date.withMonthOfYear(04),
                date.withMonthOfYear(05),
                date.withMonthOfYear(07));

        List<TimeSeries> subseries = series.split(Period.months(1), Period.weeks(1));

        assertEquals(3, subseries.size());

        assertEquals(0, subseries.get(0).getBegin());
        assertEquals(0, subseries.get(0).getEnd());

        assertEquals(1, subseries.get(1).getBegin());
        assertEquals(1, subseries.get(1).getEnd());

        assertEquals(2, subseries.get(2).getBegin());
        assertEquals(2, subseries.get(2).getEnd());
    }

    @Test
    public void splitByHour() {

        DateTime time = new DateTime(0).withTime(10, 0, 0, 0);
        TimeSeries series = new MockTimeSeries(
                time,
                time.plusMinutes(1),
                time.plusMinutes(2),
                time.plusMinutes(10),
                time.plusMinutes(15),
                time.plusMinutes(25),
                time.plusHours(1),
                time.plusHours(5),
                time.plusHours(10).plusMinutes(10),
                time.plusHours(10).plusMinutes(20),
                time.plusHours(10).plusMinutes(30));

        List<TimeSeries> subseries = series.split(Period.hours(1));

        assertEquals(4, subseries.size());

        assertEquals(0, subseries.get(0).getBegin());
        assertEquals(5, subseries.get(0).getEnd());

        assertEquals(6, subseries.get(1).getBegin());
        assertEquals(6, subseries.get(1).getEnd());

        assertEquals(7, subseries.get(2).getBegin());
        assertEquals(7, subseries.get(2).getEnd());

        assertEquals(8, subseries.get(3).getBegin());
        assertEquals(10, subseries.get(3).getEnd());

    }

    @Test
    public void runOnWholeSeries() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);

        List<Trade> allTrades = series.run(strategy);
        assertEquals(2, allTrades.size());
    }

    @Test
    public void runOnSlice() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(2000));
        List<Trade> trades = subseries.get(0).run(strategy);
        assertEquals(2, trades.size());

        assertEquals(new Operation(2, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());

        assertEquals(new Operation(6, OperationType.BUY), trades.get(1).getEntry());
        assertEquals(new Operation(7, OperationType.SELL), trades.get(1).getExit());
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

        Strategy strategy = new MockStrategy(enter, exit);
        List<Trade> trades = subseries.get(0).run(strategy);
        assertEquals(1, trades.size());

        assertEquals(new Operation(1, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(3, OperationType.SELL), trades.get(0).getExit());
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.BUY), null, null, null, null, null };

        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));
        Strategy strategy = new MockStrategy(enter, exit);
        List<Trade> trades = subseries.get(0).run(strategy, OperationType.SELL);
        assertEquals(1, trades.size());

        assertEquals(new Operation(1, OperationType.SELL), trades.get(0).getEntry());
        assertEquals(new Operation(3, OperationType.BUY), trades.get(0).getExit());
    }

    @Test
    public void runSplitted() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));

        List<Trade> trades = subseries.get(0).run(strategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(2, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());

        trades = subseries.get(1).run(strategy);
        assertTrue(trades.isEmpty());

        trades = subseries.get(2).run(strategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(6, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(7, OperationType.SELL), trades.get(0).getExit());

    }

    @Test
    public void splitted(){
        DateTime date = new DateTime();
        TimeSeries series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d},
                    new DateTime[]{date.withYear(2000), date.withYear(2000), date.withYear(2001), date.withYear(2001), date.withYear(2002),
                                   date.withYear(2002), date.withYear(2002), date.withYear(2003), date.withYear(2004), date.withYear(2005)});

        Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, new Operation(3, OperationType.BUY),
                null, new Operation(5, OperationType.BUY), null, new Operation(7, OperationType.BUY), null, null };
        Operation[] exit = new Operation[] { null, null, new Operation(2, OperationType.SELL), null, new Operation(4, OperationType.SELL), null, new Operation(6, OperationType.SELL),
                null, null, new Operation(9, OperationType.SELL) };
        Strategy mockStrategy = new MockStrategy(enter, exit);

        List<TimeSeries> subseries = series.split(Period.years(1));

        List<Trade> trades = subseries.get(0).run(mockStrategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(0, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(2, OperationType.SELL), trades.get(0).getExit());

        trades = subseries.get(1).run(mockStrategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(3, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());

        trades = subseries.get(2).run(mockStrategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(5, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(6, OperationType.SELL), trades.get(0).getExit());

        trades = subseries.get(3).run(mockStrategy);
        assertEquals(1, trades.size());
        assertEquals(new Operation(7, OperationType.BUY), trades.get(0).getEntry());
        assertEquals(new Operation(9, OperationType.SELL), trades.get(0).getExit());

        trades = subseries.get(4).run(mockStrategy);
        assertTrue(trades.isEmpty());

        trades = subseries.get(5).run(mockStrategy);
        assertTrue(trades.isEmpty());
    }
}
