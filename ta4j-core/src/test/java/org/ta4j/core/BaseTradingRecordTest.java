/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.SqnCriterion;
import org.ta4j.core.criteria.helpers.VarianceCriterion;
import org.ta4j.core.criteria.pnl.GrossLossCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossRatioCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseTradingRecordTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void recordsPartialFillsUsingFifo() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.recordFill(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one()));
        record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.two(), closed.getEntry().getAmount());
        assertEquals(numFactory.numOf(120), closed.getExit().getPricePerAsset());

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.one(), net.amount());
        assertEquals(numFactory.numOf(110), net.averageEntryPrice());
    }

    @Test
    void recordsShortEntriesAndExits() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.SELL, numFactory.hundred(), numFactory.two()));

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(ExecutionSide.SELL, net.side());
        assertEquals(numFactory.two(), net.amount());
        assertEquals(numFactory.hundred(), net.averageEntryPrice());

        record.recordFill(fill(ExecutionSide.BUY, numFactory.numOf(90), numFactory.two()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(TradeType.SELL, closed.getEntry().getType());
        assertEquals(TradeType.BUY, closed.getExit().getType());
        assertEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.numOf(90), closed.getExit().getPricePerAsset());
        assertTrue(record.getOpenPositions().isEmpty());
    }

    @Test
    void enterExitUsesShortStartingType() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        assertTrue(record.enter(0, numFactory.hundred(), numFactory.one()));
        Trade entry = record.getLastEntry();
        assertNotNull(entry);
        assertEquals(TradeType.SELL, entry.getType());

        assertTrue(record.exit(1, numFactory.numOf(90), numFactory.one()));
        Trade exit = record.getLastExit();
        assertNotNull(exit);
        assertEquals(TradeType.BUY, exit.getType());
    }

    @Test
    void operateWithAggregatedTradeReplaysAllFills() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        Trade aggregatedEntry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(4, numFactory.hundred(), numFactory.one()),
                        new TradeFill(5, numFactory.numOf(101), numFactory.two())));

        record.operate(aggregatedEntry);

        assertEquals(2, record.getTrades().size());
        assertEquals(4, record.getTrades().get(0).getIndex());
        assertEquals(5, record.getTrades().get(1).getIndex());
        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.three(), net.amount());
        assertEquals(numFactory.numOf(302).dividedBy(numFactory.three()), net.averageEntryPrice());
    }

    @Test
    void operatePreservesFillLevelMetadataWhenPresent() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        Instant firstFillTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant secondFillTime = Instant.parse("2025-01-01T00:01:00Z");
        Trade aggregatedEntry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(4, firstFillTime, numFactory.hundred(), numFactory.one(), numFactory.numOf(0.1),
                        ExecutionSide.BUY, "order-1", "corr-1"),
                        new TradeFill(5, secondFillTime, numFactory.numOf(101), numFactory.two(), numFactory.numOf(0.2),
                                ExecutionSide.BUY, "order-2", "corr-2")));

        record.operate(aggregatedEntry);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(firstFillTime, trades.get(0).getTime());
        assertEquals(secondFillTime, trades.get(1).getTime());
        assertEquals("order-1", trades.get(0).getOrderId());
        assertEquals("order-2", trades.get(1).getOrderId());
        assertEquals("corr-1", trades.get(0).getCorrelationId());
        assertEquals("corr-2", trades.get(1).getCorrelationId());
        assertEquals(numFactory.numOf(0.3), record.getRecordedTotalFees());
    }

    @Test
    void operateFallsBackToTradeMetadataWhenFillMetadataMissing() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        Instant tradeTime = Instant.parse("2025-01-01T00:05:00Z");
        List<TradeFill> fills = List.of(
                new TradeFill(4, null, numFactory.hundred(), numFactory.one(), numFactory.numOf(0.1), null, null, null),
                new TradeFill(5, null, numFactory.numOf(101), numFactory.two(), numFactory.numOf(0.2), null, null,
                        null));
        Trade aggregatedEntry = tradeViewWithFills(TradeType.BUY, tradeTime, "trade-order", "trade-correlation", fills);

        record.operate(aggregatedEntry);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(tradeTime, trades.get(0).getTime());
        assertEquals(tradeTime, trades.get(1).getTime());
        assertEquals("trade-order", trades.get(0).getOrderId());
        assertEquals("trade-order", trades.get(1).getOrderId());
        assertEquals("trade-correlation", trades.get(0).getCorrelationId());
        assertEquals("trade-correlation", trades.get(1).getCorrelationId());
        assertEquals(TradeType.BUY, trades.get(0).getType());
        assertEquals(TradeType.BUY, trades.get(1).getType());
    }

    @Test
    void operateDefaultsMissingMetadataToEpochAndNullIds() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        List<TradeFill> fills = List.of(new TradeFill(4, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(101), numFactory.two()));
        Trade aggregatedEntry = tradeViewWithFills(TradeType.BUY, null, null, null, fills);

        record.operate(aggregatedEntry);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(Instant.EPOCH, trades.get(0).getTime());
        assertEquals(Instant.EPOCH, trades.get(1).getTime());
        assertNull(trades.get(0).getOrderId());
        assertNull(trades.get(1).getOrderId());
        assertNull(trades.get(0).getCorrelationId());
        assertNull(trades.get(1).getCorrelationId());
    }

    @Test
    void operateWithOppositeTypeOpensPositionWhenNoLotsOpen() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        Trade exitTradeWithoutEntry = Trade.fromFills(TradeType.SELL,
                List.of(new TradeFill(1, numFactory.hundred(), numFactory.one())));

        record.operate(exitTradeWithoutEntry);

        assertFalse(record.isClosed());
        assertEquals(TradeType.SELL, record.getLastTrade().getType());
        assertEquals(ExecutionSide.SELL, record.getNetOpenPosition().side());
    }

    @Test
    void shortCriteriaMatchBaseTradingRecord() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 100, 110).build();
        TradingRecord baseRecord = buildBaseShortRecord(series);
        TradingRecord liveRecord = buildLiveShortRecord(series);

        assertParity(new NetProfitCriterion(), series, baseRecord, liveRecord);
        assertParity(new GrossProfitCriterion(), series, baseRecord, liveRecord);
        assertParity(new GrossLossCriterion(), series, baseRecord, liveRecord);
        assertParity(new GrossProfitLossRatioCriterion(), series, baseRecord, liveRecord);
        assertParity(new NetProfitLossRatioCriterion(), series, baseRecord, liveRecord);
        assertParity(new NetReturnCriterion(), series, baseRecord, liveRecord);
        assertParity(new GrossReturnCriterion(), series, baseRecord, liveRecord);
        assertParity(new NumberOfPositionsCriterion(), series, baseRecord, liveRecord);
        assertParity(new NumberOfWinningPositionsCriterion(), series, baseRecord, liveRecord);
        assertParity(new NumberOfLosingPositionsCriterion(), series, baseRecord, liveRecord);
        assertParity(new PositionsRatioCriterion(AnalysisCriterion.PositionFilter.PROFIT), series, baseRecord,
                liveRecord);
        assertParity(new ExpectancyCriterion(), series, baseRecord, liveRecord);
        assertParity(new SqnCriterion(), series, baseRecord, liveRecord);
        assertParity(new VarianceCriterion(new NetProfitCriterion()), series, baseRecord, liveRecord);
    }

    @Test
    void recordsPartialFillsUsingLifo() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.LIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.recordFill(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one()));
        record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.numOf(110), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.one(), closed.getEntry().getAmount());
    }

    @Test
    void recordsAvgCostForMergedEntries() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.AVG_COST,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.recordFill(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.two()));
        record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.numOf(105), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.one(), closed.getEntry().getAmount());

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.three(), net.amount());
        assertEquals(numFactory.numOf(105), net.averageEntryPrice());
        assertEquals(1, record.getOpenPositions().size());
    }

    @Test
    void recordsSpecificIdExitAgainstMatchingLot() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));
        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one(), "order-2", "corr-2"));
        record.recordFill(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one(), null, "corr-2"));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.numOf(110), closed.getEntry().getPricePerAsset());
    }

    @Test
    void rejectsSpecificIdExitWithoutIdentifier() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class,
                () -> record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one())));
    }

    @Test
    void rejectsSpecificIdExitWithoutMatchingLot() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class, () -> record
                .recordFill(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one(), null, "corr-2")));
    }

    @Test
    void rejectsSpecificIdExitExceedingLotAmount() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class, () -> record
                .recordFill(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two(), null, "corr-1")));
    }

    @Test
    void splitsLotWhenExitIsPartial() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.three()));
        record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.one(), closed.getEntry().getAmount());
        assertEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.two(), net.amount());
        assertEquals(numFactory.hundred(), net.averageEntryPrice());
    }

    @Test
    void rejectsExitExceedingOpenLots() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));

        assertThrows(IllegalStateException.class,
                () -> record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two())));
    }

    @Test
    void snapshotCollectionsAreImmutable() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        var openPositions = record.getOpenPositions();
        assertThrows(UnsupportedOperationException.class, () -> openPositions.add(null));
        assertThrows(UnsupportedOperationException.class, () -> openPositions.getFirst().lots().add(null));
    }

    @Test
    void openPositionsExposeSnapshotLots() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));

        OpenPosition first = record.getOpenPositions().getFirst();
        OpenPosition second = record.getOpenPositions().getFirst();

        assertTrue(first.lots().getFirst() != second.lots().getFirst());
    }

    @Test
    void ordersTradesByIndexThenFillSequence() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:02Z"), numFactory.numOf(120),
                numFactory.two(), numFactory.zero(), ExecutionSide.SELL, null, null));

        List<Trade> trades = record.getTrades();
        assertEquals(4, trades.size());
        assertEquals(TradeType.BUY, trades.get(0).getType());
        assertEquals(TradeType.BUY, trades.get(1).getType());
        assertEquals(TradeType.SELL, trades.get(2).getType());
        assertEquals(TradeType.SELL, trades.get(3).getType());
    }

    @Test
    void aggregatesFeesInOpenPosition() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.numOf(0.3), net.totalFees());
    }

    @Test
    void currentPositionViewPreservesRecordedEntryFees() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new BaseTrade(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Position currentPosition = record.getCurrentPosition();

        assertTrue(currentPosition.isOpened());
        assertEquals(numFactory.numOf(0.3), currentPosition.getEntry().getCost());
    }

    @Test
    void currentPositionViewPreservesModeledEntryFees() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        CostModel transactionCost = new FixedTransactionCostModel(1d);
        Trade aggregatedEntry = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(0, numFactory.hundred(), numFactory.one()),
                        new TradeFill(1, numFactory.numOf(110), numFactory.one())),
                transactionCost);

        record.operate(aggregatedEntry);

        Position currentPosition = record.getCurrentPosition();
        assertTrue(currentPosition.isOpened());
        assertEquals(numFactory.one(), currentPosition.getEntry().getCost());
        assertEquals(numFactory.numOf(105.5), currentPosition.getEntry().getNetPrice());
        assertEquals(numFactory.one(), record.getRecordedTotalFees());
    }

    @Test
    void aggregatesTotalFeesAcrossFills() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        assertEquals(numFactory.numOf(0.3), record.getTotalFees());
    }

    @Test
    void rejectsInvalidFillAmounts() {
        BaseTradingRecord record = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.zero())));
    }

    @Test
    void rejectsNegativeFillAmounts() {
        BaseTradingRecord record = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.minusOne())));
    }

    @Test
    void defaultEnterAndOperateUseUnitAmountWhenAmountIsNaN() {
        BaseTradingRecord record = new BaseTradingRecord();

        assertTrue(record.enter(0));
        assertFalse(record.isClosed());
        assertEquals(numFactory.one(), record.getCurrentPosition().getEntry().getAmount());

        record.operate(1);
        assertTrue(record.isClosed());
        assertEquals(1, record.getPositionCount());
    }

    @Test
    void firstFillCanOpenShortWhenStartingTypeIsBuy() {
        BaseTradingRecord record = new BaseTradingRecord();
        record.recordFill(fill(ExecutionSide.SELL, numFactory.hundred(), numFactory.one()));
        assertEquals(TradeType.SELL, record.getLastTrade().getType());
        assertFalse(record.isClosed());
    }

    @Test
    void keepsNaNPriceWhenRecordingLegacyFill() {
        BaseTradingRecord record = new BaseTradingRecord();

        record.recordFill(fill(ExecutionSide.BUY, NaN.NaN, numFactory.one()));

        assertEquals(1, record.getTrades().size());
        assertTrue(record.getLastTrade().getPricePerAsset().isNaN());
    }

    @Test
    void normalizesNaNFeeToZero() {
        BaseTradingRecord record = new BaseTradingRecord();

        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), NaN.NaN, ExecutionSide.BUY, null, null));

        assertEquals(numFactory.zero(), record.getRecordedTotalFees());
        assertEquals(numFactory.zero(), record.getLastTrade().getCost());
    }

    @Test
    void recordsTradeInterfaceFillsAndAutoIndexes() {
        BaseTradingRecord record = new BaseTradingRecord();
        Trade entry = tradeView(42, TradeType.BUY, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), null, "order-1", "corr-1");
        Trade exit = tradeView(99, TradeType.SELL, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(120),
                numFactory.one(), null, "order-1", "corr-1");

        record.recordFill(entry);
        record.recordFill(exit);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(0, trades.get(0).getIndex());
        assertEquals(1, trades.get(1).getIndex());
        assertEquals(numFactory.zero(), record.getRecordedTotalFees());
        assertEquals(1, record.getPositions().size());
    }

    @Test
    void operateTradeRejectsExitAmountGreaterThanOpenPosition() {
        BaseTradingRecord record = new BaseTradingRecord();
        record.operate(0, numFactory.hundred(), numFactory.one());

        Trade oversizedExit = Trade.fromFills(TradeType.SELL,
                List.of(new TradeFill(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                        numFactory.zero(), ExecutionSide.SELL, null, null),
                        new TradeFill(2, Instant.parse("2025-01-01T00:00:02Z"), numFactory.numOf(111), numFactory.one(),
                                numFactory.zero(), ExecutionSide.SELL, null, null)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> record.operate(oversizedExit));

        assertTrue(exception.getMessage().contains("Exit amount"));
        assertEquals(1, record.getTrades().size());
        assertTrue(record.getCurrentPosition().isOpened());
        assertEquals(numFactory.one(), record.getCurrentPosition().getEntry().getAmount());
        assertTrue(record.getPositions().isEmpty());
    }

    @Test
    void operateTradeAutoAssignsMissingFillIndices() {
        BaseTradingRecord record = new BaseTradingRecord();
        Trade fillBackedTrade = Trade.fromFills(TradeType.BUY,
                List.of(new TradeFill(-1, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                        numFactory.zero(), ExecutionSide.BUY, "order-1", "corr-1")));

        record.operate(fillBackedTrade);

        Trade recordedTrade = record.getLastTrade();
        assertEquals(0, recordedTrade.getIndex());
        assertEquals(0, recordedTrade.getFills().getFirst().index());
        assertEquals("order-1", recordedTrade.getOrderId());
        assertEquals("corr-1", recordedTrade.getCorrelationId());
    }

    @Test
    void cachesTradesAndInvalidatesOnUpdate() {
        BaseTradingRecord record = new BaseTradingRecord();
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        List<Trade> first = record.getTrades();
        List<Trade> second = record.getTrades();
        assertSame(first, second);

        record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));
        List<Trade> third = record.getTrades();
        assertNotSame(first, third);
        assertEquals(2, third.size());
    }

    @Test
    void initializesCoreSafelyUnderConcurrentAccess() throws Exception {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();

        executor.execute(() -> {
            try {
                startLatch.await(2, TimeUnit.SECONDS);
                assertNotNull(record.getOpenPositions());
            } catch (Throwable ex) {
                failed.set(true);
                error.set(ex);
            } finally {
                doneLatch.countDown();
            }
        });
        executor.execute(() -> {
            try {
                startLatch.countDown();
                assertNotNull(record.getTotalFees());
            } catch (Throwable ex) {
                failed.set(true);
                error.set(ex);
            } finally {
                doneLatch.countDown();
            }
        });

        try {
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }
        }
        if (failed.get()) {
            throw new AssertionError("Concurrent core initialization failed", error.get());
        }
    }

    @Test
    void supportsConcurrentReadsDuringWrites() throws Exception {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();

        executor.execute(() -> {
            try {
                startLatch.await(2, TimeUnit.SECONDS);
                for (int i = 0; i < 50; i++) {
                    record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
                    record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(110), numFactory.one()));
                }
            } catch (Throwable ex) {
                failed.set(true);
                error.set(ex);
            } finally {
                doneLatch.countDown();
            }
        });
        executor.execute(() -> {
            try {
                startLatch.countDown();
                for (int i = 0; i < 50; i++) {
                    assertNotNull(record.getOpenPositions());
                }
            } catch (Throwable ex) {
                failed.set(true);
                error.set(ex);
            } finally {
                doneLatch.countDown();
            }
        });

        try {
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }
        }
        if (failed.get()) {
            throw new AssertionError("Concurrent access failed", error.get());
        }
    }

    @Test
    void supportsRecordSerializationRoundTrip() throws Exception {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));

        byte[] data;
        try (var output = new ByteArrayOutputStream(); var objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(record);
            objectOutput.flush();
            data = output.toByteArray();
        }

        try (var input = new ByteArrayInputStream(data); var objectInput = new ObjectInputStream(input)) {
            BaseTradingRecord rehydrated = (BaseTradingRecord) objectInput.readObject();
            rehydrated.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));
            assertEquals(1, rehydrated.getPositions().size());
            assertNotNull(rehydrated.getPositions().getFirst().getTransactionCostModel());
            assertNotNull(rehydrated.getPositions().getFirst().getHoldingCostModel());
        }
    }

    @Test
    void tradeFillIndexIsAppliedConsistentlyWithLiveFill() {
        BaseTradingRecord liveFillRecord = new BaseTradingRecord();
        BaseTradingRecord genericFillRecord = new BaseTradingRecord();

        BaseTrade liveFill = new BaseTrade(42, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, "live-order", "live-correlation");
        TradeFill genericFill = fillContract(42, ExecutionSide.BUY, numFactory.hundred(), numFactory.one(),
                "generic-order", "generic-correlation");

        liveFillRecord.recordFill(42, liveFill);
        genericFillRecord.recordExecutionFill(genericFill);

        assertEquals(42, liveFillRecord.getLastTrade().getIndex());
        assertEquals(42, genericFillRecord.getLastTrade().getIndex());
    }

    @Test
    void tradeFillWithoutIndexUsesAutoIncrementedIndex() {
        BaseTradingRecord record = new BaseTradingRecord();

        record.recordExecutionFill(fillContract(-1, ExecutionSide.BUY, numFactory.hundred(), numFactory.one(),
                "order-1", "generic-correlation-1"));
        record.recordExecutionFill(fillContract(-1, ExecutionSide.BUY, numFactory.numOf(101), numFactory.one(),
                "order-2", "generic-correlation-2"));

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(0, trades.get(0).getIndex());
        assertEquals(1, trades.get(1).getIndex());
    }

    @Test
    void toStringSupportsDecimalNumValues() {
        var decimalFactory = DecimalNumFactory.getInstance();
        BaseTradingRecord record = new BaseTradingRecord();
        record.recordFill(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), decimalFactory.hundred(),
                decimalFactory.one(), decimalFactory.zero(), ExecutionSide.BUY, "order-1", "corr-1"));

        String recordJson = record.toString();
        String openPositionJson = record.getNetOpenPosition().toString();
        String lotJson = record.getOpenPositions().getFirst().lots().getFirst().toString();

        assertTrue(recordJson.contains("\"tradeCount\":1"));
        assertTrue(openPositionJson.contains("\"side\":\"BUY\""));
        assertTrue(lotJson.contains("\"entryIndex\":0"));
    }

    private BaseTrade fill(ExecutionSide side, Num price, Num amount) {
        return new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, null, null);
    }

    private BaseTrade fill(int index, ExecutionSide side, Num price) {
        return new BaseTrade(index, Instant.EPOCH, price, numFactory.one(), numFactory.zero(), side, null, null);
    }

    private BaseTrade fillWithIds(ExecutionSide side, Num price, Num amount, String orderId, String correlationId) {
        return new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, orderId,
                correlationId);
    }

    private TradeFill fillContract(int index, ExecutionSide side, Num price, Num amount, String orderId,
            String correlationId) {
        return new TradeFill(index, Instant.parse("2025-01-01T00:00:00Z"), price, amount, numFactory.zero(), side,
                orderId, correlationId);
    }

    private Trade tradeView(int index, TradeType type, Instant time, Num price, Num amount, Num cost, String orderId,
            String correlationId) {
        return new Trade() {
            @Override
            public TradeType getType() {
                return type;
            }

            @Override
            public int getIndex() {
                return index;
            }

            @Override
            public Num getPricePerAsset() {
                return price;
            }

            @Override
            public Num getNetPrice() {
                return price;
            }

            @Override
            public Num getAmount() {
                return amount;
            }

            @Override
            public Num getCost() {
                return cost;
            }

            @Override
            public CostModel getCostModel() {
                return new ZeroCostModel();
            }

            @Override
            public Instant getTime() {
                return time;
            }

            @Override
            public String getOrderId() {
                return orderId;
            }

            @Override
            public String getCorrelationId() {
                return correlationId;
            }
        };
    }

    private Trade tradeViewWithFills(TradeType type, Instant time, String orderId, String correlationId,
            List<TradeFill> fills) {
        Trade aggregatedTrade = Trade.fromFills(type, fills);
        return new Trade() {
            @Override
            public TradeType getType() {
                return type;
            }

            @Override
            public int getIndex() {
                return aggregatedTrade.getIndex();
            }

            @Override
            public Num getPricePerAsset() {
                return aggregatedTrade.getPricePerAsset();
            }

            @Override
            public Num getNetPrice() {
                return aggregatedTrade.getNetPrice();
            }

            @Override
            public Num getAmount() {
                return aggregatedTrade.getAmount();
            }

            @Override
            public Num getCost() {
                return aggregatedTrade.getCost();
            }

            @Override
            public CostModel getCostModel() {
                return aggregatedTrade.getCostModel();
            }

            @Override
            public Instant getTime() {
                return time;
            }

            @Override
            public String getOrderId() {
                return orderId;
            }

            @Override
            public String getCorrelationId() {
                return correlationId;
            }

            @Override
            public List<TradeFill> getFills() {
                return fills;
            }
        };
    }

    private TradingRecord buildBaseShortRecord(BarSeries series) {
        TradingRecord record = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(), new ZeroCostModel());
        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        record.exit(1, series.getBar(1).getClosePrice(), numFactory.one());
        record.enter(2, series.getBar(2).getClosePrice(), numFactory.one());
        record.exit(3, series.getBar(3).getClosePrice(), numFactory.one());
        return record;
    }

    private TradingRecord buildLiveShortRecord(BarSeries series) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(0, ExecutionSide.SELL, series.getBar(0).getClosePrice()));
        record.recordFill(fill(1, ExecutionSide.BUY, series.getBar(1).getClosePrice()));
        record.recordFill(fill(2, ExecutionSide.SELL, series.getBar(2).getClosePrice()));
        record.recordFill(fill(3, ExecutionSide.BUY, series.getBar(3).getClosePrice()));
        return record;
    }

    private void assertParity(AnalysisCriterion criterion, BarSeries series, TradingRecord baseRecord,
            TradingRecord liveRecord) {
        assertEquals(criterion.calculate(series, baseRecord), criterion.calculate(series, liveRecord),
                criterion.getClass().getSimpleName());
    }
}
