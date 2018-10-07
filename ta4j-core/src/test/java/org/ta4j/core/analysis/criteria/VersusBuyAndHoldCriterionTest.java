package org.ta4j.core.analysis.criteria;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

public class VersusBuyAndHoldCriterionTest extends AbstractCriterionTest{

    public VersusBuyAndHoldCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new VersusBuyAndHoldCriterion((AnalysisCriterion) params[0]), numFunction);
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 110, 100, 95, 105);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(2,series),
                Order.buyAt(3,series), Order.sellAt(5,series));

        AnalysisCriterion buyAndHold = getCriterion(new TotalProfitCriterion());
        assertNumEquals(1.10 * 1.05 / 1.05, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        AnalysisCriterion buyAndHold = getCriterion(new TotalProfitCriterion());
        assertNumEquals(0.95 * 0.7 / 0.7, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnlyOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);
        Trade trade = new Trade(Order.buyAt(0,series), Order.sellAt(1,series));

        AnalysisCriterion buyAndHold = getCriterion(new TotalProfitCriterion());
        assertNumEquals((100d / 70) / (100d / 95), buyAndHold.calculate(series, trade));
    }

    @Test
    public void calculateWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 70);

        AnalysisCriterion buyAndHold = getCriterion(new TotalProfitCriterion());
        assertNumEquals(1 / 0.7, buyAndHold.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithAverageProfit() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 130);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, NaN, NaN), Order.sellAt(1, NaN, NaN),
                Order.buyAt(2, NaN, NaN), Order.sellAt(5, NaN, NaN));

        AnalysisCriterion buyAndHold = getCriterion(new AverageProfitCriterion());

        assertNumEquals(Math.pow(95d/100 * 130d/100, 1d/6) / Math.pow(130d / 100, 1d/6), buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNumberOfBars() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 100, 80, 85, 130);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0,series), Order.sellAt(1,series),
                Order.buyAt(2,series), Order.sellAt(5,series));

        AnalysisCriterion buyAndHold = getCriterion(new NumberOfBarsCriterion());

        assertNumEquals(6d/6d, buyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion(new TotalProfitCriterion());
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }
}
