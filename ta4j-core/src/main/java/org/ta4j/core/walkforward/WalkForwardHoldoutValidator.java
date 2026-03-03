/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates holdout metrics for release sign-off decisions.
 *
 * @since 0.22.4
 */
public final class WalkForwardHoldoutValidator {

    /**
     * Validates holdout metrics for the run's primary horizon.
     *
     * @param runResult           run result containing fold metrics
     * @param candidateId         candidate id for reporting
     * @param minimumMetricValues minimum thresholds (metric {@code >= min})
     * @param maximumMetricValues maximum thresholds (metric {@code <= max})
     * @return holdout validation report
     * @since 0.22.4
     */
    public WalkForwardHoldoutReport validate(WalkForwardRunResult<?, ?> runResult, String candidateId,
            Map<String, Double> minimumMetricValues, Map<String, Double> maximumMetricValues) {
        Objects.requireNonNull(runResult, "runResult");
        Objects.requireNonNull(candidateId, "candidateId");

        int primaryHorizon = runResult.config().primaryHorizonBars();
        Map<String, Map<String, Double>> perFold = runResult.foldMetricsForHorizon(primaryHorizon);
        Map<String, Double> holdoutMetrics = perFold.getOrDefault("holdout", Map.of());

        List<String> notes = new ArrayList<>();
        if (holdoutMetrics.isEmpty()) {
            notes.add("holdout metrics unavailable");
            return new WalkForwardHoldoutReport(candidateId, primaryHorizon, Map.of(), false, notes);
        }

        Map<String, Double> minValues = minimumMetricValues == null ? Map.of() : minimumMetricValues;
        Map<String, Double> maxValues = maximumMetricValues == null ? Map.of() : maximumMetricValues;

        boolean passed = true;
        for (Map.Entry<String, Double> entry : minValues.entrySet()) {
            double actual = holdoutMetrics.getOrDefault(entry.getKey(), Double.NaN);
            if (Double.isNaN(actual) || actual < entry.getValue()) {
                notes.add(entry.getKey() + " < " + entry.getValue());
                passed = false;
            }
        }
        for (Map.Entry<String, Double> entry : maxValues.entrySet()) {
            double actual = holdoutMetrics.getOrDefault(entry.getKey(), Double.NaN);
            if (Double.isNaN(actual) || actual > entry.getValue()) {
                notes.add(entry.getKey() + " > " + entry.getValue());
                passed = false;
            }
        }

        if (passed) {
            notes.add("holdout metrics satisfied all guardrails");
        }

        return new WalkForwardHoldoutReport(candidateId, primaryHorizon, holdoutMetrics, passed, List.copyOf(notes));
    }
}
