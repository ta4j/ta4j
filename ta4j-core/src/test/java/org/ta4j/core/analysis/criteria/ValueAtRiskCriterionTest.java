/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class ValueAtRiskCriterionTest extends AbstractCriterionTest {
    private MockBarSeries series;

    public ValueAtRiskCriterionTest(Function<Number, Num> numFunction) {
        // LOG returns requre DoubleNum implementation
        super((params) -> new ValueAtRiskCriterion(0.95), DoubleNum::valueOf);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        series = new MockBarSeries(numFunction, 100d, 105d, 106d, 107d, 108d, 115d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, series), Order.sellAt(2, series),
                Order.buyAt(3, series), Order.sellAt(5, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numOf(0.0), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimpleTrade() {
        series = new MockBarSeries(numFunction, 100d, 104d, 90d, 100d, 95d, 105d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, series), Order.sellAt(2, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numOf(Math.log(90d / 104)), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        series = new MockBarSeries(numFunction, 100d, 95d, 100d, 80d, 85d, 70d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, series), Order.sellAt(1, series),
                Order.buyAt(2, series), Order.sellAt(5, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numOf(Math.log(80d / 100)), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNoBarsShouldReturn0() {
        series = new MockBarSeries(numFunction, 100d, 95d, 100d, 80d, 85d, 70d);
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numOf(0), varCriterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithBuyAndHold() {
        series = new MockBarSeries(numFunction, 100d, 99d);
        Trade trade = new Trade(Order.buyAt(0, series), Order.sellAt(1, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numOf(Math.log(99d / 100)), varCriterion.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(-0.1), numOf(-0.2)));
        assertFalse(criterion.betterThan(numOf(-0.1), numOf(0.0)));
    }
}
