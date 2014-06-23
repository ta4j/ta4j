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
package eu.verdelhan.ta4j.analysis;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.slicers.RegularSlicer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.assertj.core.api.Fail;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;

public class RunnerTest {

    private Operation[] enter;

    private Operation[] exit;

    private Strategy strategy;
    
    private TimeSeries series;

    @Before
    public void setUp() {
        final DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
        series = new MockTimeSeries(
                new double[] { 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d },
                new DateTime[] {
                    dtf.parseDateTime("2013-04-01"),
                    dtf.parseDateTime("2013-07-01"),
                    dtf.parseDateTime("2013-10-01"),
                    dtf.parseDateTime("2013-12-01"),
                    dtf.parseDateTime("2014-06-01"),
                    dtf.parseDateTime("2015-01-01"),
                    dtf.parseDateTime("2015-04-01"),
                    dtf.parseDateTime("2015-07-01"),
                    dtf.parseDateTime("2015-10-01")
                });
        
        enter = new Operation[] { null, null, new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY),
                null, null, new Operation(6, OperationType.BUY), null, null };
        exit = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null,
                new Operation(7, OperationType.SELL), new Operation(8, OperationType.SELL) };
        strategy = new MockStrategy(enter, exit);
    }

    @Test
    public void runOnWholeSeries() {
        TimeSeries series = new MockTimeSeries(20d, 40d, 60d, 10d, 30d, 50d, 0d, 20d, 40d);
        Runner runner = new Runner(series, strategy);

        List<Trade> allTrades = runner.run();
        List<Trade> sliceTrades = runner.run(0);

        assertThat(allTrades).hasSize(2);
        assertThat(allTrades).containsExactlyElementsOf(sliceTrades);
    }

    @Test
    public void runOnSlice() {
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
        Runner runner = new Runner(slicer, strategy);
        List<Trade> trades = runner.run(0);
        assertThat(trades).hasSize(2);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));

        assertThat(trades.get(1).getEntry()).isEqualTo(new Operation(6, OperationType.BUY));
        assertThat(trades.get(1).getExit()).isEqualTo(new Operation(7, OperationType.SELL));
    }

    @Test
    public void runWithOpenEntryBuyLeft() {
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

        Strategy strategy = new MockStrategy(enter, exit);
        Runner runner = new Runner(slicer, strategy);
        List<Trade> trades = runner.run(0);
        assertThat(trades).hasSize(1);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.SELL));
    }

    @Test
    public void runWithOpenEntrySellLeft() {
        Operation[] enter = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null, null, null, null, null };
        Operation[] exit = { null, null, null, new Operation(3, OperationType.BUY), null, null, null, null, null };

        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
        Strategy strategy = new MockStrategy(enter, exit);
        Runner runner = new Runner(OperationType.SELL, slicer, strategy);
        List<Trade> trades = runner.run(0);
        assertThat(trades).hasSize(1);

        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.SELL));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.BUY));
    }


    @Test
    public void nullTypeShouldThrowException() {
        Runner runner;
        TimeSeriesSlicer slicer = new RegularSlicer(series, Period.days(1));
        OperationType type = OperationType.BUY;
        
        try {
            runner = new Runner(series, null);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
        try {
            runner = new Runner(slicer, null);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
        try {
            runner = new Runner(null, series, strategy);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
        try {
            runner = new Runner(null, slicer, strategy);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
        try {
            slicer = null;
            runner = new Runner(type, slicer, strategy);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
        try {
            series = null;
            runner = new Runner(type, series, strategy);
            Fail.failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException iae) {
            assertThat(iae).hasMessage("Arguments cannot be null");
        }
    }

    @Test
    public void runSplitted() {
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
        Runner runner = new Runner(slicer, strategy);
        List<Trade> trades = runner.run(0);
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));
        
        trades = runner.run(1);

        assertThat(trades).isEmpty();
        

        trades = runner.run(2);

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
        
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
        Runner runner = new Runner(slicer, strategy);
        List<Trade> trades = runner.run(0);
        
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(0, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(2, OperationType.SELL));
        
        trades = runner.run(1);
        
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(3, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));
        
        trades = runner.run(2);
        
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(5, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(6, OperationType.SELL));
        
        
        trades = runner.run(3);
        
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(7, OperationType.BUY));
        assertThat(trades.get(0).getExit()).isEqualTo(new Operation(9, OperationType.SELL));
        
        trades = runner.run(4);
        assertThat(trades).isEmpty();
        
        trades = runner.run(5);
        assertThat(trades).isEmpty();
        
    }
}
