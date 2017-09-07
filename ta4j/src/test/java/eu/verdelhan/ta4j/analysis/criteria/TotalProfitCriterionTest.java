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
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.*;
import org.junit.Test;

public class TotalProfitCriterionTest {

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(2),
                Order.buyAt(3), Order.sellAt(5));

        AnalysisCriterion profit = new TotalProfitCriterion();
        assertEquals(1.10 * 1.05, profit.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(2), Order.sellAt(5));

        AnalysisCriterion profit = new TotalProfitCriterion();
        assertEquals(0.95 * 0.7, profit.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateProfitWithTradesThatStartSelling() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.sellAt(0), Order.buyAt(1),
                Order.sellAt(2), Order.buyAt(5));

        AnalysisCriterion profit = new TotalProfitCriterion();
        assertEquals((1 / 0.95) * (1 / 0.7), profit.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithNoTradesShouldReturn1() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);

        AnalysisCriterion profit = new TotalProfitCriterion();
        assertEquals(1d, profit.calculate(series, new BaseTradingRecord()), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithOpenedTradeShouldReturn1() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        AnalysisCriterion profit = new TotalProfitCriterion();
        Trade trade = new Trade();
        assertEquals(1d, profit.calculate(series, trade), TATestsUtils.TA_OFFSET);
        trade.operate(0);
        assertEquals(1d, profit.calculate(series, trade), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new TotalProfitCriterion();
        assertTrue(criterion.betterThan(2.0, 1.5));
        assertFalse(criterion.betterThan(1.5, 2.0));
    }
}
