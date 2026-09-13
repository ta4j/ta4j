/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.reports.TradingStatement;
import org.ta4j.core.rules.FixedRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

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
    public void calculateFuturesPositionCostThroughCurrentIndex() {
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(DoubleNum.valueOf(0.01))
                .build();
        TradeFill entryFill = TradeFill.builder()
                .index(0)
                .time(Instant.EPOCH)
                .price(DoubleNum.valueOf(100))
                .amount(DoubleNum.valueOf(1))
                .side(ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        TradeFill exitFill = entryFill.toBuilder()
                .index(5)
                .time(Instant.EPOCH.plusSeconds(5))
                .price(DoubleNum.valueOf(200))
                .side(ExecutionSide.SELL)
                .build();
        Trade entry = Trade.fromFill(entryFill, transactionModel);
        Trade exit = Trade.fromFill(exitFill, transactionModel);
        Position position = new Position(entry, exit, transactionModel, new ZeroCostModel());

        assertNumEquals(entry.getCost(), transactionModel.calculate(position, 2));
        assertNumEquals(entry.getCost().plus(exit.getCost()), transactionModel.calculate(position));
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
        BaseBarSeries series = new BaseBarSeriesBuilder().withName("CostModel test")
                .withBarBuilderFactory(new MockBarBuilderFactory())
                .build();
        Instant now = Instant.now();
        Num one = series.numFactory().one();
        Num two = series.numFactory().numOf(2);
        Num three = series.numFactory().numOf(3);
        Num four = series.numFactory().numOf(4);
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

        Rule entryRule = new FixedRule(0, 2);
        Rule exitRule = new FixedRule(1, 3);
        List<Strategy> strategies = new LinkedList<>();
        strategies.add(new BaseStrategy("Cost model test strategy", entryRule, exitRule));

        Num orderFee = series.numFactory().numOf(new BigDecimal("0.0026"));
        BacktestExecutor executor = new BacktestExecutor(series, new LinearTransactionCostModel(orderFee.doubleValue()),
                new ZeroCostModel(), new TradeOnCurrentCloseModel());

        Num amount = series.numFactory().numOf(25);
        TradingStatement strategyResult = executor.execute(strategies, amount).get(0);

        Num firstPositionBuy = one.plus(one.multipliedBy(orderFee));
        Num firstPositionSell = two.minus(two.multipliedBy(orderFee));
        Num firstPositionProfit = firstPositionSell.minus(firstPositionBuy).multipliedBy(amount);

        Num secondPositionBuy = three.plus(three.multipliedBy(orderFee));
        Num secondPositionSell = four.minus(four.multipliedBy(orderFee));
        Num secondPositionProfit = secondPositionSell.minus(secondPositionBuy).multipliedBy(amount);

        Num overallProfit = firstPositionProfit.plus(secondPositionProfit);

        assertEquals(overallProfit, strategyResult.getPerformanceReport().getPerformanceMetric());
    }
}
