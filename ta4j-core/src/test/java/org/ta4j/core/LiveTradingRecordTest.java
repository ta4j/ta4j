/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void snapshotCollectionsAreImmutable() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        LiveTradingRecordSnapshot snapshot = record.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.positions().add(new Position()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.openPositions().add(null));
    }

    @Test
    void aggregatesFeesInOpenPosition() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.recordFill(new ExecutionFill(Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(),
                numFactory.one(), numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.recordFill(new ExecutionFill(Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110),
                numFactory.one(), numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        OpenPosition net = record.getNetOpenPosition();
        assertNotNull(net);
        assertEquals(numFactory.numOf(0.3), net.totalFees());
    }

    @Test
    void rejectsInvalidFillAmounts() {
        LiveTradingRecord record = new LiveTradingRecord();
        assertThrows(IllegalArgumentException.class,
                () -> record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.zero())));
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
                    LiveTradingRecordSnapshot snapshot = record.snapshot();
                    assertNotNull(snapshot);
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
    void supportsSnapshotSerializationRoundTrip() throws Exception {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.recordFill(fill(ExecutionSide.BUY, numFactory.hundred(), numFactory.one()));
        LiveTradingRecordSnapshot snapshot = record.snapshot();

        byte[] data;
        try (var output = new ByteArrayOutputStream(); var objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(snapshot);
            objectOutput.flush();
            data = output.toByteArray();
        }

        try (var input = new ByteArrayInputStream(data); var objectInput = new ObjectInputStream(input)) {
            LiveTradingRecordSnapshot rehydrated = (LiveTradingRecordSnapshot) objectInput.readObject();
            assertNotNull(rehydrated);
            assertEquals(snapshot.openPositions().size(), rehydrated.openPositions().size());
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
        }
    }

    private ExecutionFill fill(ExecutionSide side, Num price, Num amount) {
        return new ExecutionFill(Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, null, null);
    }

    private ExecutionFill fillWithIds(ExecutionSide side, Num price, Num amount, String orderId, String correlationId) {
        return new ExecutionFill(Instant.parse("2025-01-01T00:00:00Z"), price, amount, null, side, orderId,
                correlationId);
    }
}
