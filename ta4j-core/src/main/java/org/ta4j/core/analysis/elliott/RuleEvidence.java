/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured result of one relationship rule evaluated against one candidate.
 *
 * @param ruleId       stable rule identifier
 * @param state        explicit evidence state
 * @param score        optional normalized evidence score in {@code [0, 1]};
 *                     empty for structural pass/fail states and for
 *                     {@code UNAVAILABLE}/{@code PENDING}
 * @param observations raw measured values the decision was derived from
 * @param explanation  deterministic human-readable rationale
 */
record RuleEvidence(String ruleId, EvidenceState state, Optional<Double> score, List<String> observations,
        String explanation) {

    private static final double EPSILON = 1e-9;

    RuleEvidence {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(state, "state");
        score = score == null ? Optional.empty() : score;
        score.ifPresent(value -> {
            if (value < -EPSILON || value > 1 + EPSILON) {
                throw new IllegalArgumentException("score must be within [0, 1]: " + value);
            }
        });
        observations = observations == null ? List.of() : List.copyOf(observations);
        Objects.requireNonNull(explanation, "explanation");
    }

    static RuleEvidence pass(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PASS, Optional.empty(), observations, explanation);
    }

    static RuleEvidence fail(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.FAIL, Optional.empty(), observations, explanation);
    }

    static RuleEvidence pending(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PENDING, Optional.empty(), observations, explanation);
    }

    static RuleEvidence unavailable(final String ruleId, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.UNAVAILABLE, Optional.empty(), List.of(), explanation);
    }

    static RuleEvidence notApplicable(final String ruleId, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.NOT_APPLICABLE, Optional.empty(), List.of(), explanation);
    }

    static RuleEvidence scored(final String ruleId, final double score, final List<String> observations,
            final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PASS, Optional.of(score), observations, explanation);
    }
}
