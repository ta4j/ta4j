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
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTradingRecordTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void recordsPartialFillsUsingFifo() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.LIFO, new ZeroCostModel(),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.AVG_COST,
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class,
                () -> record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one())));
    }

    @Test
    void rejectsSpecificIdExitWithoutMatchingLot() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class, () -> record
                .recordFill(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one(), null, "corr-2")));
    }

    @Test
    void rejectsSpecificIdExitExceedingLotAmount() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID,
                new ZeroCostModel(), new ZeroCostModel(), null, null);

        record.recordFill(fillWithIds(ExecutionSide.BUY, numFactory.hundred(), numFactory.one(), "order-1", "corr-1"));

        assertThrows(IllegalStateException.class, () -> record
                .recordFill(fillWithIds(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two(), null, "corr-1")));
    }

    @Test
    void splitsLotWhenExitIsPartial() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));

        assertThrows(IllegalStateException.class,
                () -> record.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.two())));
    }

    @Test
    void snapshotCollectionsAreImmutable() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        var openPositions = record.getOpenPositions();
        assertThrows(UnsupportedOperationException.class, () -> openPositions.add(null));
        assertThrows(UnsupportedOperationException.class, () -> openPositions.getFirst().lots().add(null));
    }

    @Test
    void openPositionsExposeSnapshotLots() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));

        OpenPosition first = record.getOpenPositions().getFirst();
        OpenPosition second = record.getOpenPositions().getFirst();

        assertTrue(first.lots().getFirst() != second.lots().getFirst());
    }

    @Test
    void ordersTradesByIndexThenFillSequence() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.zero(), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:02Z"), numFactory.numOf(120),
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.numOf(0.3), net.totalFees());
    }

    @Test
    void aggregatesTotalFeesAcrossFills() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.SELL, null, null));

        assertEquals(numFactory.numOf(0.3), record.getTotalFees());
    }

    @Test
    void rejectsInvalidFillAmounts() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.zero())));
    }

    @Test
    void rejectsNegativeFillAmounts() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.minusOne())));
    }

    @Test
    void rejectsDefaultEnterOperateWithNaN() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class, () -> record.enter(0));
        assertThrows(IllegalArgumentException.class, () -> record.operate(0));
    }

    @Test
    void rejectsExitWithoutOpenLots() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalStateException.class,
                () -> record.recordFill(fill(ExecutionSide.SELL, numFactory.hundred(), numFactory.one())));
    }

    @Test
    void rejectsNaNPrice() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, NaN.NaN, numFactory.one())));
    }

    @Test
    void rejectsNaNFee() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                        numFactory.one(), NaN.NaN, ExecutionSide.BUY, null, null)));
    }

    @Test
    void cachesTradesAndInvalidatesOnUpdate() {
        LiveTradingRecord record = new LiveTradingRecord();
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
    void supportsConcurrentReadsDuringWrites() throws Exception {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        if (failed.get()) {
            throw new AssertionError("Concurrent access failed", error.get());
        }
    }

    @Test
    void supportsRecordSerializationRoundTrip() throws Exception {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));

        byte[] data;
        try (var output = new ByteArrayOutputStream(); var objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(record);
            objectOutput.flush();
            data = output.toByteArray();
        }

        try (var input = new ByteArrayInputStream(data); var objectInput = new ObjectInputStream(input)) {
            LiveTradingRecord rehydrated = (LiveTradingRecord) objectInput.readObject();
            rehydrated.recordFill(fill(ExecutionSide.SELL, numFactory.numOf(120), numFactory.one()));
            assertEquals(1, rehydrated.getPositions().size());
            assertNotNull(rehydrated.getPositions().getFirst().getTransactionCostModel());
            assertNotNull(rehydrated.getPositions().getFirst().getHoldingCostModel());
        }
    }

    private LiveTrade fill(ExecutionSide side, Num price, Num amount) {
        return new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, null, null);
    }

    private LiveTrade fill(int index, ExecutionSide side, Num price) {
        return new LiveTrade(index, Instant.EPOCH, price, numFactory.one(), numFactory.zero(), side, null, null);
    }

    private LiveTrade fillWithIds(ExecutionSide side, Num price, Num amount, String orderId, String correlationId) {
        return new LiveTrade(0, Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, orderId,
                correlationId);
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
        LiveTradingRecord record = new LiveTradingRecord(TradeType.SELL, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
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
