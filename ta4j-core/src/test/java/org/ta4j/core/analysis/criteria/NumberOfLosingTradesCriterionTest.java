package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class NumberOfLosingTradesCriterionTest extends AbstractCriterionTest {

    public NumberOfLosingTradesCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new NumberOfLosingTradesCriterion(), numFunction);
    }

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);

        assertNumEquals(0, getCriterion().calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithTwoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(1, series), Order.sellAt(3, series),
                Order.buyAt(3, series), Order.sellAt(4, series));

        assertNumEquals(2, getCriterion().calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        Trade trade = new Trade(Order.buyAt(1, series), Order.sellAt(3, series));

        assertNumEquals(1, getCriterion().calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3), numOf(6)));
        assertFalse(criterion.betterThan(numOf(7), numOf(4)));
    }

    @Test
    public void testCalculateOneOpenTradeShouldReturnZero() {
        openedTradeUtils.testCalculateOneOpenTradeShouldReturnExpectedValue(numFunction, getCriterion(), 0);
    }
}
