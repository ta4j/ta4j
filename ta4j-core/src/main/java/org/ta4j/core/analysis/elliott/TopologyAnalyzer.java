/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, deterministic wave-grammar matcher over contiguous confirmed
 * pivots.
 *
 * <p>
 * The analyzer enumerates contiguous pivot windows without skipping pivots,
 * validates them topology-first for a grammar/direction pair, and reports an
 * explicit {@link TopologyStatus}. Ties between materially equal candidates
 * stay ambiguous; nothing forces a count.
 *
 * <p>
 * Structural invalidation policy: the newest complete candidate is invalidated
 * when a later confirmed pivot breaches its origin extreme (a bullish
 * candidate dies below its origin low, a bearish candidate above its origin
 * high).
 *
 * <p>
 * Bounds: only the trailing {@code maxHistoryPivots} confirmed pivots are
 * considered, and at most {@code 64} tied candidates are retained (the most
 * recent ones, in ascending start order), so rolling evaluation cannot grow
 * without limit.
 */
final class TopologyAnalyzer {

    private static final int DEFAULT_MAX_HISTORY_PIVOTS = 200;
    private static final double EPSILON = 1e-12;

    private final int maxHistoryPivots;

    TopologyAnalyzer() {
        this(DEFAULT_MAX_HISTORY_PIVOTS);
    }

    TopologyAnalyzer(final int maxHistoryPivots) {
        if (maxHistoryPivots < 2) {
            throw new IllegalArgumentException("maxHistoryPivots must allow at least two pivots");
        }
        this.maxHistoryPivots = maxHistoryPivots;
    }

    /**
     * Evaluates one grammar against the causal pivot view at an as-of index.
     *
     * @param grammar   grammar to evaluate
     * @param history   confirmed-pivot history
     * @param asOfIndex observation bar index
     * @return explicit topology outcome
     */
    TopologyAnalysis analyze(final TopologyGrammar grammar, final PivotHistory history, final int asOfIndex) {
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(history, "history");
        return analyze(grammar, history.asOf(asOfIndex));
    }

    /**
     * Evaluates one grammar against an explicit ordered confirmed-pivot list.
     *
     * @param grammar   grammar to evaluate
     * @param confirmed ordered confirmed pivots
     * @return explicit topology outcome
     */
    TopologyAnalysis analyze(final TopologyGrammar grammar, final List<ConfirmedPivot> confirmed) {
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(confirmed, "confirmed");
        final int windowStart = Math.max(0, confirmed.size() - maxHistoryPivots);
        final List<ConfirmedPivot> window = confirmed.subList(windowStart, confirmed.size());
        if (window.size() < 2) {
            return TopologyAnalysis.insufficientHistory(
                    "need at least two confirmed pivots, got " + window.size());
        }

        final List<TopologyCandidate> live = new ArrayList<>();
        final List<TopologyCandidate> breached = new ArrayList<>();
        for (final WaveDirection direction : WaveDirection.values()) {
            for (int start = 0; start + grammar.requiredPivots() <= window.size(); start++) {
                final TopologyCandidate candidate = buildCandidate(grammar, direction, window, start);
                if (candidate == null || !matchesShape(candidate)) {
                    continue;
                }
                if (isBreachedByAnyLaterPivot(candidate, window)) {
                    breached.add(candidate);
                } else {
                    live.add(candidate);
                }
            }
        }
        // Report the kill moment: when the newest confirmed pivot breaches
        // the origin of the most recently completed prior candidate, that
        // hypothesis died even if fresh overlapping mirrors may form later.
        final ConfirmedPivot newestPivot = window.get(window.size() - 1);
        TopologyCandidate mostRecentPrior = null;
        for (final TopologyCandidate candidate : live) {
            if (candidate.endBarIndex() < newestPivot.pivotIndex()
                    && (mostRecentPrior == null || candidate.endBarIndex() > mostRecentPrior.endBarIndex())) {
                mostRecentPrior = candidate;
            }
        }
        for (final TopologyCandidate candidate : breached) {
            if (candidate.endBarIndex() < newestPivot.pivotIndex()
                    && (mostRecentPrior == null || candidate.endBarIndex() > mostRecentPrior.endBarIndex())) {
                mostRecentPrior = candidate;
            }
        }
        if (mostRecentPrior != null && isOriginBreach(mostRecentPrior, newestPivot)) {
            return TopologyAnalysis.invalidated("newest confirmed pivot breached the origin of the most recent "
                    + grammar + " candidate spanning bars " + mostRecentPrior.startBarIndex() + "-"
                    + mostRecentPrior.endBarIndex());
        }
        if (live.size() == 1) {
            return new TopologyAnalysis(TopologyStatus.COMPLETE, live.get(0).direction(), live,
                    "single " + grammar + " candidate spans bars " + live.get(0).startBarIndex() + "-"
                            + live.get(0).endBarIndex());
        }
        if (live.size() > 1) {
            final List<TopologyCandidate> bounded = boundedMostRecent(live);
            return new TopologyAnalysis(TopologyStatus.AMBIGUOUS, null, bounded,
                    bounded.size() + " of " + live.size() + " tied " + grammar + " candidates remain");
        }

        for (final WaveDirection direction : WaveDirection.values()) {
            if (matchesPartialShape(grammar, direction, window)) {
                return TopologyAnalysis.forming(direction, "partial " + grammar + " prefix present in "
                        + direction + " orientation");
            }
        }
        if (window.size() < grammar.requiredPivots()) {
            return TopologyAnalysis.noMatch(
                    "no " + grammar + " prefix or complete candidate in " + window.size() + " pivots");
        }
        return TopologyAnalysis.noMatch(
                "no " + grammar + " candidate in " + window.size() + " confirmed pivots");
    }



    private boolean isBreachedByAnyLaterPivot(final TopologyCandidate candidate,
            final List<ConfirmedPivot> window) {
        for (final ConfirmedPivot pivot : window) {
            if (pivot.pivotIndex() > candidate.endBarIndex() && isOriginBreach(candidate, pivot)) {
                return true;
            }
        }
        return false;
    }
    private boolean isOriginBreach(final TopologyCandidate candidate, final ConfirmedPivot pivot) {
        final double originPrice = candidate.legStartPrice(0);
        return switch (candidate.direction()) {
            case BULLISH -> pivot.type() == org.ta4j.core.analysis.elliott.swing.SwingPivotType.LOW
                    && pivot.price().doubleValue() < originPrice - EPSILON;
            case BEARISH -> pivot.type() == org.ta4j.core.analysis.elliott.swing.SwingPivotType.HIGH
                    && pivot.price().doubleValue() > originPrice + EPSILON;
        };
    }

    private List<TopologyCandidate> boundedMostRecent(final List<TopologyCandidate> complete) {
        if (complete.size() <= 64) {
            return complete;
        }
        return complete.subList(complete.size() - 64, complete.size());
    }

    private TopologyCandidate buildCandidate(final TopologyGrammar grammar, final WaveDirection direction,
            final List<ConfirmedPivot> window, final int start) {
        final List<ConfirmedPivot> pivots = window.subList(start, start + grammar.requiredPivots());
        try {
            return new TopologyCandidate(grammar, direction, pivots);
        } catch (final IllegalArgumentException nonAlternatingWindow) {
            return null;
        }
    }

    private boolean matchesShape(final TopologyCandidate candidate) {
        for (int leg = 0; leg < candidate.pivots().size() - 1; leg++) {
            final double size = candidate.legSize(leg);
            final boolean positiveExpected = expectedLegPositive(candidate.grammar(), leg);
            final boolean positive = size > EPSILON;
            final boolean negative = size < -EPSILON;
            if (positiveExpected ? !positive : !negative) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPartialShape(final TopologyGrammar grammar, final WaveDirection direction,
            final List<ConfirmedPivot> window) {
        final int legs = window.size() - 1;
        if (legs < 1 || legs >= grammar.legCount()) {
            return false;
        }
        for (int leg = 0; leg < legs; leg++) {
            final double startPrice = window.get(leg).price().doubleValue();
            final double endPrice = window.get(leg + 1).price().doubleValue();
            final double signed = direction == WaveDirection.BULLISH ? endPrice - startPrice
                    : startPrice - endPrice;
            if (expectedLegPositive(grammar, leg) ? !(signed > EPSILON) : !(signed < -EPSILON)) {
                return false;
            }
        }
        return true;
    }


    private boolean expectedLegPositive(final TopologyGrammar grammar, final int leg) {
        return switch (grammar) {
            case MOTIVE_5 -> leg % 2 == 0;
            // A corrective block moves against the declared trend: its
            // middle leg is the only positive one in trend-direction space.
            case CORRECTIVE_3 -> leg == 1;
            case CYCLE_5_3 -> leg % 2 == 0;
        };
    }
}
