/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;

/**
 * Computes candidate objective score from global and fold metric maps.
 *
 * @since 0.22.4
 */
@FunctionalInterface
public interface WalkForwardObjective {

    /**
     * Scores one candidate.
     *
     * @param globalMetrics global metric values for primary horizon
     * @param foldMetrics   fold metric values for primary horizon
     * @return objective score
     * @since 0.22.4
     */
    Score evaluate(Map<String, Double> globalMetrics, Map<String, Map<String, Double>> foldMetrics);

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
    record Score(double totalScore, double weightedScore, double foldVariance, boolean guardrailPassed,
            List<String> violations, Map<String, Double> metricValues) {
    }
}
