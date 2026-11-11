/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.orderbook;

import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.Indicator;
import org.ta4j.core.OrderBook;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.AbstractRule;

/**
 * Requires a synchronized, two-sided, noncrossed observation with known
 * coverage on both sides. Locked quotes are ready. A complete side may end
 * before the requested depth. Custom sources must enforce availability and
 * freshness, and pin immutable observations to historical indices. Keep this
 * guard outside negation: NOT(an unavailable comparison) is not evidence of
 * readiness.
 *
 * @since 0.28.1
 */
public final class OrderBookReadyRule extends AbstractRule {
    private final Indicator<Optional<OrderBook>> source;
    private final int requiredLevelsPerSide;

    /**
     * @param source                availability/freshness-filtered observations
     * @param requiredLevelsPerSide positive requested coverage on each side
     * @since 0.28.1
     */
    public OrderBookReadyRule(Indicator<Optional<OrderBook>> source, int requiredLevelsPerSide) {
        this(requiredLevelsPerSide, validate(source, requiredLevelsPerSide));
    }

    private OrderBookReadyRule(int requiredLevelsPerSide, Indicator<Optional<OrderBook>> source) {
        this.source = source;
        this.requiredLevelsPerSide = requiredLevelsPerSide;
    }

    private static Indicator<Optional<OrderBook>> validate(Indicator<Optional<OrderBook>> source, int levels) {
        Objects.requireNonNull(source, "source");
        if (levels <= 0) {
            throw new IllegalArgumentException("requiredLevelsPerSide must be positive");
        }
        return source;
    }

    /**
     * Checks one observation; the trading record does not affect readiness.
     *
     * @since 0.28.1
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        if (index >= source.getCountOfUnstableBars()) {
            Optional<OrderBook> observation = Objects.requireNonNull(source.getValue(index), "source result");
            if (observation.isPresent()) {
                OrderBook book = observation.get();
                satisfied = book.getMetadata().state() == OrderBook.State.SYNCHRONIZED && !book.getBids().isEmpty()
                        && !book.getAsks().isEmpty()
                        && (book.getBids().getFirst().price().isZero() && book.getAsks().getFirst().price().isZero()
                                || book.getBids()
                                        .getFirst()
                                        .price()
                                        .isLessThanOrEqual(book.getAsks().getFirst().price()))
                        && book.hasDepth(OrderBook.Side.BID, requiredLevelsPerSide)
                        && book.hasDepth(OrderBook.Side.ASK, requiredLevelsPerSide);
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
