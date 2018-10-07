package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class BuyAndHoldCriterionTest extends AbstractCriterionTest{

    public BuyAndHoldCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new BuyAndHoldCriterion(), numFunction);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, series), Order.sellAt(2, series),
                Order.buyAt(3, series), Order.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(1.05, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction,100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, series), Order.sellAt(1, series),
                Order.buyAt(2, series), Order.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0.7, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0.7, buyAndHold.calculate(series, new BaseTradingRecord()));
    }
    
    @Test
    public void calculateWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105);
        Trade trade = new Trade(Order.buyAt(0, series), Order.sellAt(1, series));
        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(105d/100, buyAndHold.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(1.3), numOf(1.1)));
        assertFalse(criterion.betterThan(numOf(0.6), numOf(0.9)));
    }
}
