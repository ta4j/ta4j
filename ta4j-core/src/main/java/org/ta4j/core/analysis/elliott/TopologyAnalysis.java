/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of one as-of topology evaluation.
 *
 * @param status               explicit outcome; never a forced count
 * @param direction            declared direction of reported candidates;
 *                             {@code null} when no candidates are reported
 * @param candidates           bounded candidate list; non-empty only for
 *                             {@link TopologyStatus#COMPLETE} and
 *                             {@link TopologyStatus#AMBIGUOUS}
 * @param explanation          deterministic human-readable reason describing
 *                             why the state was reached
 * @param formingStartBarIndex first pivot bar in the reported forming suffix,
 *                             or {@code -1} when the result is not
 *                             {@link TopologyStatus#FORMING}
 * @param formingEndBarIndex   last pivot bar in the reported forming suffix, or
 *                             {@code -1} when the result is not
 *                             {@link TopologyStatus#FORMING}
 */
record TopologyAnalysis(TopologyStatus status, WaveDirection direction, List<TopologyCandidate> candidates,
        String explanation, int formingStartBarIndex, int formingEndBarIndex) {

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
        if (status == TopologyStatus.FORMING) {
            Objects.requireNonNull(direction, "FORMING direction");
            if (formingStartBarIndex < 0 || formingEndBarIndex < formingStartBarIndex) {
                throw new IllegalArgumentException("FORMING requires an ordered pivot range");
            }
        } else if (formingStartBarIndex != -1 || formingEndBarIndex != -1) {
            throw new IllegalArgumentException("only FORMING may carry a pivot range");
        }
    }

    static TopologyAnalysis insufficientHistory(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.INSUFFICIENT_HISTORY, null, List.of(), explanation, -1, -1);
    }

    static TopologyAnalysis noMatch(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.NO_MATCH, null, List.of(), explanation, -1, -1);
    }

    static TopologyAnalysis forming(final WaveDirection direction, final int startBarIndex, final int endBarIndex,
            final String explanation) {
        return new TopologyAnalysis(TopologyStatus.FORMING, direction, List.of(), explanation, startBarIndex,
                endBarIndex);
    }

    static TopologyAnalysis invalidated(final String explanation) {
        return new TopologyAnalysis(TopologyStatus.INVALIDATED, null, List.of(), explanation, -1, -1);
    }
}
