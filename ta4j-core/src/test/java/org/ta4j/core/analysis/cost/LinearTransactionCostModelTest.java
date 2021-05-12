/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BacktestExecutor;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
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
        Num price = DoubleNum.valueOf(100);
        Num amount = DoubleNum.valueOf(2);
        Num cost = transactionModel.calculate(price, amount);

        assertNumEquals(DoubleNum.valueOf(2), cost);
    }

    @Test
    public void calculateBuyPosition() {
        // Calculate the transaction costs of a closed long position
        int holdingPeriod = 2;
        Trade entry = Trade.buyAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Trade exit = Trade.sellAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        Position position = new Position(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateSellPosition() {
        // Calculate the transaction costs of a closed short position
        int holdingPeriod = 2;
        Trade entry = Trade.sellAt(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1), transactionModel);
        Trade exit = Trade.buyAt(holdingPeriod, DoubleNum.valueOf(110), DoubleNum.valueOf(1), transactionModel);

        Position position = new Position(entry, exit, transactionModel, new ZeroCostModel());

        Num costFromBuy = entry.getCost();
        Num costFromSell = exit.getCost();
        Num costsFromModel = transactionModel.calculate(position, holdingPeriod);

        assertNumEquals(costsFromModel, costFromBuy.plus(costFromSell));
        assertNumEquals(costsFromModel, DoubleNum.valueOf(2.1));
        assertNumEquals(costFromBuy, DoubleNum.valueOf(1));
    }

    @Test
    public void calculateOpenSellPosition() {
        // Calculate the transaction costs of an open position
        int currentIndex = 4;
        Position position = new Position(Trade.TradeType.BUY, transactionModel, new ZeroCostModel());
        position.operate(0, DoubleNum.valueOf(100), DoubleNum.valueOf(1));

        Num costsFromModel = transactionModel.calculate(position, currentIndex);

        assertNumEquals(costsFromModel, DoubleNum.valueOf(1));
    }

    @Test
    public void testEquality() {
        LinearTransactionCostModel model = new LinearTransactionCostModel(0.1);
        CostModel modelSameClass = new LinearTransactionCostModel(0.2);
        CostModel modelSameFee = new LinearTransactionCostModel(0.1);
        CostModel modelOther = new ZeroCostModel();

        boolean equality = model.equals(modelSameFee);
        boolean inequality1 = model.equals(modelSameClass);
        boolean inequality2 = model.equals(modelOther);

        assertTrue(equality);
        assertFalse(inequality1);
        assertFalse(inequality2);
    }

    @Test
    public void testBacktesting() {
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("CostModel test").build();
        ZonedDateTime now = ZonedDateTime.now();
        Num one = series.numOf(1);
        Num two = series.numOf(2);
        Num three = series.numOf(3);
        Num four = series.numOf(4);
        series.addBar(now, one, one, one, one, one);
        series.addBar(now.plusSeconds(1), two, two, two, two, two);
        series.addBar(now.plusSeconds(2), three, three, three, three, three);
        series.addBar(now.plusSeconds(3), four, four, four, four, four);

        Rule entryRule = new FixedRule(0, 2);
        Rule exitRule = new FixedRule(1, 3);
        List<Strategy> strategies = new LinkedList<>();
        strategies.add(new BaseStrategy("Cost model test strategy", entryRule, exitRule));

        Num orderFee = series.numOf(new BigDecimal("0.0026"));
        BacktestExecutor executor = new BacktestExecutor(series, new LinearTransactionCostModel(orderFee.doubleValue()),
                new ZeroCostModel());

        Num amount = series.numOf(25);
        TradingStatement strategyResult = executor.execute(strategies, amount).get(0);

        Num firstPositionBuy = one.plus(one.multipliedBy(orderFee));
        Num firstPositionSell = two.minus(two.multipliedBy(orderFee));
        Num firstPositionProfit = firstPositionSell.minus(firstPositionBuy).multipliedBy(amount);

        Num secondPositionBuy = three.plus(three.multipliedBy(orderFee));
        Num secondPositionSell = four.minus(four.multipliedBy(orderFee));
        Num secondPositionProfit = secondPositionSell.minus(secondPositionBuy).multipliedBy(amount);

        Num overallProfit = firstPositionProfit.plus(secondPositionProfit);

        assertEquals(overallProfit, strategyResult.getPerformanceReport().getTotalProfit());
    }
}