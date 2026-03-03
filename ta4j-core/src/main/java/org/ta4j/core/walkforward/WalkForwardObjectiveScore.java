/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;

/**
 * Objective evaluation result for one candidate.
 *
 * @param totalScore      final objective score after penalties
 * @param weightedScore   weighted metric sum before penalties
 * @param foldVariance    fold-level objective variance
 * @param guardrailPassed whether all guardrails passed
 * @param violations      guardrail violations
 * @param metricValues    metric values used to score
 * @since 0.22.4
 */
public record WalkForwardObjectiveScore(double totalScore, double weightedScore, double foldVariance,
        boolean guardrailPassed, List<String> violations, Map<String, Double> metricValues) {
}
