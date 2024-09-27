/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
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
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.FixedRule;

public class AbstractAnalysisCriterionTest extends AbstractCriterionTest {

    private Strategy alwaysStrategy;

    private Strategy buyAndHoldStrategy;

    private List<Strategy> strategies;

    public AbstractAnalysisCriterionTest(NumFactory numFactory) {
        super(params -> new ReturnCriterion(), numFactory);
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
        AbstractAnalysisCriterion c2 = new EnterAndHoldCriterion(new ReturnCriterion());
        assertEquals("EnterAndHoldCriterion of ReturnCriterion", c2.toString());
        AbstractAnalysisCriterion c3 = new ReturnOverMaxDrawdownCriterion();
        assertEquals("Return Over Max Drawdown", c3.toString());
    }

}
