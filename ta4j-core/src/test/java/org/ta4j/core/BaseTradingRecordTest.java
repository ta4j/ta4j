/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import static org.ta4j.core.TestUtils.assertNumEquals;
import java.util.ArrayList;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.FuturesTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.SqnCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.helpers.VarianceCriterion;
import org.ta4j.core.criteria.pnl.GrossLossCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossRatioCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossPercentageCriterion;
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

        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.operate(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one()));
        record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.two(), closed.getEntry().getAmount());
        assertEquals(numFactory.numOf(120), closed.getExit().getPricePerAsset());

        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(numFactory.one(), net.amount());
        assertEquals(numFactory.numOf(110), net.averageEntryPrice());
    }

    @Test
    void recordsShortEntriesAndExits() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(fill(ExecutionSide.SELL, numFactory.hundred(), numFactory.two()));

        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(ExecutionSide.SELL, net.side());
        assertEquals(numFactory.two(), net.amount());
        assertEquals(numFactory.hundred(), net.averageEntryPrice());

        record.operate(fill(ExecutionSide.BUY, numFactory.numOf(90), numFactory.two()));

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
        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(numFactory.three(), net.amount());
        assertEquals(numFactory.numOf(302).dividedBy(numFactory.three()), net.averageEntryPrice());
    }

    @Test
    void operateWithDirectTradeFillsMatchesGroupedTradePath() {
        BaseTradingRecord directFillRecord = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        BaseTradingRecord groupedTradeRecord = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        List<TradeFill> entryFills = List.of(
                new TradeFill(4, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                        numFactory.numOf(0.1), ExecutionSide.BUY, "entry-1", "entry-order"),
                new TradeFill(5, Instant.parse("2025-01-01T00:01:00Z"), numFactory.numOf(101), numFactory.two(),
                        numFactory.numOf(0.2), ExecutionSide.BUY, "entry-2", "entry-order"));
        List<TradeFill> exitFills = List.of(
                new TradeFill(8, Instant.parse("2025-01-01T00:02:00Z"), numFactory.numOf(110), numFactory.one(),
                        numFactory.numOf(0.05), ExecutionSide.SELL, "exit-1", "exit-order"),
                new TradeFill(9, Instant.parse("2025-01-01T00:03:00Z"), numFactory.numOf(111), numFactory.two(),
                        numFactory.numOf(0.06), ExecutionSide.SELL, "exit-2", "exit-order"));

        for (TradeFill fill : entryFills) {
            directFillRecord.operate(fill);
        }
        for (TradeFill fill : exitFills) {
            directFillRecord.operate(fill);
        }

        groupedTradeRecord.operate(Trade.fromFills(TradeType.BUY, entryFills));
        groupedTradeRecord.operate(Trade.fromFills(TradeType.SELL, exitFills));

        assertEquals(groupedTradeRecord.getTrades(), directFillRecord.getTrades());
        assertEquals(groupedTradeRecord.getPositions(), directFillRecord.getPositions());
        assertEquals(groupedTradeRecord.getRecordedTotalFees(), directFillRecord.getRecordedTotalFees());
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
        assertEquals(numFactory.numOf(0.30000000000000004), record.getRecordedTotalFees());
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
    void operatePreservesMissingTimeAsNullAndNullIds() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        List<TradeFill> fills = List.of(new TradeFill(4, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(101), numFactory.two()));
        Trade aggregatedEntry = tradeViewWithFills(TradeType.BUY, null, null, null, fills);

        record.operate(aggregatedEntry);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertNull(trades.get(0).getTime());
        assertNull(trades.get(1).getTime());
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
        assertEquals(ExecutionSide.SELL, record.getCurrentPosition().side());
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

        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.operate(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one()));
        record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

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

        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.two()));
        record.operate(fill(ExecutionSide.BUY, numFactory.numOf(110), numFactory.two()));
        record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.numOf(105), closed.getEntry().getPricePerAsset());
        assertEquals(numFactory.one(), closed.getEntry().getAmount());

        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(numFactory.three(), net.amount());
        assertEquals(numFactory.numOf(105), net.averageEntryPrice());
        assertEquals(1, record.getOpenPositions().size());
    }

    @Test
    void recordsSpecificIdExitAgainstMatchingLot() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.operate(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));
        record.operate(fillWithIds(ExecutionSide.BUY, numFactory.numOf(110), numFactory.one(), "order-2", "corr-2"));
        record.operate(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one(), null, "corr-2"));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.numOf(110), closed.getEntry().getPricePerAsset());
    }

    @Test
    void rejectsSpecificIdExitWithoutIdentifier() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.operate(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class,
                () -> record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one())));
    }

    @Test
    void rejectsSpecificIdExitWithoutMatchingLot() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.operate(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class, () -> record
                .operate(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one(), null, "corr-2")));
    }

    @Test
    void rejectsSpecificIdExitExceedingLotAmount() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.operate(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalArgumentException.class, () -> record
                .operate(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two(), null, "corr-1")));
    }

    @Test
    void splitsLotWhenExitIsPartial() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.three()));
        record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));

        List<Position> positions = record.getPositions();
        assertEquals(1, positions.size());
        Position closed = positions.get(0);
        assertEquals(numFactory.one(), closed.getEntry().getAmount());
        assertEquals(numFactory.hundred(), closed.getEntry().getPricePerAsset());

        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(numFactory.two(), net.amount());
        assertEquals(numFactory.hundred(), net.averageEntryPrice());
    }

    @Test
    void rejectsExitExceedingOpenLots() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));

        assertThrows(IllegalArgumentException.class,
                () -> record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two())));
    }

    @Test
    void snapshotCollectionsAreImmutable() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        List<Position> openPositions = record.getOpenPositions();
        assertThrows(UnsupportedOperationException.class, () -> openPositions.add(null));
    }

    @Test
    void openPositionsExposeSnapshotPositions() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));

        Position first = record.getOpenPositions().getFirst();
        Position second = record.getOpenPositions().getFirst();

        assertNotSame(first, second);
        assertNotSame(first.getEntry(), second.getEntry());
    }

    @Test
    void ordersTradesByIndexThenFillSequence() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:02Z"), numFactory.numOf(120), numFactory.two(),
                numFactory.zero(), ExecutionSide.SELL, null, null));

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

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Position net = record.getCurrentPosition();
        assertNotNull(net);
        assertEquals(numFactory.numOf(0.30000000000000004), net.totalFees());
    }

    @Test
    void currentPositionViewPreservesRecordedEntryFees() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Position currentPosition = record.getCurrentPosition();

        assertTrue(currentPosition.isOpened());
        assertEquals(numFactory.numOf(0.30000000000000004), currentPosition.getEntry().getCost());
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

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        assertEquals(numFactory.numOf(0.30000000000000004), record.getTotalFees());
    }

    @Test
    void rejectsInvalidFillAmounts() {
        BaseTradingRecord record = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.zero())));
    }

    @Test
    void rejectsNegativeFillAmounts() {
        BaseTradingRecord record = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.minusOne())));
    }

    @Test
    void rejectsNegativeExecutionFillAmounts() {
        BaseTradingRecord record = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class, () -> record
                .operate(new TradeFill(0, null, numFactory.hundred(), numFactory.minusOne(), ExecutionSide.BUY)));
    }

    @Test
    void normalizesNegativeSyntheticAmountsToMagnitudes() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.SELL, new ZeroCostModel(), new ZeroCostModel());

        record.enter(0, numFactory.hundred(), numFactory.minusOne());

        assertEquals(numFactory.one(), record.getCurrentPosition().getEntry().getAmount());
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
        record.operate(fill(ExecutionSide.SELL, numFactory.hundred(), numFactory.one()));
        assertEquals(TradeType.SELL, record.getLastTrade().getType());
        assertFalse(record.isClosed());
    }

    @Test
    void lastEntryAndExitTrackReversalTradesInsteadOfStartingTypeBuckets() {
        Trade longEntry = Trade.buyAt(0, numFactory.hundred(), numFactory.one());
        Trade longExit = Trade.sellAt(1, numFactory.numOf(110), numFactory.one());
        Trade shortEntry = Trade.sellAt(2, numFactory.numOf(105), numFactory.one());
        BaseTradingRecord record = new BaseTradingRecord(longEntry, longExit, shortEntry);

        Trade lastEntry = record.getLastEntry();
        Trade lastExit = record.getLastExit();

        assertNotNull(lastEntry);
        assertEquals(shortEntry.getType(), lastEntry.getType());
        assertEquals(shortEntry.getIndex(), lastEntry.getIndex());
        assertEquals(shortEntry.getPricePerAsset(), lastEntry.getPricePerAsset());
        assertEquals(shortEntry.getAmount(), lastEntry.getAmount());

        assertNotNull(lastExit);
        assertEquals(longExit.getType(), lastExit.getType());
        assertEquals(longExit.getIndex(), lastExit.getIndex());
        assertEquals(longExit.getPricePerAsset(), lastExit.getPricePerAsset());
        assertEquals(longExit.getAmount(), lastExit.getAmount());
    }

    @Test
    void operateFillRejectsNanPrice() {
        BaseTradingRecord record = new BaseTradingRecord();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> record.operate(fill(ExecutionSide.BUY, NaN.NaN, numFactory.one())));

        assertEquals("fill price must be set", exception.getMessage());
    }

    @Test
    void normalizesNaNFeeToZero() {
        BaseTradingRecord record = new BaseTradingRecord();

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                NaN.NaN, ExecutionSide.BUY, null, null));

        assertEquals(numFactory.zero(), record.getRecordedTotalFees());
        assertEquals(numFactory.zero(), record.getLastTrade().getCost());
    }

    @Test
    void recordsTradeInterfaceFillsAndAutoIndexes() {
        BaseTradingRecord record = new BaseTradingRecord();
        Trade entry = tradeView(-1, TradeType.BUY, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), null, "order-1", "corr-1");
        Trade exit = tradeView(-1, TradeType.SELL, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(120),
                numFactory.one(), null, "order-1", "corr-1");

        record.operate(entry);
        record.operate(exit);

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(0, trades.get(0).getIndex());
        assertEquals(1, trades.get(1).getIndex());
        assertEquals(numFactory.zero(), record.getRecordedTotalFees());
        assertEquals(1, record.getPositions().size());
    }

    @Test
    void operateAcceptsTradeInterfaceEntriesAndExitsWithNullFeeAndTime() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        Trade entry = tradeView(-1, TradeType.BUY, null, numFactory.hundred(), numFactory.one(), null, "order-1",
                "corr-1");
        Trade exit = tradeView(-1, TradeType.SELL, null, numFactory.numOf(120), numFactory.one(), null, "order-1",
                "corr-1");

        record.operate(entry);
        record.operate(exit);

        assertEquals(1, record.getPositions().size());
        Position position = record.getPositions().getFirst();
        assertEquals(0, position.getEntry().getIndex());
        assertEquals(1, position.getExit().getIndex());
        assertNull(position.getEntry().getTime());
        assertNull(position.getExit().getTime());
        assertEquals(numFactory.zero(), position.getEntry().getCost());
        assertEquals(numFactory.zero(), position.getExit().getCost());
    }

    @Test
    void avgCostLotsKeepUnknownEntryTimeUnknown() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.AVG_COST,
                new ZeroCostModel(), new ZeroCostModel(), null, null);
        Trade firstEntry = tradeView(-1, TradeType.BUY, null, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                null, null);
        Trade secondEntry = tradeView(-1, TradeType.BUY, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(101),
                numFactory.one(), numFactory.zero(), null, null);

        record.operate(firstEntry);
        record.operate(secondEntry);

        assertNull(record.getCurrentPosition().getEntry().getTime());
        assertNull(record.getOpenPositions().getFirst().getEntry().getTime());
    }

    @Test
    void operateRejectsNanFillPrice() {
        BaseTradingRecord record = new BaseTradingRecord();
        Trade invalidTrade = tradeView(3, TradeType.BUY, null, NaN.NaN, numFactory.one(), numFactory.zero(), null,
                null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> record.operate(invalidTrade));

        assertEquals("Fill price must be set", exception.getMessage());
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
        record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        List<Trade> first = record.getTrades();
        List<Trade> second = record.getTrades();
        assertSame(first, second);

        record.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));
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
                    record.operate(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
                    record.operate(fill(ExecutionSide.SELL, numFactory.numOf(110), numFactory.one()));
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
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.two(),
                numFactory.numOf(0.1), ExecutionSide.BUY, "order-1", "corr-1"));
        record.operate(new BaseTrade(1, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.05), ExecutionSide.SELL, null, "corr-1"));
        record.operate(new BaseTrade(2, Instant.parse("2025-01-01T00:00:02Z"), numFactory.numOf(120), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, "order-2", "corr-2"));

        byte[] data;
        try (var output = new ByteArrayOutputStream(); var objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(record);
            objectOutput.flush();
            data = output.toByteArray();
        }

        try (var input = new ByteArrayInputStream(data); var objectInput = new ObjectInputStream(input)) {
            BaseTradingRecord rehydrated = (BaseTradingRecord) objectInput.readObject();

            List<Position> closed = rehydrated.getPositions();
            assertEquals(1, closed.size());
            assertEquals(numFactory.one(), closed.getFirst().getEntry().getAmount());
            assertEquals(numFactory.numOf(0.05), closed.getFirst().getEntry().getCost());

            List<Position> openPositions = rehydrated.getOpenPositions();
            assertEquals(2, openPositions.size());
            assertEquals("order-1", openPositions.getFirst().getEntry().getOrderId());
            assertEquals("corr-1", openPositions.getFirst().getEntry().getCorrelationId());
            assertEquals(numFactory.numOf(0.05), openPositions.getFirst().getEntry().getCost());
            assertEquals("order-2", openPositions.get(1).getEntry().getOrderId());
            assertEquals("corr-2", openPositions.get(1).getEntry().getCorrelationId());

            rehydrated.operate(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));
            assertEquals(2, rehydrated.getPositions().size());
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

        liveFillRecord.operate(liveFill);
        genericFillRecord.operate(genericFill);

        assertEquals(42, liveFillRecord.getLastTrade().getIndex());
        assertEquals(42, genericFillRecord.getLastTrade().getIndex());
    }

    @Test
    void tradeFillWithoutIndexUsesAutoIncrementedIndex() {
        BaseTradingRecord record = new BaseTradingRecord();

        record.operate(fillContract(-1, ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1",
                "generic-correlation-1"));
        record.operate(fillContract(-1, ExecutionSide.BUY, numFactory.numOf(101), numFactory.one(), "order-2",
                "generic-correlation-2"));

        List<Trade> trades = record.getTrades();
        assertEquals(2, trades.size());
        assertEquals(0, trades.get(0).getIndex());
        assertEquals(1, trades.get(1).getIndex());
    }

    @Test
    void toStringSupportsDecimalNumValues() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        BaseTradingRecord record = new BaseTradingRecord();
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), decimalFactory.hundred(),
                decimalFactory.one(), decimalFactory.zero(), ExecutionSide.BUY, "order-1", "corr-1"));

        String recordJson = record.toString();
        String openPositionJson = record.getCurrentPosition().toString();
        String openEntryJson = record.getOpenPositions().getFirst().getEntry().toString();

        assertTrue(recordJson.contains("\"tradeCount\":1"));
        assertTrue(openPositionJson.contains("\"side\":\"BUY\""));
        assertTrue(openEntryJson.contains("\"index\":0"));
    }

    private TradeFill fill(ExecutionSide side, Num price, Num amount) {
        return new TradeFill(-1, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, null, null);
    }

    private TradeFill fill(int index, ExecutionSide side, Num price) {
        return new TradeFill(index, Instant.EPOCH, price, numFactory.one(), numFactory.zero(), side, null, null);
    }

    private TradeFill fillWithIds(ExecutionSide side, Num price, Num amount, String orderId, String correlationId) {
        return new TradeFill(-1, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, orderId,
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
        record.operate(fill(0, ExecutionSide.SELL, series.getBar(0).getClosePrice()));
        record.operate(fill(1, ExecutionSide.BUY, series.getBar(1).getClosePrice()));
        record.operate(fill(2, ExecutionSide.SELL, series.getBar(2).getClosePrice()));
        record.operate(fill(3, ExecutionSide.BUY, series.getBar(3).getClosePrice()));
        return record;
    }

    private void assertParity(AnalysisCriterion criterion, BarSeries series, TradingRecord baseRecord,
            TradingRecord liveRecord) {
        assertEquals(criterion.calculate(series, baseRecord), criterion.calculate(series, liveRecord),
                criterion.getClass().getSimpleName());
    }

    @Test
    void futuresTradeWithoutAnExecutionTimestampIsRejected() {
        CostModel costModel = new ZeroCostModel();
        Trade futuresTrade = new Trade() {
            @Override
            public TradeType getType() {
                return TradeType.BUY;
            }

            @Override
            public int getIndex() {
                return 3;
            }

            @Override
            public Num getPricePerAsset() {
                return numFactory.numOf(50_000);
            }

            @Override
            public Num getNetPrice() {
                return numFactory.numOf(50_000);
            }

            @Override
            public Num getAmount() {
                return numFactory.numOf(2);
            }

            @Override
            public Num getCost() {
                return numFactory.zero();
            }

            @Override
            public CostModel getCostModel() {
                return costModel;
            }

            @Override
            public FuturesContract getFuturesContract() {
                return FuturesContract.builder()
                        .venue("CDE")
                        .symbol("BTC-PERP")
                        .productType(FuturesContract.ProductType.PERPETUAL)
                        .settlementType(FuturesContract.SettlementType.LINEAR)
                        .baseCurrency("BTC")
                        .quoteCurrency("USD")
                        .settlementCurrency("USD")
                        .contractSize(numFactory.numOf(0.01))
                        .quantityIncrement(numFactory.one())
                        .minimumQuantity(numFactory.one())
                        .build();
            }
        };

        BaseTradingRecord record = BaseTradingRecord.builder().build();
        record.operate(Trade.buyAt(1, numFactory.numOf(100), numFactory.one(), new ZeroCostModel()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> record.operate(futuresTrade));
        assertEquals("a native futures trade requires a non-null execution timestamp; set the fill time",
                failure.getMessage());
        assertEquals(1, record.getTrades().size());
    }

    @Test
    void recordFundingAndCashFlowsRejectIndicesThatBreakTimeOrder() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 100, 10_000, List.of()));

            record.recordFunding(FuturesFunding.builder()
                    .contract(contract)
                    .eventId("funding-late")
                    .index(10)
                    .time(T0.plusSeconds(10))
                    .rate(numFactory.numOf(0.001))
                    .referencePrice(numFactory.numOf(10_000))
                    .build());
            // Recording this event later must not carry an earlier index: profit
            // queries filter by index, so they would include it while excluding the
            // event that happened before it.
            FuturesFunding backInTime = FuturesFunding.builder()
                    .contract(contract)
                    .eventId("funding-early")
                    .index(5)
                    .time(T0.plusSeconds(20))
                    .rate(numFactory.numOf(0.001))
                    .referencePrice(numFactory.numOf(10_000))
                    .build();
            assertThrows(IllegalArgumentException.class, () -> record.recordFunding(backInTime));

            FuturesCashFlow lateFlow = FuturesCashFlow.builder()
                    .contract(contract)
                    .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                    .eventId("vm-late")
                    .index(15)
                    .time(T0.plusSeconds(30))
                    .amount(numFactory.one())
                    .currency(contract.settlementCurrency())
                    .build();
            record.recordCashFlow(lateFlow);
            FuturesCashFlow flowBackInTime = FuturesCashFlow.builder()
                    .contract(contract)
                    .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                    .eventId("vm-early")
                    .index(12)
                    .time(T0.plusSeconds(40))
                    .amount(numFactory.one())
                    .currency(contract.settlementCurrency())
                    .build();
            assertThrows(IllegalArgumentException.class, () -> record.recordCashFlow(flowBackInTime));
        }
    }

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static List<NumFactory> factories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    private static FuturesContract linearBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
    }

    private static FuturesContract inverseBtcPerpetual(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTCUSD-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.INVERSE)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("BTC")
                .contractSize(numFactory.numOf(100))
                .build();
    }

    private static TradeFee commission(NumFactory numFactory, double amount, String currency) {
        return TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(numFactory.numOf(amount))
                .currency(currency)
                .build();
    }

    private static TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount, double price,
            List<TradeFee> fees) {
        return fillAtTime(contract, index, T0.plusSeconds(index), side, amount, price, fees);
    }

    private static TradeFill fillAtTime(FuturesContract contract, int index, Instant time, ExecutionSide side,
            double amount, double price, List<TradeFee> fees) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(time)
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .futuresContract(contract)
                .fees(fees)
                .build();
    }

    private static Position openPosition(FuturesContract contract, int index, double amount, double price,
            List<TradeFee> fees) {
        Trade entry = Trade.fromFill(fill(contract, index, ExecutionSide.BUY, amount, price, fees),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private static FuturesFunding fundingEvent(FuturesContract contract, int index, double rate,
            double referencePrice) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return FuturesFunding.builder()
                .contract(contract)
                .eventId("funding-" + index)
                .index(index)
                .time(T0.plusSeconds(index))
                .rate(numFactory.numOf(rate))
                .referencePrice(numFactory.numOf(referencePrice))
                .build();
    }

    private static FuturesCashFlow cashFlow(FuturesContract contract, FuturesCashFlow.Type type, String eventId,
            int index, double amount) {
        return FuturesCashFlow.builder()
                .contract(contract)
                .type(type)
                .eventId(eventId)
                .index(index)
                .time(T0.plusSeconds(index))
                .amount(contract.contractSize().getNumFactory().numOf(amount))
                .currency(contract.settlementCurrency())
                .build();
    }

    @Test
    void builderExposesFuturesConfiguration() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .build();

            assertEquals(contract, record.getFuturesContract());
            assertNumEquals(1_000, record.getInitialCapital());
            assertNumEquals(0.1, record.getInitialMarginRate());
            assertTrue(record.getFundingSchedule().isEmpty());
            assertTrue(record.getPositions().isEmpty());
            assertTrue(record.getOpenPositions().isEmpty());
        }
    }

    @Test
    void linearRecordSettlesInSettlementCurrency() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 3, 50_000, List.of(commission(numFactory, 0.15, "USD"))));
            record.operate(
                    fill(contract, 1, ExecutionSide.SELL, 3, 52_000, List.of(commission(numFactory, 0.15, "USD"))));

            assertEquals(1, record.getPositions().size());
            Position position = record.getPositions().getFirst();
            assertEquals(contract, position.getFuturesContract());
            assertNumEquals(60, position.getGrossProfit());
            assertNumEquals(59.7, position.getProfit());
            assertNumEquals(1.04, position.getGrossReturn());
            assertNumEquals(59.7, position.getRealizedProfit(1));
            assertNumEquals(0, position.getUnrealizedProfit(numFactory.numOf(52_000), 1));
            assertNumEquals(1_500, contract.settlementNotional(numFactory.numOf(3), numFactory.numOf(50_000)));
            assertNumEquals(150,
                    contract.marginRequirement(numFactory.numOf(3), numFactory.numOf(50_000), numFactory.numOf(0.1)));

            List<TradeFee> entryFees = position.getEntry().getFees();
            assertEquals(1, entryFees.size());
            assertEquals(TradeFee.Type.COMMISSION, entryFees.getFirst().type());
            assertNumEquals(0.15, entryFees.getFirst().settlementAmount());
            assertEquals("order-0", position.getEntry().getOrderId());
            assertEquals("order-1", position.getExit().getOrderId());
        }
    }

    @Test
    void inverseRecordSettlesInBaseCurrency() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 100, 20_000, List.of(commission(numFactory, 0.0001, "BTC"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 100, 25_000,
                    List.of(commission(numFactory, 0.00012, "BTC"))));

            Position position = record.getPositions().getFirst();
            assertNumEquals(0.5, contract.settlementNotional(numFactory.numOf(100), numFactory.numOf(20_000)));
            assertNumEquals(0.1, position.getGrossProfit());
            assertNumEquals(0.09978, position.getProfit());
            assertNumEquals(1.2, position.getGrossReturn());
            assertNumEquals(0.09978, position.getRealizedProfit(1));
        }
    }

    @Test
    void inverseOpenPositionUsesHarmonicAverageEntryPrice() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = inverseBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(
                    fill(contract, 0, ExecutionSide.BUY, 50, 20_000, List.of(commission(numFactory, 0.0001, "BTC"))));
            record.operate(
                    fill(contract, 1, ExecutionSide.BUY, 50, 40_000, List.of(commission(numFactory, 0.0001, "BTC"))));

            Position open = record.getCurrentPosition();
            assertTrue(open.isOpened());
            assertNumEquals(80_000d / 3d, open.getEntry().getPricePerAsset());
            assertNumEquals(100, open.getEntry().getAmount());
            assertNumEquals(0.125, open.getGrossProfit(numFactory.numOf(40_000)));
            assertEquals(2, open.getEntry().getFees().size());
            assertNumEquals(0.0001, open.getEntry().getFees().get(0).settlementAmount());
        }
    }

    @Test
    void partialCloseAllocatesEntryFeesAndKeepsRemainingExposure() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertEquals(1, record.getPositions().size());
            Position closed = record.getPositions().getFirst();
            assertNumEquals(10, closed.getGrossProfit());
            assertNumEquals(8, closed.getProfit());
            assertNumEquals(8, closed.getRealizedProfit(1));
            assertNumEquals(0, closed.getUnrealizedProfit(numFactory.numOf(11_000), 1));

            assertEquals(1, record.getOpenPositions().size());
            Position open = record.getOpenPositions().getFirst();
            assertNumEquals(3, open.getEntry().getAmount());
            assertNumEquals(3, open.getEntry().getFees().getFirst().settlementAmount());
            assertNumEquals(60, open.getGrossProfit(numFactory.numOf(12_000)));
            assertNumEquals(57, open.getProfit(2, numFactory.numOf(12_000)));
            assertNumEquals(-3, open.getRealizedProfit(2));
            assertNumEquals(60, open.getUnrealizedProfit(numFactory.numOf(12_000), 2));
        }
    }

    @Test
    void singleExitFillAllocatesRecordedFeesPerClosedLot() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 3, 10_000, List.of(commission(numFactory, 3, "USD"))));
            record.operate(fill(contract, 1, ExecutionSide.BUY, 3, 10_100, List.of(commission(numFactory, 3, "USD"))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 6, 10_200, List.of(commission(numFactory, 6, "USD"))));

            assertEquals(2, record.getPositions().size());
            Num totalRecordedExitFees = numFactory.zero();
            for (Position position : record.getPositions()) {
                TradeFill recordedExit = position.getExit().getFills().getFirst();
                assertEquals(1, recordedExit.fees().size());
                Num lotFee = recordedExit.fees().getFirst().settlementAmount();
                assertNumEquals(3, lotFee);
                totalRecordedExitFees = totalRecordedExitFees.plus(lotFee);
            }
            assertNumEquals(6, totalRecordedExitFees);
        }
    }

    @Test
    void variationMarginMovesProfitFromUnrealizedToRealized() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position withoutMargin = openPosition(contract, 0, 3, 10_000, List.of(commission(numFactory, 3, "USD")));
            Position withMargin = new Position(withoutMargin.getEntry(), RecordedTradeCostModel.INSTANCE,
                    new ZeroCostModel(),
                    List.of(FuturesCashFlow.builder()
                            .contract(contract)
                            .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                            .eventId("vm-1")
                            .index(1)
                            .time(T0.plusSeconds(1))
                            .amount(numFactory.numOf(20))
                            .currency("USD")
                            .build()));

            Num mark = numFactory.numOf(12_000);
            assertNumEquals(57, withoutMargin.getProfit(1, mark));
            assertNumEquals(57, withMargin.getProfit(1, mark));
            assertNumEquals(-3, withoutMargin.getRealizedProfit(1));
            assertNumEquals(60, withoutMargin.getUnrealizedProfit(mark, 1));
            assertNumEquals(17, withMargin.getRealizedProfit(1));
            assertNumEquals(40, withMargin.getUnrealizedProfit(mark, 1));
        }
    }

    @Test
    void rejectsMixingSpotAndFuturesAndScalarOperations() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            TradeFill futuresFill = fill(contract, 0, ExecutionSide.BUY, 1, 50_000,
                    List.of(commission(numFactory, 0.1, "USD")));
            TradeFill spotFill = TradeFill.builder()
                    .index(0)
                    .time(T0)
                    .price(numFactory.numOf(50_000))
                    .amount(numFactory.one())
                    .side(ExecutionSide.BUY)
                    .build();

            BaseTradingRecord spotRecord = new BaseTradingRecord();
            IllegalArgumentException spotRejection = assertThrows(IllegalArgumentException.class,
                    () -> spotRecord.operate(futuresFill));
            assertTrue(spotRejection.getMessage().contains("A spot record cannot record fills of futures contract"));

            BaseTradingRecord futuresRecord = BaseTradingRecord.builder().futuresContract(contract).build();
            IllegalArgumentException futuresRejection = assertThrows(IllegalArgumentException.class,
                    () -> futuresRecord.operate(spotFill));
            assertTrue(futuresRejection.getMessage()
                    .contains("A futures record requires fills that reference the futures contract"));

            IllegalStateException scalarRejection = assertThrows(IllegalStateException.class,
                    () -> futuresRecord.operate(0, numFactory.numOf(50_000), numFactory.one()));
            assertTrue(scalarRejection.getMessage().contains("cannot be used with a futures record"));
            assertTrue(futuresRecord.getPositions().isEmpty());
            assertTrue(futuresRecord.getOpenPositions().isEmpty());

            FuturesContract otherContract = linearBtcPerpetual(numFactory).toBuilder().symbol("ETH-PERP").build();
            Trade futuresTrade = Trade.fromFill(futuresFill, RecordedTradeCostModel.INSTANCE);
            Trade otherTrade = Trade.fromFill(TradeFill.builder()
                    .index(1)
                    .time(T0.plusSeconds(1))
                    .price(numFactory.numOf(3_000))
                    .amount(numFactory.one())
                    .side(ExecutionSide.BUY)
                    .futuresContract(otherContract)
                    .fees(List.of(commission(numFactory, 0.1, "USD")))
                    .build(), RecordedTradeCostModel.INSTANCE);
            IllegalArgumentException mixedTrades = assertThrows(IllegalArgumentException.class,
                    () -> new BaseTradingRecord(futuresTrade, otherTrade));
            assertTrue(mixedTrades.getMessage().contains("All trades must reference the same futures contract"));

            Position futuresPosition = openPosition(contract, 0, 1, 50_000,
                    List.of(commission(numFactory, 0.1, "USD")));
            Trade spotEntry = new BaseTrade(0, T0, numFactory.numOf(100), numFactory.one(), numFactory.zero(),
                    ExecutionSide.BUY, null, null);
            Trade spotExit = new BaseTrade(1, T0.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                    numFactory.zero(), ExecutionSide.SELL, null, null);
            Position spotPosition = new Position(spotEntry, spotExit, spotEntry.getCostModel(), new ZeroCostModel());
            IllegalArgumentException mixedPositions = assertThrows(IllegalArgumentException.class,
                    () -> new BaseTradingRecord(List.of(futuresPosition, spotPosition)));
            assertTrue(mixedPositions.getMessage().contains("Cannot mix spot and futures positions in one record"));
        }
    }

    @Test
    void futuresRecordSurvivesSerializationAndKeepsTrading() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.1))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 1, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            BaseTradingRecord rehydrated = serializedCopy(record);

            assertEquals(contract, rehydrated.getFuturesContract());
            assertNumEquals(1_000, rehydrated.getInitialCapital());
            assertNumEquals(0.1, rehydrated.getInitialMarginRate());
            Position rehydratedClosed = rehydrated.getPositions().getFirst();
            assertNumEquals(8, rehydratedClosed.getProfit());
            assertNumEquals(1, rehydratedClosed.getEntry().getFees().getFirst().settlementAmount());
            Position rehydratedOpen = rehydrated.getOpenPositions().getFirst();
            assertNumEquals(3, rehydratedOpen.getEntry().getFees().getFirst().settlementAmount());
            assertNotNull(rehydratedClosed.getTransactionCostModel());
            rehydrated.rehydrate(new FixedTransactionCostModel(1d), new ZeroCostModel());
            assertSame(RecordedTradeCostModel.INSTANCE, rehydrated.getPositions().getFirst().getEntry().getCostModel());

            record.operate(fill(contract, 2, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));
            rehydrated.operate(
                    fill(contract, 2, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));

            assertEquals(2, rehydrated.getPositions().size());
            for (int i = 0; i < 2; i++) {
                Position expected = record.getPositions().get(i);
                Position actual = rehydrated.getPositions().get(i);
                assertNumEquals(expected.getProfit(), actual.getProfit(), 1e-12);
                assertNumEquals(expected.getEntry().getCost(), actual.getEntry().getCost(), 1e-12);
                assertNumEquals(expected.getExit().getCost(), actual.getExit().getCost(), 1e-12);
                assertEquals(expected.getEntry().getOrderId(), actual.getEntry().getOrderId());
                assertEquals(expected.getExit().getOrderId(), actual.getExit().getOrderId());
            }
            assertTrue(rehydrated.getOpenPositions().isEmpty());
            assertNumEquals(8, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(54, rehydrated.getPositions().get(1).getProfit());
        }
    }

    @Test
    void futuresPositionsConstructorPreservesCashFlowsThroughSerialization() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position position = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 3, 10_000,
                            List.of(commission(numFactory, 3, "USD"))), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel(),
                    List.of(FuturesCashFlow.builder()
                            .contract(contract)
                            .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                            .eventId("vm-1")
                            .index(1)
                            .time(T0.plusSeconds(1))
                            .amount(numFactory.numOf(20))
                            .currency("USD")
                            .build()));
            BaseTradingRecord record = new BaseTradingRecord(List.of(position));

            assertEquals(contract, record.getFuturesContract());
            assertEquals(1, record.getOpenPositions().size());
            assertNumEquals(17, record.getOpenPositions().getFirst().getRealizedProfit(1));
            assertNumEquals(3, record.getTotalFees());

            BaseTradingRecord rehydrated = serializedCopy(record);

            Position restored = rehydrated.getOpenPositions().getFirst();
            assertEquals(1, restored.getCashFlows().size());
            assertNumEquals(20, restored.getCashFlows().getFirst().settlementAmount());
            assertNumEquals(17, restored.getRealizedProfit(1));
            assertNumEquals(40, restored.getUnrealizedProfit(numFactory.numOf(12_000), 1));
            assertNumEquals(57, restored.getProfit(1, numFactory.numOf(12_000)));
        }
    }

    @Test
    void singleFuturesPositionConstructorPreservesCashFlows() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position position = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 3, 10_000,
                            List.of(commission(numFactory, 3, "USD"))), RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel(),
                    List.of(FuturesCashFlow.builder()
                            .contract(contract)
                            .type(FuturesCashFlow.Type.VARIATION_MARGIN)
                            .eventId("vm-single")
                            .index(1)
                            .time(T0.plusSeconds(1))
                            .amount(numFactory.numOf(20))
                            .currency("USD")
                            .build()));

            BaseTradingRecord record = new BaseTradingRecord(position);

            assertEquals(contract, record.getFuturesContract());
            assertEquals(1, record.getOpenPositions().size());
            Position imported = record.getOpenPositions().getFirst();
            assertEquals(1, imported.getCashFlows().size());
            assertNumEquals(20, imported.getCashFlows().getFirst().settlementAmount());
            assertNumEquals(17, imported.getRealizedProfit(1));
            assertNumEquals(3, record.getTotalFees());
        }
    }

    @Test
    void scheduledFundingAllocatesOppositeHeldSlicesIndividually() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position closedLong = new Position(
                    Trade.fromFill(fillAtTime(contract, 0, T0, ExecutionSide.BUY, 1, 10_000, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFill(fillAtTime(contract, 3, T0.plusSeconds(3), ExecutionSide.SELL, 1, 10_000, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            Position openShort = new Position(
                    Trade.fromFill(fillAtTime(contract, 1, T0.plusSeconds(1), ExecutionSide.SELL, 1, 10_000, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BaseTradingRecord record = new BaseTradingRecord(List.of(closedLong, openShort));

            record.recordFunding(fundingEvent(contract, 2, 0.001, 10_000));

            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(0, record.getCashFlows().getFirst().amount());
            FuturesCashFlow longFunding = record.getPositions().getFirst().getCashFlows().getFirst();
            FuturesCashFlow shortFunding = record.getOpenPositions().getFirst().getCashFlows().getFirst();
            assertTrue(longFunding.amount().isNegative());
            assertTrue(shortFunding.amount().isPositive());
            assertNumEquals(0, longFunding.amount().plus(shortFunding.amount()));
        }
    }

    @Test
    void fundingBoundariesDecideWhoPaysAndEventsCannotMoveBackInTime() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            FuturesFunding atEntry = fundingEvent(contract, 10, 0.001, 10_000);
            FuturesFunding afterClose = fundingEvent(contract, 40, 0.001, 10_000);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(List.of(atEntry, afterClose))
                    .build();

            record.operate(fill(contract, 9, ExecutionSide.BUY, 2, 10_000, List.of()));
            record.recordFunding(atEntry);

            assertEquals(1, record.getCashFlows().size());
            FuturesCashFlow charged = record.getCashFlows().getFirst();
            assertEquals("funding-10", charged.eventId());
            assertEquals(FuturesCashFlow.Type.FUNDING, charged.type());
            assertNumEquals(-0.2, charged.amount());
            assertNumEquals(-0.2, record.getCurrentPosition().getRealizedProfit(10));

            // A duplicate of the same event is counted once, and a lot entered
            // exactly at the funding boundary is not charged for it.
            record.recordFunding(atEntry);
            record.operate(fill(contract, 10, ExecutionSide.BUY, 1, 10_000, List.of()));
            assertEquals(1, record.getCashFlows().size());
            assertEquals(2, record.getOpenPositions().size());
            assertNumEquals(-0.2, record.getCurrentPosition().getRealizedProfit(10));
            assertNumEquals(-0.2, record.getOpenPositions().get(0).getCashFlows().getFirst().amount());
            assertTrue(record.getOpenPositions().get(1).getCashFlows().isEmpty());

            IllegalArgumentException conflicting = assertThrows(IllegalArgumentException.class, () -> record
                    .recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-10", 10, 0.3)));
            assertTrue(conflicting.getMessage().contains("already recorded with different values"));
            assertEquals(1, record.getCashFlows().size());

            record.operate(fill(contract, 19, ExecutionSide.SELL, 3, 11_000, List.of()));
            assertEquals(2, record.getPositions().size());
            assertNumEquals(19.8, record.getPositions().get(0).getProfit());
            assertNumEquals(10, record.getPositions().get(1).getProfit());

            // A funding event observed at the exit instant still charges the slices
            // that were held immediately before it.
            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "late-at-exit", 19, -0.3));

            Position firstClose = record.getPositions().get(0);
            Position secondClose = record.getPositions().get(1);
            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, firstClose.getCashFlows().size());
            assertNumEquals(-0.2, firstClose.getCashFlows().getLast().amount());
            assertEquals(1, secondClose.getCashFlows().size());
            assertNumEquals(-0.1, secondClose.getCashFlows().getLast().amount());
            assertNumEquals(19.6, firstClose.getProfit());
            assertNumEquals(19.6, firstClose.getRealizedProfit(19));
            assertNumEquals(9.9, secondClose.getProfit());
            assertNumEquals(29.5, firstClose.getProfit().plus(secondClose.getProfit()));

            // An event after every exit has no held exposure: rejected without mutation.
            IllegalArgumentException unheld = assertThrows(IllegalArgumentException.class, () -> record
                    .recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "after-exit", 20, -0.3)));
            assertTrue(unheld.getMessage().contains("No eligible futures exposure"));
            assertEquals(2, record.getCashFlows().size());
            assertNumEquals(29.5,
                    record.getPositions().get(0).getProfit().plus(record.getPositions().get(1).getProfit()));

            IllegalArgumentException backwards = assertThrows(IllegalArgumentException.class,
                    () -> record.operate(fill(contract, 15, ExecutionSide.BUY, 1, 10_000, List.of())));
            assertTrue(backwards.getMessage().contains("precedes the processed event horizon"));
            assertEquals(2, record.getPositions().size());
            assertEquals(2, record.getCashFlows().size());

            record.advanceTo(T0.plusSeconds(40));
            assertEquals(3, record.getCashFlows().size());
            assertTrue(record.getCashFlows().getLast().amount().isZero());
            record.advanceTo(T0.plusSeconds(40));
            record.advanceTo(T0);
            assertEquals(3, record.getCashFlows().size());
        }
    }

    @Test
    void partialCloseFundingAndVariationMarginConserveRecordProfit() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 3, 0.001, 12_000)))
                    .build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertNumEquals(7.89, record.getPositions().getFirst().getProfit());
            assertNumEquals(-0.44, record.getCashFlows().getFirst().amount());

            Position openAfterPartialClose = record.getCurrentPosition();
            assertNumEquals(3, openAfterPartialClose.getEntry().getAmount());
            assertNumEquals(-3.33, openAfterPartialClose.getRealizedProfit(2));
            assertNumEquals(26.67, openAfterPartialClose.getProfit(2, numFactory.numOf(11_000)));

            record.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.VARIATION_MARGIN, "vm-4", 4, 20));

            assertEquals(3, record.getCashFlows().size());
            assertNumEquals(-0.36, record.getCashFlows().get(1).amount());
            Position open = record.getCurrentPosition();
            assertNumEquals(16.31, open.getRealizedProfit(4));
            assertNumEquals(40, open.getUnrealizedProfit(numFactory.numOf(12_000), 4));
            assertNumEquals(56.31, open.getProfit(4, numFactory.numOf(12_000)));
            assertNumEquals(24.2,
                    record.getPositions().getFirst().getRealizedProfit(4).plus(open.getRealizedProfit(4)));
            assertNumEquals(5, record.getTotalFees());

            record.operate(fill(contract, 5, ExecutionSide.SELL, 3, 12_000, List.of(commission(numFactory, 3, "USD"))));

            assertEquals(2, record.getPositions().size());
            assertNumEquals(7.89, record.getPositions().get(0).getProfit());
            assertNumEquals(53.31, record.getPositions().get(1).getProfit());
            assertNumEquals(61.2,
                    record.getPositions().get(0).getProfit().plus(record.getPositions().get(1).getProfit()));
            assertNumEquals(61.2,
                    record.getPositions()
                            .get(1)
                            .getRealizedProfit(5)
                            .plus(record.getPositions().get(0).getRealizedProfit(5)));
            assertTrue(record.getOpenPositions().isEmpty());
            assertNumEquals(8, record.getTotalFees());
        }
    }

    @Test
    void lateObservedCashFlowAllocatesAcrossClosedAndOpenSlices() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(
                    fill(contract, 10, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            Position closedBeforeFlow = record.getPositions().getFirst();
            Position openBeforeFlow = record.getCurrentPosition();
            assertNumEquals(8, closedBeforeFlow.getProfit());
            assertTrue(closedBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(27, openBeforeFlow.getProfit(10, numFactory.numOf(11_000)));

            FuturesCashFlow late = cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-late", 5, 4);
            record.recordCashFlow(late);

            assertTrue(closedBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(8, closedBeforeFlow.getRealizedProfit(10));
            assertTrue(openBeforeFlow.getCashFlows().isEmpty());
            assertNumEquals(27, openBeforeFlow.getProfit(10, numFactory.numOf(11_000)));

            Position closed = record.getPositions().getFirst();
            assertEquals(1, closed.getCashFlows().size());
            assertNumEquals(1, closed.getCashFlows().getFirst().amount());
            assertNumEquals(9, closed.getRealizedProfit(10));

            Position open = record.getCurrentPosition();
            assertEquals(1, open.getCashFlows().size());
            assertNumEquals(3, open.getCashFlows().getFirst().amount());
            assertNumEquals(0, open.getRealizedProfit(10));
            assertNumEquals(30, open.getUnrealizedProfit(numFactory.numOf(11_000), 10));
            assertNumEquals(30, open.getProfit(10, numFactory.numOf(11_000)));
            assertNumEquals(30,
                    open.getRealizedProfit(10).plus(open.getUnrealizedProfit(numFactory.numOf(11_000), 10)));

            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(4, record.getCashFlows().getFirst().amount());

            record.recordCashFlow(late);
            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(9, record.getPositions().getFirst().getRealizedProfit(10));
            assertNumEquals(0, record.getCurrentPosition().getRealizedProfit(10));
        }
    }

    @Test
    void snapshotsAreRetainedForFuturesRecordsAndImmutable() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 3, 0.001, 12_000), fundingEvent(contract, 1, 0.001, 11_000)))
                    .build();

            assertEquals("funding-1", record.getFundingSchedule().get(0).eventId());
            assertEquals("funding-3", record.getFundingSchedule().get(1).eventId());

            FuturesMarketSnapshot market = FuturesMarketSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .markPrice(numFactory.numOf(11_000))
                    .build();
            FuturesPositionSnapshot position = FuturesPositionSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .signedContracts(numFactory.numOf(3))
                    .realizedPnl(numFactory.numOf(1))
                    .build();
            record.recordMarketSnapshot(market);
            record.recordPositionSnapshot(position);

            assertEquals(1, record.getMarketSnapshots().size());
            assertNumEquals(11_000, record.getMarketSnapshots().getFirst().markPrice());
            assertEquals(1, record.getPositionSnapshots().size());
            assertNumEquals(3, record.getPositionSnapshots().getFirst().signedContracts());
            assertThrows(UnsupportedOperationException.class, () -> record.getMarketSnapshots().clear());
            assertThrows(UnsupportedOperationException.class, () -> record.getPositionSnapshots().clear());

            FuturesContract foreign = contract.toBuilder().symbol("ETH-PERP").build();
            IllegalArgumentException foreignMarket = assertThrows(IllegalArgumentException.class, () -> record
                    .recordMarketSnapshot(FuturesMarketSnapshot.builder().contract(foreign).observedAt(T0).build()));
            assertTrue(foreignMarket.getMessage().contains("does not match the record contract"));
            IllegalArgumentException foreignPosition = assertThrows(IllegalArgumentException.class,
                    () -> record.recordPositionSnapshot(FuturesPositionSnapshot.builder()
                            .contract(foreign)
                            .observedAt(T0)
                            .signedContracts(numFactory.numOf(1))
                            .build()));
            assertTrue(foreignPosition.getMessage().contains("does not match the record contract"));
            assertEquals(1, record.getMarketSnapshots().size());
            assertEquals(1, record.getPositionSnapshots().size());

            BaseTradingRecord spot = new BaseTradingRecord();
            assertThrows(UnsupportedOperationException.class,
                    () -> spot.recordFunding(fundingEvent(contract, 1, 0.001, 11_000)));
            assertThrows(UnsupportedOperationException.class,
                    () -> spot.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -0.1)));
            assertThrows(UnsupportedOperationException.class, () -> spot.recordMarketSnapshot(market));
            assertThrows(UnsupportedOperationException.class, () -> spot.recordPositionSnapshot(position));
            spot.advanceTo(T0);
            assertTrue(spot.getCashFlows().isEmpty());
            assertTrue(spot.getMarketSnapshots().isEmpty());
            assertTrue(spot.getPositionSnapshots().isEmpty());
            assertTrue(spot.getFundingSchedule().isEmpty());
        }
    }

    @Test
    void fundingScheduleAndCursorSurviveSerialization() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 3, 0.001, 12_000)))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 4, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));

            assertEquals(2, record.getFundingSchedule().size());
            assertEquals(2, record.getCashFlows().size());
            assertNumEquals(-0.44, record.getCashFlows().get(0).amount());
            assertNumEquals(-0.48, record.getCashFlows().get(1).amount());
            assertNumEquals(7.77, record.getPositions().getFirst().getProfit());
            assertNumEquals(-3.69, record.getOpenPositions().getFirst().getRealizedProfit(4));

            BaseTradingRecord rehydrated = serializedCopy(record);

            assertEquals(contract, rehydrated.getFuturesContract());
            assertEquals(2, rehydrated.getFundingSchedule().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertNumEquals(-0.44, rehydrated.getCashFlows().get(0).amount());
            assertNumEquals(-0.48, rehydrated.getCashFlows().get(1).amount());
            assertNumEquals(7.77, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(-3.69, rehydrated.getOpenPositions().getFirst().getRealizedProfit(4));

            record.operate(fill(contract, 5, ExecutionSide.BUY, 2, 12_000, List.of(commission(numFactory, 2, "USD"))));
            rehydrated.operate(
                    fill(contract, 5, ExecutionSide.BUY, 2, 12_000, List.of(commission(numFactory, 2, "USD"))));

            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertEquals(2, rehydrated.getOpenPositions().size());
            assertNumEquals(-3.69, rehydrated.getOpenPositions().get(0).getRealizedProfit(5));
            assertNumEquals(-2, rehydrated.getOpenPositions().get(1).getRealizedProfit(5));
            assertNumEquals(5,
                    rehydrated.getOpenPositions()
                            .get(0)
                            .getEntry()
                            .getAmount()
                            .plus(rehydrated.getOpenPositions().get(1).getEntry().getAmount()));
            assertNumEquals(54.31, rehydrated.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)));
            assertNumEquals(record.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)),
                    rehydrated.getCurrentPosition().getProfit(5, numFactory.numOf(12_000)), 1e-12);
        }
    }

    @Test
    void rehydratedRecordAppliesPendingFundingAndSnapshotsExactlyOnce() throws Exception {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .fundingSchedule(
                            List.of(fundingEvent(contract, 1, 0.001, 11_000), fundingEvent(contract, 5, 0.001, 12_000)))
                    .build();
            record.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            record.operate(fill(contract, 2, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));
            record.recordMarketSnapshot(FuturesMarketSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .markPrice(numFactory.numOf(11_000))
                    .build());
            record.recordPositionSnapshot(FuturesPositionSnapshot.builder()
                    .contract(contract)
                    .observedAt(T0)
                    .signedContracts(numFactory.numOf(3))
                    .realizedPnl(numFactory.numOf(1))
                    .build());

            // The schedule entry behind the processed horizon stays pending.
            assertEquals(1, record.getCashFlows().size());
            assertNumEquals(-0.44, record.getCashFlows().getFirst().amount());

            BaseTradingRecord rehydrated = serializedCopy(record);
            assertEquals(record.getFundingSchedule(), rehydrated.getFundingSchedule());
            assertEquals(record.getCashFlows(), rehydrated.getCashFlows());
            assertEquals(record.getMarketSnapshots(), rehydrated.getMarketSnapshots());
            assertEquals(record.getPositionSnapshots(), rehydrated.getPositionSnapshots());
            assertNumEquals(7.89, rehydrated.getPositions().getFirst().getProfit());
            assertNumEquals(-3.33, rehydrated.getCurrentPosition().getRealizedProfit(2));

            record.advanceTo(T0.plusSeconds(6));
            rehydrated.advanceTo(T0.plusSeconds(6));

            // The pending entry is charged once and only once after rehydration.
            assertEquals(2, record.getCashFlows().size());
            assertEquals(2, rehydrated.getCashFlows().size());
            assertEquals(record.getCashFlows(), rehydrated.getCashFlows());
            assertNumEquals(-0.36, rehydrated.getCashFlows().get(1).amount());
            assertNumEquals(-0.8,
                    rehydrated.getCashFlows()
                            .stream()
                            .map(FuturesCashFlow::amount)
                            .reduce(numFactory.zero(), Num::plus));
            assertNumEquals(-3.69, rehydrated.getCurrentPosition().getRealizedProfit(6));
            assertEquals(record.getPositions().getFirst().getRealizedProfit(6),
                    rehydrated.getPositions().getFirst().getRealizedProfit(6));
        }
    }

    private static BaseTradingRecord serializedCopy(BaseTradingRecord record) throws Exception {
        byte[] data;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(record);
            objectOutput.flush();
            data = output.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
                ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (BaseTradingRecord) objectInput.readObject();
        }
    }

    @Test
    void scheduledFundingChargesExposureHeldAtItsOwnTimestamp() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord scheduled = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .fundingSchedule(List.of(fundingEvent(contract, 10, 0.001, 10_000)))
                    .build();

            scheduled.operate(fill(contract, 0, ExecutionSide.BUY, 2, 10_000, List.of()));
            // The close fill advances the accounting horizon past the funding time
            // before the schedule is ever reached.
            scheduled.operate(fill(contract, 15, ExecutionSide.SELL, 2, 11_000, List.of()));

            assertEquals(1, scheduled.getCashFlows().size());
            assertNumEquals(-0.2, scheduled.getCashFlows().getFirst().amount());
            Position scheduledClose = scheduled.getPositions().getFirst();
            assertEquals(1, scheduledClose.getCashFlows().size());
            assertNumEquals(-0.2, scheduledClose.getCashFlows().getFirst().amount());
            assertNumEquals(19.8, scheduledClose.getRealizedProfit(15));

            // An out-of-band observation older than the horizon charges the slice
            // that was held at the event's own timestamp.
            BaseTradingRecord observed = BaseTradingRecord.builder().futuresContract(contract).build();
            observed.operate(fill(contract, 0, ExecutionSide.BUY, 2, 10_000, List.of()));
            observed.operate(fill(contract, 15, ExecutionSide.SELL, 2, 11_000, List.of()));
            observed.recordFunding(fundingEvent(contract, 10, 0.001, 10_000));

            assertEquals(1, observed.getCashFlows().size());
            assertNumEquals(-0.2, observed.getCashFlows().getFirst().amount());
            Position observedClose = observed.getPositions().getFirst();
            assertNumEquals(-0.2, observedClose.getCashFlows().getFirst().amount());
            assertNumEquals(19.8, observedClose.getRealizedProfit(15));

            // Every funding time is behind the horizon: nothing more is charged.
            observed.advanceTo(T0);
            assertEquals(1, observed.getCashFlows().size());
        }
    }

    @Test
    void projectedFuturesCopiesOnlyTheSelectedPositionsAndTheirFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(numFactory.numOf(1_000))
                    .initialMarginRate(numFactory.numOf(0.5))
                    .build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            source.operate(fill(contract, 5, ExecutionSide.SELL, 4, 11_000, List.of(commission(numFactory, 2, "USD"))));
            source.operate(fill(contract, 6, ExecutionSide.BUY, 2, 11_000, List.of(commission(numFactory, 3, "USD"))));
            source.operate(fill(contract, 9, ExecutionSide.SELL, 2, 12_000, List.of(commission(numFactory, 1, "USD"))));
            assertNumEquals(10, source.getRecordedTotalFees());

            BaseTradingRecord projected = BaseTradingRecord.projectedFutures(source,
                    List.of(source.getPositions().get(1)), 6, 9);

            assertEquals(contract, projected.getFuturesContract());
            assertNumEquals(numFactory.numOf(1_000), projected.getInitialCapital());
            assertNumEquals(numFactory.numOf(0.5), projected.getInitialMarginRate());
            assertTrue(projected.getFundingSchedule().isEmpty());
            assertEquals(1, projected.getPositions().size());
            assertTrue(projected.getOpenPositions().isEmpty());

            Position selected = projected.getPositions().getFirst();
            assertEquals(6, selected.getEntry().getIndex());
            assertEquals(9, selected.getExit().getIndex());
            assertNumEquals(4, projected.getRecordedTotalFees());
            assertNumEquals(16, selected.getRealizedProfit(9));
        }
    }

    @Test
    void projectedFuturesAggregatesOnlyTheAllocatedEvents() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder().futuresContract(contract).build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 4, 10_000, List.of(commission(numFactory, 4, "USD"))));
            source.operate(
                    fill(contract, 10, ExecutionSide.SELL, 1, 11_000, List.of(commission(numFactory, 1, "USD"))));
            source.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-late", 5, 4));

            List<Position> slices = new ArrayList<>(source.getPositions());
            slices.addAll(source.getOpenPositions());
            assertEquals(2, slices.size());

            // The event is split across both slices: the projection reports it once
            // with the summed allocation, never twice.
            BaseTradingRecord whole = BaseTradingRecord.projectedFutures(source, slices, 0, 10);
            assertEquals(1, whole.getCashFlows().size());
            assertNumEquals(4, whole.getCashFlows().getFirst().amount());
            assertEquals("funding-late", whole.getCashFlows().getFirst().eventId());
            assertNumEquals(9, whole.getPositions().getFirst().getRealizedProfit(10));

            // Only the closed slice is selected: only its own allocation is reported.
            BaseTradingRecord closedOnly = BaseTradingRecord.projectedFutures(source, List.of(slices.getFirst()), 0,
                    10);
            assertEquals(1, closedOnly.getCashFlows().size());
            assertNumEquals(1, closedOnly.getCashFlows().getFirst().amount());

            // The window end cuts the allocations away from both slices.
            BaseTradingRecord trimmed = BaseTradingRecord.projectedFutures(source, slices, 0, 3);
            assertTrue(trimmed.getCashFlows().isEmpty());
            assertNumEquals(8, trimmed.getPositions().getFirst().getRealizedProfit(10));

            // The source record keeps every allocation and the live event row.
            assertEquals(1, source.getCashFlows().size());
            assertNumEquals(4, source.getCashFlows().getFirst().amount());
            assertNumEquals(1, source.getPositions().getFirst().getCashFlows().getFirst().amount());
            assertNumEquals(3, source.getCurrentPosition().getCashFlows().getFirst().amount());
        }
    }

    @Test
    void projectedFuturesRejectsEveryMutation() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord source = BaseTradingRecord.builder().futuresContract(contract).build();
            source.operate(fill(contract, 0, ExecutionSide.BUY, 1, 10_000, List.of(commission(numFactory, 1, "USD"))));
            BaseTradingRecord projected = BaseTradingRecord.projectedFutures(source, source.getOpenPositions(), 0, 1);

            assertThrows(UnsupportedOperationException.class,
                    () -> projected.operate(fill(contract, 1, ExecutionSide.BUY, 1, 10_000, List.of())));
            assertThrows(UnsupportedOperationException.class, () -> projected.operate(1));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.operate(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.enter(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.exit(1, numFactory.numOf(10_000), numFactory.numOf(1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.recordCashFlow(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "later", 1, 1)));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.recordFunding(fundingEvent(contract, 1, 0.001, 10_000)));
            assertThrows(UnsupportedOperationException.class, () -> projected.recordMarketSnapshot(
                    FuturesMarketSnapshot.builder().contract(contract).observedAt(T0.plusSeconds(1)).build()));
            assertThrows(UnsupportedOperationException.class, () -> projected.advanceTo(T0.plusSeconds(1)));
            assertThrows(UnsupportedOperationException.class, () -> projected.setName("projected"));
            assertThrows(UnsupportedOperationException.class,
                    () -> projected.rehydrate(new ZeroCostModel(), new ZeroCostModel()));

            assertEquals(1, projected.getPositions().size() + projected.getOpenPositions().size());
        }
    }

    @Test
    void projectedFuturesRequiresAFuturesSource() {
        BaseTradingRecord spot = new BaseTradingRecord();
        assertThrows(IllegalArgumentException.class, () -> BaseTradingRecord.projectedFutures(spot, List.of(), 0, 1));
    }

    @Test
    void importedFuturesPositionsPreserveHoldingModelAndAdvanceIndex() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            LinearBorrowingCostModel holdingCostModel = new LinearBorrowingCostModel(0.01,
                    LinearBorrowingCostModel.Applicability.BOTH);
            Position imported = new Position(Trade.fromFill(fill(contract, 10, ExecutionSide.BUY, 1, 100, List.of()),
                    RecordedTradeCostModel.INSTANCE), RecordedTradeCostModel.INSTANCE, holdingCostModel);

            BaseTradingRecord record = new BaseTradingRecord(List.of(imported));
            assertTrue(holdingCostModel.equals(record.getHoldingCostModel()));

            assertThrows(IllegalArgumentException.class, () -> record
                    .operate(fillAtTime(contract, -1, T0.plusSeconds(5), ExecutionSide.SELL, 1, 101, List.of())));

            record.operate(fillAtTime(contract, -1, T0.plusSeconds(11), ExecutionSide.SELL, 1, 101, List.of()));

            assertEquals(11, record.getLastTrade().getIndex());
        }
    }

    @Test
    void pendingFuturesFillsDoNotContributeProfitOrFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            TradeFill pendingFill = fill(contract, -1, ExecutionSide.BUY, 1, 100,
                    List.of(commission(numFactory, 3, "USD")));
            Position position = new Position(Trade.fromFill(pendingFill, RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

            assertNumEquals(0, position.getProfit(0, numFactory.numOf(100)));
            assertNumEquals(0, position.getRealizedProfit(0));
        }
    }

    @Test
    void averageCostPartialClosePreservesEntryCashFlowSlices() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .matchPolicy(ExecutionMatchPolicy.AVG_COST)
                    .build();

            record.operate(fillAtTime(contract, 0, T0, ExecutionSide.BUY, 1, 10_000, List.of()));
            record.recordFunding(fundingEvent(contract, 1, 0.001, 10_000));
            record.operate(fillAtTime(contract, 2, T0.plusSeconds(2), ExecutionSide.BUY, 1, 10_000, List.of()));
            record.recordFunding(fundingEvent(contract, 3, 0.001, 10_000));
            record.operate(fillAtTime(contract, 4, T0.plusSeconds(4), ExecutionSide.SELL, 1, 10_000, List.of()));

            assertEquals(1, record.getPositions().size());
            assertEquals(1, record.getOpenPositions().size());
            assertEquals(2, record.getPositions().getFirst().getCashFlows().size());
            assertNumEquals(-0.2,
                    record.getPositions()
                            .getFirst()
                            .getCashFlows()
                            .stream()
                            .map(FuturesCashFlow::amount)
                            .reduce(Num::plus)
                            .orElseThrow());
            assertNumEquals(-0.1, record.getOpenPositions().getFirst().getCashFlows().getFirst().amount());
            assertNumEquals(-0.3,
                    record.getCashFlows().stream().map(FuturesCashFlow::amount).reduce(Num::plus).orElseThrow());
        }
    }

    @Test
    void numericallyEqualCashFlowsHaveFactoryIndependentHashes() {
        FuturesCashFlow doubleFlow = cashFlow(linearBtcPerpetual(DoubleNumFactory.getInstance()),
                FuturesCashFlow.Type.FUNDING, "numeric", 1, 0.1);
        FuturesCashFlow decimalFlow = cashFlow(linearBtcPerpetual(DecimalNumFactory.getInstance()),
                FuturesCashFlow.Type.FUNDING, "numeric", 1, 0.1);

        assertEquals(doubleFlow, decimalFlow);
        assertEquals(doubleFlow.hashCode(), decimalFlow.hashCode());
    }

    @Test
    void numericallyEqualMarketSnapshotsHaveFactoryIndependentHashes() {
        FuturesMarketSnapshot doubleSnapshot = FuturesMarketSnapshot.builder()
                .contract(linearBtcPerpetual(DoubleNumFactory.getInstance()))
                .observedAt(T0)
                .markPrice(DoubleNumFactory.getInstance().numOf(11_000))
                .build();
        FuturesMarketSnapshot decimalSnapshot = FuturesMarketSnapshot.builder()
                .contract(linearBtcPerpetual(DecimalNumFactory.getInstance()))
                .observedAt(T0)
                .markPrice(DecimalNumFactory.getInstance().numOf(11_000))
                .build();

        assertEquals(doubleSnapshot, decimalSnapshot);
        assertEquals(doubleSnapshot.hashCode(), decimalSnapshot.hashCode());
    }

    @Test
    void rehydratedRecordKeepsApplyingItsOwnNumericFactory() throws Exception {
        FuturesContract doubleContract = linearBtcPerpetual(DoubleNumFactory.getInstance());
        FuturesContract decimalContract = linearBtcPerpetual(DecimalNumFactory.getInstance());
        BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(doubleContract).build();
        record.operate(fill(doubleContract, 0, ExecutionSide.BUY, 2, 10_000, List.of()));

        BaseTradingRecord rehydrated = serializedCopy(record);
        // The rehydrated record has to normalize foreign fills into its own factory.
        rehydrated.operate(fill(decimalContract, 1, ExecutionSide.BUY, 1, 10_000, List.of()));

        assertEquals(2, rehydrated.getOpenPositions().size());
        assertNumEquals(3d, rehydrated.getCurrentPosition().getEntry().getAmount());
        assertNumEquals(10_000d, rehydrated.getCurrentPosition().getEntry().getNetPrice());
    }

    @Test
    void importedOpenLotsShareTheRecordNumericFactory() {
        FuturesContract doubleContract = linearBtcPerpetual(DoubleNumFactory.getInstance());
        FuturesContract decimalContract = linearBtcPerpetual(DecimalNumFactory.getInstance());
        Position doubleLot = openPosition(doubleContract, 0, 2, 10_000, List.of());
        Position decimalLot = openPosition(decimalContract, 0, 3, 10_000, List.of());

        BaseTradingRecord record = new BaseTradingRecord(List.of(doubleLot, decimalLot));

        assertEquals(2, record.getOpenPositions().size());
        assertNumEquals(5d, record.getCurrentPosition().getEntry().getAmount());
        assertNumEquals(10_000d, record.getCurrentPosition().getEntry().getNetPrice());
    }

    @Test
    void specificIdBatchLeavesTheBookUntouchedWhenAFillCannotBeMatched() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .matchPolicy(ExecutionMatchPolicy.SPECIFIC_ID)
                    .build();
            record.operate(identifiedFill(contract, 0, ExecutionSide.BUY, 1, 10_000, "lot-a"));
            record.operate(identifiedFill(contract, 1, ExecutionSide.BUY, 1, 10_000, "lot-b"));
            Trade batch = Trade.fromFills(TradeType.SELL,
                    List.of(identifiedFill(contract, 2, ExecutionSide.SELL, 1, 11_000, "lot-a"),
                            identifiedFill(contract, 3, ExecutionSide.SELL, 1, 11_000, "unknown-lot")),
                    RecordedTradeCostModel.INSTANCE);

            assertThrows(IllegalStateException.class, () -> record.operate(batch));

            // The matched fill of a rejected batch must not be published on its own.
            assertTrue(record.getPositions().isEmpty());
            assertEquals(2, record.getOpenPositions().size());
            assertNumEquals(1, record.getOpenPositions().get(0).getEntry().getAmount());
            assertNumEquals(1, record.getOpenPositions().get(1).getEntry().getAmount());
        }
    }

    @Test
    void importedFillAtCashFlowTimeKeepsTheCashFlowOffItsSlice() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            TradeFill earlierFill = fillAtTime(contract, 0, T0, ExecutionSide.BUY, 1, 10_000, List.of());
            TradeFill boundaryFill = fillAtTime(contract, 1, T0.plusSeconds(1), ExecutionSide.BUY, 1, 10_000,
                    List.of());
            Trade entry = Trade.fromFills(TradeType.BUY, List.of(earlierFill, boundaryFill),
                    RecordedTradeCostModel.INSTANCE);
            Position imported = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel(),
                    List.of(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "boundary", 1, -1)));

            BaseTradingRecord record = new BaseTradingRecord(imported);
            record.operate(fillAtTime(contract, 2, T0.plusSeconds(2), ExecutionSide.SELL, 1, 10_000, List.of()));

            // A fill executed at the cash flow timestamp is not eligible for it, so the
            // closed slice carries the whole flow.
            assertEquals(1, record.getPositions().size());
            assertNumEquals(-1,
                    record.getPositions()
                            .getFirst()
                            .getCashFlows()
                            .stream()
                            .map(FuturesCashFlow::amount)
                            .reduce(Num::plus)
                            .orElseThrow());
            assertTrue(record.getOpenPositions().getFirst().getCashFlows().isEmpty());
        }
    }

    @Test
    void windowProjectionAttributesCashFlowsToTheEntryFillsThatOwnThem() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                    .withData(100, 100, 100, 100, 100, 100, 100)
                    .build();
            // The funding flow is owned by the first entry fill alone: the second
            // entry fill is executed after the flow timestamp.
            List<FuturesCashFlow> funding = List
                    .of(cashFlow(contract, FuturesCashFlow.Type.FUNDING, "funding-1", 1, -2));
            // Record-produced positions always carry a single exit fill, so a
            // position that is only half closed inside the window has to be imported.
            Position partiallyClosed = new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()),
                                    fill(contract, 2, ExecutionSide.BUY, 1_000, 100, List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFills(TradeType.SELL,
                            List.of(fill(contract, 4, ExecutionSide.SELL, 1_000, 100, List.of()),
                                    fill(contract, 6, ExecutionSide.SELL, 1_000, 100, List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel(), funding);
            Position closedInsideTheWindow = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 1_000, 100, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFill(fill(contract, 4, ExecutionSide.SELL, 1_000, 100, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel(), funding);

            NetProfitLossPercentageCriterion criterion = new NetProfitLossPercentageCriterion(
                    ReturnRepresentation.MULTIPLICATIVE);
            AnalysisWindow window = AnalysisWindow.barRange(0, 4);
            // The half that is still open at the window end is ignored, so the merged
            // position must report what the same 1000 contracts report on their own:
            // 1 - 2 / 1000.
            Num expected = criterion.calculate(series, new BaseTradingRecord(List.of(closedInsideTheWindow)), window,
                    AnalysisContext.defaults());
            Num actual = criterion.calculate(series, new BaseTradingRecord(List.of(partiallyClosed)), window,
                    AnalysisContext.defaults());
            assertNumEquals(expected, actual);
        }
    }

    private static TradeFill identifiedFill(FuturesContract contract, int index, ExecutionSide side, double amount,
            double price, String correlationId) {
        NumFactory numFactory = contract.contractSize().getNumFactory();
        return TradeFill.builder()
                .index(index)
                .time(T0.plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .orderId("order-" + index)
                .correlationId(correlationId)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    @Test
    void rejectedSpecificIdExitLeavesScheduledFundingUnapplied() {
        FuturesContract contract = linearBtcPerpetual(numFactory);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .matchPolicy(ExecutionMatchPolicy.SPECIFIC_ID)
                .initialCapital(numFactory.numOf(500))
                .fundingSchedule(List.of(fundingEvent(contract, 2, 0.001, 100)))
                .build();
        record.operate(fill(contract, 1, ExecutionSide.BUY, 1, 100, List.of()));

        assertThrows(IllegalStateException.class,
                () -> record.operate(fill(contract, 3, ExecutionSide.SELL, 1, 120, List.of())));

        // the rejected exit must not advance the horizon or settle the pending funding
        // event
        assertTrue(record.getCashFlows().isEmpty());
        assertTrue(record.getPositions().isEmpty());
        assertEquals(1, record.getOpenPositions().size());
    }

    @Test
    void averageCostPartialCloseKeepsTheRetainedFillAtTheLotBasis() {
        FuturesContract contract = linearBtcPerpetual(numFactory);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .matchPolicy(ExecutionMatchPolicy.AVG_COST)
                .initialCapital(numFactory.numOf(500))
                .build();
        record.operate(fill(contract, 1, ExecutionSide.BUY, 1, 100, List.of()));
        record.operate(fill(contract, 2, ExecutionSide.BUY, 1, 110, List.of()));
        record.operate(fill(contract, 3, ExecutionSide.SELL, 1, 120, List.of()));

        Position open = record.getOpenPositions().get(0);

        // the merged lot basis is 105; the retained execution must not leak its own 110
        // price
        assertNumEquals(numFactory.numOf(105), open.getEntry().getPricePerAsset());
    }

    @Test
    void separateFuturesFillBatchesRejectADecreasingIndex() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();

            record.operate(fillAtTime(contract, 10, T0, ExecutionSide.BUY, 1, 10_000, List.of()));

            // The later timestamp must not let a lower logical index slip past the
            // index horizon of the executions that were already recorded.
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> record
                    .operate(fillAtTime(contract, 5, T0.plusSeconds(1), ExecutionSide.BUY, 1, 10_000, List.of())));
            assertTrue(rejected.getMessage().contains("nondecreasing index order"));
            assertEquals(1, record.getOpenPositions().size());
        }
    }

    @Test
    void importedFuturesPositionExcludesDeferredFillsFromRecordedFees() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Trade entry = Trade.fromFills(TradeType.BUY,
                    List.of(fillAtTime(contract, 0, T0, ExecutionSide.BUY, 2, 10_000,
                            List.of(commission(numFactory, 1, "USD"))),
                            fillAtTime(contract, -1, T0.plusSeconds(1), ExecutionSide.BUY, 2, 10_000,
                                    List.of(commission(numFactory, 2, "USD")))),
                    RecordedTradeCostModel.INSTANCE);
            Position position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

            BaseTradingRecord record = new BaseTradingRecord(position);

            // The aggregate trade cost carries both components, but only the executed
            // fill has been charged.
            assertNumEquals(3, entry.getCost());
            assertNumEquals(1, record.getRecordedTotalFees());
        }
    }

    @Test
    void fundingAllocationIgnoresDeferredEntryFills() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            Position openLong = new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(fillAtTime(contract, 0, T0, ExecutionSide.BUY, 2, 10_000, List.of()),
                                    fillAtTime(contract, -1, T0.plusSeconds(1), ExecutionSide.BUY, 2, 10_000,
                                            List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BaseTradingRecord openRecord = new BaseTradingRecord(openLong);

            openRecord.recordFunding(fundingEvent(contract, 3, 0.001, 10_000));

            // Only the 2 executed contracts of the 4 planned contracts are charged:
            // 2 * 0.01 * 10_000 * 0.001.
            assertNumEquals(-0.2, openRecord.getCashFlows().getFirst().amount());
            assertNumEquals(-0.2, openRecord.getOpenPositions().getFirst().getCashFlows().getFirst().amount());

            // The same rule holds for a closed position whose exit has not been
            // reached by the event yet.
            Position closedLong = new Position(
                    Trade.fromFills(TradeType.BUY,
                            List.of(fillAtTime(contract, 0, T0, ExecutionSide.BUY, 2, 10_000, List.of()),
                                    fillAtTime(contract, -1, T0.plusSeconds(1), ExecutionSide.BUY, 2, 10_000,
                                            List.of())),
                            RecordedTradeCostModel.INSTANCE),
                    Trade.fromFill(fillAtTime(contract, 3, T0.plusSeconds(3), ExecutionSide.SELL, 4, 10_000, List.of()),
                            RecordedTradeCostModel.INSTANCE),
                    RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
            BaseTradingRecord closedRecord = new BaseTradingRecord(closedLong);

            closedRecord.recordFunding(fundingEvent(contract, 2, 0.001, 10_000));

            assertNumEquals(-0.2, closedRecord.getCashFlows().getFirst().amount());
            assertNumEquals(-0.2, closedRecord.getPositions().getFirst().getCashFlows().getFirst().amount());
        }
    }

    @Test
    void importedFuturesPositionsKeepTheirModeledTransactionCostModel() {
        for (NumFactory numFactory : factories()) {
            FuturesContract contract = linearBtcPerpetual(numFactory);
            CostModel modeled = FuturesTransactionCostModel.builder()
                    .makerRate(numFactory.zero())
                    .takerRate(numFactory.zero())
                    .perContractCharge(TradeFee.Type.COMMISSION, numFactory.numOf(0.5))
                    .build();
            Position imported = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 2, 10_000, List.of()), modeled), modeled,
                    new ZeroCostModel());
            BaseTradingRecord record = new BaseTradingRecord(imported);

            // A fill without recorded components is priced by the imported model
            // instead of failing in the recorded-fee model of the projection.
            TradeFill unpriced = TradeFill.builder()
                    .index(1)
                    .time(T0.plusSeconds(1))
                    .price(numFactory.numOf(10_000))
                    .amount(numFactory.one())
                    .side(ExecutionSide.BUY)
                    .futuresContract(contract)
                    .build();
            record.operate(unpriced);

            assertEquals(modeled, record.getTransactionCostModel());
            assertNumEquals(0.5, record.getRecordedTotalFees());

            CostModel other = new ZeroCostModel();
            Position mismatched = new Position(
                    Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 1, 10_000, List.of()), other), other,
                    new ZeroCostModel());
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                    () -> new BaseTradingRecord(List.of(imported, mismatched)));
            assertTrue(rejected.getMessage().contains("same transaction cost model"));
        }
    }
}
