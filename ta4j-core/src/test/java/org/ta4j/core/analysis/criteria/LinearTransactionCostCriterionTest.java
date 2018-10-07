package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class LinearTransactionCostCriterionTest extends AbstractCriterionTest{

    private ExternalCriterionTest xls;

    public LinearTransactionCostCriterionTest(Function<Number, Num> numFunction) throws Exception {
        super((params) -> new LinearTransactionCostCriterion((double) params[0], (double) params[1], (double) params[2]),numFunction);
        xls = new XLSCriterionTest(this.getClass(), "LTC.xls", 16, 6, numFunction);
    }

    @Test
    public void externalData() throws Exception {
        TimeSeries xlsSeries = xls.getSeries();
        TradingRecord xlsTradingRecord = xls.getTradingRecord();
        Num value;

        value = getCriterion(1000d, 0.005, 0.2).calculate(xlsSeries, xlsTradingRecord);
        assertNumEquals(xls.getFinalCriterionValue(1000d, 0.005, 0.2).doubleValue(), value);
        assertNumEquals(843.5492, value);

        value = getCriterion(1000d, 0.1, 1.0).calculate(xlsSeries, xlsTradingRecord);
        assertNumEquals(xls.getFinalCriterionValue(1000d, 0.1, 1.0).doubleValue(), value);
        assertNumEquals(1122.4410, value);
    }

    @Test
    public void dummyData() {
        MockTimeSeries series = new MockTimeSeries(numFunction,100, 150, 200, 100, 50, 100);
        TradingRecord tradingRecord = new BaseTradingRecord();
        Num criterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(12.861, criterion);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(24.3759, criterion);

        tradingRecord.operate(5);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(28.2488, criterion);
    }

    @Test
    public void fixedCost() {
        MockTimeSeries series = new MockTimeSeries(numFunction,100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord();
        Num criterion;

        tradingRecord.operate(0);  tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(2.6d, criterion);

        tradingRecord.operate(2);  tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(5.2d, criterion);

        tradingRecord.operate(0);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(6.5d, criterion);
    }

    @Test
    public void fixedCostWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        Trade trade = new Trade();
        Num criterion;

        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertNumEquals(0d, criterion);

        trade.operate(1);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertNumEquals(0.75d, criterion);

        trade.operate(3);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertNumEquals(1.5d, criterion);

        trade.operate(4);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, trade);
        assertNumEquals(1.5d, criterion);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new LinearTransactionCostCriterion(1000, 0.5);
        assertTrue(criterion.betterThan(numOf(3.1), numOf(4.2)));
        assertFalse(criterion.betterThan(numOf(2.1), numOf(1.9)));
    }
}
