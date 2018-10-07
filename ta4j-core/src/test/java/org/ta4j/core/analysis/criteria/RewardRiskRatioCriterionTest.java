package org.ta4j.core.analysis.criteria;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class RewardRiskRatioCriterionTest extends AbstractCriterionTest{

    private AnalysisCriterion rrc;

    public RewardRiskRatioCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new RewardRiskRatioCriterion(), numFunction);
    }

    @Before
    public void setUp() {
        this.rrc = getCriterion();
    }

    @Test
    public void rewardRiskRatioCriterion() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 105, 95, 100, 90, 95, 80, 120);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, series), Order.sellAt(1, series),
                Order.buyAt(2, series), Order.sellAt(4, series),
                Order.buyAt(5, series), Order.sellAt(7, series));



        double totalProfit = (105d / 100) * (90d / 95d) * (120d / 95);
        double peak = (105d / 100) * (100d / 95);
        double low = (105d / 100) * (90d / 95) * (80d / 95);

        assertNumEquals(totalProfit / ((peak - low) / peak), rrc.calculate(series, tradingRecord));
    }

    @Test
    public void rewardRiskRatioCriterionOnlyWithGain() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 2, 3, 6, 8, 20, 3);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0, series), Order.sellAt(1, series),
                Order.buyAt(2, series), Order.sellAt(5, series));
        assertTrue(rrc.calculate(series, tradingRecord).isNaN());
    }

    @Test
    public void rewardRiskRatioCriterionWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 1, 2, 3, 6, 8, 20, 3);
        assertTrue(rrc.calculate(series, new BaseTradingRecord()).isNaN());
    }

    @Test
    public void withOneTrade() {
        MockTimeSeries series = new MockTimeSeries(numFunction, 100, 95, 95, 100, 90, 95, 80, 120);
        Trade trade = new Trade(Order.buyAt(0, series), Order.sellAt(1, series));

        AnalysisCriterion ratioCriterion = getCriterion();
        assertNumEquals((95d/100) / ((1d - 0.95d)), ratioCriterion.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3.5), numOf(2.2)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.7)));
    }
}
