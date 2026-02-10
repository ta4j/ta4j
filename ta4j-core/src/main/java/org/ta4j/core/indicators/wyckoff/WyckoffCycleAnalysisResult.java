/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.num.Num;

/**
 * Result of running Wyckoff cycle analysis across one or more degrees or
 * configurations.
 *
 * <p>
 * Produced by {@link WyckoffCycleAnalysis}. The result contains one analysis
 * snapshot per requested degree.
 *
 * @param baseDegreeOffset degree offset that drives recommended access patterns
 *                         (typically {@code 0})
 * @param analyses         per-degree analysis snapshots (includes base)
 * @param notes            human-readable notes (skipped degrees, etc.)
 * @since 0.22.2
 */
public record WyckoffCycleAnalysisResult(int baseDegreeOffset, List<DegreeAnalysis> analyses, List<String> notes) {

    public WyckoffCycleAnalysisResult {
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * Returns the analysis snapshot for a given degree offset.
     *
     * @param degreeOffset degree offset to look up
     * @return matching analysis snapshot, if present
     * @since 0.22.2
     */
    public Optional<DegreeAnalysis> analysisFor(final int degreeOffset) {
        for (final DegreeAnalysis analysis : analyses) {
            if (analysis != null && analysis.degreeOffset() == degreeOffset) {
                return Optional.of(analysis);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the base-degree analysis snapshot, if present
     * @since 0.22.2
     */
    public Optional<DegreeAnalysis> baseAnalysis() {
        return analysisFor(baseDegreeOffset);
    }

    /**
     * Snapshot of a single-degree analysis run, including series metadata.
     *
     * @param degreeOffset  analyzed degree offset
     * @param barCount      number of bars used for analysis
     * @param barDuration   duration of bars used for analysis
     * @param configuration configuration applied for this degree
     * @param cycleSnapshot computed cycle snapshot
     * @since 0.22.2
     */
    public record DegreeAnalysis(int degreeOffset, int barCount, Duration barDuration,
            DegreeConfiguration configuration, CycleSnapshot cycleSnapshot) {

        public DegreeAnalysis {
            Objects.requireNonNull(barDuration, "barDuration");
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(cycleSnapshot, "cycleSnapshot");
            if (barCount < 0) {
                throw new IllegalArgumentException("barCount must be >= 0");
            }
        }
    }

    /**
     * Normalized configuration used for a single-degree cycle analysis.
     *
     * @param precedingSwingBars    bars preceding a swing point
     * @param followingSwingBars    bars following a swing point
     * @param allowedEqualSwingBars number of equal bars allowed in swing detection
     * @param volumeShortWindow     short volume SMA window
     * @param volumeLongWindow      long volume SMA window
     * @param breakoutTolerance     breakout tolerance applied to range bounds
     * @param retestTolerance       retest tolerance applied to range bounds
     * @param climaxThreshold       ratio above which volume is treated as a climax
     * @param dryUpThreshold        ratio below which volume is treated as drying up
     * @since 0.22.2
     */
    public record DegreeConfiguration(int precedingSwingBars, int followingSwingBars, int allowedEqualSwingBars,
            int volumeShortWindow, int volumeLongWindow, Num breakoutTolerance, Num retestTolerance,
            Num climaxThreshold, Num dryUpThreshold) {

        public DegreeConfiguration {
            Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
            Objects.requireNonNull(retestTolerance, "retestTolerance");
            Objects.requireNonNull(climaxThreshold, "climaxThreshold");
            Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
        }
    }

    /**
     * Summary of phase transitions and the inferred latest state.
     *
     * @param startIndex          first analyzed index (after warmup)
     * @param endIndex            last analyzed index
     * @param unstableBars        warmup bars for the configured indicators
     * @param finalPhase          inferred phase at {@code endIndex}
     * @param tradingRangeLow     trading-range low at {@code endIndex}
     * @param tradingRangeHigh    trading-range high at {@code endIndex}
     * @param lastTransitionIndex last phase transition index at {@code endIndex}
     * @param transitions         list of detected phase transitions
     * @since 0.22.2
     */
    public record CycleSnapshot(int startIndex, int endIndex, int unstableBars, WyckoffPhase finalPhase,
            Num tradingRangeLow, Num tradingRangeHigh, int lastTransitionIndex, List<PhaseTransition> transitions) {

        public CycleSnapshot {
            Objects.requireNonNull(finalPhase, "finalPhase");
            Objects.requireNonNull(tradingRangeLow, "tradingRangeLow");
            Objects.requireNonNull(tradingRangeHigh, "tradingRangeHigh");
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }
    }

    /**
     * Captures a single phase transition detected during analysis.
     *
     * @param index            bar index where the transition was observed
     * @param phase            inferred phase at {@code index}
     * @param tradingRangeLow  trading-range low at {@code index}
     * @param tradingRangeHigh trading-range high at {@code index}
     * @since 0.22.2
     */
    public record PhaseTransition(int index, WyckoffPhase phase, Num tradingRangeLow, Num tradingRangeHigh) {

        public PhaseTransition {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(tradingRangeLow, "tradingRangeLow");
            Objects.requireNonNull(tradingRangeHigh, "tradingRangeHigh");
        }
    }
}
