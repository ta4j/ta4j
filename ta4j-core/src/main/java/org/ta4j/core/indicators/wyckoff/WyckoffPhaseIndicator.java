/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Indicator that infers Wyckoff phases by composing structural and volume
 * detectors.
 *
 * @since 0.22.2
 */
public final class WyckoffPhaseIndicator extends CachedIndicator<WyckoffPhase> {

    private final int precedingSwingBars;
    private final int followingSwingBars;
    private final int allowedEqualSwingBars;
    private final int volumeShortWindow;
    private final int volumeLongWindow;
    private final Num breakoutTolerance;
    private final Num retestTolerance;
    private final Num climaxThreshold;
    private final Num dryUpThreshold;

    private final transient WyckoffStructureTracker structureTracker;
    private final transient WyckoffVolumeProfile volumeProfile;
    private final transient WyckoffEventDetector eventDetector;
    private final transient int unstableBars;

    private final transient Map<Integer, WyckoffStructureTracker.StructureSnapshot> structureSnapshots;
    private final transient Map<Integer, Integer> lastTransitionIndices;

    /**
     * Creates a Wyckoff phase indicator with default configuration.
     *
     * @param series underlying bar series
     * @since 0.22.2
     */
    public WyckoffPhaseIndicator(BarSeries series) {
        this(Objects.requireNonNull(series, "series"), 3, 3, 1, 5, 20, series.numFactory().numOf(0.02),
                series.numFactory().numOf(0.05), series.numFactory().numOf(1.6), series.numFactory().numOf(0.7));
    }

    /**
     * Creates a Wyckoff phase indicator with full configuration.
     *
     * @param series                underlying bar series
     * @param precedingSwingBars    bars preceding a swing point
     * @param followingSwingBars    bars following a swing point
     * @param allowedEqualSwingBars number of equal bars allowed in swing detection
     * @param volumeShortWindow     short volume SMA window
     * @param volumeLongWindow      long volume SMA window
     * @param breakoutTolerance     breakout tolerance applied to range bounds
     * @param retestTolerance       retest tolerance applied to range bounds
     * @param climaxThreshold       ratio above which volume is a climax
     * @param dryUpThreshold        ratio below which volume is drying up
     * @since 0.22.2
     */
    public WyckoffPhaseIndicator(BarSeries series, int precedingSwingBars, int followingSwingBars,
            int allowedEqualSwingBars, int volumeShortWindow, int volumeLongWindow, Num breakoutTolerance,
            Num retestTolerance, Num climaxThreshold, Num dryUpThreshold) {
        super(Objects.requireNonNull(series, "series"));
        this.precedingSwingBars = precedingSwingBars;
        this.followingSwingBars = followingSwingBars;
        this.allowedEqualSwingBars = allowedEqualSwingBars;
        this.volumeShortWindow = volumeShortWindow;
        this.volumeLongWindow = volumeLongWindow;
        this.breakoutTolerance = Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
        this.retestTolerance = Objects.requireNonNull(retestTolerance, "retestTolerance");
        this.climaxThreshold = Objects.requireNonNull(climaxThreshold, "climaxThreshold");
        this.dryUpThreshold = Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
        this.structureTracker = new WyckoffStructureTracker(series, precedingSwingBars, followingSwingBars,
                allowedEqualSwingBars, this.breakoutTolerance);
        this.volumeProfile = new WyckoffVolumeProfile(series, volumeShortWindow, volumeLongWindow, this.climaxThreshold,
                this.dryUpThreshold);
        this.eventDetector = new WyckoffEventDetector(series, this.retestTolerance);
        this.unstableBars = Math.max(precedingSwingBars + followingSwingBars, Math.max(0, volumeLongWindow - 1));
        this.structureSnapshots = new ConcurrentHashMap<>();
        this.lastTransitionIndices = new ConcurrentHashMap<>();
    }

    /**
     * Creates a builder for the indicator.
     *
     * @param series underlying bar series
     * @return configured builder
     * @since 0.22.2
     */
    public static Builder builder(BarSeries series) {
        return new Builder(series);
    }

    @Override
    protected WyckoffPhase calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return WyckoffPhase.UNKNOWN;
        }
        final WyckoffStructureTracker.StructureSnapshot structure = structureTracker.snapshot(index);
        final WyckoffVolumeProfile.VolumeSnapshot volume = volumeProfile.snapshot(index);
        structureSnapshots.put(index, structure);

        final WyckoffPhase previous = index > getBarSeries().getBeginIndex() ? getValue(index - 1)
                : WyckoffPhase.UNKNOWN;
        final WyckoffPhase previousDecayed = previous.withConfidence(Math.max(0.0, previous.confidence() * 0.95));
        final EnumSet<WyckoffEvent> events = eventDetector.detect(index, structure, volume, previous);
        final WyckoffPhase next = transition(previousDecayed, events);

        final int previousTransitionIndex = index > getBarSeries().getBeginIndex()
                ? getLastPhaseTransitionIndex(index - 1)
                : -1;
        final int currentTransitionIndex = hasPhaseChanged(previous, next) ? index : previousTransitionIndex;
        lastTransitionIndices.put(index, currentTransitionIndex);
        return events.isEmpty() ? next : next.withLatestEventIndex(index);
    }

    private boolean hasPhaseChanged(WyckoffPhase previous, WyckoffPhase next) {
        return previous.cycleType() != next.cycleType() || previous.phaseType() != next.phaseType();
    }

    private WyckoffPhase transition(WyckoffPhase previous, EnumSet<WyckoffEvent> events) {
        WyckoffCycleType cycle = previous.cycleType();
        WyckoffPhaseType phase = previous.phaseType();
        double confidence = previous.confidence();

        if (events.contains(WyckoffEvent.SELLING_CLIMAX)) {
            cycle = WyckoffCycleType.ACCUMULATION;
            phase = WyckoffPhaseType.PHASE_A;
            confidence = Math.max(confidence, 0.4);
        }
        if (events.contains(WyckoffEvent.AUTOMATIC_RALLY) || events.contains(WyckoffEvent.SECONDARY_TEST)) {
            if (cycle == WyckoffCycleType.ACCUMULATION && phase.ordinal() <= WyckoffPhaseType.PHASE_B.ordinal()) {
                phase = WyckoffPhaseType.PHASE_B;
                confidence = Math.max(confidence, 0.55);
            }
        }
        if (events.contains(WyckoffEvent.SPRING) || events.contains(WyckoffEvent.LAST_POINT_OF_SUPPORT)) {
            if (cycle == WyckoffCycleType.ACCUMULATION && phase.ordinal() <= WyckoffPhaseType.PHASE_C.ordinal()) {
                phase = WyckoffPhaseType.PHASE_C;
                confidence = Math.max(confidence, 0.7);
            }
        }
        if (events.contains(WyckoffEvent.SIGN_OF_STRENGTH)) {
            if (cycle == WyckoffCycleType.ACCUMULATION && phase.ordinal() <= WyckoffPhaseType.PHASE_D.ordinal()) {
                phase = WyckoffPhaseType.PHASE_D;
                confidence = Math.max(confidence, 0.85);
            }
        }
        if (events.contains(WyckoffEvent.RANGE_BREAKOUT)) {
            if (cycle == WyckoffCycleType.ACCUMULATION && phase.ordinal() <= WyckoffPhaseType.PHASE_E.ordinal()) {
                phase = WyckoffPhaseType.PHASE_E;
                confidence = Math.max(confidence, 0.95);
            }
        }
        if (events.contains(WyckoffEvent.BUYING_CLIMAX)) {
            cycle = WyckoffCycleType.DISTRIBUTION;
            phase = WyckoffPhaseType.PHASE_A;
            confidence = Math.max(confidence, 0.4);
        }
        if (events.contains(WyckoffEvent.UPTHRUST) || events.contains(WyckoffEvent.SECONDARY_TEST)) {
            if (cycle == WyckoffCycleType.DISTRIBUTION && phase.ordinal() <= WyckoffPhaseType.PHASE_B.ordinal()) {
                phase = WyckoffPhaseType.PHASE_B;
                confidence = Math.max(confidence, 0.55);
            }
        }
        if (events.contains(WyckoffEvent.UPTHRUST_AFTER_DISTRIBUTION)
                || events.contains(WyckoffEvent.LAST_POINT_OF_SUPPLY)) {
            if (cycle == WyckoffCycleType.DISTRIBUTION && phase.ordinal() <= WyckoffPhaseType.PHASE_C.ordinal()) {
                phase = WyckoffPhaseType.PHASE_C;
                confidence = Math.max(confidence, 0.7);
            }
        }
        if (events.contains(WyckoffEvent.RANGE_BREAKDOWN)) {
            if (cycle == WyckoffCycleType.DISTRIBUTION && phase.ordinal() <= WyckoffPhaseType.PHASE_E.ordinal()) {
                phase = WyckoffPhaseType.PHASE_E;
                confidence = Math.max(confidence, 0.95);
            }
        }
        if (events.contains(WyckoffEvent.LAST_POINT_OF_SUPPLY) && cycle == WyckoffCycleType.DISTRIBUTION
                && phase.ordinal() <= WyckoffPhaseType.PHASE_D.ordinal()) {
            phase = WyckoffPhaseType.PHASE_D;
            confidence = Math.max(confidence, 0.85);
        }
        if (confidence < 0.15) {
            cycle = WyckoffCycleType.UNKNOWN;
            phase = WyckoffPhaseType.PHASE_A;
        }
        return new WyckoffPhase(cycle, phase, Math.min(confidence, 1.0), previous.latestEventIndex());
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * Returns the current trading-range high for {@code index}.
     *
     * @param index bar index
     * @return trading-range high or {@code NaN}
     * @since 0.22.2
     */
    public Num getTradingRangeHigh(int index) {
        return ensureStructureSnapshot(index).rangeHigh();
    }

    /**
     * Returns the current trading-range low for {@code index}.
     *
     * @param index bar index
     * @return trading-range low or {@code NaN}
     * @since 0.22.2
     */
    public Num getTradingRangeLow(int index) {
        return ensureStructureSnapshot(index).rangeLow();
    }

    /**
     * Returns the index of the last phase transition observed up to {@code index}.
     *
     * @param index bar index
     * @return index of the last phase transition or {@code -1} if none were
     *         recorded yet
     * @since 0.22.2
     */
    public int getLastPhaseTransitionIndex(int index) {
        final Integer transition = lastTransitionIndices.get(index);
        if (transition != null) {
            return transition;
        }
        if (index <= getBarSeries().getBeginIndex()) {
            return -1;
        }
        return getLastPhaseTransitionIndex(index - 1);
    }

    private WyckoffStructureTracker.StructureSnapshot ensureStructureSnapshot(int index) {
        WyckoffStructureTracker.StructureSnapshot snapshot = structureSnapshots.get(index);
        if (snapshot == null) {
            snapshot = structureTracker.snapshot(index);
            structureSnapshots.put(index, snapshot);
        }
        return snapshot;
    }

    /**
     * Fluent builder for {@link WyckoffPhaseIndicator}.
     *
     * @since 0.22.2
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
         * @param precedingSwingBars bars preceding a swing point
         * @param followingSwingBars bars following a swing point
         * @param allowedEqualBars   number of equal bars allowed in swing detection
         * @return builder
         * @since 0.22.2
         */
        public Builder withSwingConfiguration(int precedingSwingBars, int followingSwingBars, int allowedEqualBars) {
            this.precedingSwingBars = precedingSwingBars;
            this.followingSwingBars = followingSwingBars;
            this.allowedEqualSwingBars = allowedEqualBars;
            return this;
        }

        /**
         * Sets volume window lengths.
         *
         * @param shortWindow short volume SMA window
         * @param longWindow  long volume SMA window
         * @return builder
         * @since 0.22.2
         */
        public Builder withVolumeWindows(int shortWindow, int longWindow) {
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
         * @since 0.22.2
         */
        public Builder withTolerances(Num breakoutTolerance, Num retestTolerance) {
            this.breakoutTolerance = Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
            this.retestTolerance = Objects.requireNonNull(retestTolerance, "retestTolerance");
            return this;
        }

        /**
         * Sets volume climax and dry-up thresholds.
         *
         * @param climaxThreshold ratio above which volume is treated as a climax
         * @param dryUpThreshold  ratio below which volume is treated as drying up
         * @return builder
         * @since 0.22.2
         */
        public Builder withVolumeThresholds(Num climaxThreshold, Num dryUpThreshold) {
            this.climaxThreshold = Objects.requireNonNull(climaxThreshold, "climaxThreshold");
            this.dryUpThreshold = Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
            return this;
        }

        /**
         * Builds the configured indicator.
         *
         * @return WyckoffPhaseIndicator instance
         * @since 0.22.2
         */
        public WyckoffPhaseIndicator build() {
            return new WyckoffPhaseIndicator(series, precedingSwingBars, followingSwingBars, allowedEqualSwingBars,
                    volumeShortWindow, volumeLongWindow, breakoutTolerance, retestTolerance, climaxThreshold,
                    dryUpThreshold);
        }
    }
}
