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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluators.Decision;
import eu.verdelhan.ta4j.mocks.MockDecision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class MaximumDrawdownCriterionTest {

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 5, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();

        assertThat(mdd.calculate(series, trades)).isEqualTo(0d);
    }

    @Test
    public void calculateWithOnlyGains() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 8, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

        assertThat(mdd.calculate(series, trades)).isEqualTo(0d);
    }

    @Test
    public void calculateShouldWork() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 5, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
        trades.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));

        assertThat(mdd.calculate(series, trades)).isEqualTo(.875d);

    }

    @Test
    public void calculateWithNullSeriesSizeShouldReturn1() {
        MockTimeSeries series = new MockTimeSeries(new double[] {});
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();

        assertThat(mdd.calculate(series, trades)).isEqualTo(0d);
    }

    @Test
    public void withTradesThatSellBeforeBuying() {
        MockTimeSeries series = new MockTimeSeries(2, 1, 3, 5, 6, 3, 20);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
        trades.add(new Trade(new Operation(5, OperationType.SELL), new Operation(6, OperationType.BUY)));

        assertThat(mdd.calculate(series, trades)).isEqualTo(.91);
    }

    @Test
    public void withSimpleTrades() {
        MockTimeSeries series = new MockTimeSeries(1, 10, 5, 6, 1);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        trades.add(new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.SELL)));
        trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
        trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
        // TODO: should raise IndexOutOfBoundsException
        // trades.add(new Trade(new Operation(4, OperationType.BUY), new
        // Operation(5, OperationType.SELL)));

        assertThat(mdd.calculate(series, trades)).isEqualTo(.9d);
    }

    @Test
    public void summarize() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 5, 20, 3);
        List<Decision> decisions = new LinkedList<Decision>();

        List<Trade> tradesToDummy1 = new LinkedList<Trade>();
        tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        Decision dummy1 = new MockDecision(tradesToDummy1, series);
        decisions.add(dummy1);

        List<Trade> tradesToDummy2 = new LinkedList<Trade>();
        tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
        Decision dummy2 = new MockDecision(tradesToDummy2, series);
        decisions.add(dummy2);

        List<Trade> tradesToDummy3 = new LinkedList<Trade>();
        tradesToDummy3.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));
        Decision dummy3 = new MockDecision(tradesToDummy3, series);
        decisions.add(dummy3);

        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();

        assertThat(mdd.summarize(series, decisions)).isEqualTo(.875d);

    }
    @Test
    public void withConstrainedTimeSeries()
    {
        MockTimeSeries sampleSeries = new MockTimeSeries(new double[] {1, 1, 1, 1, 1, 10, 5, 6, 1, 1, 1 });
        TimeSeries subSeries = sampleSeries.subseries(4, 8);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
        trades.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));
        trades.add(new Trade(new Operation(6, OperationType.BUY), new Operation(7, OperationType.SELL)));
        trades.add(new Trade(new Operation(7, OperationType.BUY), new Operation(8, OperationType.SELL)));
        assertThat(mdd.calculate(subSeries, trades)).isEqualTo(.9d);
        
    }
}