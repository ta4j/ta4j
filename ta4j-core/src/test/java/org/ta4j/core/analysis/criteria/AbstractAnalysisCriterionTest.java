package org.ta4j.core.analysis.criteria;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.FixedRule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;

public class AbstractAnalysisCriterionTest extends AbstractCriterionTest {

    private Strategy alwaysStrategy;

    private Strategy buyAndHoldStrategy;

    private List<Strategy> strategies;

    public AbstractAnalysisCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new TotalProfitCriterion(), numFunction);
    }

    @Before
    public void setUp() {
        alwaysStrategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        buyAndHoldStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(4));
        strategies = new ArrayList<Strategy>();
        strategies.add(alwaysStrategy);
        strategies.add(buyAndHoldStrategy);
    }

    @Test
    public void bestShouldBeAlwaysOperateOnProfit() {
        MockTimeSeries series = new MockTimeSeries(numFunction,6.0, 9.0, 6.0, 6.0);
        TimeSeriesManager manager = new TimeSeriesManager(series);
        Strategy bestStrategy = getCriterion().chooseBest(manager, strategies);
        assertEquals(alwaysStrategy, bestStrategy);
    }

    @Test
    public void bestShouldBeBuyAndHoldOnLoss() {
        MockTimeSeries series = new MockTimeSeries(numFunction,6.0, 3.0, 6.0, 6.0);
        TimeSeriesManager manager = new TimeSeriesManager(series);
        Strategy bestStrategy = getCriterion().chooseBest(manager, strategies);
        assertEquals(buyAndHoldStrategy, bestStrategy);
    }

    @Test
    public void toStringMethod() {
        AbstractAnalysisCriterion c1 = new AverageProfitCriterion();
        assertEquals("Average Profit", c1.toString());
        AbstractAnalysisCriterion c2 = new BuyAndHoldCriterion();
        assertEquals("Buy And Hold", c2.toString());
        AbstractAnalysisCriterion c3 = new RewardRiskRatioCriterion();
        assertEquals("Reward Risk Ratio", c3.toString());
    }

}
