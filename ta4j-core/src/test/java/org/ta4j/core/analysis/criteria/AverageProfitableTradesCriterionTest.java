/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class AverageProfitableTradesCriterionTest extends AbstractCriterionTest {

    public AverageProfitableTradesCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new AverageProfitableTradesCriterion(), numFunction);
    }

    @Test
    public void calculate() {
        TimeSeries series = new MockTimeSeries(numFunction,100d, 95d, 102d, 105d, 97d, 113d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1, series),
                Order.buyAt(2, series), Order.sellAt(3, series),
                Order.buyAt(4, series), Order.sellAt(5, series));
        
        AnalysisCriterion average = getCriterion();
        
        assertNumEquals(2d/3, average.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneTrade() {
        TimeSeries series = new MockTimeSeries(numFunction,100d, 95d, 102d, 105d, 97d, 113d);
        Trade trade = new Trade(Order.buyAt(0, series), Order.sellAt(1, series));
            
        AnalysisCriterion average = getCriterion();
        assertNumEquals(numOf(0), average.calculate(series, trade));
        
        trade = new Trade(Order.buyAt(1, series), Order.sellAt(2, series));
        assertNumEquals(1, average.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(12), numOf(8)));
        assertFalse(criterion.betterThan(numOf(8), numOf(12)));
    }
}
