/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class StopLimitExecutionModelTest extends AbstractIndicatorTest<BarSeries, Num> {

    public StopLimitExecutionModelTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void rejectsUntriggeredOrderWhenItExpires() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(102d).lowPrice(98d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(103d).lowPrice(97d).closePrice(101d).volume(10d).add();
        series.barBuilder().openPrice(101d).highPrice(104d).lowPrice(98d).closePrice(100d).volume(10d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.05), numOf(0.06), numOf(0.5), 2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy);

        assertTrue(tradingRecord.getTrades().isEmpty());
        assertFalse(model.getPendingOrder(tradingRecord).isPresent());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("expired"));
        assertEquals(series.numFactory().zero(), rejection.filledAmount());
    }

    @Test
    public void aggregatesPartialFillsAcrossBarsUntilRequestedAmountIsReached() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(6d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(8d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(6d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                4);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.numOf(10));

        assertEquals(1, tradingRecord.getTrades().size());
        Trade entry = tradingRecord.getTrades().getFirst();
        assertEquals(numFactory.numOf(10), entry.getAmount());
        assertEquals(3, entry.getFills().size());
        assertEquals(numFactory.numOf(3), entry.getFills().get(0).amount());
        assertEquals(numFactory.numOf(4), entry.getFills().get(1).amount());
        assertEquals(numFactory.numOf(3), entry.getFills().get(2).amount());
        assertFalse(model.getPendingOrder(tradingRecord).isPresent());
        assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());
    }

    @Test
    public void recordsFilledPortionAndRejectsRemainingAmountOnExpiry() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(4d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.numOf(5));

        assertEquals(1, tradingRecord.getTrades().size());
        Trade trade = tradingRecord.getTrades().getFirst();
        assertEquals(numFactory.numOf(3), trade.getAmount());
        assertEquals(2, trade.getFills().size());
        TradeFill firstFill = trade.getFills().get(0);
        TradeFill secondFill = trade.getFills().get(1);
        assertEquals(numFactory.numOf(2), firstFill.amount());
        assertEquals(numFactory.one(), secondFill.amount());

        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.numOf(5), rejection.requestedAmount());
        assertEquals(numFactory.numOf(3), rejection.filledAmount());
    }

    @Test
    public void rejectsInvalidRequestedAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 101d).build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                2);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.zero());

        assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("Invalid requested amount"));
        assertEquals(numFactory.zero(), rejection.filledAmount());
    }

    @Test
    public void rejectsSignalWhenAnotherOrderIsAlreadyPending() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 101d, 102d).build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                3);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.two());
        model.execute(0, tradingRecord, series, numFactory.two());

        assertTrue(model.getPendingOrder(tradingRecord).isPresent());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("another stop-limit order is pending"));
    }

    @Test
    public void rejectsSignalWhenNextOpenReferenceCannotBeResolved() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d).build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.one());

        assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("Unable to resolve reference bar"));
    }

    @Test
    public void zeroVolumeBarsDoNotFillPendingOrders() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(0d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(0d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.one());

        assertTrue(tradingRecord.getTrades().isEmpty());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        assertEquals(numFactory.zero(), model.getRejectedOrders(tradingRecord).getFirst().filledAmount());
    }

    @Test
    public void exposesCurrentCloseReferenceInPendingOrder() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2, StopLimitExecutionModel.ReferencePrice.CURRENT_CLOSE);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.one());
        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();

        assertEquals(0, pendingOrder.activationIndex());
        assertEquals(series.getBar(0).getClosePrice(), pendingOrder.stopPrice());
        assertEquals(series.getBar(0).getClosePrice(), pendingOrder.limitPrice());
    }

    @Test
    public void exitOrderUsesCurrentOpenPositionAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();

        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2, StopLimitExecutionModel.ReferencePrice.CURRENT_CLOSE);
        model.execute(1, tradingRecord, series, numFactory.one());

        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();
        assertEquals(numFactory.numOf(5), pendingOrder.requestedAmount());
    }

    @Test
    public void partialExitOrderExpiryDoesNotMutateTradingRecord() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(20d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5), 1,
                StopLimitExecutionModel.ReferencePrice.CURRENT_CLOSE);

        model.execute(1, tradingRecord, series, numFactory.one());
        model.onBar(1, tradingRecord, series);

        assertEquals(1, tradingRecord.getTrades().size());
        assertTrue(tradingRecord.getCurrentPosition().isOpened());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.one(), rejection.filledAmount());
    }

    @Test
    public void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.minusOne(), numFactory.one(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.one(), numFactory.zero(), numFactory.one(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numFactory.zero(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numFactory.one(), 0));
    }
}
