/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Indicator that infers Wyckoff phases by composing structural and volume
 * detectors.
 *
 * @since 0.19
 */
public final class WyckoffPhaseIndicator extends CachedIndicator<WyckoffPhase> {

    private final WyckoffStructureTracker structureTracker;
    private final WyckoffVolumeProfile volumeProfile;
    private final WyckoffEventDetector eventDetector;
    private final int unstableBars;

    private final Map<Integer, WyckoffStructureTracker.StructureSnapshot> structureSnapshots;
    private final Map<Integer, Integer> lastTransitionIndices;

    private WyckoffPhaseIndicator(Builder builder) {
        super(builder.series);
        this.structureTracker = new WyckoffStructureTracker(builder.series, builder.precedingSwingBars,
                builder.followingSwingBars, builder.allowedEqualSwingBars, builder.breakoutTolerance);
        this.volumeProfile = new WyckoffVolumeProfile(builder.series, builder.volumeShortWindow,
                builder.volumeLongWindow, builder.climaxThreshold, builder.dryUpThreshold);
        this.eventDetector = new WyckoffEventDetector(builder.series, builder.retestTolerance);
        this.unstableBars = Math.max(builder.precedingSwingBars + builder.followingSwingBars, builder.volumeLongWindow);
        this.structureSnapshots = new HashMap<>();
        this.lastTransitionIndices = new HashMap<>();
    }

    /**
     * Creates a builder for the indicator.
     *
     * @param series underlying bar series
     * @return configured builder
     * @since 0.19
     */
    public static Builder builder(BarSeries series) {
        return new Builder(series);
    }

    @Override
    protected WyckoffPhase calculate(int index) {
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
     * @since 0.19
     */
    public Num getTradingRangeHigh(int index) {
        return ensureStructureSnapshot(index).rangeHigh();
    }

    /**
     * Returns the current trading-range low for {@code index}.
     *
     * @param index bar index
     * @return trading-range low or {@code NaN}
     * @since 0.19
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
     * @since 0.19
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
     * @since 0.19
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
         * Configures the swing-window parameters.
         *
         * @param preceding number of bars before the swing point
         * @param following number of bars after the swing point
         * @param equals    tolerated equal bars when confirming a swing
         * @return fluent builder
         * @since 0.19
         */
        public Builder withSwingConfiguration(int preceding, int following, int equals) {
            this.precedingSwingBars = preceding;
            this.followingSwingBars = following;
            this.allowedEqualSwingBars = equals;
            return this;
        }

        /**
         * Configures the volume averaging windows.
         *
         * @param shortWindow short SMA window
         * @param longWindow  long SMA window
         * @return fluent builder
         * @since 0.19
         */
        public Builder withVolumeWindows(int shortWindow, int longWindow) {
            this.volumeShortWindow = shortWindow;
            this.volumeLongWindow = longWindow;
            return this;
        }

        /**
         * Configures breakout and retest tolerances.
         *
         * @param breakout tolerance applied to breakout classification
         * @param retest   tolerance applied to retests near range bounds
         * @return fluent builder
         * @since 0.19
         */
        public Builder withTolerances(Num breakout, Num retest) {
            this.breakoutTolerance = breakout;
            this.retestTolerance = retest;
            return this;
        }

        /**
         * Configures the volume thresholds.
         *
         * @param climax ratio above which volume is considered a climax
         * @param dryUp  ratio below which volume is considered a dry-up
         * @return fluent builder
         * @since 0.19
         */
        public Builder withVolumeThresholds(Num climax, Num dryUp) {
            this.climaxThreshold = climax;
            this.dryUpThreshold = dryUp;
            return this;
        }

        /**
         * Builds the indicator.
         *
         * @return configured indicator
         * @since 0.19
         */
        public WyckoffPhaseIndicator build() {
            return new WyckoffPhaseIndicator(this);
        }
    }
}
