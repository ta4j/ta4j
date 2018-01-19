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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExternalCriterionTest;
import org.ta4j.core.TATestsUtils;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockTimeSeries;

public class LinearTransactionCostCriterionTest extends CriterionTest {

    private ExternalCriterionTest xls;

    public LinearTransactionCostCriterionTest() throws Exception {
        super((params) -> new LinearTransactionCostCriterion((double) params[0], (double) params[1], (double) params[2]));
        xls = new XLSCriterionTest(this.getClass(), "LTC.xls", 16, 6);
    }

    @Test
    public void externalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        TradingRecord xlsTradingRecord = xls.getTradingRecord();
        double value;

        value = getCriterion(1000d, 0.005, 0.2).calculate(xlsSeries, xlsTradingRecord);
        assertEquals(xls.getFinalCriterionValue(1000d, 0.005, 0.2).doubleValue(), value, TATestsUtils.TA_OFFSET);
        assertEquals(843.5492, value, TATestsUtils.TA_OFFSET);

        value = getCriterion(1000d, 0.1, 1.0).calculate(xlsSeries, xlsTradingRecord);
        assertEquals(xls.getFinalCriterionValue(1000d, 0.1, 1.0).doubleValue(), value, TATestsUtils.TA_OFFSET);
        assertEquals(1122.4410, value, TATestsUtils.TA_OFFSET);
    }

    @Test
    public void dummyData() {
        MockTimeSeries series = new MockTimeSeries(100, 150, 200, 100, 50, 100);
        TradingRecord tradingRecord = new BaseTradingRecord();
        double criterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertEquals(12.861, criterion, TATestsUtils.TA_OFFSET);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertEquals(24.3759, criterion, TATestsUtils.TA_OFFSET);

        tradingRecord.operate(5);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertEquals(28.2488, criterion, TATestsUtils.TA_OFFSET);
    }

    @Test
    public void fixedCost() {
        MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord();
        double criterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertEquals(2.6d, criterion, TATestsUtils.TA_OFFSET);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertEquals(5.2d, criterion, TATestsUtils.TA_OFFSET);

        tradingRecord.operate(0);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertEquals(6.5d, criterion, TATestsUtils.TA_OFFSET);
    }

    @Test
    public void fixedCostWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        Trade trade = new Trade();
        double criterion;

        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertEquals(0d, criterion, TATestsUtils.TA_OFFSET);

        trade.operate(1);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertEquals(0.75d, criterion, TATestsUtils.TA_OFFSET);

        trade.operate(3);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertEquals(1.5d, criterion, TATestsUtils.TA_OFFSET);

        trade.operate(4);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertEquals(1.5d, criterion, TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new LinearTransactionCostCriterion(1000, 0.5);
        assertTrue(criterion.betterThan(3.1, 4.2));
        assertFalse(criterion.betterThan(2.1, 1.9));
    }
}
