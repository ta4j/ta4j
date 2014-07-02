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

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.Decision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.junit.Test;


public class FixedTransactionCostCriterionTest {
    
    @Test
    public void calculate() {
        MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
        List<Trade> trades = new ArrayList<Trade>();
        AnalysisCriterion transactionCost = new FixedTransactionCostCriterion(1.3d);
        
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        assertThat(transactionCost.calculate(series, trades)).isEqualTo(2.6d);
        
        trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
        assertThat(transactionCost.calculate(series, trades)).isEqualTo(5.2d);

        Trade t = new Trade();
        trades.add(t);
        assertThat(transactionCost.calculate(series, trades)).isEqualTo(5.2d);

        t.operate(0);
        assertThat(transactionCost.calculate(series, trades)).isEqualTo(6.5d);
    }

    @Test
    public void calculateWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        Trade trade = new Trade();
        AnalysisCriterion transactionCost = new FixedTransactionCostCriterion(0.75d);

        assertThat(transactionCost.calculate(series, trade)).isZero();

        trade.operate(1);
        assertThat(transactionCost.calculate(series, trade)).isEqualTo(0.75d);

        trade.operate(3);
        assertThat(transactionCost.calculate(series, trade)).isEqualTo(1.5d);

        trade.operate(4);
        assertThat(transactionCost.calculate(series, trade)).isEqualTo(1.5d);
    }
    
    @Test
    public void summarize() {
        DateTime date = new DateTime();
        
        MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 }, new DateTime[]{date, date, date, date, date, date});
        List<Trade> trades = new ArrayList<Trade>();
        List<Decision> decisions = new ArrayList<Decision>();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        
        decisions.add(new Decision(null, series, null, trades));
        
        trades = new ArrayList<Trade>();
        trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
        trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
        
        decisions.add(new Decision(null, series, null, trades));
        decisions.add(new Decision(null, series, null, trades));
        
        AnalysisCriterion transactionCosts = new FixedTransactionCostCriterion(1.10d);

        assertThat(transactionCosts.summarize(series, decisions)).isEqualTo(11d);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new FixedTransactionCostCriterion(0.5);
        assertThat(criterion.betterThan(3.1, 4.2)).isTrue();
        assertThat(criterion.betterThan(2.1, 1.9)).isFalse();
    }
}
