/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.orderbook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.OrderBook;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Dimensionless bid-side quantity dominance in [-1, 1], not a probability.
 * Reads one source observation per call; unavailable measurements return NaN.
 * Custom sources must enforce causal availability, freshness and immutable
 * historical-index meaning. Null source results are programming errors.
 *
 * @since 0.28.1
 */
public final class OrderBookImbalanceIndicator extends AbstractIndicator<Num> {
    private final Indicator<Optional<OrderBook>> source;
    private final int levelCount;

    /**
     * @param source     availability/freshness-filtered observations
     * @param levelCount positive number of levels
     * @since 0.28.1
     */
    public OrderBookImbalanceIndicator(Indicator<Optional<OrderBook>> source, int levelCount) {
        super(validate(source, levelCount));
        this.source = source;
        this.levelCount = levelCount;
    }

    private static BarSeries validate(Indicator<Optional<OrderBook>> source, int levelCount) {
        Objects.requireNonNull(source, "source");
        if (levelCount <= 0) {
            throw new IllegalArgumentException("levelCount must be positive");
        }
        return source.getBarSeries();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.28.1
     */
    @Override
    public Num getValue(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Optional<OrderBook> book = Objects.requireNonNull(source.getValue(index), "source result");
        return book.isPresent() ? book.get().getImbalance(levelCount) : NaN.NaN;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.28.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return source.getCountOfUnstableBars();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.28.1
     */
    @Override
    public List<Indicator<?>> getDependencies() {
        return List.of(source);
    }
}
