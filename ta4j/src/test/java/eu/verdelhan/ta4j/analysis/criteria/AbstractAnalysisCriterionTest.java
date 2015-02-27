/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.strategies.rules.BooleanRule;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class AbstractAnalysisCriterionTest {

    private Strategy alwaysStrategy;

    private MockStrategy buyAndHoldStrategy;

    private List<Strategy> strategies;

    @Before
    public void setUp() {
        alwaysStrategy = new Strategy();
        alwaysStrategy.setEntryRule(BooleanRule.TRUE);
        alwaysStrategy.setExitRule(BooleanRule.TRUE);
        buyAndHoldStrategy = new MockStrategy(new Operation[] { Operation.buyAt(0), null, null, null },
                new Operation[] { null, null, null, Operation.sellAt(4) });
        strategies = new ArrayList<Strategy>();
        strategies.add(alwaysStrategy);
        strategies.add(buyAndHoldStrategy);
    }

    @Test
    public void bestShouldBeAlwaysOperateOnProfit() {
        MockTimeSeries series = new MockTimeSeries(6.0, 9.0, 6.0, 6.0);
        Strategy bestStrategy = new TotalProfitCriterion().chooseBest(series, strategies);
        assertEquals(alwaysStrategy, bestStrategy);
    }

    @Test
    public void bestShouldBeBuyAndHoldOnLoss() {
        MockTimeSeries series = new MockTimeSeries(6.0, 3.0, 6.0, 6.0);
        Strategy bestStrategy = new TotalProfitCriterion().chooseBest(series, strategies);
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
