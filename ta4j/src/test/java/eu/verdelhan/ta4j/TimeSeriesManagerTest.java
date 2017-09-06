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
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.trading.rules.FixedRule;
import java.util.List;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TimeSeriesManagerTest {

    private TimeSeries seriesForRun;

    private TimeSeriesManager manager;
    
    private Strategy strategy;

    @Before
    public void setUp() {

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
        manager = new TimeSeriesManager(seriesForRun);

        strategy = new BaseStrategy(new FixedRule(0, 2, 3, 6), new FixedRule(1, 4, 7, 8));
        strategy.setUnstablePeriod(2); // Strategy would need a real test class
    }

    @Test
    public void runOnWholeSeries() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);
        manager.setTimeSeries(series);
        List<Trade> allTrades = manager.run(strategy).getTrades();
        assertEquals(2, allTrades.size());
    }
    
    @Test
    public void runOnWholeSeriesWithAmount() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);
        manager.setTimeSeries(series);
        List<Trade> allTrades = manager.run(strategy, OrderType.BUY, Decimal.HUNDRED).getTrades();
        
        assertEquals(2, allTrades.size());
        assertEquals(Decimal.HUNDRED, allTrades.get(0).getEntry().getAmount());
        assertEquals(Decimal.HUNDRED, allTrades.get(1).getEntry().getAmount());

    }

    @Test
    public void runOnSeries() {
        List<Trade> trades = manager.run(strategy).getTrades();
        assertEquals(2, trades.size());

        assertEquals(Order.buyAt(2, seriesForRun.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, seriesForRun.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        assertEquals(Order.buyAt(6, seriesForRun.getTick(6).getClosePrice(), Decimal.NaN), trades.get(1).getEntry());
        assertEquals(Order.sellAt(7, seriesForRun.getTick(7).getClosePrice(), Decimal.NaN), trades.get(1).getExit());
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Trade> trades = manager.run(aStrategy, 0, 3).getTrades();
        assertEquals(1, trades.size());

        assertEquals(Order.buyAt(1, seriesForRun.getTick(1).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(3, seriesForRun.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Strategy aStrategy = new BaseStrategy(new FixedRule(1), new FixedRule(3));
        List<Trade> trades = manager.run(aStrategy, OrderType.SELL, 0, 3).getTrades();
        assertEquals(1, trades.size());

        assertEquals(Order.sellAt(1, seriesForRun.getTick(1).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.buyAt(3, seriesForRun.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void runBetweenIndexes() {

        List<Trade> trades = manager.run(strategy, 0, 3).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(2, seriesForRun.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, seriesForRun.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = manager.run(strategy, 4, 4).getTrades();
        assertTrue(trades.isEmpty());

        trades = manager.run(strategy, 5, 8).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(6, seriesForRun.getTick(6).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(7, seriesForRun.getTick(7).getClosePrice(), Decimal.NaN), trades.get(0).getExit());
    }

    @Test
    public void runOnSeriesSlices(){
        ZonedDateTime dateTime = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        TimeSeries series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d},
                    new ZonedDateTime[]{dateTime.withYear(2000), dateTime.withYear(2000), dateTime.withYear(2001), dateTime.withYear(2001), dateTime.withYear(2002),
                    dateTime.withYear(2002), dateTime.withYear(2002), dateTime.withYear(2003), dateTime.withYear(2004), dateTime.withYear(2005)});
        manager.setTimeSeries(series);
        
        Strategy aStrategy = new BaseStrategy(new FixedRule(0, 3, 5, 7), new FixedRule(2, 4, 6, 9));

        List<Trade> trades = manager.run(aStrategy, 0, 1).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(0, series.getTick(0).getClosePrice(), Decimal.NaN),trades.get(0).getEntry());
        assertEquals(Order.sellAt(2, series.getTick(2).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = manager.run(aStrategy, 2, 3).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(3, series.getTick(3).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(4, series.getTick(4).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = manager.run(aStrategy, 4, 6).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(5, series.getTick(5).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(6, series.getTick(6).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = manager.run(aStrategy, 7, 7).getTrades();
        assertEquals(1, trades.size());
        assertEquals(Order.buyAt(7, series.getTick(7).getClosePrice(), Decimal.NaN), trades.get(0).getEntry());
        assertEquals(Order.sellAt(9, series.getTick(9).getClosePrice(), Decimal.NaN), trades.get(0).getExit());

        trades = manager.run(aStrategy, 8, 8).getTrades();
        assertTrue(trades.isEmpty());

        trades = manager.run(aStrategy, 9, 9).getTrades();
        assertTrue(trades.isEmpty());
    }
}
