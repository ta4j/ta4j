/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Map;

/**
 * Holdout validation summary for a tuned candidate.
 *
 * @param candidateId  candidate id
 * @param horizonBars  horizon used for validation
 * @param metricValues holdout metric values
 * @param passed       whether all guardrails passed
 * @param notes        validation notes and guardrail violations
 * @since 0.22.4
 */
public record WalkForwardHoldoutReport(String candidateId, int horizonBars, Map<String, Double> metricValues,
        boolean passed, List<String> notes) {
}
