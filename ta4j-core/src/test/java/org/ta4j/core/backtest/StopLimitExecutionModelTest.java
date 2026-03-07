/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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

        assertEquals(3, tradingRecord.getTrades().size());
        Trade firstEntryFillTrade = tradingRecord.getTrades().get(0);
        Trade secondEntryFillTrade = tradingRecord.getTrades().get(1);
        Trade thirdEntryFillTrade = tradingRecord.getTrades().get(2);
        assertEquals(numFactory.numOf(3), firstEntryFillTrade.getAmount());
        assertEquals(numFactory.numOf(4), secondEntryFillTrade.getAmount());
        assertEquals(numFactory.numOf(3), thirdEntryFillTrade.getAmount());
        assertEquals(1, firstEntryFillTrade.getFills().size());
        assertEquals(1, secondEntryFillTrade.getFills().size());
        assertEquals(1, thirdEntryFillTrade.getFills().size());
        assertEquals(ExecutionSide.BUY, firstEntryFillTrade.getFills().getFirst().side());
        assertEquals(ExecutionSide.BUY, secondEntryFillTrade.getFills().getFirst().side());
        assertEquals(ExecutionSide.BUY, thirdEntryFillTrade.getFills().getFirst().side());
        assertEquals(series.getBar(1).getEndTime(), firstEntryFillTrade.getFills().getFirst().time());
        assertEquals(series.getBar(2).getEndTime(), secondEntryFillTrade.getFills().getFirst().time());
        assertEquals(series.getBar(3).getEndTime(), thirdEntryFillTrade.getFills().getFirst().time());
        assertFalse(model.getPendingOrder(tradingRecord).isPresent());
        assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());
    }

    @Test
    public void singleFillStopLimitOrderUsesScalarTradeRepresentation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.one());

        Trade entry = tradingRecord.getTrades().getFirst();
        assertTrue(entry instanceof BaseTrade);
        assertEquals(1, entry.getFills().size());
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

        assertEquals(2, tradingRecord.getTrades().size());
        Trade firstEntryFillTrade = tradingRecord.getTrades().get(0);
        Trade secondEntryFillTrade = tradingRecord.getTrades().get(1);
        assertEquals(numFactory.numOf(2), firstEntryFillTrade.getAmount());
        assertEquals(numFactory.one(), secondEntryFillTrade.getAmount());
        assertEquals(1, firstEntryFillTrade.getFills().size());
        assertEquals(1, secondEntryFillTrade.getFills().size());
        TradeFill firstFill = firstEntryFillTrade.getFills().getFirst();
        TradeFill secondFill = secondEntryFillTrade.getFills().getFirst();
        assertEquals(numFactory.numOf(2), firstFill.amount());
        assertEquals(numFactory.one(), secondFill.amount());
        assertEquals(ExecutionSide.BUY, firstFill.side());
        assertEquals(ExecutionSide.BUY, secondFill.side());
        assertEquals(series.getBar(1).getEndTime(), firstFill.time());
        assertEquals(series.getBar(2).getEndTime(), secondFill.time());

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
                numFactory.one(), 2, TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.one());
        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();

        assertEquals(1, pendingOrder.activationIndex());
        assertEquals(series.getBar(0).getClosePrice(), pendingOrder.stopPrice());
        assertEquals(series.getBar(0).getClosePrice(), pendingOrder.limitPrice());
    }

    @Test
    public void currentCloseOrdersDoNotFillOnSignalBar() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(120d).highPrice(120d).lowPrice(120d).closePrice(120d).volume(10d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 1, TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());

        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy);

        assertTrue(tradingRecord.getTrades().isEmpty());
        assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
    }

    @Test
    public void runEndExpiresPendingEntryOrderAndCommitsFilledPortion() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(100d).lowPrice(100d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(100d).lowPrice(100d).closePrice(100d).volume(2d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                3);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());

        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.numOf(3));

        assertEquals(1, tradingRecord.getTrades().size());
        assertEquals(numFactory.one(), tradingRecord.getTrades().getFirst().getAmount());
        assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        assertEquals(numFactory.one(), model.getRejectedOrders(tradingRecord).getFirst().filledAmount());
        assertEquals(numFactory.numOf(3), model.getRejectedOrders(tradingRecord).getFirst().requestedAmount());
    }

    @Test
    public void exitOrderUsesCurrentOpenPositionAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();

        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2, TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        model.execute(0, tradingRecord, series, numFactory.one());

        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();
        assertEquals(numFactory.numOf(5), pendingOrder.requestedAmount());
        assertEquals(1, pendingOrder.activationIndex());
    }

    @Test
    public void partialExitOrderExpiryCommitsFilledPortionWhenRecordIsExposedAsTradingRecord() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(20d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5), 1,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        model.execute(0, tradingRecord, series, numFactory.one());
        model.onBar(1, tradingRecord, series);

        assertEquals(3, tradingRecord.getTrades().size());
        assertTrue(tradingRecord.getCurrentPosition().isOpened());
        assertEquals(numFactory.numOf(4), tradingRecord.getCurrentPosition().getEntry().getAmount());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.one(), rejection.filledAmount());
    }

    @Test
    public void partialExitOrderExpiryDoesNotCommitForLegacyRecordWithoutLotExposure() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(20d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        LegacyTradingRecordWithoutLotExposure tradingRecord = new LegacyTradingRecordWithoutLotExposure(numFactory);
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5), 1,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        model.execute(0, tradingRecord, series, numFactory.one());
        model.onBar(1, tradingRecord, series);

        assertEquals(0, tradingRecord.recordedOperations().size());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.one(), rejection.filledAmount());
    }

    @Test
    public void partialExitOrderExpiryCommitsFilledPortionForBaseTradingRecord() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(20d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5), 1,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        model.execute(0, tradingRecord, series, numFactory.one());
        model.onBar(1, tradingRecord, series);

        assertEquals(3, tradingRecord.getTrades().size());
        assertTrue(tradingRecord.getCurrentPosition().isOpened());
        assertEquals(numFactory.numOf(4), tradingRecord.getCurrentPosition().getEntry().getAmount());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.one(), rejection.filledAmount());
    }

    @Test
    public void stalePendingOrderExpiresWhenNextSignalArrives() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 101d, 102d, 103d)
                .build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.5), numOf(0.6), numFactory.one(), 1);
        TradingRecord tradingRecord = new BaseTradingRecord();

        model.execute(0, tradingRecord, series, numFactory.one());
        assertTrue(model.getPendingOrder(tradingRecord).isPresent());

        model.execute(2, tradingRecord, series, numFactory.one());

        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();
        assertEquals(2, pendingOrder.signalIndex());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("expired"));
    }

    @Test
    public void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.minusOne(), numFactory.one(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.one(), numFactory.zero(), numFactory.one(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.one(), numFactory.one(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numFactory.zero(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numFactory.one(), 0));
    }

    @Test
    public void sellOrdersTagFillsWithSellExecutionSide() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();

        TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 1);

        model.execute(0, tradingRecord, series, numFactory.one());
        model.onBar(1, tradingRecord, series);

        assertEquals(1, tradingRecord.getTrades().size());
        assertEquals(ExecutionSide.SELL, tradingRecord.getTrades().getFirst().getFills().getFirst().side());
    }

    private static final class LegacyTradingRecordWithoutLotExposure implements TradingRecord {

        private final List<Trade> recordedOperations = new ArrayList<>();
        private final Position openPosition;
        private final NumFactory numFactory;

        private LegacyTradingRecordWithoutLotExposure(NumFactory numFactory) {
            this.numFactory = numFactory;
            Position position = new Position(Trade.TradeType.BUY);
            position.operate(0, numFactory.hundred(), numFactory.numOf(5));
            this.openPosition = position;
        }

        @Override
        public Trade.TradeType getStartingType() {
            return Trade.TradeType.BUY;
        }

        @Override
        public String getName() {
            return "legacy-record";
        }

        @Override
        public void operate(int index, Num price, Num amount) {
            recordedOperations.add(Trade.sellAt(index, price, amount));
        }

        @Override
        public CostModel getTransactionCostModel() {
            return new ZeroCostModel();
        }

        @Override
        public CostModel getHoldingCostModel() {
            return new ZeroCostModel();
        }

        @Override
        public List<Position> getPositions() {
            return List.of();
        }

        @Override
        public Position getCurrentPosition() {
            return openPosition;
        }

        @Override
        public List<Trade> getTrades() {
            return List.copyOf(recordedOperations);
        }

        @Override
        public Integer getStartIndex() {
            return 0;
        }

        @Override
        public Integer getEndIndex() {
            return 1;
        }

        @Override
        public boolean enter(int index, Num price, Num amount) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public boolean exit(int index, Num price, Num amount) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        private List<Trade> recordedOperations() {
            return recordedOperations;
        }
    }
}
