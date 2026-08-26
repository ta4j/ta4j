/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

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
 *                     {@code UNAVAILABLE}/{@code PENDING}; values within
 *                     {@link #EPSILON} of a bound are clamped onto it
 * @param observations raw measured values the decision was derived from
 * @param explanation  deterministic human-readable rationale
 */
public record RuleEvidence(String ruleId, EvidenceState state, Optional<Double> score, List<String> observations,
        String explanation) {

    private static final double EPSILON = 1e-9;

    public RuleEvidence {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(state, "state");
        score = score == null ? Optional.empty() : score;
        if (state != EvidenceState.PASS && score.isPresent()) {
            throw new IllegalArgumentException("score is only valid for PASS evidence");
        }
        score = score.map(value -> {
            if (!Double.isFinite(value) || value < -EPSILON || value > 1 + EPSILON) {
                throw new IllegalArgumentException("score must be within [0, 1]: " + value);
            }
            // Epsilon-adjacent arithmetic noise is tolerated on entry but
            // never stored out of range: downstream report metrics reject
            // any score outside the strict [0, 1] contract.
            return Math.min(1.0d, Math.max(0.0d, value));
        });
        observations = observations == null ? List.of() : List.copyOf(observations);
        Objects.requireNonNull(explanation, "explanation");
    }

    public static RuleEvidence pass(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PASS, Optional.empty(), observations, explanation);
    }

    public static RuleEvidence fail(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.FAIL, Optional.empty(), observations, explanation);
    }

    public static RuleEvidence pending(final String ruleId, final List<String> observations, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PENDING, Optional.empty(), observations, explanation);
    }

    public static RuleEvidence unavailable(final String ruleId, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.UNAVAILABLE, Optional.empty(), List.of(), explanation);
    }

    public static RuleEvidence notApplicable(final String ruleId, final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.NOT_APPLICABLE, Optional.empty(), List.of(), explanation);
    }

    public static RuleEvidence scored(final String ruleId, final double score, final List<String> observations,
            final String explanation) {
        return new RuleEvidence(ruleId, EvidenceState.PASS, Optional.of(score), observations, explanation);
    }
}
