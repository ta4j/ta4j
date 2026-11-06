/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.orderbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class OrderBookDepthIndicatorTest {
    static Stream<NumFactory> factories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    void rejectsInvalidDepthAndMissingInputsBeforeEvaluation() {
        OrderBookIndicator source = new OrderBookIndicator(new BaseBarSeriesBuilder().build(), List.of(), Duration.ZERO,
                OrderBookIndicator.BarTime.END);
        assertThrows(IllegalArgumentException.class, () -> new OrderBookDepthIndicator(source, OrderBook.Side.BID, 0));
        assertThrows(IllegalArgumentException.class, () -> new OrderBookDepthIndicator(source, OrderBook.Side.ASK, -1));
        assertThrows(NullPointerException.class, () -> new OrderBookDepthIndicator(source, null, 1));
        assertThrows(NullPointerException.class, () -> new OrderBookDepthIndicator(null, OrderBook.Side.BID, 1));
    }

    @Test
    void respectsSourceWarmupWithoutReadingUnavailableHistory() {
        BarSeries series = new BaseBarSeriesBuilder().build();
        NumFactory factory = series.numFactory();
        BaseOrderBook book = new BaseOrderBook(factory,
                new OrderBook.Metadata("venue", "instrument", "contracts", null, Instant.EPOCH, null,
                        OrderBook.State.SYNCHRONIZED, true, true),
                List.of(new OrderBookLevel(factory.numOf(99), factory.numOf(4))),
                List.of(new OrderBookLevel(factory.numOf(101), factory.two())));
        AtomicInteger reads = new AtomicInteger();
        AbstractIndicator<Optional<OrderBook>> source = new AbstractIndicator<>(series) {
            @Override
            public Optional<OrderBook> getValue(int index) {
                assertEquals(2, index);
                reads.incrementAndGet();
                return Optional.of(book);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 2;
            }
        };
        OrderBookDepthIndicator metric = new OrderBookDepthIndicator(source, OrderBook.Side.BID, 1);
        assertEquals(2, metric.getCountOfUnstableBars());
        assertTrue(metric.getValue(1).isNaN());
        assertEquals(0, reads.get());
        assertNumEquals(4, metric.getValue(2));
        assertEquals(1, reads.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void propagatesCausalAbsenceAndDomainAvailability(NumFactory factory) {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        for (int i = 0; i < 4; i++) {
            series.barBuilder()
                    .timePeriod(Duration.ofSeconds(1))
                    .endTime(time.plusSeconds(i))
                    .openPrice(100)
                    .highPrice(100)
                    .lowPrice(100)
                    .closePrice(100)
                    .volume(1)
                    .add();
        }
        List<OrderBookLevel> bids = List.of(new OrderBookLevel(factory.numOf(99), factory.numOf(4)));
        List<OrderBookLevel> asks = List.of(new OrderBookLevel(factory.numOf(101), factory.numOf(2)));
        BaseOrderBook healthy = new BaseOrderBook(factory, new OrderBook.Metadata("venue", "instrument", "contracts",
                null, time.plusSeconds(1), null, OrderBook.State.SYNCHRONIZED, true, true), bids, asks);
        BaseOrderBook unavailable = new BaseOrderBook(factory, new OrderBook.Metadata("venue", "instrument",
                "contracts", null, time.plusSeconds(2), null, OrderBook.State.SYNCHRONIZED, false, false), bids, asks);
        OrderBookIndicator source = new OrderBookIndicator(series, List.of(healthy, unavailable), Duration.ZERO,
                OrderBookIndicator.BarTime.END);
        OrderBookDepthIndicator metric = new OrderBookDepthIndicator(source, OrderBook.Side.BID, 2);
        assertTrue(metric.getValue(0).isNaN());
        assertNumEquals(4, metric.getValue(1));
        assertTrue(metric.getValue(2).isNaN());
        assertTrue(metric.getValue(3).isNaN());
        assertNumEquals(4, metric.getValue(1));
    }
}
