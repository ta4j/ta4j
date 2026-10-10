/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExternalCriterionTest;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LinearTransactionCostCriterionTest extends AbstractCriterionTest {

    private final ExternalCriterionTest xls;

    public LinearTransactionCostCriterionTest(NumFactory numFactory) {
        super(params -> new LinearTransactionCostCriterion((double) params[0], (double) params[1], (double) params[2]),
                numFactory);
        xls = new XLSCriterionTest(this.getClass(), "LTC.xls", 16, 6, numFactory);
    }

    @Test
    public void externalData() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
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
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 150, 200, 100, 50, 100)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        Num criterion;

        tradingRecord.operate(0);
        tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(12.861, criterion);

        tradingRecord.operate(2);
        tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(24.3759, criterion);

        tradingRecord.operate(5);
        criterion = getCriterion(1000d, 0.005, 0.2).calculate(series, tradingRecord);
        assertNumEquals(28.2488, criterion);
    }

    @Test
    public void fixedCost() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        Num criterion;

        tradingRecord.operate(0);
        tradingRecord.operate(1);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(2.6d, criterion);

        tradingRecord.operate(2);
        tradingRecord.operate(3);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(5.2d, criterion);

        tradingRecord.operate(0);
        criterion = getCriterion(1000d, 0d, 1.3d).calculate(series, tradingRecord);
        assertNumEquals(6.5d, criterion);
    }

    @Test
    public void fixedCostWithOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        Position position = new Position();
        Num criterion;

        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, position);
        assertNumEquals(0d, criterion);

        position.operate(1);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, position);
        assertNumEquals(0.75d, criterion);

        position.operate(3);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, position);
        assertNumEquals(1.5d, criterion);

        position.operate(4);
        criterion = getCriterion(1000d, 0d, 0.75d).calculate(series, position);
        assertNumEquals(1.5d, criterion);
    }

    @Test
    public void betterThan() {
        var criterion = new LinearTransactionCostCriterion(1000, 0.5);
        assertTrue(criterion.betterThan(numOf(3.1), numOf(4.2)));
        assertFalse(criterion.betterThan(numOf(2.1), numOf(1.9)));
    }
}
