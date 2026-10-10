/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.Bar;
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
        model.execute(0, tradingRecord, series, numFactory.numOf(3));

        assertTrue(model.getPendingOrder(tradingRecord).isPresent());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("another stop-limit order is pending"));
        assertEquals(numFactory.numOf(3), rejection.requestedAmount());
        assertEquals(numFactory.zero(), rejection.filledAmount());
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
    public void currentCloseSignalOnTerminalBarHasNoActivationBar() {
        // The bar after Integer.MAX_VALUE cannot exist: stop-limit activation
        // must not wrap to a negative index, which would hand a sizing
        // context a negative entry index and make sizers that read the entry
        // bar throw.
        Bar bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(bar))
                .withBeginIndex(Integer.MAX_VALUE)
                .build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.05), numOf(0.06), numOf(0.5), 2,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        assertNull(model.estimateEntryTarget(Integer.MAX_VALUE, series, Trade.TradeType.BUY));

        TradingRecord tradingRecord = new BaseTradingRecord();
        model.execute(Integer.MAX_VALUE, tradingRecord, series, numFactory.one());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        assertTrue(model.getRejectedOrders(tradingRecord).getFirst().reason().contains("activation bar"));
    }

    @Test
    public void stopLimitExpiryDoesNotWrapNearMaxValue() {
        // When activation happens near Integer.MAX_VALUE, the fillable-bar
        // window must not wrap the expiry index negative: the order stays
        // pending past its activation bar and fills on the terminal bar.
        Bar first = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        Bar second = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-02T00:00:00Z"))
                .openPrice(100)
                .highPrice(100)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        Bar third = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-03T00:00:00Z"))
                .openPrice(100)
                .highPrice(150)
                .lowPrice(90)
                .closePrice(120)
                .volume(10)
                .build();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(first, second, third))
                .withBeginIndex(Integer.MAX_VALUE - 2)
                .build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.05), numOf(0.06), numOf(0.5), 3,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        TradingRecord tradingRecord = new BaseTradingRecord();
        model.execute(Integer.MAX_VALUE - 2, tradingRecord, series, numFactory.one());
        assertTrue(model.getPendingOrder(tradingRecord).isPresent());

        // Activation bar: the stop is not triggered, so the order must
        // survive instead of expiring through a wrapped expiry index.
        model.onBar(Integer.MAX_VALUE - 1, tradingRecord, series);
        assertTrue(model.getPendingOrder(tradingRecord).isPresent());
        assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());

        // Terminal bar: trigger and limit are both reached; the order fills.
        model.onBar(Integer.MAX_VALUE, tradingRecord, series);
        assertTrue(model.getPendingOrder(tradingRecord).isEmpty());
        assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());
        assertEquals(1, tradingRecord.getTrades().size());
    }

    @Test
    public void stopLimitTtlIsNotClampedToConstrainedSeriesEnd() {
        // Expiry follows the configured time-to-live, clamped only at
        // Integer.MAX_VALUE: clamping to a constrained series end instead
        // would expire orders before tail bars beyond that end could fill.
        Bar first = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        Bar second = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-02T00:00:00Z"))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        Bar third = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-03T00:00:00Z"))
                .openPrice(100)
                .highPrice(101)
                .lowPrice(99)
                .closePrice(100)
                .volume(10)
                .build();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(List.of(first, second, third))
                .build();
        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.05), numOf(0.06), numOf(0.5), 5,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        TradingRecord tradingRecord = new BaseTradingRecord();
        model.execute(0, tradingRecord, series, numFactory.one());

        assertEquals(5, model.getPendingOrder(tradingRecord).orElseThrow().expiryIndex());
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
    public void exitOrderUsesRemainingOpenExposureAfterPartialExit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(10d).add();

        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.operate(0, numFactory.hundred(), numFactory.numOf(5));
        tradingRecord.operate(1, numFactory.numOf(101), numFactory.one());

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(),
                numFactory.one(), 2, TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        model.execute(1, tradingRecord, series, numFactory.one());

        StopLimitExecutionModel.PendingOrderSnapshot pendingOrder = model.getPendingOrder(tradingRecord).orElseThrow();
        assertEquals(numFactory.numOf(4), pendingOrder.requestedAmount());
        assertEquals(2, pendingOrder.activationIndex());
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

    @Test
    public void concurrentExecutionsOnSharedModelKeepAllOrders() throws Exception {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(4d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(4d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(4d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                2);
        int threadCount = 8;
        int recordsPerThread = 12;
        int trials = 3;

        for (int trial = 0; trial < trials; trial++) {
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<List<RecordOutcome>>> futures = new ArrayList<>(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    futures.add(pool.submit(() -> {
                        startGate.await();
                        return driveRecordsOnSharedModel(model, series, recordsPerThread);
                    }));
                }
                startGate.countDown();
                for (Future<List<RecordOutcome>> future : futures) {
                    for (RecordOutcome outcome : future.get(30, TimeUnit.SECONDS)) {
                        // Expiry commits the filled portion as one trade per fill
                        assertEquals(2, outcome.tradeCount());
                        assertEquals(numFactory.numOf(4), outcome.totalTradeAmount());
                        assertEquals(2, outcome.rejectionCount());
                        assertFalse(outcome.pendingOrderPresent());
                    }
                }
            } finally {
                pool.shutdown();
            }
        }
    }

    private List<RecordOutcome> driveRecordsOnSharedModel(StopLimitExecutionModel model, BarSeries series,
            int recordsPerThread) {
        List<TradingRecord> records = new ArrayList<>(recordsPerThread);
        for (int r = 0; r < recordsPerThread; r++) {
            TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY);
            records.add(record);
            model.execute(0, record, series, numFactory.numOf(10));
        }
        List<RecordOutcome> outcomes = new ArrayList<>(recordsPerThread);
        for (TradingRecord record : records) {
            // Second signal while a pending order exists -> rejection
            model.execute(0, record, series, numFactory.numOf(10));
            // First partial fill on the activation bar (no expiry yet)
            model.onBar(0, record, series);
            // Second partial fill, then expiry (maxBarsToFill=2) commits the filled
            // portion as one trade per fill
            model.onBar(1, record, series);
            // No pending order left, so this must be a no-op
            model.onBar(2, record, series);
            // Still no pending order, so this must be a no-op
            model.onRunEnd(2, record);

            List<Trade> trades = record.getTrades();
            Num totalTradeAmount = null;
            for (Trade trade : trades) {
                totalTradeAmount = totalTradeAmount == null ? trade.getAmount()
                        : totalTradeAmount.plus(trade.getAmount());
            }
            outcomes.add(new RecordOutcome(trades.size(), totalTradeAmount, model.getRejectedOrders(record).size(),
                    model.getPendingOrder(record).isPresent()));
        }
        return outcomes;
    }

    /**
     * Per-record outcome of one concurrent drive: the expiry commits the filled
     * portion as one trade per fill (2 x 2), two rejections (ignored-while-pending,
     * expired-partial), and no leftover pending order.
     */
    private record RecordOutcome(int tradeCount, Num totalTradeAmount, int rejectionCount,
            boolean pendingOrderPresent) {
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
