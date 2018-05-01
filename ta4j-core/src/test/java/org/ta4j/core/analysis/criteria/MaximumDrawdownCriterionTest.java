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

public class MaximumDrawdownCriterionTest extends AbstractCriterionTest {

    public MaximumDrawdownCriterionTest(Function<Number, Num> numFunction) {
        super(MaximumDrawdownCriterion.class, numFunction);
    }

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 2, 3, 6, 5, 20, 3);
        AnalysisCriterion mdd = getCriterion();

        assertNumEquals(0d, mdd.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOnlyGains() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 2, 3, 6, 8, 20, 3);
        AnalysisCriterion mdd = getCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        assertNumEquals(0d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void calculateShouldWork() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 2, 3, 6, 5, 20, 3);
        AnalysisCriterion mdd = getCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(3,series), Order.sellAt(4,series),
                Order.buyAt(5,series), Order.sellAt(6,series));

        assertNumEquals(.875d, mdd.calculate(series, tradingRecord));

    }

    @Test
    public void calculateWithNullSeriesSizeShouldReturn0() {
        MockTimeSeries series = new MockTimeSeries(numFunction, new double[] {});
        AnalysisCriterion mdd = getCriterion();
        assertNumEquals(0d, mdd.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void withTradesThatSellBeforeBuying() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 2, 1, 3, 5, 6, 3, 20);
        AnalysisCriterion mdd = getCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(3,series), Order.sellAt(4,series),
                Order.sellAt(5,series), Order.buyAt(6,series));
        assertNumEquals(.91, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void withSimpleTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 10, 5, 6, 1);
        AnalysisCriterion mdd = getCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(1,series), Order.sellAt(2,series),
                Order.buyAt(2,series), Order.sellAt(3,series),
                Order.buyAt(3,series), Order.sellAt(4,series));
        assertNumEquals(.9d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();;
        assertTrue(criterion.betterThan(numOf(0.9), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.2), numOf(0.4)));
    }
}