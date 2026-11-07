/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.orderbook;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.Indicator;
import org.ta4j.core.OrderBook;
import org.ta4j.core.OrderBookLevel;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.orderbook.OrderBookImbalanceIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.OverIndicatorRule;

class OrderBookReadyRuleTest {
    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    private static OrderBook book(OrderBook.State state, boolean complete, String bid, String ask) {
        return new BaseOrderBook(FACTORY,
                new OrderBook.Metadata("venue", "instrument", "contracts", null, Instant.EPOCH, null, state, complete,
                        complete),
                List.of(new OrderBookLevel(FACTORY.numOf(bid), FACTORY.one())),
                List.of(new OrderBookLevel(FACTORY.numOf(ask), FACTORY.one())));
    }

    private static Indicator<Optional<OrderBook>> source(Optional<OrderBook> observation, AtomicInteger reads) {
        return new AbstractIndicator<>(new BaseBarSeriesBuilder().withNumFactory(FACTORY).build()) {
            @Override
            public Optional<OrderBook> getValue(int index) {
                reads.incrementAndGet();
                return observation;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    @Test
    void acceptsLockedAndCompleteShortDepthButRejectsGapsCrossesAndUnknownDepth() {
        for (OrderBook unavailable : List.of(book(OrderBook.State.UNSYNCHRONIZED, true, "99", "101"),
                book(OrderBook.State.SYNCHRONIZED, true, "102", "101"),
                book(OrderBook.State.SYNCHRONIZED, false, "99", "101"))) {
            assertFalse(
                    new OrderBookReadyRule(source(Optional.of(unavailable), new AtomicInteger()), 2).isSatisfied(0));
        }
        assertFalse(new OrderBookReadyRule(source(Optional.empty(), new AtomicInteger()), 1).isSatisfied(0));
        for (String[] quotes : List.of(new String[] { "100", "100" }, new String[] { "0.0", "-0.0" })) {
            OrderBook locked = book(OrderBook.State.SYNCHRONIZED, true, quotes[0], quotes[1]);
            assertTrue(new OrderBookReadyRule(source(Optional.of(locked), new AtomicInteger()), 2).isSatisfied(0));
        }
        OrderBook empty = new BaseOrderBook(FACTORY,
                book(OrderBook.State.SYNCHRONIZED, true, "99", "101").getMetadata(), List.of(), List.of());
        assertFalse(new OrderBookReadyRule(source(Optional.of(empty), new AtomicInteger()), 1).isSatisfied(0));
    }

    @Test
    void guardOutsideNegationShortCircuitsUnavailableMeasurements() {
        AtomicInteger reads = new AtomicInteger();
        Indicator<Optional<OrderBook>> source = source(Optional.empty(), reads);
        Rule comparison = new OverIndicatorRule(new OrderBookImbalanceIndicator(source, 1), FACTORY.zero());
        Rule guarded = new OrderBookReadyRule(source, 1).and(comparison.negation());
        assertFalse(guarded.isSatisfied(0, null));
        assertEquals(1, reads.get());
        reads.set(0);
        assertTrue(comparison.negation().isSatisfied(0, null));
        assertEquals(1, reads.get());
    }

    @Test
    void rejectsBadConfigurationAndNullSourceResults() {
        Indicator<Optional<OrderBook>> source = source(Optional.empty(), new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () -> new OrderBookReadyRule(source, 0));
        assertThrows(NullPointerException.class, () -> new OrderBookReadyRule(null, 1));
        OrderBookReadyRule rule = new OrderBookReadyRule(source(null, new AtomicInteger()), 1);
        assertThrows(NullPointerException.class, () -> rule.isSatisfied(0));
    }
}
