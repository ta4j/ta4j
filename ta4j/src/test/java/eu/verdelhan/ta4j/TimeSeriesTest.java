/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesTest {

    private TimeSeries defaultSeries;

    private TimeSeries subSeries;

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

    @Test
    public void getEndSizeBegin() {
        // Original series
        assertThat(defaultSeries.getBegin()).isEqualTo(0);
        assertThat(defaultSeries.getEnd()).isEqualTo(ticks.size() - 1);
        assertThat(defaultSeries.getSize()).isEqualTo(ticks.size());
        // Sub-series
        assertThat(subSeries.getBegin()).isEqualTo(2);
        assertThat(subSeries.getEnd()).isEqualTo(4);
        assertThat(subSeries.getSize()).isEqualTo(3);
    }

    @Test
    public void getPeriodName() {
        // Original series
        assertThat(defaultSeries.getPeriodName()).endsWith(ticks.get(defaultSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        assertThat(defaultSeries.getPeriodName()).startsWith(ticks.get(defaultSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        // Sub-series
        assertThat(subSeries.getPeriodName()).endsWith(ticks.get(subSeries.getEnd()).getEndTime().toString("hh:mm dd/MM/yyyy"));
        assertThat(subSeries.getPeriodName()).startsWith(ticks.get(subSeries.getBegin()).getEndTime().toString("hh:mm dd/MM/yyyy"));

    }

    @Test
    public void getName() {
        assertThat(defaultSeries.getName()).isEqualTo(defaultName);
        assertThat(subSeries.getName()).isEqualTo(defaultName);
    }

    @Test
    public void getPeriod() {
        // Original series
        Period origSeriesPeriod = new Period(ticks.get(1).getEndTime().getMillis() - ticks.get(0).getEndTime().getMillis());
        assertThat(defaultSeries.getPeriod()).isEqualTo(origSeriesPeriod);
        // Sub-series
        Period subSeriesPeriod = new Period(ticks.get(3).getEndTime().getMillis() - ticks.get(2).getEndTime().getMillis());
        assertThat(subSeries.getPeriod()).isEqualTo(subSeriesPeriod);
    }

    @Test
    public void subseries() {
        TimeSeries subSeries2 = defaultSeries.subseries(2, 5);
        assertThat(subSeries2.getName()).isEqualTo(defaultSeries.getName());
        assertThat(subSeries2.getBegin()).isEqualTo(2);
        assertThat(subSeries2.getBegin()).isNotEqualTo(defaultSeries.getBegin());
        assertThat(subSeries2.getEnd()).isEqualTo(5);
        assertThat(subSeries2.getEnd()).isEqualTo(defaultSeries.getEnd());
        assertThat(subSeries2.getSize()).isEqualTo(4);
        assertThat(subSeries2.getPeriod()).isNotEqualTo(defaultSeries.getPeriod());
    }

    @Test
    public void splitByYearOneDatePerYear() {

        TimeSeries series = new MockTimeSeries(
                date.withYear(2010),
                date.withYear(2011),
                date.withYear(2012),
                date.withYear(2015),
                date.withYear(2016));

        List<TimeSeries> subseries = series.split(Period.years(1));

        assertThat(subseries).hasSize(5);

        assertThat(subseries.get(0).getBegin()).isEqualTo(0);
        assertThat(subseries.get(0).getEnd()).isEqualTo(0);

        assertThat(subseries.get(1).getBegin()).isEqualTo(1);
        assertThat(subseries.get(1).getEnd()).isEqualTo(1);

        assertThat(subseries.get(4).getBegin()).isEqualTo(4);
        assertThat(subseries.get(4).getEnd()).isEqualTo(4);
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

        assertThat(subseries).hasSize(4);

        assertThat(subseries.get(0).getBegin()).isEqualTo(0);
        assertThat(subseries.get(0).getEnd()).isEqualTo(5);

        assertThat(subseries.get(1).getBegin()).isEqualTo(6);
        assertThat(subseries.get(1).getEnd()).isEqualTo(6);

        assertThat(subseries.get(2).getBegin()).isEqualTo(7);
        assertThat(subseries.get(2).getEnd()).isEqualTo(7);

        assertThat(subseries.get(3).getBegin()).isEqualTo(8);
        assertThat(subseries.get(3).getEnd()).isEqualTo(10);

    }

    @Test
    public void runOnWholeSeries() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);

        List<Trade> allTrades = series.run(strategy);
        assertThat(allTrades).hasSize(2);
    }

    @Test
    public void runOnSlice() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(2000));
        List<Trade> trades = subseries.get(0).run(strategy);
        assertThat(trades).hasSize(2);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));

        assertThat(trades.get(1).getEntry()).isEqualTo(new Operation(6, OperationType.BUY));
        assertThat(trades.get(1).getExit()).isEqualTo(new Operation(7, OperationType.SELL));
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

        Strategy strategy = new MockStrategy(enter, exit);
        List<Trade> trades = subseries.get(0).run(strategy);
        assertThat(trades).hasSize(1);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.SELL));
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.BUY), null, null, null, null, null };

        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));
        Strategy strategy = new MockStrategy(enter, exit);
        List<Trade> trades = subseries.get(0).run(strategy, OperationType.SELL);
        assertThat(trades).hasSize(1);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.SELL));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.BUY));
    }

    @Test
    public void runSplitted() {
        List<TimeSeries> subseries = seriesForRun.split(Period.years(1));

        List<Trade> trades = subseries.get(0).run(strategy);
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));

        trades = subseries.get(1).run(strategy);;
        assertThat(trades).isEmpty();

        trades = subseries.get(2).run(strategy);;
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(6, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(7, OperationType.SELL));

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
        Strategy strategy = new MockStrategy(enter, exit);

        List<TimeSeries> subseries = series.split(Period.years(1));

        List<Trade> trades = subseries.get(0).run(strategy);;
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(0, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(2, OperationType.SELL));

        trades = subseries.get(1).run(strategy);;
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(3, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));

        trades = subseries.get(2).run(strategy);;
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(5, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(6, OperationType.SELL));

        trades = subseries.get(3).run(strategy);
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(7, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(9, OperationType.SELL));

        trades = subseries.get(4).run(strategy);
        assertThat(trades).isEmpty();

        trades = subseries.get(5).run(strategy);
        assertThat(trades).isEmpty();

    }
}
