/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.orderbook;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.Indicator;
import org.ta4j.core.OrderBook;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Quote midpoint, not a predicted execution price. Reads one source observation
 * per call; unavailable measurements return NaN. Custom sources must enforce
 * causal availability, freshness and immutable historical-index meaning. Null
 * source results are programming errors.
 *
 * @since 0.28.1
 */
public final class OrderBookMidPriceIndicator extends AbstractIndicator<Num> {
    private final Indicator<Optional<OrderBook>> source;

    /**
     * @param source availability/freshness-filtered observations
     * @since 0.28.1
     */
    public OrderBookMidPriceIndicator(Indicator<Optional<OrderBook>> source) {
        super(Objects.requireNonNull(source, "source").getBarSeries());
        this.source = source;
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
        return book.isPresent() ? book.get().getMidPrice() : NaN.NaN;
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
