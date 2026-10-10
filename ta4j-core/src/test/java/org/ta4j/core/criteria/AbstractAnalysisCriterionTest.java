/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static junit.framework.TestCase.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.FixedRule;

public class AbstractAnalysisCriterionTest extends AbstractCriterionTest {

    private Strategy alwaysStrategy;

    private Strategy buyAndHoldStrategy;

    private List<Strategy> strategies;

    public AbstractAnalysisCriterionTest(NumFactory numFactory) {
        super(params -> new GrossReturnCriterion(), numFactory);
    }

    @Before
    public void setUp() {
        alwaysStrategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        buyAndHoldStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(4));
        strategies = new ArrayList<>();
        strategies.add(alwaysStrategy);
        strategies.add(buyAndHoldStrategy);
    }

    @Test
    public void bestShouldBeAlwaysOperateOnProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(6.0, 9.0, 6.0, 6.0).build();
        var manager = new BarSeriesManager(series);
        Strategy bestStrategy = getCriterion().chooseBest(manager, TradeType.BUY, strategies);
        assertEquals(alwaysStrategy, bestStrategy);
    }

    @Test
    public void bestShouldBeBuyAndHoldOnLoss() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(6.0, 3.0, 6.0, 6.0).build();
        var manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy bestStrategy = getCriterion().chooseBest(manager, TradeType.BUY, strategies);
        assertEquals(buyAndHoldStrategy, bestStrategy);
    }

    @Test
    public void toStringMethod() {
        AbstractAnalysisCriterion c1 = new AverageReturnPerBarCriterion();
        assertEquals("Average Return Per Bar", c1.toString());
        AbstractAnalysisCriterion c2 = new EnterAndHoldCriterion(new GrossReturnCriterion());
        assertEquals("EnterAndHoldCriterion of GrossReturnCriterion", c2.toString());
        AbstractAnalysisCriterion c3 = new ReturnOverMaxDrawdownCriterion();
        assertEquals("Return Over Max Drawdown", c3.toString());
    }

}
