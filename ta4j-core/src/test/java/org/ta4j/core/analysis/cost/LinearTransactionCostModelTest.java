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
package org.ta4j.core.analysis.cost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.rules.FixedRule;

public class LinearTransactionCostModelTest {

    private CostModel transactionModel;

    @Before
    public void setUp() throws Exception {
        transactionModel = new LinearTransactionCostModel(0.01);
    }

    @Test
    public void calculateSingleTradeCost() {
        // Price - Amount calculation Test
        var price = DoubleNum.valueOf(100);
        var amount = DoubleNum.valueOf(2);
        var cost = transactionModel.calculate(price, amount);

        assertNumEquals(DoubleNum.valueOf(2), cost);
    }

    @Test
    public void calculateBuyPosition() {
        // Calculate the transaction costs of a closed long position
        var holdingPeriod = 2;
        var entry = Trade.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        var exit = Trade.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        var position = new Position(entry, exit, transactionModel, new ZeroCostModel());

        var costFromBuy = entry.getCost();
        var costFromSell = exit.getCost();
        var costsFromModel = transactionModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateSellPosition() {
        // Calculate the transaction costs of a closed short position
        var holdingPeriod = 2;
        var entry = Trade.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        var exit = Trade.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        var position = new Position(entry, exit, transactionModel, new ZeroCostModel());

        var costFromBuy = entry.getCost();
        var costFromSell = exit.getCost();
        var costsFromModel = transactionModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateOpenSellPosition() {
        // Calculate the transaction costs of an open position
        var currentIndex = 4;
        var position = new Position(Trade.TradeType.BUY, transactionModel, new ZeroCostModel());
        position.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        var costsFromModel = transactionModel.calculate(position, currentIndex);

        assertNumEquals(costsFromModel, DoubleNum.valueOf(1));
    }

    @Test
    public void testEquality() {
        LinearTransactionCostModel model = new LinearTransactionCostModel(0.1);
        CostModel modelSameClass = new LinearTransactionCostModel(0.2);
        CostModel modelSameFee = new LinearTransactionCostModel(0.1);
        CostModel modelOther = new ZeroCostModel();

        var equality = model.equals(modelSameFee);
        var inequality1 = model.equals(modelSameClass);
        var inequality2 = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality1);
        assertFalse(inequality2);
    }

    @Test
    public void testBacktesting() {
        var series = new BaseBarSeriesBuilder().withName("CostModel test")
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        var now = ZonedDateTime.now();
        var one = series.numFactory().one();
        var two = series.numFactory().numOf(2);
        var three = series.numFactory().numOf(3);
        var four = series.numFactory().numOf(4);
        series.barBuilder().endTime(now).openPrice(one).closePrice(one).highPrice(one).lowPrice(one).add();
        series.barBuilder()
                .endTime(now.plusSeconds(1))
                .openPrice(two)
                .closePrice(two)
                .highPrice(two)
                .lowPrice(two)
                .add();
        series.barBuilder()
                .endTime(now.plusSeconds(2))
                .openPrice(three)
                .closePrice(three)
                .highPrice(three)
                .lowPrice(three)
                .add();
        series.barBuilder()
                .endTime(now.plusSeconds(3))
                .openPrice(four)
                .closePrice(four)
                .highPrice(four)
                .lowPrice(four)
                .add();

        var entryRule = new FixedRule(0, 2);
        var exitRule = new FixedRule(1, 3);
        var strategies = new LinkedList<Strategy>();
        strategies.add(new BaseStrategy("Cost model test strategy", entryRule, exitRule));

        var orderFee = series.numFactory().numOf(new BigDecimal("0.0026"));
        var executor = new BacktestExecutor(series, new LinearTransactionCostModel(orderFee.doubleValue()),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        var amount = series.numFactory().numOf(25);
        var strategyResult = executor.execute(strategies, amount).get(0);

        var firstPositionBuy = one.plus(one.multipliedBy(orderFee));
        var firstPositionSell = two.minus(two.multipliedBy(orderFee));
        var firstPositionProfit = firstPositionSell.minus(firstPositionBuy).multipliedBy(amount);

        var secondPositionBuy = three.plus(three.multipliedBy(orderFee));
        var secondPositionSell = four.minus(four.multipliedBy(orderFee));
        var secondPositionProfit = secondPositionSell.minus(secondPositionBuy).multipliedBy(amount);

        var overallProfit = firstPositionProfit.plus(secondPositionProfit);

        assertEquals(overallProfit, strategyResult.getPerformanceReport().getTotalProfit());
    }
}
