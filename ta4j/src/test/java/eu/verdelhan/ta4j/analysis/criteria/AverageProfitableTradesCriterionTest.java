/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
public class AverageProfitableTradesCriterionTest {

    @Test
    public void calculate() {
        TimeSeries series = new MockTimeSeries(100d, 95d, 102d, 105d, 97d, 113d);
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(new Trade(Operation.buyAt(0), Operation.buyAt(1)));
        trades.add(new Trade(Operation.buyAt(2), Operation.buyAt(3)));
        trades.add(new Trade(Operation.buyAt(4), Operation.buyAt(5)));
        
        AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
        
        assertEquals(2d/3, average.calculate(series, trades), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithOneTrade() {
        TimeSeries series = new MockTimeSeries(100d, 95d, 102d, 105d, 97d, 113d);
        Trade trade = new Trade(Operation.buyAt(0), Operation.buyAt(1));
            
        AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
        assertEquals(0d, average.calculate(series, trade), TATestsUtils.TA_OFFSET);
        
        trade = new Trade(Operation.buyAt(1), Operation.buyAt(2));
        assertEquals(1d, average.calculate(series, trade), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new AverageProfitableTradesCriterion();
        assertTrue(criterion.betterThan(12, 8));
        assertFalse(criterion.betterThan(8, 12));
    }
}
