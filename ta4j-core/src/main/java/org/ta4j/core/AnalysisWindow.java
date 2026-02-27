/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Describes a requested analysis window.
 *
 * <p>
 * A window can be expressed by:
 * </p>
 * <ul>
 * <li>explicit bar index range (inclusive/inclusive)</li>
 * <li>lookback bars (count of bars ending at an anchor)</li>
 * <li>explicit time range (inclusive/exclusive)</li>
 * <li>lookback duration (duration ending at an anchor)</li>
 * </ul>
 *
 * <p>
 * Use one of the static factory methods to create an immutable window.
 * </p>
 *
 * @since 0.22.3
 */
public sealed interface AnalysisWindow permits AnalysisWindow.BarRange, AnalysisWindow.LookbackBars,
        AnalysisWindow.TimeRange, AnalysisWindow.LookbackDuration {

    /**
     * Creates a window from explicit bar indices.
     *
     * @param startIndexInclusive the first bar index to include
     * @param endIndexInclusive   the last bar index to include
     * @return the requested bar-range window
     * @since 0.22.3
     */
    static AnalysisWindow barRange(int startIndexInclusive, int endIndexInclusive) {
        return new BarRange(startIndexInclusive, endIndexInclusive);
    }

    /**
     * Creates a window from a lookback bar count.
     *
     * <p>
     * The anchor is resolved at calculation time from {@link AnalysisContext} (or
     * defaults).
     * </p>
     *
     * @param barCount the number of bars to include
     * @return the requested lookback-bars window
     * @since 0.22.3
     */
    static AnalysisWindow lookbackBars(int barCount) {
        return new LookbackBars(barCount);
    }

    /**
     * Creates a window from an explicit time range.
     *
     * <p>
     * Time windows use start-inclusive/end-exclusive semantics.
     * </p>
     *
     * @param startInclusive the start instant (inclusive)
     * @param endExclusive   the end instant (exclusive)
     * @return the requested time-range window
     * @since 0.22.3
     */
    static AnalysisWindow timeRange(Instant startInclusive, Instant endExclusive) {
        return new TimeRange(startInclusive, endExclusive);
    }

    /**
     * Creates a window from a lookback duration.
     *
     * <p>
     * The anchor is resolved at calculation time from {@link AnalysisContext} (or
     * defaults).
     * </p>
     *
     * @param duration the lookback duration
     * @return the requested lookback-duration window
     * @since 0.22.3
     */
    static AnalysisWindow lookbackDuration(Duration duration) {
        return new LookbackDuration(duration);
    }

    /**
     * Explicit bar-index range window with inclusive boundaries.
     *
     * @param startIndexInclusive the start index (inclusive)
     * @param endIndexInclusive   the end index (inclusive)
     * @since 0.22.3
     */
    record BarRange(int startIndexInclusive, int endIndexInclusive) implements AnalysisWindow {

        /**
         * Creates a bar-index range.
         *
         * @param startIndexInclusive the start index (inclusive)
         * @param endIndexInclusive   the end index (inclusive)
         */
        public BarRange {
            if (startIndexInclusive < 0) {
                throw new IllegalArgumentException("startIndexInclusive must be >= 0");
            }
            if (endIndexInclusive < startIndexInclusive) {
                throw new IllegalArgumentException("endIndexInclusive must be >= startIndexInclusive");
            }
        }
    }

    /**
     * Lookback-bar-count window.
     *
     * @param barCount number of bars to include
     * @since 0.22.3
     */
    record LookbackBars(int barCount) implements AnalysisWindow {

        /**
         * Creates a lookback-bar-count window.
         *
         * @param barCount number of bars to include
         */
        public LookbackBars {
            if (barCount <= 0) {
                throw new IllegalArgumentException("barCount must be > 0");
            }
        }
    }

    /**
     * Explicit time-range window with start-inclusive/end-exclusive boundaries.
     *
     * @param startInclusive the start instant (inclusive)
     * @param endExclusive   the end instant (exclusive)
     * @since 0.22.3
     */
    record TimeRange(Instant startInclusive, Instant endExclusive) implements AnalysisWindow {

        /**
         * Creates a time-range window.
         *
         * @param startInclusive the start instant (inclusive)
         * @param endExclusive   the end instant (exclusive)
         */
        public TimeRange {
            Objects.requireNonNull(startInclusive, "startInclusive");
            Objects.requireNonNull(endExclusive, "endExclusive");
            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("startInclusive must be before endExclusive");
            }
        }
    }

    /**
     * Lookback-duration window.
     *
     * @param duration the lookback duration
     * @since 0.22.3
     */
    record LookbackDuration(Duration duration) implements AnalysisWindow {

        /**
         * Creates a lookback-duration window.
         *
         * @param duration the lookback duration
         */
        public LookbackDuration {
            Objects.requireNonNull(duration, "duration");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("duration must be positive");
            }
        }
    }
}
