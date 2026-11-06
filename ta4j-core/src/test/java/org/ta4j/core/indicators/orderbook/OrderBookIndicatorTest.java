/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.orderbook;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.indicators.orderbook.OrderBookIndicator.BarTime;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class OrderBookIndicatorTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final NumFactory FACTORY = DecimalNumFactory.getInstance();

    private static BarSeries series(int... seconds) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(FACTORY).build();
        for (int second : seconds) {
            addBar(series, second);
        }
        return series;
    }

    private static void addBar(BarSeries series, int second) {
        series.barBuilder()
                .timePeriod(Duration.ofSeconds(1))
                .endTime(START.plusSeconds(second))
                .openPrice(100)
                .highPrice(100)
                .lowPrice(100)
                .closePrice(100)
                .volume(1)
                .add();
    }

    private static BaseOrderBook book(int second, OrderBook.State state) {
        return new BaseOrderBook(FACTORY,
                new OrderBook.Metadata("venue", "instrument", "contracts", START.minusSeconds(100),
                        START.plusSeconds(second), null, state, true, true),
                List.of(new OrderBookLevel(FACTORY.numOf(99), FACTORY.one())),
                List.of(new OrderBookLevel(FACTORY.numOf(101), FACTORY.one())));
    }

    @Test
    void selectsByAvailabilityWithInclusiveExpiryAndLastTieWithoutSkippingGap() {
        BaseOrderBook first = book(1, OrderBook.State.SYNCHRONIZED);
        BaseOrderBook gap = book(2, OrderBook.State.UNSYNCHRONIZED);
        BaseOrderBook replacement = book(4, OrderBook.State.SYNCHRONIZED);
        OrderBookIndicator indicator = new OrderBookIndicator(series(0, 1, 2, 3, 4, 5, 6),
                List.of(first, book(2, OrderBook.State.SYNCHRONIZED), gap, replacement), Duration.ofSeconds(1),
                BarTime.END);
        assertTrue(indicator.getValue(0).isEmpty());
        assertSame(first, indicator.getValue(1).orElseThrow());
        assertSame(gap, indicator.getValue(2).orElseThrow());
        assertSame(gap, indicator.getValue(3).orElseThrow());
        assertSame(replacement, indicator.getValue(4).orElseThrow());
        assertSame(replacement, indicator.getValue(5).orElseThrow());
        assertTrue(indicator.getValue(6).isEmpty());
        assertSame(first, indicator.getValue(1).orElseThrow());
    }

    @Test
    void explicitBoundaryAndZeroAgeNeverUseFutureObservation() {
        BarSeries series = series(2);
        BaseOrderBook opening = book(1, OrderBook.State.SYNCHRONIZED);
        BaseOrderBook closing = book(2, OrderBook.State.SYNCHRONIZED);
        List<OrderBook> history = List.of(opening, closing);
        assertSame(opening,
                new OrderBookIndicator(series, history, Duration.ZERO, BarTime.BEGIN).getValue(0).orElseThrow());
        assertSame(closing,
                new OrderBookIndicator(series, history, Duration.ZERO, BarTime.END).getValue(0).orElseThrow());
        assertTrue(new OrderBookIndicator(series, List.of(opening), Duration.ZERO, BarTime.END).getValue(0).isEmpty());
        assertTrue(new OrderBookIndicator(series, List.of(), Duration.ZERO, BarTime.END).getValue(0).isEmpty());
    }

    @Test
    void rejectsRemovedNegativeFutureAndClearedIndices() {
        BarSeries series = series(1, 2);
        OrderBookIndicator indicator = new OrderBookIndicator(series, List.of(book(1, OrderBook.State.SYNCHRONIZED)),
                Duration.ofDays(1), BarTime.END);
        indicator.getValue(0);
        series.setMaximumBarCount(1);
        assertThrows(IndexOutOfBoundsException.class, () -> indicator.getValue(0));
        assertThrows(IndexOutOfBoundsException.class, () -> indicator.getValue(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> indicator.getValue(2));
        assertEquals(START.plusSeconds(1), indicator.getValue(1).orElseThrow().getMetadata().availableTime());
        series.clear();
        assertThrows(IndexOutOfBoundsException.class, () -> indicator.getValue(1));
    }

    @Test
    void retentionCannotSubstituteALaterDecisionBoundaryDuringLookup() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        AtomicReference<Runnable> eviction = new AtomicReference<>();
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(series(1), lock, index -> {
            // A feed may evict between separate reads, but not inside a read lease.
            if (lock.getReadHoldCount() == 0 && eviction.get() != null) {
                eviction.getAndSet(null).run();
            }
        });
        BaseOrderBook first = book(1, OrderBook.State.SYNCHRONIZED);
        BaseOrderBook future = book(2, OrderBook.State.SYNCHRONIZED);
        OrderBookIndicator indicator = new OrderBookIndicator(series, List.of(first, future), Duration.ZERO,
                BarTime.END);
        eviction.set(() -> series.addBar(series(2).getBar(0)));

        assertSame(first, indicator.getValue(0).orElseThrow());
        eviction.getAndSet(null).run();
        assertThrows(IndexOutOfBoundsException.class, () -> indicator.getValue(0));
        assertSame(future, indicator.getValue(1).orElseThrow());
    }

    @Test
    void observesAppendAndTerminalReplacementWithoutBookCache() {
        BarSeries series = series(1);
        BaseOrderBook first = book(1, OrderBook.State.SYNCHRONIZED);
        BaseOrderBook next = book(2, OrderBook.State.SYNCHRONIZED);
        OrderBookIndicator indicator = new OrderBookIndicator(series, List.of(first, next), Duration.ZERO, BarTime.END);
        assertSame(first, indicator.getValue(0).orElseThrow());
        BarSeries replacement = series(2);
        series.addBar(replacement.getBar(0), true);
        assertSame(next, indicator.getValue(0).orElseThrow());
        addBar(series, 3);
        assertTrue(indicator.getValue(1).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> indicator.getBarSeries().addBar(replacement.getBar(0)));
    }

    @Test
    void cachedCompositionTracksChangedBarBoundariesAndRetention() {
        BarSeries series = series(1, 2);
        OrderBookIndicator books = new OrderBookIndicator(series,
                List.of(book(1, OrderBook.State.SYNCHRONIZED), book(3, OrderBook.State.UNSYNCHRONIZED)),
                Duration.ofDays(1), BarTime.END);
        SMAIndicator smoothed = new SMAIndicator(new OrderBookSpreadIndicator(books), 1);
        assertEquals(FACTORY.two(), smoothed.getValue(0));
        assertEquals(FACTORY.two(), smoothed.getValue(1));
        series.addBar(series(3).getBar(0), true);
        assertTrue(smoothed.getValue(1).isNaN());
        series.setMaximumBarCount(1);
        addBar(series, 4);
        assertTrue(smoothed.getValue(2).isNaN());
        series.clear();
        addBar(series, 1);
        assertEquals(FACTORY.two(), smoothed.getValue(series.getEndIndex()));
    }

    @Test
    void freezesCallerListAndExternalSnapshot() {
        List<OrderBookLevel> bids = new ArrayList<>(List.of(new OrderBookLevel(FACTORY.numOf(99), FACTORY.one())));
        BaseOrderBook original = book(1, OrderBook.State.SYNCHRONIZED);
        OrderBook external = new OrderBook() {
            @Override
            public NumFactory numFactory() {
                return FACTORY;
            }

            @Override
            public Metadata getMetadata() {
                return original.getMetadata();
            }

            @Override
            public List<OrderBookLevel> getBids() {
                return bids;
            }

            @Override
            public List<OrderBookLevel> getAsks() {
                return original.getAsks();
            }
        };
        List<OrderBook> history = new ArrayList<>(List.of(external));
        OrderBookIndicator indicator = new OrderBookIndicator(series(1), history, Duration.ZERO, BarTime.END);
        history.clear();
        bids.clear();
        OrderBook frozen = indicator.getValue(0).orElseThrow();
        assertNotSame(external, frozen);
        assertEquals(original.getBids(), frozen.getBids());
        assertEquals(original.getAsks(), frozen.getAsks());
    }

    @Test
    void rejectsInvalidHistoryConfigurationAndNumericMarketMismatch() {
        BarSeries series = series(1);
        BaseOrderBook first = book(1, OrderBook.State.SYNCHRONIZED);
        BaseOrderBook later = book(2, OrderBook.State.SYNCHRONIZED);
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookIndicator(series, List.of(later, first), Duration.ZERO, BarTime.END));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookIndicator(series, List.of(first), Duration.ofNanos(-1), BarTime.END));
        assertThrows(NullPointerException.class,
                () -> new OrderBookIndicator(series, List.of(first), null, BarTime.END));
        assertThrows(NullPointerException.class,
                () -> new OrderBookIndicator(series, List.of(first), Duration.ZERO, null));
        BaseOrderBook foreign = new BaseOrderBook(DoubleNumFactory.getInstance(), first.getMetadata(), List.of(),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new OrderBookIndicator(series, List.of(foreign), Duration.ZERO, BarTime.END));
        for (int field = 0; field < 3; field++) {
            OrderBook.Metadata mixed = new OrderBook.Metadata(field == 0 ? "other" : "venue",
                    field == 1 ? "other" : "instrument", field == 2 ? "other" : "contracts", null, START.plusSeconds(1),
                    null, OrderBook.State.SYNCHRONIZED, true, true);
            BaseOrderBook wrongMarket = new BaseOrderBook(FACTORY, mixed, List.of(), List.of());
            assertThrows(IllegalArgumentException.class,
                    () -> new OrderBookIndicator(series, List.of(first, wrongMarket), Duration.ZERO, BarTime.END));
        }
    }
}
