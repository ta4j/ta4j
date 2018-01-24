/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class CashFlowTest {

    @Test
    public void cashFlowSize() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1d, 2d, 3d, 4d, 5d);
        CashFlow cashFlow = new CashFlow(sampleTimeSeries, new BaseTradingRecord());
        assertEquals(5, cashFlow.getSize());
    }

    @Test
    public void cashFlowBuyWithOnlyOneTrade() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleTimeSeries), Order.sellAt(1, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 2);
    }

    @Test
    public void cashFlowWithSellAndBuyOrders() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(2, 1, 3, 5, 6, 3, 20);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, sampleTimeSeries), Order.sellAt(1, sampleTimeSeries),
                Order.buyAt(3, sampleTimeSeries), Order.sellAt(4, sampleTimeSeries),
                Order.sellAt(5, sampleTimeSeries), Order.buyAt(6, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), "0.5");
        assertDecimalEquals(cashFlow.getValue(2), "0.5");
        assertDecimalEquals(cashFlow.getValue(3), "0.5");
        assertDecimalEquals(cashFlow.getValue(4), "0.6");
        assertDecimalEquals(cashFlow.getValue(5), "0.6");
        assertDecimalEquals(cashFlow.getValue(6), "0.09");
    }


    @Test
    public void cashFlowSell() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1, 2, 4, 8, 16, 32);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.sellAt(2, sampleTimeSeries), Order.buyAt(3, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 1);
        assertDecimalEquals(cashFlow.getValue(2), 1);
        assertDecimalEquals(cashFlow.getValue(3), "0.5");
        assertDecimalEquals(cashFlow.getValue(4), "0.5");
        assertDecimalEquals(cashFlow.getValue(5), "0.5");
    }

    @Test
    public void cashFlowShortSell() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1, 2, 4, 8, 16, 32);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, sampleTimeSeries), Order.sellAt(2, sampleTimeSeries),
                Order.sellAt(2, sampleTimeSeries), Order.buyAt(4, sampleTimeSeries),
                Order.buyAt(4, sampleTimeSeries), Order.sellAt(5, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 2);
        assertDecimalEquals(cashFlow.getValue(2), 4);
        assertDecimalEquals(cashFlow.getValue(3), 2);
        assertDecimalEquals(cashFlow.getValue(4), 1);
        assertDecimalEquals(cashFlow.getValue(5), 2);
    }

    @Test
    public void cashFlowValueWithOnlyOneTradeAndAGapBefore() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1d, 1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(1, sampleTimeSeries), Order.sellAt(2, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 1);
        assertDecimalEquals(cashFlow.getValue(2), 2);
    }

    @Test
    public void cashFlowValueWithOnlyOneTradeAndAGapAfter() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1d, 2d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleTimeSeries), Order.sellAt(1, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertEquals(3, cashFlow.getSize());
        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 2);
        assertDecimalEquals(cashFlow.getValue(2), 2);
    }

    @Test
    public void cashFlowValueWithTwoTradesAndLongTimeWithoutOrders() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(1d, 2d, 4d, 8d, 16d, 32d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(1, sampleTimeSeries), Order.sellAt(2, sampleTimeSeries),
                Order.buyAt(4, sampleTimeSeries), Order.sellAt(5, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 1);
        assertDecimalEquals(cashFlow.getValue(2), 2);
        assertDecimalEquals(cashFlow.getValue(3), 2);
        assertDecimalEquals(cashFlow.getValue(4), 2);
        assertDecimalEquals(cashFlow.getValue(5), 4);
    }

    @Test
    public void cashFlowValue() {
    	// First sample series
        TimeSeries sampleTimeSeries = new MockTimeSeries(3d, 2d, 5d, 1000d, 5000d, 0.0001d, 4d, 7d,
                6d, 7d, 8d, 5d, 6d);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, sampleTimeSeries), Order.sellAt(2, sampleTimeSeries),
                Order.buyAt(6, sampleTimeSeries), Order.sellAt(8, sampleTimeSeries),
                Order.buyAt(9, sampleTimeSeries), Order.sellAt(11, sampleTimeSeries));

        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);

        assertDecimalEquals(cashFlow.getValue(0), 1);
        assertDecimalEquals(cashFlow.getValue(1), 2d/3);
        assertDecimalEquals(cashFlow.getValue(2), 5d/3);
        assertDecimalEquals(cashFlow.getValue(3), 5d/3);
        assertDecimalEquals(cashFlow.getValue(4), 5d/3);
        assertDecimalEquals(cashFlow.getValue(5), 5d/3);
        assertDecimalEquals(cashFlow.getValue(6), 5d/3);
        assertDecimalEquals(cashFlow.getValue(7), 5d/3 * 7d/4);
        assertDecimalEquals(cashFlow.getValue(8), 5d/3 * 6d/4);
        assertDecimalEquals(cashFlow.getValue(9), 5d/3 * 6d/4);
        assertDecimalEquals(cashFlow.getValue(10), 5d/3 * 6d/4 * 8d/7);
        assertDecimalEquals(cashFlow.getValue(11), 5d/3 * 6d/4 * 5d/7);
        assertDecimalEquals(cashFlow.getValue(12), 5d/3 * 6d/4 * 5d/7);

        // Second sample series
        sampleTimeSeries = new MockTimeSeries(5d, 6d, 3d, 7d, 8d, 6d, 10d, 15d, 6d);
		tradingRecord = new BaseTradingRecord(
				Order.buyAt(4, sampleTimeSeries), Order.sellAt(5, sampleTimeSeries),
				Order.buyAt(6, sampleTimeSeries), Order.sellAt(8, sampleTimeSeries));

		CashFlow flow = new CashFlow(sampleTimeSeries, tradingRecord);
		assertDecimalEquals(flow.getValue(0), 1);
		assertDecimalEquals(flow.getValue(1), 1);
		assertDecimalEquals(flow.getValue(2), 1);
		assertDecimalEquals(flow.getValue(3), 1);
		assertDecimalEquals(flow.getValue(4), 1);
		assertDecimalEquals(flow.getValue(5), "0.75");
		assertDecimalEquals(flow.getValue(6), "0.75");
		assertDecimalEquals(flow.getValue(7), "1.125");
		assertDecimalEquals(flow.getValue(8), "0.45");
    }

    @Test
    public void cashFlowValueWithNoTrades() {
        TimeSeries sampleTimeSeries = new MockTimeSeries(3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d);
        CashFlow cashFlow = new CashFlow(sampleTimeSeries, new BaseTradingRecord());
        assertDecimalEquals(cashFlow.getValue(4), 1);
        assertDecimalEquals(cashFlow.getValue(7), 1);
        assertDecimalEquals(cashFlow.getValue(9), 1);
    }

    @Test
    public void reallyLongCashFlow() {
        int size = 1000000;
        TimeSeries sampleTimeSeries = new MockTimeSeries(Collections.nCopies(size, new MockBar(10)));
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleTimeSeries), Order.sellAt(size - 1, sampleTimeSeries));
        CashFlow cashFlow = new CashFlow(sampleTimeSeries, tradingRecord);
        assertDecimalEquals(cashFlow.getValue(size - 1), 1);
    }

}
