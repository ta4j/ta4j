/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.Num;

/**
 * Bounded, deterministic wave-grammar matcher over contiguous confirmed pivots.
 *
 * <p>
 * The analyzer enumerates contiguous pivot windows without skipping pivots,
 * validates them topology-first for a grammar/direction pair, and reports an
 * explicit {@link TopologyStatus}. Ties between materially equal candidates
 * stay ambiguous; nothing forces a count.
 *
 * <p>
 * Structural invalidation policy: the newest complete candidate is invalidated
 * when a later confirmed pivot breaches its origin extreme -- a bullish
 * candidate dies when any later pivot's price falls below its origin low, a
 * bearish candidate when any later pivot's price rises above its origin high,
 * regardless of whether that pivot is a HIGH or a LOW.
 *
 * <p>
 * Bounds: only the trailing {@code maxHistoryPivots} confirmed pivots are
 * considered, and at most {@value #MAX_RETAINED_CANDIDATES} tied candidates are
 * retained (the most recent ones, in ascending start order), so rolling
 * evaluation cannot grow without limit.
 *
 * <p>
 * All leg and origin comparisons stay in the series' own {@link Num} domain so
 * {@code DecimalNum} precision survives every decision.
 */
final class TopologyAnalyzer {

    private static final int DEFAULT_MAX_HISTORY_PIVOTS = 200;

    private static final int MAX_RETAINED_CANDIDATES = 64;

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
        return analyze(grammar, history.asOf(asOfIndex), asOfIndex);
    }

    /**
     * Evaluates one grammar against an explicit ordered confirmed-pivot list.
     *
     * @param grammar   grammar to evaluate
     * @param confirmed ordered confirmed pivots
     * @return explicit topology outcome
     */
    TopologyAnalysis analyze(final TopologyGrammar grammar, final List<ConfirmedPivot> confirmed) {
        return analyze(grammar, confirmed, null);
    }

    TopologyAnalysis analyze(final TopologyGrammar grammar, final List<ConfirmedPivot> confirmed,
            final Integer observationIndex) {
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(confirmed, "confirmed");
        final int windowStart = Math.max(0, confirmed.size() - maxHistoryPivots);
        final List<ConfirmedPivot> window = confirmed.subList(windowStart, confirmed.size());
        if (window.size() < 2) {
            return TopologyAnalysis.insufficientHistory("need at least two confirmed pivots, got " + window.size());
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
        // Chronological order makes every downstream selection and truncation
        // deterministic and keeps "most recent" semantics honest.
        live.sort(TopologyAnalyzer::chronological);
        breached.sort(TopologyAnalyzer::chronological);
        // An explicit pivot list has no observation cursor, so preserve its
        // historical newest-pivot behavior. Causal observations instead emit an
        // invalidation only when the breach pivot first becomes confirmed.
        TopologyCandidate breachedByObservation = null;
        for (final TopologyCandidate candidate : breached) {
            final boolean breachedNow;
            if (observationIndex == null) {
                final ConfirmedPivot newestPivot = window.get(window.size() - 1);
                breachedNow = candidate.endBarIndex() < newestPivot.pivotIndex()
                        && isOriginBreach(candidate, newestPivot);
            } else {
                breachedNow = window.stream()
                        .filter(pivot -> pivot.confirmationIndex() == observationIndex)
                        .anyMatch(pivot -> pivot.pivotIndex() > candidate.endBarIndex()
                                && isOriginBreach(candidate, pivot));
            }
            if (breachedNow && (breachedByObservation == null
                    || candidate.endBarIndex() > breachedByObservation.endBarIndex())) {
                breachedByObservation = candidate;
            }
        }
        if (breachedByObservation != null) {
            final String breachSubject = observationIndex == null ? "newest confirmed pivot"
                    : "newly confirmed pivot at bar " + observationIndex;
            return TopologyAnalysis.invalidated(breachSubject + " breached the origin of the most recent "
                    + breachedByObservation.direction() + " " + grammar + " candidate spanning bars "
                    + breachedByObservation.startBarIndex() + "-" + breachedByObservation.endBarIndex());
        }
        if (live.size() == 1) {
            return new TopologyAnalysis(
                    TopologyStatus.COMPLETE, live.get(0).direction(), live, "single " + grammar
                            + " candidate spans bars " + live.get(0).startBarIndex() + "-" + live.get(0).endBarIndex(),
                    -1, -1);
        }
        if (live.size() > 1) {
            // Only candidates whose windows overlap the newest live candidate
            // can compete for the current interpretation. Older completed
            // candidates remain historical evidence, not current ambiguity.
            final TopologyCandidate newestLive = live.get(live.size() - 1);
            final List<TopologyCandidate> current = live.stream()
                    .filter(candidate -> candidate.endBarIndex() > newestLive.startBarIndex())
                    .toList();
            if (current.size() == 1) {
                return new TopologyAnalysis(TopologyStatus.COMPLETE, current.get(0).direction(), current,
                        "newest " + grammar + " candidate spans bars " + current.get(0).startBarIndex() + "-"
                                + current.get(0).endBarIndex(),
                        -1, -1);
            }
            if (current.size() > 1) {
                final List<TopologyCandidate> bounded = boundedMostRecent(current);
                return new TopologyAnalysis(TopologyStatus.AMBIGUOUS, null, bounded,
                        bounded.size() + " of " + current.size() + " tied " + grammar + " candidates remain", -1, -1);
            }
        }

        // No live complete candidate survived; the freshest partial pattern may
        // still be forming in the newest pivots. Scan trailing suffixes of every
        // allowed length so a fresh prefix can form even when the retained
        // history already exceeds the grammar length.
        final int maxSuffixPivots = Math.min(window.size(), grammar.requiredPivots() - 1);
        // A single leg fits some orientation of every kernel grammar, so
        // reporting FORMING from a two-pivot suffix would fold nearly every
        // genuine non-match into forming and drive the null no-match rate to
        // zero. Require the leading two legs pinned -- three pivots -- before
        // claiming a partial pattern; grammars that complete on three pivots
        // have no meaningful partial state left and surface only complete or
        // no-match outcomes.
        final int minSuffixPivots = Math.min(3, grammar.requiredPivots());
        if (minSuffixPivots <= maxSuffixPivots) {
            // Resolve suffix length before direction: a longer trailing
            // prefix is stronger evidence than any shorter one, so the
            // forming direction must never depend on enum iteration order.
            // At a given length, opposing orientations cancel out -- a
            // contested shape is not evidence of either direction -- and the
            // scan falls through to the next shorter suffix.
            for (int suffix = maxSuffixPivots; suffix >= minSuffixPivots; suffix--) {
                final List<ConfirmedPivot> suffixPivots = window.subList(window.size() - suffix, window.size());
                final List<WaveDirection> matching = new ArrayList<>();
                for (final WaveDirection direction : WaveDirection.values()) {
                    if (matchesPartialShape(grammar, direction, suffixPivots)) {
                        matching.add(direction);
                    }
                }
                if (matching.size() == 1) {
                    final WaveDirection direction = matching.get(0);
                    return TopologyAnalysis.forming(direction, suffixPivots.get(0).pivotIndex(),
                            suffixPivots.get(suffixPivots.size() - 1).pivotIndex(),
                            "partial " + grammar + " prefix present in " + direction + " orientation over the " + suffix
                                    + " newest pivots");
                }
            }
        }
        if (window.size() < grammar.requiredPivots()) {
            return TopologyAnalysis
                    .noMatch("no " + grammar + " prefix or complete candidate in " + window.size() + " pivots");
        }
        return TopologyAnalysis.noMatch("no " + grammar + " candidate in " + window.size() + " confirmed pivots");
    }

    private boolean isBreachedByAnyLaterPivot(final TopologyCandidate candidate, final List<ConfirmedPivot> window) {
        for (final ConfirmedPivot pivot : window) {
            if (pivot.pivotIndex() > candidate.endBarIndex() && isOriginBreach(candidate, pivot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOriginBreach(final TopologyCandidate candidate, final ConfirmedPivot pivot) {
        final Num originPrice = candidate.legStartPrice(0);
        // A price crossing of the origin is a breach whichever way the pivot
        // is labelled: a HIGH printed below a bullish origin still proves
        // price traded under the structure's root.
        return switch (candidate.direction()) {
        case BULLISH -> pivot.price().isLessThan(originPrice);
        case BEARISH -> pivot.price().isGreaterThan(originPrice);
        };
    }

    private static int chronological(final TopologyCandidate first, final TopologyCandidate second) {
        int order = Integer.compare(first.endBarIndex(), second.endBarIndex());
        order = order != 0 ? order : Integer.compare(first.startBarIndex(), second.startBarIndex());
        return order != 0 ? order : first.direction().compareTo(second.direction());
    }

    private List<TopologyCandidate> boundedMostRecent(final List<TopologyCandidate> complete) {
        if (complete.size() <= MAX_RETAINED_CANDIDATES) {
            return complete;
        }
        return List.copyOf(complete.subList(complete.size() - MAX_RETAINED_CANDIDATES, complete.size()));
    }

    private TopologyCandidate buildCandidate(final TopologyGrammar grammar, final WaveDirection direction,
            final List<ConfirmedPivot> window, final int start) {
        final List<ConfirmedPivot> pivots = window.subList(start, start + grammar.requiredPivots());
        if (!startsInExpectedDirection(grammar, direction, pivots)) {
            return null;
        }
        try {
            return new TopologyCandidate(grammar, direction, pivots);
        } catch (final IllegalArgumentException nonAlternatingWindow) {
            return null;
        }
    }

    private boolean startsInExpectedDirection(final TopologyGrammar grammar, final WaveDirection direction,
            final List<ConfirmedPivot> pivots) {
        final boolean firstLegPositive = expectedLegPositive(grammar, 0);
        final SwingPivotType expectedOrigin;
        if (direction == WaveDirection.BULLISH) {
            expectedOrigin = firstLegPositive ? SwingPivotType.LOW : SwingPivotType.HIGH;
        } else {
            expectedOrigin = firstLegPositive ? SwingPivotType.HIGH : SwingPivotType.LOW;
        }
        return pivots.get(0).type() == expectedOrigin;
    }

    private boolean matchesShape(final TopologyCandidate candidate) {
        for (int leg = 0; leg < candidate.pivots().size() - 1; leg++) {
            final Num size = candidate.legSize(leg);
            final boolean positiveExpected = expectedLegPositive(candidate.grammar(), leg);
            if (positiveExpected ? !size.isPositive() : !size.isNegative()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPartialShape(final TopologyGrammar grammar, final WaveDirection direction,
            final List<ConfirmedPivot> segment) {
        final int legs = segment.size() - 1;
        if (legs < 1 || legs >= grammar.legCount() || !startsInExpectedDirection(grammar, direction, segment)) {
            return false;
        }
        for (int leg = 0; leg < legs; leg++) {
            if (segment.get(leg).type() == segment.get(leg + 1).type()) {
                return false;
            }
            final Num startPrice = segment.get(leg).price();
            final Num endPrice = segment.get(leg + 1).price();
            final Num signed = direction == WaveDirection.BULLISH ? endPrice.minus(startPrice)
                    : startPrice.minus(endPrice);
            if (expectedLegPositive(grammar, leg) ? !signed.isPositive() : !signed.isNegative()) {
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
