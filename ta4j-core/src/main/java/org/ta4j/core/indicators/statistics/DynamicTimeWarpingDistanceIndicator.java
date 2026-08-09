/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling Dynamic Time Warping (DTW) distance indicator.
 *
 * <p>
 * DTW measures the minimum-cost monotonic alignment between two same-length
 * rolling windows, so it captures shape similarity even when one sequence
 * develops faster or slower than the other. The result is a distance, not a
 * correlation: lower means more similar, zero means identical under the
 * selected normalization and local cost, and the value is always non-negative.
 * </p>
 *
 * <p>
 * The alignment is bounded by a Sakoe–Chiba band by default; unconstrained
 * warping is an explicit opt-in. Both windows end at the evaluated index, so no
 * future samples are ever read. A window that is unavailable or contains
 * non-finite values produces {@code NaN}.
 * </p>
 *
 * <p>
 * Complexity for window size {@code W} and Sakoe–Chiba radius {@code r} is
 * {@code O(W * min(W, 2r + 1))} time with {@code O(W)} memory; unconstrained
 * mode is {@code O(W^2)} time.
 * </p>
 *
 * @since 0.24.1
 */
public final class DynamicTimeWarpingDistanceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;
    private final SequenceNormalization normalization;
    private final LocalDistance localDistance;
    private final int radius;
    private final boolean unconstrained;
    private final PathCostNormalization pathCostNormalization;

    private final transient DynamicTimeWarpingConfig config;

    /**
     * Constructor.
     *
     * @param first    first numeric indicator
     * @param second   second numeric indicator
     * @param barCount rolling window length, must be at least 2
     * @param config   normalization, local distance, alignment band, and path cost
     *                 normalization
     * @throws IllegalArgumentException if {@code barCount < 2} or the indicators
     *                                  use different series
     * @throws NullPointerException     if an indicator or the config is null
     */
    public DynamicTimeWarpingDistanceIndicator(Indicator<Num> first, Indicator<Num> second, int barCount,
            DynamicTimeWarpingConfig config) {
        this(first, second, barCount, Objects.requireNonNull(config, "config").normalization(), config.localDistance(),
                config.warpingWindow().radius(), config.warpingWindow().unrestricted(), config.pathCostNormalization());
    }

    /**
     * Flattened constructor used by indicator JSON deserialization, which can only
     * reconstruct enum, boolean, and numeric constructor parameters.
     *
     * @param first                 first numeric indicator
     * @param second                second numeric indicator
     * @param barCount              rolling window length, must be at least 2
     * @param normalization         sequence normalization
     * @param localDistance         pointwise local distance
     * @param radius                Sakoe–Chiba radius, must be 0 when
     *                              {@code unconstrained} is {@code true}
     * @param unconstrained         whether the alignment band is unbounded
     * @param pathCostNormalization path cost normalization
     */
    DynamicTimeWarpingDistanceIndicator(Indicator<Num> first, Indicator<Num> second, int barCount,
            SequenceNormalization normalization, LocalDistance localDistance, int radius, boolean unconstrained,
            PathCostNormalization pathCostNormalization) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
        this.normalization = Objects.requireNonNull(normalization, "normalization");
        this.localDistance = Objects.requireNonNull(localDistance, "localDistance");
        this.pathCostNormalization = Objects.requireNonNull(pathCostNormalization, "pathCostNormalization");
        this.radius = radius;
        this.unconstrained = unconstrained;
        this.config = new DynamicTimeWarpingConfig(normalization, localDistance,
                unconstrained ? WarpingWindow.unconstrained() : WarpingWindow.sakoeChiba(radius),
                pathCostNormalization);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.pairedWindow(first, second, index,
                barCount);
        if (window == null) {
            return NaN.NaN;
        }
        return DynamicTimeWarpingSupport.distance(getBarSeries().numFactory(), window.firstValues(),
                window.secondValues(), config);
    }

    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.unstableBars(barCount, first, second);
    }
}
