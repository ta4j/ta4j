/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of one as-of topology evaluation.
 *
 * @param status      explicit outcome; never a forced count
 * @param direction   declared direction of reported candidates; {@code null}
 *                    when no candidates are reported
 * @param candidates  bounded candidate list; non-empty only for
 *                    {@link TopologyStatus#COMPLETE} and
 *                    {@link TopologyStatus#AMBIGUOUS}
 * @param explanation deterministic human-readable reason describing why the
 *                    state was reached
 */
record TopologyAnalysis(TopologyStatus status, WaveDirection direction, List<TopologyCandidate> candidates,
        String explanation) {

    private static final int MAX_CANDIDATES = 64;

    TopologyAnalysis {
        Objects.requireNonNull(status, "status");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        Objects.requireNonNull(explanation, "explanation");
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("candidate bound exceeded: " + candidates.size());
        }
        if (status == TopologyStatus.COMPLETE && candidates.size() != 1) {
            throw new IllegalArgumentException("COMPLETE requires exactly one candidate");
        }
        if (status == TopologyStatus.AMBIGUOUS && candidates.size() < 2) {
            throw new IllegalArgumentException("AMBIGUOUS requires at least two tied candidates");
        }
    }

    static TopologyAnalysis insufficientHistory(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.INSUFFICIENT_HISTORY, null, List.of(), explanation);
    }

    static TopologyAnalysis noMatch(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.NO_MATCH, null, List.of(), explanation);
    }

    static TopologyAnalysis forming(final WaveDirection direction, final String explanation) {
        return new TopologyAnalysis(TopologyStatus.FORMING, direction, List.of(), explanation);
    }

    static TopologyAnalysis invalidated(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.INVALIDATED, null, List.of(), explanation);
    }
}
