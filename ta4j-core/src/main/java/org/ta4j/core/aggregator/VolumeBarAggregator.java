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
 * Aggregates source bars into volume-threshold bars.
 *
 * <p>
 * A volume bar is closed once the aggregated volume reaches the configured
 * threshold. Source bars must be contiguous and evenly spaced.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * BarAggregator volumeAggregator = new VolumeBarAggregator(10_000);
 * List<Bar> volumeBars = volumeAggregator.aggregate(sourceBars);
 * }</pre>
 *
 * <p>
 * Source bars that do not complete the configured volume threshold are emitted
 * only when {@code onlyFinalBars} is {@code false}.
 *
 * @since 0.22.3
 */
public class VolumeBarAggregator implements BarAggregator {

    private final Number volumeThreshold;
    private final boolean onlyFinalBars;

    /**
     * Creates a volume-bar aggregator that emits only completed volume bars.
     *
     * @param volumeThreshold the minimum aggregated volume required to close a bar
     * @throws NullPointerException     if {@code volumeThreshold} is {@code null}
     * @throws IllegalArgumentException if {@code volumeThreshold} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public VolumeBarAggregator(Number volumeThreshold) {
        this(volumeThreshold, true);
    }

    /**
     * Creates a volume-bar aggregator.
     *
     * @param volumeThreshold the minimum aggregated volume required to close a bar
     * @param onlyFinalBars   if {@code true}, incomplete trailing bars are omitted
     * @throws NullPointerException     if {@code volumeThreshold} is {@code null}
     * @throws IllegalArgumentException if {@code volumeThreshold} is not a finite,
     *                                  positive value
     *
     * @since 0.22.3
     */
    public VolumeBarAggregator(Number volumeThreshold, boolean onlyFinalBars) {
        this.volumeThreshold = BarAggregator.requirePositiveFiniteNumber(volumeThreshold, "volumeThreshold");
        this.onlyFinalBars = onlyFinalBars;
    }

    /**
     * Aggregates bars into volume bars.
     *
     * @param bars source bars in chronological order
     * @return volume bars
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
        Num resolvedVolumeThreshold = numFactory.numOf(volumeThreshold);
        return ThresholdBarAggregationSupport.aggregate(bars, numFactory, onlyFinalBars,
                snapshot -> snapshot.volume().isGreaterThanOrEqual(resolvedVolumeThreshold),
                snapshot -> ThresholdBarAggregationSupport.buildTimeBar(numFactory, snapshot));
    }
}
