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
 * @param direction            the sole candidate's direction for
 *                             {@link TopologyStatus#COMPLETE}, the forming
 *                             suffix's direction for {@link TopologyStatus#FORMING},
 *                             otherwise {@code null}
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
        validateCandidateCount(status, candidates.size());
        validateDirection(status, direction,
                status == TopologyStatus.COMPLETE ? candidates.get(0).direction() : null);
        if (status == TopologyStatus.FORMING) {
            if (formingStartBarIndex < 0 || formingEndBarIndex < formingStartBarIndex) {
                throw new IllegalArgumentException("FORMING requires an ordered pivot range");
            }
        } else if (formingStartBarIndex != -1 || formingEndBarIndex != -1) {
            throw new IllegalArgumentException("only FORMING may carry a pivot range");
        }
    }

    static void validateCandidateCount(final TopologyStatus status, final int count) {
        if (count > MAX_CANDIDATES) {
            throw new IllegalArgumentException("candidate bound exceeded: " + count);
        }
        if (status == TopologyStatus.COMPLETE && count != 1) {
            throw new IllegalArgumentException("COMPLETE requires exactly one candidate");
        }
        if (status == TopologyStatus.AMBIGUOUS && count < 2) {
            throw new IllegalArgumentException("AMBIGUOUS requires at least two tied candidates");
        }
        if (status != TopologyStatus.COMPLETE && status != TopologyStatus.AMBIGUOUS && count != 0) {
            throw new IllegalArgumentException(status + " cannot carry candidates");
        }
    }

    static void validateDirection(final TopologyStatus status, final WaveDirection direction,
            final WaveDirection candidateDirection) {
        if (status == TopologyStatus.COMPLETE) {
            if (direction == null || direction != candidateDirection) {
                throw new IllegalArgumentException("COMPLETE direction must match its candidate");
            }
        } else if (status == TopologyStatus.FORMING) {
            Objects.requireNonNull(direction, "FORMING direction");
        } else if (direction != null) {
            throw new IllegalArgumentException(status + " cannot carry a direction");
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
