package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class TotalLossCriterionTest extends AbstractCriterionTest{

    public TotalLossCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new TotalLossCriterion(), numFunction);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(2,series),
                Order.buyAt(3,series), Order.sellAt(5,series));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(0, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(-35, profit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateProfitWithTradesThatStartSelling() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.sellAt(0,series), Order.buyAt(1,series),
                Order.sellAt(2,series), Order.buyAt(5,series));

        AnalysisCriterion profit = getCriterion();
        assertNumEquals(-35, profit.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }
}
