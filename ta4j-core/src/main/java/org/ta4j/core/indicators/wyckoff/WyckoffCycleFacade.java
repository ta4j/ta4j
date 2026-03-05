/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Facade that creates and coordinates Wyckoff cycle indicators from a single
 * configuration.
 *
 * <p>
 * <b>Entry point</b>: Start here when you want indicator-style, per-bar access
 * to Wyckoff outputs for composing into rules and strategies. If you need a
 * one-shot analysis snapshot across one or more degrees/configurations, prefer
 * {@link WyckoffCycleAnalysisRunner}.
 *
 * <p>
 * Basic usage:
 *
 * <pre>
 * WyckoffCycleFacade facade = WyckoffCycleFacade.builder(series).build();
 * WyckoffPhase phase = facade.phase().getValue(index);
 * Num rangeHigh = facade.tradingRangeHigh(index);
 * </pre>
 *
 * @since 0.22.3
 */
public final class WyckoffCycleFacade {

    private final BarSeries series;
    private final int precedingSwingBars;
    private final int followingSwingBars;
    private final int allowedEqualSwingBars;
    private final int volumeShortWindow;
    private final int volumeLongWindow;
    private final Num breakoutTolerance;
    private final Num retestTolerance;
    private final Num climaxThreshold;
    private final Num dryUpThreshold;

    private final WyckoffPhaseIndicator phaseIndicator;

    /**
     * Creates a new WyckoffCycleFacade instance.
     */
    private WyckoffCycleFacade(Builder builder) {
        this.series = builder.series;
        this.precedingSwingBars = builder.precedingSwingBars;
        this.followingSwingBars = builder.followingSwingBars;
        this.allowedEqualSwingBars = builder.allowedEqualSwingBars;
        this.volumeShortWindow = builder.volumeShortWindow;
        this.volumeLongWindow = builder.volumeLongWindow;
        this.breakoutTolerance = builder.breakoutTolerance;
        this.retestTolerance = builder.retestTolerance;
        this.climaxThreshold = builder.climaxThreshold;
        this.dryUpThreshold = builder.dryUpThreshold;
        this.phaseIndicator = new WyckoffPhaseIndicator(series, precedingSwingBars, followingSwingBars,
                allowedEqualSwingBars, volumeShortWindow, volumeLongWindow, breakoutTolerance, retestTolerance,
                climaxThreshold, dryUpThreshold);
    }

    /**
     * Creates a facade with default configuration.
     *
     * @param series bar series under analysis
     * @return configured facade
     * @since 0.22.3
     */
    public static WyckoffCycleFacade of(BarSeries series) {
        return builder(series).build();
    }

    /**
     * Creates a builder for the facade.
     *
     * @param series bar series under analysis
     * @return builder
     * @since 0.22.3
     */
    public static Builder builder(BarSeries series) {
        return new Builder(series);
    }

    /**
     * Returns the underlying bar series.
     *
     * @return bar series
     * @since 0.22.3
     */
    public BarSeries series() {
        return series;
    }

    /**
     * Returns the phase indicator configured by this facade.
     *
     * @return Wyckoff phase indicator
     * @since 0.22.3
     */
    public WyckoffPhaseIndicator phase() {
        return phaseIndicator;
    }

    /**
     * Returns the Wyckoff phase for {@code index}.
     *
     * @param index bar index
     * @return inferred phase
     * @since 0.22.3
     */
    public WyckoffPhase phase(int index) {
        return phase().getValue(index);
    }

    /**
     * Returns the current trading-range high for {@code index}.
     *
     * @param index bar index
     * @return trading-range high or {@code NaN}
     * @since 0.22.3
     */
    public Num tradingRangeHigh(int index) {
        return phase().getTradingRangeHigh(index);
    }

    /**
     * Returns the current trading-range low for {@code index}.
     *
     * @param index bar index
     * @return trading-range low or {@code NaN}
     * @since 0.22.3
     */
    public Num tradingRangeLow(int index) {
        return phase().getTradingRangeLow(index);
    }

    /**
     * Returns the last phase transition index observed up to {@code index}.
     *
     * @param index bar index
     * @return index of the most recent phase transition, or {@code -1} if none were
     *         recorded
     * @since 0.22.3
     */
    public int lastPhaseTransitionIndex(int index) {
        return phase().getLastPhaseTransitionIndex(index);
    }

    /**
     * Returns the number of unstable bars for the configured phase indicator.
     *
     * @return unstable bar count
     * @since 0.22.3
     */
    public int unstableBars() {
        return phase().getCountOfUnstableBars();
    }

    /**
     * Fluent builder for {@link WyckoffCycleFacade}.
     *
     * @since 0.22.3
     */
    public static final class Builder {

        private final BarSeries series;
        private final NumFactory numFactory;
        private int precedingSwingBars = 3;
        private int followingSwingBars = 3;
        private int allowedEqualSwingBars = 1;
        private int volumeShortWindow = 5;
        private int volumeLongWindow = 20;
        private Num breakoutTolerance;
        private Num retestTolerance;
        private Num climaxThreshold;
        private Num dryUpThreshold;

        /**
         * Implements builder.
         */
        private Builder(BarSeries series) {
            this.series = Objects.requireNonNull(series, "series");
            this.numFactory = series.numFactory();
            this.breakoutTolerance = numFactory.numOf(0.02);
            this.retestTolerance = numFactory.numOf(0.05);
            this.climaxThreshold = numFactory.numOf(1.6);
            this.dryUpThreshold = numFactory.numOf(0.7);
        }

        /**
         * Sets swing-point configuration.
         *
         * @param precedingSwingBars bars preceding a swing point (must be {@code >= 1})
         * @param followingSwingBars bars following a swing point (must be {@code >= 0})
         * @param allowedEqualBars   number of equal bars allowed in swing detection
         *                           (must be {@code >= 0})
         * @return builder
         * @since 0.22.3
         */
        public Builder withSwingConfiguration(int precedingSwingBars, int followingSwingBars, int allowedEqualBars) {
            if (precedingSwingBars < 1) {
                throw new IllegalArgumentException("precedingSwingBars must be greater than 0");
            }
            if (followingSwingBars < 0) {
                throw new IllegalArgumentException("followingSwingBars must be 0 or greater");
            }
            if (allowedEqualBars < 0) {
                throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
            }
            this.precedingSwingBars = precedingSwingBars;
            this.followingSwingBars = followingSwingBars;
            this.allowedEqualSwingBars = allowedEqualBars;
            return this;
        }

        /**
         * Sets volume window lengths.
         *
         * @param shortWindow short volume SMA window (must be {@code >= 1})
         * @param longWindow  long volume SMA window (must be {@code >= shortWindow})
         * @return builder
         * @since 0.22.3
         */
        public Builder withVolumeWindows(int shortWindow, int longWindow) {
            if (shortWindow < 1) {
                throw new IllegalArgumentException("shortWindow must be greater than 0");
            }
            if (longWindow < shortWindow) {
                throw new IllegalArgumentException("longWindow must be greater than or equal to shortWindow");
            }
            this.volumeShortWindow = shortWindow;
            this.volumeLongWindow = longWindow;
            return this;
        }

        /**
         * Sets breakout and retest tolerances.
         *
         * @param breakoutTolerance breakout tolerance applied to range bounds
         * @param retestTolerance   retest tolerance applied to range bounds
         * @return builder
         * @since 0.22.3
         */
        public Builder withTolerances(Num breakoutTolerance, Num retestTolerance) {
            Num safeBreakoutTolerance = Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
            Num safeRetestTolerance = Objects.requireNonNull(retestTolerance, "retestTolerance");
            if (Num.isNaNOrNull(safeBreakoutTolerance) || safeBreakoutTolerance.isNegative()) {
                throw new IllegalArgumentException("breakoutTolerance must be finite and >= 0");
            }
            if (Num.isNaNOrNull(safeRetestTolerance) || safeRetestTolerance.isNegative()) {
                throw new IllegalArgumentException("retestTolerance must be finite and >= 0");
            }
            this.breakoutTolerance = safeBreakoutTolerance;
            this.retestTolerance = safeRetestTolerance;
            return this;
        }

        /**
         * Sets volume climax and dry-up thresholds.
         *
         * @param climaxThreshold ratio above which volume is treated as a climax
         * @param dryUpThreshold  ratio below which volume is treated as drying up
         * @return builder
         * @since 0.22.3
         */
        public Builder withVolumeThresholds(Num climaxThreshold, Num dryUpThreshold) {
            Num safeClimaxThreshold = Objects.requireNonNull(climaxThreshold, "climaxThreshold");
            Num safeDryUpThreshold = Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
            if (Num.isNaNOrNull(safeClimaxThreshold) || safeClimaxThreshold.isNegative()) {
                throw new IllegalArgumentException("climaxThreshold must be finite and >= 0");
            }
            if (Num.isNaNOrNull(safeDryUpThreshold) || safeDryUpThreshold.isNegative()) {
                throw new IllegalArgumentException("dryUpThreshold must be finite and >= 0");
            }
            this.climaxThreshold = safeClimaxThreshold;
            this.dryUpThreshold = safeDryUpThreshold;
            return this;
        }

        /**
         * Builds the configured facade.
         *
         * @return facade instance
         * @since 0.22.3
         */
        public WyckoffCycleFacade build() {
            return new WyckoffCycleFacade(this);
        }
    }
}
