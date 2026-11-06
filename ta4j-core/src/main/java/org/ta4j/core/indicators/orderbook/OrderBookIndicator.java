/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.orderbook;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseOrderBook;
import org.ta4j.core.OrderBook;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * Frozen L2 history joined to a bar timeline by availability time. Each lookup
 * selects the last observation available at the chosen bar boundary, including
 * the last input observation on a timestamp tie. Event time is not eligibility.
 * Fresh unsynchronized observations remain visible; no lookup skips a gap.
 *
 * <p>
 * Construction uses O(observations × retained depth) storage; lookup uses O(log
 * observations) comparisons. There is no live append API: changing the L2
 * dataset requires a new indicator graph. Bar boundaries are read on every
 * call. Configuration JSON/expression reconstruction cannot bind this external
 * dataset.
 *
 * @since 0.28.1
 */
public final class OrderBookIndicator extends AbstractIndicator<Optional<OrderBook>> {

    /**
     * Decision boundary. END models a signal at bar close, not an execution fill.
     *
     * @since 0.28.1
     */
    public enum BarTime {
        /**
         * Bar opening boundary.
         * 
         * @since 0.28.1
         */
        BEGIN,
        /**
         * Bar closing boundary.
         * 
         * @since 0.28.1
         */
        END
    }

    private final List<OrderBook> observations;
    private final Duration maximumAge;
    private final BarTime barTime;

    /**
     * Freezes a single market's nondecreasing availability history. Immutable
     * BaseOrderBook instances are reused; external implementations are copied. The
     * caller explicitly binds the market to the series, irrespective of name.
     *
     * @param series       decision timeline
     * @param observations history in arrival order; equal timestamps retain order
     * @param maximumAge   nonnegative inclusive age limit; zero requires an exact
     *                     match
     * @param barTime      explicit opening or closing decision boundary
     * @throws IllegalArgumentException for decreasing availability, mixed market or
     *                                  incompatible numeric values, or negative
     *                                  maximumAge
     * @since 0.28.1
     */
    public OrderBookIndicator(BarSeries series, List<? extends OrderBook> observations, Duration maximumAge,
            BarTime barTime) {
        this(freeze(series, observations, maximumAge, barTime), series, maximumAge, barTime);
    }

    private OrderBookIndicator(List<OrderBook> observations, BarSeries series, Duration maximumAge, BarTime barTime) {
        super(series);
        this.observations = observations;
        this.maximumAge = maximumAge;
        this.barTime = barTime;
    }

    private static List<OrderBook> freeze(BarSeries series, List<? extends OrderBook> observations, Duration maximumAge,
            BarTime barTime) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(maximumAge, "maximumAge");
        Objects.requireNonNull(barTime, "barTime");
        if (maximumAge.isNegative()) {
            throw new IllegalArgumentException("maximumAge must be nonnegative");
        }
        List<OrderBook> frozen = new ArrayList<>(observations.size());
        OrderBook.Metadata first = null;
        Instant previous = null;
        for (OrderBook observation : observations) {
            BaseOrderBook book = BaseOrderBook.copyOf(observation);
            OrderBook.Metadata metadata = book.getMetadata();
            if (previous != null && metadata.availableTime().isBefore(previous)) {
                throw new IllegalArgumentException("observations must have nondecreasing availableTime");
            }
            if (first != null
                    && (!first.venue().equals(metadata.venue()) || !first.instrument().equals(metadata.instrument())
                            || !first.quantityUnit().equals(metadata.quantityUnit()))) {
                throw new IllegalArgumentException(
                        "observations must belong to one venue, instrument and quantity unit");
            }
            if (!series.numFactory().produces(book.numFactory().zero())) {
                throw new IllegalArgumentException("book numeric factory must be compatible with series");
            }
            first = metadata;
            previous = metadata.availableTime();
            frozen.add(book);
        }
        return List.copyOf(frozen);
    }

    /**
     * Returns a fresh as-of observation, or empty before history/after expiry.
     *
     * @throws IndexOutOfBoundsException outside the currently retained bar range
     * @since 0.28.1
     */
    @Override
    public Optional<OrderBook> getValue(int index) {
        BarSeries series = getBarSeries();
        if (series.isEmpty() || index < series.getBeginIndex() || index > series.getEndIndex()) {
            throw new IndexOutOfBoundsException("bar index outside retained range: " + index);
        }
        Bar bar = series.getBar(index);
        Instant cutoff = barTime == BarTime.BEGIN ? bar.getBeginTime() : bar.getEndTime();
        int low = 0;
        int high = observations.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (observations.get(middle).getMetadata().availableTime().isAfter(cutoff)) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        if (low == 0) {
            return Optional.empty();
        }
        OrderBook selected = observations.get(low - 1);
        return Duration.between(selected.getMetadata().availableTime(), cutoff).compareTo(maximumAge) <= 0
                ? Optional.of(selected)
                : Optional.empty();
    }

    /**
     * No fixed warm-up; zero does not imply an observation exists at every index.
     *
     * @return zero
     * @since 0.28.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
