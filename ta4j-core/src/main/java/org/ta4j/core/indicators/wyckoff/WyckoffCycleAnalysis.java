/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Runs Wyckoff cycle one-shot analysis across one or more degrees or
 * configurations.
 *
 * <p>
 * This is the analysis entry point for workflows where you want a complete
 * snapshot (phase transitions and the latest inferred state) rather than
 * per-bar indicator outputs.
 *
 * <p>
 * For indicator-style access (rules/strategies and chart overlays), use
 * {@link WyckoffCycleFacade}.
 *
 * <h2>Degrees</h2>
 * <p>
 * Unlike Elliott Wave, this package does not define a canonical degree enum.
 * This analysis treats "degrees" as configuration offsets from the base
 * configuration: higher degree offsets typically use coarser swing detection
 * and longer volume windows; lower degree offsets typically use finer swing
 * detection and shorter windows.
 *
 * <p>
 * The default {@link DegreeConfigurationProvider} applies a small, linear
 * scaling based on the degree offset. For full control, provide a custom
 * configuration provider via {@link Builder#configurationProvider}.
 *
 * @since 0.22.2
 */
public final class WyckoffCycleAnalysis {

    /**
     * Executes a single-degree Wyckoff analysis.
     *
     * <p>
     * This is a pluggable seam. Implementations may run the built-in analysis
     * pipeline, build results from indicator-style analysis
     * ({@link WyckoffCycleFacade}), or run on resampled series.
     *
     * @since 0.22.2
     */
    @FunctionalInterface
    public interface AnalysisRunner {

        /**
         * Runs analysis for a given configuration.
         *
         * @param series        series to analyze
         * @param configuration configuration for this run
         * @return cycle snapshot
         * @since 0.22.2
         */
        WyckoffCycleAnalysisResult.CycleSnapshot analyze(BarSeries series,
                WyckoffCycleAnalysisResult.DegreeConfiguration configuration);
    }

    /**
     * Selects the series window (or transformed series) to use for a given degree
     * offset.
     *
     * @since 0.22.2
     */
    @FunctionalInterface
    public interface SeriesSelector {

        /**
         * Selects a series for the requested degree offset.
         *
         * @param series       root input series
         * @param degreeOffset degree offset to select for
         * @return selected series (may be a subseries)
         * @since 0.22.2
         */
        BarSeries select(BarSeries series, int degreeOffset);
    }

    /**
     * Produces degree-specific configurations from a base configuration.
     *
     * @since 0.22.2
     */
    @FunctionalInterface
    public interface DegreeConfigurationProvider {

        /**
         * Returns the configuration to use for {@code degreeOffset}.
         *
         * @param series            series selected for this degree offset
         * @param degreeOffset      degree offset (base is {@code 0})
         * @param baseConfiguration base configuration normalized to the series
         *                          {@link NumFactory}
         * @return configuration for the degree offset
         * @since 0.22.2
         */
        WyckoffCycleAnalysisResult.DegreeConfiguration configurationFor(BarSeries series, int degreeOffset,
                WyckoffCycleAnalysisResult.DegreeConfiguration baseConfiguration);
    }

    private static final int DEFAULT_PRECEDING_SWING_BARS = 3;
    private static final int DEFAULT_FOLLOWING_SWING_BARS = 3;
    private static final int DEFAULT_ALLOWED_EQUAL_SWING_BARS = 1;
    private static final int DEFAULT_VOLUME_SHORT_WINDOW = 5;
    private static final int DEFAULT_VOLUME_LONG_WINDOW = 20;
    private static final double DEFAULT_BREAKOUT_TOLERANCE = 0.02;
    private static final double DEFAULT_RETEST_TOLERANCE = 0.05;
    private static final double DEFAULT_CLIMAX_THRESHOLD = 1.6;
    private static final double DEFAULT_DRY_UP_THRESHOLD = 0.7;

    private final int baseDegreeOffset;
    private final int higherDegrees;
    private final int lowerDegrees;
    private final SeriesSelector seriesSelector;
    private final DegreeConfigurationProvider configurationProvider;
    private final AnalysisRunner analysisRunner;

    private final int precedingSwingBars;
    private final int followingSwingBars;
    private final int allowedEqualSwingBars;
    private final int volumeShortWindow;
    private final int volumeLongWindow;
    private final Number breakoutTolerance;
    private final Number retestTolerance;
    private final Number climaxThreshold;
    private final Number dryUpThreshold;

    /**
     * Creates a new WyckoffCycleAnalysis instance.
     */
    private WyckoffCycleAnalysis(final Builder builder) {
        this.baseDegreeOffset = 0;
        this.higherDegrees = builder.higherDegrees;
        this.lowerDegrees = builder.lowerDegrees;
        this.seriesSelector = builder.seriesSelector == null ? defaultSeriesSelector() : builder.seriesSelector;
        this.configurationProvider = builder.configurationProvider == null ? defaultDegreeConfigurationProvider()
                : builder.configurationProvider;
        this.analysisRunner = builder.analysisRunner == null ? this::runDefaultAnalysis : builder.analysisRunner;

        this.precedingSwingBars = builder.precedingSwingBars;
        this.followingSwingBars = builder.followingSwingBars;
        this.allowedEqualSwingBars = builder.allowedEqualSwingBars;
        this.volumeShortWindow = builder.volumeShortWindow;
        this.volumeLongWindow = builder.volumeLongWindow;
        this.breakoutTolerance = builder.breakoutTolerance;
        this.retestTolerance = builder.retestTolerance;
        this.climaxThreshold = builder.climaxThreshold;
        this.dryUpThreshold = builder.dryUpThreshold;
    }

    /**
     * Creates a new builder.
     *
     * @return builder
     * @since 0.22.2
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs analysis on the supplied series.
     *
     * <p>
     * The result always contains the base-degree analysis. If
     * {@link Builder#higherDegrees(int)} or {@link Builder#lowerDegrees(int)} are
     * configured to positive values, supporting degree analyses are included.
     *
     * @param series root series
     * @return analysis result
     * @since 0.22.2
     */
    public WyckoffCycleAnalysisResult analyze(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        final List<String> notes = new ArrayList<>();
        final List<Integer> degreeOffsets = degreeOffsetsToAnalyze(higherDegrees, lowerDegrees);
        final List<WyckoffCycleAnalysisResult.DegreeAnalysis> analyses = new ArrayList<>(degreeOffsets.size());

        WyckoffCycleAnalysisResult.DegreeAnalysis baseAnalysis = null;

        for (final int offset : degreeOffsets) {
            final BarSeries selected = seriesSelector.select(series, offset);
            if (selected == null || selected.isEmpty()) {
                notes.add("Skipped degreeOffset=" + offset + " analysis: selected series was empty");
                continue;
            }

            final Duration barDuration = safeBarDuration(selected);
            final int barCount = selected.getBarCount();

            final WyckoffCycleAnalysisResult.DegreeConfiguration baseConfiguration = baseConfiguration(selected);
            final WyckoffCycleAnalysisResult.DegreeConfiguration configuration = configurationProvider
                    .configurationFor(selected, offset, baseConfiguration);
            if (configuration == null) {
                notes.add("Skipped degreeOffset=" + offset + " analysis: configuration provider returned null");
                continue;
            }

            final WyckoffCycleAnalysisResult.CycleSnapshot snapshot = analysisRunner.analyze(selected, configuration);
            if (snapshot == null) {
                notes.add("Skipped degreeOffset=" + offset + " analysis: runner returned null snapshot");
                continue;
            }

            final WyckoffCycleAnalysisResult.DegreeAnalysis degreeAnalysis = new WyckoffCycleAnalysisResult.DegreeAnalysis(
                    offset, barCount, barDuration, configuration, snapshot);
            analyses.add(degreeAnalysis);

            if (offset == baseDegreeOffset) {
                baseAnalysis = degreeAnalysis;
            }
        }

        if (baseAnalysis == null) {
            throw new IllegalStateException("Base degreeOffset=" + baseDegreeOffset + " analysis was not available");
        }

        return new WyckoffCycleAnalysisResult(baseDegreeOffset, analyses, notes);
    }

    private WyckoffCycleAnalysisResult.CycleSnapshot runDefaultAnalysis(final BarSeries series,
            final WyckoffCycleAnalysisResult.DegreeConfiguration configuration) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(configuration, "configuration");

        final WyckoffCycleFacade facade = WyckoffCycleFacade.builder(series)
                .withSwingConfiguration(configuration.precedingSwingBars(), configuration.followingSwingBars(),
                        configuration.allowedEqualSwingBars())
                .withVolumeWindows(configuration.volumeShortWindow(), configuration.volumeLongWindow())
                .withTolerances(configuration.breakoutTolerance(), configuration.retestTolerance())
                .withVolumeThresholds(configuration.climaxThreshold(), configuration.dryUpThreshold())
                .build();

        final WyckoffPhaseIndicator indicator = facade.phase();
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        final int unstableBars = indicator.getCountOfUnstableBars();
        final int startIndex = beginIndex + unstableBars;

        final List<WyckoffCycleAnalysisResult.PhaseTransition> transitions = new ArrayList<>();
        if (startIndex <= endIndex) {
            for (int i = startIndex; i <= endIndex; i++) {
                final WyckoffPhase phase = indicator.getValue(i);
                if (phase == null || phase.cycleType() == WyckoffCycleType.UNKNOWN) {
                    continue;
                }
                if (facade.lastPhaseTransitionIndex(i) != i) {
                    continue;
                }
                transitions.add(new WyckoffCycleAnalysisResult.PhaseTransition(i, phase, facade.tradingRangeLow(i),
                        facade.tradingRangeHigh(i)));
            }
        }

        final WyckoffPhase finalPhase = indicator.getValue(endIndex);
        final Num rangeLow = facade.tradingRangeLow(endIndex);
        final Num rangeHigh = facade.tradingRangeHigh(endIndex);
        final int lastTransitionIndex = facade.lastPhaseTransitionIndex(endIndex);

        return new WyckoffCycleAnalysisResult.CycleSnapshot(startIndex, endIndex, unstableBars, finalPhase, rangeLow,
                rangeHigh, lastTransitionIndex, transitions);
    }

    /**
     * Implements base configuration.
     */
    private WyckoffCycleAnalysisResult.DegreeConfiguration baseConfiguration(BarSeries series) {
        final NumFactory numFactory = series.numFactory();
        return new WyckoffCycleAnalysisResult.DegreeConfiguration(precedingSwingBars, followingSwingBars,
                allowedEqualSwingBars, volumeShortWindow, volumeLongWindow, numFactory.numOf(breakoutTolerance),
                numFactory.numOf(retestTolerance), numFactory.numOf(climaxThreshold), numFactory.numOf(dryUpThreshold));
    }

    /**
     * Implements safe bar duration.
     */
    private static Duration safeBarDuration(BarSeries series) {
        Duration duration = series.getFirstBar().getTimePeriod();
        return duration == null ? Duration.ZERO : duration;
    }

    /**
     * Implements degree offsets to analyze.
     */
    private static List<Integer> degreeOffsetsToAnalyze(final int higher, final int lower) {
        final int safeHigher = Math.max(0, higher);
        final int safeLower = Math.max(0, lower);

        final List<Integer> offsets = new ArrayList<>(safeHigher + 1 + safeLower);
        for (int i = safeHigher; i >= 1; i--) {
            offsets.add(i);
        }
        offsets.add(0);
        for (int i = 1; i <= safeLower; i++) {
            offsets.add(-i);
        }
        return List.copyOf(offsets);
    }

    /**
     * Builds the default series selector.
     */
    private static SeriesSelector defaultSeriesSelector() {
        return (series, degreeOffset) -> {
            Objects.requireNonNull(series, "series");
            if (degreeOffset == 0) {
                return series;
            }
            return series;
        };
    }

    /**
     * Builds the default degree configuration provider.
     */
    private static DegreeConfigurationProvider defaultDegreeConfigurationProvider() {
        return (series, degreeOffset, baseConfiguration) -> {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(baseConfiguration, "baseConfiguration");
            if (degreeOffset == 0) {
                return baseConfiguration;
            }
            int precedingSwingBars = Math.max(1, baseConfiguration.precedingSwingBars() + degreeOffset);
            int followingSwingBars = Math.max(0, baseConfiguration.followingSwingBars() + degreeOffset);
            int allowedEqualSwingBars = Math.max(0, baseConfiguration.allowedEqualSwingBars());

            int volumeShortWindow = Math.max(1, baseConfiguration.volumeShortWindow() + degreeOffset);
            int desiredLongWindow = baseConfiguration.volumeLongWindow() + (2 * degreeOffset);
            int volumeLongWindow = Math.max(volumeShortWindow, desiredLongWindow);

            return new WyckoffCycleAnalysisResult.DegreeConfiguration(precedingSwingBars, followingSwingBars,
                    allowedEqualSwingBars, volumeShortWindow, volumeLongWindow, baseConfiguration.breakoutTolerance(),
                    baseConfiguration.retestTolerance(), baseConfiguration.climaxThreshold(),
                    baseConfiguration.dryUpThreshold());
        };
    }

    /**
     * Builder for {@link WyckoffCycleAnalysis}.
     *
     * @since 0.22.2
     */
    public static final class Builder {

        private int higherDegrees;
        private int lowerDegrees;
        private SeriesSelector seriesSelector;
        private DegreeConfigurationProvider configurationProvider;
        private AnalysisRunner analysisRunner;

        private int precedingSwingBars = DEFAULT_PRECEDING_SWING_BARS;
        private int followingSwingBars = DEFAULT_FOLLOWING_SWING_BARS;
        private int allowedEqualSwingBars = DEFAULT_ALLOWED_EQUAL_SWING_BARS;
        private int volumeShortWindow = DEFAULT_VOLUME_SHORT_WINDOW;
        private int volumeLongWindow = DEFAULT_VOLUME_LONG_WINDOW;
        private Number breakoutTolerance = DEFAULT_BREAKOUT_TOLERANCE;
        private Number retestTolerance = DEFAULT_RETEST_TOLERANCE;
        private Number climaxThreshold = DEFAULT_CLIMAX_THRESHOLD;
        private Number dryUpThreshold = DEFAULT_DRY_UP_THRESHOLD;

        /**
         * Implements builder.
         */
        private Builder() {
        }

        /**
         * Sets how many higher degree offsets should be analyzed.
         *
         * @param higherDegrees number of higher degree offsets
         * @return builder
         * @since 0.22.2
         */
        public Builder higherDegrees(int higherDegrees) {
            this.higherDegrees = higherDegrees;
            return this;
        }

        /**
         * Sets how many lower degree offsets should be analyzed.
         *
         * @param lowerDegrees number of lower degree offsets
         * @return builder
         * @since 0.22.2
         */
        public Builder lowerDegrees(int lowerDegrees) {
            this.lowerDegrees = lowerDegrees;
            return this;
        }

        /**
         * Sets the series selector used to provide degree-specific series slices.
         *
         * @param seriesSelector series selector
         * @return builder
         * @since 0.22.2
         */
        public Builder seriesSelector(SeriesSelector seriesSelector) {
            this.seriesSelector = seriesSelector;
            return this;
        }

        /**
         * Sets the configuration provider used to derive degree-specific
         * configurations.
         *
         * @param configurationProvider configuration provider
         * @return builder
         * @since 0.22.2
         */
        public Builder configurationProvider(DegreeConfigurationProvider configurationProvider) {
            this.configurationProvider = configurationProvider;
            return this;
        }

        /**
         * Sets the analysis runner used to perform single-degree analysis.
         *
         * @param analysisRunner analysis runner
         * @return builder
         * @since 0.22.2
         */
        public Builder analysisRunner(AnalysisRunner analysisRunner) {
            this.analysisRunner = analysisRunner;
            return this;
        }

        /**
         * Sets swing-point configuration.
         *
         * @param precedingSwingBars bars preceding a swing point (must be {@code >= 1})
         * @param followingSwingBars bars following a swing point (must be {@code >= 0})
         * @param allowedEqualBars   number of equal bars allowed in swing detection
         *                           (must be {@code >= 0})
         * @return builder
         * @since 0.22.2
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
         * @since 0.22.2
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
         * @param breakoutTolerance breakout tolerance applied to range bounds (must be
         *                          finite and {@code >= 0})
         * @param retestTolerance   retest tolerance applied to range bounds (must be be
         *                          finite and {@code >= 0})
         * @return builder
         * @since 0.22.2
         */
        public Builder withTolerances(Number breakoutTolerance, Number retestTolerance) {
            Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
            Objects.requireNonNull(retestTolerance, "retestTolerance");
            double breakout = breakoutTolerance.doubleValue();
            double retest = retestTolerance.doubleValue();
            if (!Double.isFinite(breakout) || breakout < 0.0) {
                throw new IllegalArgumentException("breakoutTolerance must be finite and >= 0");
            }
            if (!Double.isFinite(retest) || retest < 0.0) {
                throw new IllegalArgumentException("retestTolerance must be finite and >= 0");
            }
            this.breakoutTolerance = breakoutTolerance;
            this.retestTolerance = retestTolerance;
            return this;
        }

        /**
         * Sets volume climax and dry-up thresholds.
         *
         * @param climaxThreshold ratio above which volume is treated as a climax (must
         *                        be finite and {@code >= 0})
         * @param dryUpThreshold  ratio below which volume is treated as drying up (must
         *                        be finite and {@code >= 0})
         * @return builder
         * @since 0.22.2
         */
        public Builder withVolumeThresholds(Number climaxThreshold, Number dryUpThreshold) {
            Objects.requireNonNull(climaxThreshold, "climaxThreshold");
            Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
            double climax = climaxThreshold.doubleValue();
            double dryUp = dryUpThreshold.doubleValue();
            if (!Double.isFinite(climax) || climax < 0.0) {
                throw new IllegalArgumentException("climaxThreshold must be finite and >= 0");
            }
            if (!Double.isFinite(dryUp) || dryUp < 0.0) {
                throw new IllegalArgumentException("dryUpThreshold must be finite and >= 0");
            }
            this.climaxThreshold = climaxThreshold;
            this.dryUpThreshold = dryUpThreshold;
            return this;
        }

        /**
         * Builds the configured analysis orchestrator.
         *
         * @return analysis instance
         * @since 0.22.2
         */
        public WyckoffCycleAnalysis build() {
            return new WyckoffCycleAnalysis(this);
        }
    }
}
