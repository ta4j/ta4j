/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Aggregates source bars into range bars.
 *
 * <p>
 * A range bar is closed once the aggregated high-low range reaches the
 * configured range size. Source bars must be contiguous and evenly spaced.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * BarAggregator rangeAggregator = new RangeBarAggregator(2.5);
 * List<Bar> rangeBars = rangeAggregator.aggregate(sourceBars);
 * }</pre>
 *
 * <p>
 * Source bars that do not complete the configured range are emitted only when
 * {@code onlyFinalBars} is {@code false}.
 *
 * @since 0.22.3
 */
public class RangeBarAggregator implements BarAggregator {

    private final Number rangeSize;
    private final boolean onlyFinalBars;

    /**
     * Creates a range-bar aggregator that emits only completed range bars.
     *
     * @param rangeSize the minimum high-low range required to close a bar
     * @throws NullPointerException     if {@code rangeSize} is {@code null}
     * @throws IllegalArgumentException if {@code rangeSize} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public RangeBarAggregator(Number rangeSize) {
        this(rangeSize, true);
    }

    /**
     * Creates a range-bar aggregator.
     *
     * @param rangeSize     the minimum high-low range required to close a bar
     * @param onlyFinalBars if {@code true}, incomplete trailing bars are omitted
     * @throws NullPointerException     if {@code rangeSize} is {@code null}
     * @throws IllegalArgumentException if {@code rangeSize} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public RangeBarAggregator(Number rangeSize, boolean onlyFinalBars) {
        this.rangeSize = BarAggregator.requirePositiveFiniteNumber(rangeSize, "rangeSize");
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates bars into range bars.
     *
     * @param bars source bars in chronological order
     * @return range bars
     * @throws NullPointerException     if {@code bars} is {@code null}
     * @throws IllegalArgumentException if source intervals are uneven or
     *                                  non-contiguous
     */
    @Override
    public List<Bar> aggregate(List<Bar> bars) {
        Objects.requireNonNull(bars, "bars");
        if (bars.isEmpty()) {
            return new ArrayList<>();
        }

        requireEvenIntervals(bars);

        NumFactory numFactory = bars.getFirst().numFactory();
        Num resolvedRangeSize = numFactory.numOf(rangeSize);
        Num zero = numFactory.zero();

        return ThresholdBarAggregationSupport.aggregate(bars, numFactory, onlyFinalBars, snapshot -> {
            Num currentRange = zero;
            if (snapshot.highPrice() != null && snapshot.lowPrice() != null) {
                currentRange = snapshot.highPrice().minus(snapshot.lowPrice());
            }
            return currentRange.isGreaterThanOrEqual(resolvedRangeSize);
        }, snapshot -> ThresholdBarAggregationSupport.buildTimeBar(numFactory, snapshot));
    }
}
