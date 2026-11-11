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
 * Displayed quantity up to the requested depth; unknown tails are not
 * extrapolated. Reads one source observation per call; unavailable measurements
 * return NaN. Custom sources must enforce causal availability, freshness and
 * immutable historical-index meaning. Null source results are programming
 * errors.
 *
 * @since 0.28.1
 */
public final class OrderBookDepthIndicator extends AbstractIndicator<Num> {
    private final Indicator<Optional<OrderBook>> source;
    private final OrderBook.Side side;
    private final int levelCount;

    /**
     * @param source     availability/freshness-filtered observations
     * @param side       required book side
     * @param levelCount positive number of levels
     * @since 0.28.1
     */
    public OrderBookDepthIndicator(Indicator<Optional<OrderBook>> source, OrderBook.Side side, int levelCount) {
        super(validate(source, side, levelCount));
        this.source = source;
        this.side = side;
        this.levelCount = levelCount;
    }

    private static BarSeries validate(Indicator<Optional<OrderBook>> source, OrderBook.Side side, int levelCount) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(side, "side");
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
        return book.isPresent() ? book.get().getDepth(side, levelCount) : NaN.NaN;
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
