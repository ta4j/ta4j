package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class NumberOfTradesCriterionTest extends AbstractCriterionTest{

    public NumberOfTradesCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new NumberOfTradesCriterion(), numFunction);
    }

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(0, buyAndHold.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, series), Order.sellAt(2, series),
                Order.buyAt(3, series), Order.sellAt(5, series));

        AnalysisCriterion buyAndHold = getCriterion();
        assertNumEquals(2, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        Trade trade = new Trade();
        AnalysisCriterion tradesCriterion = getCriterion();

        assertNumEquals(1, tradesCriterion.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(7), numOf(4)));
    }
}
