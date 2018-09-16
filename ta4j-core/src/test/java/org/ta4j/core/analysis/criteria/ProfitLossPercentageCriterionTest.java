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

public class ProfitLossPercentageCriterionTest extends AbstractCriterionTest {

    public ProfitLossPercentageCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new ProfitLossPercentageCriterion(), numFunction);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(2,series),
                Order.buyAt(3,series), Order.sellAt(5,series));
        AnalysisCriterion profit = getCriterion();
        assertNumEquals(10 + 5, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(-5 + -30, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneWinningAndOneLosingTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 195, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(95 + -30, profit.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(50), numOf(45)));
        assertFalse(criterion.betterThan(numOf(45), numOf(50)));
    }
}