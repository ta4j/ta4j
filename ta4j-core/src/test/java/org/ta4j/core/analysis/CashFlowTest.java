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
package org.ta4j.core.analysis;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.Collections;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class CashFlowTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public CashFlowTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void cashFlowSize() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d, 3d, 4d, 5d);
        CashFlow cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());
        assertEquals(5, cashFlow.getSize());
    }

    @Test
    public void cashFlowBuyWithOnlyOneTrade() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(1, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
    }

    @Test
    public void cashFlowWithSellAndBuyOrders() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 2, 1, 3, 5, 6, 3, 20);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(1, sampleBarSeries), Order.buyAt(3, sampleBarSeries), Order.sellAt(4, sampleBarSeries),
                Order.sellAt(5, sampleBarSeries), Order.buyAt(6, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals("0.5", cashFlow.getValue(1));
        assertNumEquals("0.5", cashFlow.getValue(2));
        assertNumEquals("0.5", cashFlow.getValue(3));
        assertNumEquals("0.6", cashFlow.getValue(4));
        assertNumEquals("0.6", cashFlow.getValue(5));
        assertNumEquals("-2.8", cashFlow.getValue(6));
    }

    @Test
    public void cashFlowSell() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1, 2, 4, 8, 16, 32);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.sellAt(2, sampleBarSeries),
                Order.buyAt(3, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(1, cashFlow.getValue(2));
        assertNumEquals(0, cashFlow.getValue(3));
        assertNumEquals(0, cashFlow.getValue(4));
        assertNumEquals(0, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowShortSell() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1, 2, 4, 8, 16, 32);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(2, sampleBarSeries), Order.sellAt(2, sampleBarSeries), Order.buyAt(4, sampleBarSeries),
                Order.buyAt(4, sampleBarSeries), Order.sellAt(5, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(4, cashFlow.getValue(2));
        assertNumEquals(0, cashFlow.getValue(3));
        assertNumEquals(-8, cashFlow.getValue(4));
        assertNumEquals(-8, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowValueWithOnlyOneTradeAndAGapBefore() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 1d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(1, sampleBarSeries),
                Order.sellAt(2, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowValueWithOnlyOneTradeAndAGapAfter() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d, 2d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(1, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertEquals(3, cashFlow.getSize());
        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
    }

    @Test
    public void cashFlowValueWithTwoTradesAndLongTimeWithoutOrders() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 1d, 2d, 4d, 8d, 16d, 32d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(1, sampleBarSeries),
                Order.sellAt(2, sampleBarSeries), Order.buyAt(4, sampleBarSeries), Order.sellAt(5, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(1, cashFlow.getValue(1));
        assertNumEquals(2, cashFlow.getValue(2));
        assertNumEquals(2, cashFlow.getValue(3));
        assertNumEquals(2, cashFlow.getValue(4));
        assertNumEquals(4, cashFlow.getValue(5));
    }

    @Test
    public void cashFlowValue() {
        // First sample series
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 3d, 2d, 5d, 1000d, 5000d, 0.0001d, 4d, 7d, 6d, 7d,
                8d, 5d, 6d);
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(2, sampleBarSeries), Order.buyAt(6, sampleBarSeries), Order.sellAt(8, sampleBarSeries),
                Order.buyAt(9, sampleBarSeries), Order.sellAt(11, sampleBarSeries));

        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);

        assertNumEquals(1, cashFlow.getValue(0));
        assertNumEquals(2d / 3, cashFlow.getValue(1));
        assertNumEquals(5d / 3, cashFlow.getValue(2));
        assertNumEquals(5d / 3, cashFlow.getValue(3));
        assertNumEquals(5d / 3, cashFlow.getValue(4));
        assertNumEquals(5d / 3, cashFlow.getValue(5));
        assertNumEquals(5d / 3, cashFlow.getValue(6));
        assertNumEquals(5d / 3 * 7d / 4, cashFlow.getValue(7));
        assertNumEquals(5d / 3 * 6d / 4, cashFlow.getValue(8));
        assertNumEquals(5d / 3 * 6d / 4, cashFlow.getValue(9));
        assertNumEquals(5d / 3 * 6d / 4 * 8d / 7, cashFlow.getValue(10));
        assertNumEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(11));
        assertNumEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(12));

        // Second sample series
        sampleBarSeries = new MockBarSeries(numFunction, 5d, 6d, 3d, 7d, 8d, 6d, 10d, 15d, 6d);
        tradingRecord = new BaseTradingRecord(Order.buyAt(4, sampleBarSeries), Order.sellAt(5, sampleBarSeries),
                Order.buyAt(6, sampleBarSeries), Order.sellAt(8, sampleBarSeries));

        CashFlow flow = new CashFlow(sampleBarSeries, tradingRecord);
        assertNumEquals(1, flow.getValue(0));
        assertNumEquals(1, flow.getValue(1));
        assertNumEquals(1, flow.getValue(2));
        assertNumEquals(1, flow.getValue(3));
        assertNumEquals(1, flow.getValue(4));
        assertNumEquals("0.75", flow.getValue(5));
        assertNumEquals("0.75", flow.getValue(6));
        assertNumEquals("1.125", flow.getValue(7));
        assertNumEquals("0.45", flow.getValue(8));
    }

    @Test
    public void cashFlowValueWithNoTrades() {
        BarSeries sampleBarSeries = new MockBarSeries(numFunction, 3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d);
        CashFlow cashFlow = new CashFlow(sampleBarSeries, new BaseTradingRecord());
        assertNumEquals(1, cashFlow.getValue(4));
        assertNumEquals(1, cashFlow.getValue(7));
        assertNumEquals(1, cashFlow.getValue(9));
    }

    @Test
    public void reallyLongCashFlow() {
        int size = 1000000;
        BarSeries sampleBarSeries = new MockBarSeries(Collections.nCopies(size, new MockBar(10, numFunction)));
        TradingRecord tradingRecord = new BaseTradingRecord(Order.buyAt(0, sampleBarSeries),
                Order.sellAt(size - 1, sampleBarSeries));
        CashFlow cashFlow = new CashFlow(sampleBarSeries, tradingRecord);
        assertNumEquals(1, cashFlow.getValue(size - 1));
    }

}
