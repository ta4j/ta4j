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
package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import static org.junit.Assert.*;
import static org.ta4j.core.TATestsUtils.*;


public class LinearTransactionCostCriterionTest extends CriterionTest {

    public LinearTransactionCostCriterionTest() throws Exception {
        super((params) -> { return new LinearTransactionCostCriterion(                  // criterion factory
                    (double) params[0], (double) params[1], (double) params[2]); },     //   (constructor params)
                "LTC.xls",                                                              // xls file name
                16,                                                                     // criterion xls column
                6);                                                                     // states xls column
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        TimeSeries series = getSeries();
        TradingRecord tradingRecord = getTradingRecord();
        Decimal actualCriterion;

        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0.005, 0.2);
        assertEquals(getCriterion(1000d, 0.005, 0.2).doubleValue(), actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);
        assertEquals(843.5492, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0.1, 1.0);
        assertDecimalEquals(getCriterion(1000d, 0.1, 1.0), actualCriterion.doubleValue());
        assertEquals(1122.4410, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateLinearCost() {
        MockTimeSeries series = new MockTimeSeries(100, 150, 200, 100, 50, 100);
        TradingRecord tradingRecord = new BaseTradingRecord();
        Decimal actualCriterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0.005, 0.2);
        assertEquals(12.861, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0.005, 0.2);
        assertEquals(24.3759, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        tradingRecord.operate(5);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0.005, 0.2);
        assertEquals(28.2488, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateFixedCost() {
        MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord();
        Decimal actualCriterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0d, 1.3d);
        assertEquals(2.6d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0d, 1.3d);
        assertEquals(5.2d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        tradingRecord.operate(0);
        actualCriterion = testCriterion(series, tradingRecord, 1000d, 0d, 1.3d);
        assertEquals(6.5d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateFixedCostWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        Trade trade = new Trade();
        Decimal actualCriterion;

        actualCriterion = testCriterion(series, trade, 1000d, 0d, 0.75d);
        assertEquals(0d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        trade.operate(1);
        actualCriterion = testCriterion(series, trade, 1000d, 0d, 0.75d);
        assertEquals(0.75d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        trade.operate(3);
        actualCriterion = testCriterion(series, trade, 1000d, 0d, 0.75d);
        assertEquals(1.5d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);

        trade.operate(4);
        actualCriterion = testCriterion(series, trade, 1000d, 0d, 0.75d);
        assertEquals(1.5d, actualCriterion.doubleValue(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new LinearTransactionCostCriterion(1000, 0.5);
        assertTrue(criterion.betterThan(3.1, 4.2));
        assertFalse(criterion.betterThan(2.1, 1.9));
    }
}
