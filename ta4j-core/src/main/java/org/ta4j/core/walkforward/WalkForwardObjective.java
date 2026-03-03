/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

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
    WalkForwardObjectiveScore evaluate(Map<String, Double> globalMetrics, Map<String, Map<String, Double>> foldMetrics);
}
