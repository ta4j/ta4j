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
 * The alignment band is caller-configured through the nested {@link Config}:
 * the Sakoe–Chiba radius bounds the warping window, and unconstrained warping
 * is an explicit opt-in. Both windows end at the evaluated index, so no future
 * samples are ever read. A window that is unavailable or contains non-finite
 * values produces {@code NaN}.
 * </p>
 *
 * <p>
 * Complexity for window size {@code W} and Sakoe–Chiba radius {@code r} is
 * {@code O(W * min(W, 2r + 1))} time with {@code O(W)} memory; unconstrained
 * mode is {@code O(W^2)} time.
 * </p>
 *
 * @since 0.24.2
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

    /** Reconstructed by the flattened constructor; never serialized directly. */
    private final transient Config config;

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
     * @since 0.24.2
     */
    public DynamicTimeWarpingDistanceIndicator(Indicator<Num> first, Indicator<Num> second, int barCount,
            Config config) {
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
     * @throws IllegalArgumentException if {@code barCount < 2}, the indicators use
     *                                  different series, or the radius is invalid
     *                                  for the alignment band
     * @throws NullPointerException     if an indicator or an enum parameter is null
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
        WarpingWindow warpingWindow = new WarpingWindow(radius, unconstrained);
        this.radius = warpingWindow.radius();
        this.unconstrained = warpingWindow.unrestricted();
        this.config = new Config(normalization, localDistance, warpingWindow, pathCostNormalization);
    }

    @Override
    protected Num calculate(int index) {
        // The unstable-bar count is relative to the retained series head, so
        // absolute indexes need the begin-index offset; long arithmetic keeps
        // the comparison overflow-proof at the extremes of the int range.
        if ((long) index < (long) getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
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

    /**
     * Immutable configuration of {@link DynamicTimeWarpingDistanceIndicator}:
     * sequence normalization, pointwise local distance, alignment band, and path
     * cost normalization.
     *
     * @param normalization         sequence normalization applied to both windows
     * @param localDistance         pointwise distance between aligned samples
     * @param warpingWindow         alignment band of the dynamic program
     * @param pathCostNormalization how the accumulated path cost is reported
     * @since 0.24.2
     */
    public record Config(SequenceNormalization normalization, LocalDistance localDistance, WarpingWindow warpingWindow,
            PathCostNormalization pathCostNormalization) {

        /**
         * Validates the configuration.
         *
         * @throws NullPointerException if a component is null
         */
        public Config {
            Objects.requireNonNull(normalization, "normalization");
            Objects.requireNonNull(localDistance, "localDistance");
            Objects.requireNonNull(warpingWindow, "warpingWindow");
            Objects.requireNonNull(pathCostNormalization, "pathCostNormalization");
        }

        /**
         * Creates the shape-comparison configuration: z-score normalization, squared
         * local distance, a Sakoe–Chiba band of the given radius, and exact path-length
         * normalization. This is the recommended baseline for comparing two series by
         * shape alone.
         *
         * @param radius the Sakoe–Chiba radius, {@code >= 0}
         * @return the shape-comparison configuration
         * @throws IllegalArgumentException if {@code radius < 0}
         * @since 0.24.2
         */
        public static Config shapeComparison(int radius) {
            return new Config(SequenceNormalization.Z_SCORE, LocalDistance.SQUARED, WarpingWindow.sakoeChiba(radius),
                    PathCostNormalization.BY_PATH_LENGTH);
        }
    }

    /**
     * Alignment band of {@link DynamicTimeWarpingDistanceIndicator}.
     *
     * <p>
     * A Sakoe–Chiba band restricts the optimal path to cells whose row/column
     * indexes differ by at most {@code radius}. A radius of zero restricts the path
     * to the diagonal, so the warped distance reduces to the sum of pointwise local
     * costs. Unconstrained warping is an explicit opt-in that costs {@code O(W^2)}
     * time instead of {@code O(W * min(W, 2r + 1))}.
     * </p>
     *
     * @param radius       the Sakoe–Chiba radius, {@code >= 0}; must be {@code 0}
     *                     when {@code unrestricted} is {@code true}
     * @param unrestricted {@code true} when every monotonic alignment is allowed
     * @since 0.24.2
     */
    public record WarpingWindow(int radius, boolean unrestricted) {

        /**
         * Creates the bounded Sakoe–Chiba window.
         *
         * @param radius the band radius, {@code >= 0}
         * @return the bounded window
         * @throws IllegalArgumentException if {@code radius < 0}
         * @since 0.24.2
         */
        public static WarpingWindow sakoeChiba(int radius) {
            return new WarpingWindow(radius, false);
        }

        /**
         * Creates the unconstrained window. Prefer a Sakoe–Chiba band unless the full
         * {@code O(W^2)} alignment is required.
         *
         * @return the unconstrained window
         * @since 0.24.2
         */
        public static WarpingWindow unconstrained() {
            return new WarpingWindow(0, true);
        }

        /**
         * Validates the window.
         *
         * @throws IllegalArgumentException if {@code radius < 0}, or if
         *                                  {@code radius != 0} while
         *                                  {@code unrestricted} is {@code true}
         */
        public WarpingWindow {
            if (radius < 0 || (unrestricted && radius != 0)) {
                throw new IllegalArgumentException(
                        "radius must be >= 0 and must be 0 when the window is unconstrained");
            }
        }

        /**
         * @param firstIndex  row index
         * @param secondIndex column index
         * @return {@code true} when the cell lies inside the alignment band
         * @since 0.24.2
         */
        public boolean inBand(int firstIndex, int secondIndex) {
            return unrestricted || Math.abs((long) firstIndex - secondIndex) <= radius;
        }
    }

    /**
     * Sequence normalization for {@link DynamicTimeWarpingDistanceIndicator}.
     *
     * <p>
     * {@link #Z_SCORE} removes level and scale so the warped distance measures
     * shape only. Under z-score normalization a zero-standard-deviation sequence is
     * mapped to zeros, so two constant sequences have zero shape distance
     * regardless of level, and a constant sequence compared with a varying one
     * measures the varying normalized shape against zeros. Callers who care about
     * absolute level use {@link #NONE}.
     * </p>
     *
     * @since 0.24.2
     */
    public enum SequenceNormalization {

        /**
         * No normalization; distances are computed on the raw values.
         */
        NONE,
        /**
         * Standardize each sequence to zero mean and unit standard deviation before
         * computing distances.
         */
        Z_SCORE
    }

    /**
     * Local distance between two aligned samples of
     * {@link DynamicTimeWarpingDistanceIndicator}.
     *
     * @since 0.24.2
     */
    public enum LocalDistance {

        /**
         * Absolute difference between the two samples.
         */
        ABSOLUTE,
        /**
         * Squared difference between the two samples; penalizes large local deviations
         * more strongly.
         */
        SQUARED
    }

    /**
     * How the accumulated path cost of {@link DynamicTimeWarpingDistanceIndicator}
     * is turned into the reported distance.
     *
     * @since 0.24.2
     */
    public enum PathCostNormalization {

        /**
         * Report the raw accumulated cost of the optimal warping path.
         */
        NONE,
        /**
         * Divide the accumulated cost by the number of cells on the optimal path so
         * that paths of different lengths remain comparable.
         */
        BY_PATH_LENGTH
    }
}
