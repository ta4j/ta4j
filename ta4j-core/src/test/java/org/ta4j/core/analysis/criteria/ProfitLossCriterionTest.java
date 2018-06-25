package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class ProfitLossCriterionTest  extends AbstractCriterionTest {

    public ProfitLossCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new ProfitLossCriterion(), numFunction);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series, series.numOf(50)), Order.sellAt(2,series, series.numOf(50)),
                Order.buyAt(3,series, series.numOf(50)), Order.sellAt(5,series, series.numOf(50)));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(500 + 250, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series, series.numOf(50)), Order.sellAt(1,series, series.numOf(50)),
                Order.buyAt(2,series, series.numOf(50)), Order.sellAt(5,series, series.numOf(50)));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(-250 - 1500, profit.calculate(series, tradingRecord));
    }


    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(5000), numOf(4500)));
        assertFalse(criterion.betterThan(numOf(4500), numOf(5000)));
    }

}
