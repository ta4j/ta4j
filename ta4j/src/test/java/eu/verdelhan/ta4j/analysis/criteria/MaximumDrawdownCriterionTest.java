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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.BaseTradingRecord;
import eu.verdelhan.ta4j.Order;
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.*;
import org.junit.Test;

public class MaximumDrawdownCriterionTest {

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 5, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();

        assertEquals(0d, mdd.calculate(series, new BaseTradingRecord()), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithOnlyGains() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 8, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(2), Order.sellAt(5));

        assertEquals(0d, mdd.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateShouldWork() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 5, 20, 3);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(3), Order.sellAt(4),
                Order.buyAt(5), Order.sellAt(6));

        assertEquals(.875d, mdd.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);

    }

    @Test
    public void calculateWithNullSeriesSizeShouldReturn0() {
        MockTimeSeries series = new MockTimeSeries(new double[] {});
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        assertEquals(0d, mdd.calculate(series, new BaseTradingRecord()), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void withTradesThatSellBeforeBuying() {
        MockTimeSeries series = new MockTimeSeries(2, 1, 3, 5, 6, 3, 20);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(3), Order.sellAt(4),
                Order.sellAt(5), Order.buyAt(6));
        assertEquals(.91, mdd.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void withSimpleTrades() {
        MockTimeSeries series = new MockTimeSeries(1, 10, 5, 6, 1);
        MaximumDrawdownCriterion mdd = new MaximumDrawdownCriterion();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(1), Order.sellAt(2),
                Order.buyAt(2), Order.sellAt(3),
                Order.buyAt(3), Order.sellAt(4));
        assertEquals(.9d, mdd.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new MaximumDrawdownCriterion();
        assertTrue(criterion.betterThan(0.9, 1.5));
        assertFalse(criterion.betterThan(1.2, 0.4));
    }
}