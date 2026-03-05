/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

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
    public Report validate(WalkForwardRunResult<?, ?> runResult, String candidateId,
            Map<String, Num> minimumMetricValues, Map<String, Num> maximumMetricValues) {
        Objects.requireNonNull(runResult, "runResult");
        Objects.requireNonNull(candidateId, "candidateId");

        int primaryHorizon = runResult.config().primaryHorizonBars();
        Map<String, Map<String, Num>> perFold = runResult.foldMetricsForHorizon(primaryHorizon);
        Map<String, Num> holdoutMetrics = perFold.getOrDefault("holdout", Map.of());

        List<String> notes = new ArrayList<>();
        if (holdoutMetrics.isEmpty()) {
            notes.add("holdout metrics unavailable");
            return new Report(candidateId, primaryHorizon, Map.of(), false, notes);
        }

        Map<String, Num> minValues = minimumMetricValues == null ? Map.of() : minimumMetricValues;
        Map<String, Num> maxValues = maximumMetricValues == null ? Map.of() : maximumMetricValues;

        boolean passed = true;
        for (Map.Entry<String, Num> entry : minValues.entrySet()) {
            Num actual = holdoutMetrics.get(entry.getKey());
            Num minimum = normalizeThreshold(entry.getValue(), actual);
            if (Num.isNaNOrNull(actual) || Num.isNaNOrNull(minimum) || actual.isLessThan(minimum)) {
                notes.add(entry.getKey() + " < " + entry.getValue());
                passed = false;
            }
        }
        for (Map.Entry<String, Num> entry : maxValues.entrySet()) {
            Num actual = holdoutMetrics.get(entry.getKey());
            Num maximum = normalizeThreshold(entry.getValue(), actual);
            if (Num.isNaNOrNull(actual) || Num.isNaNOrNull(maximum) || actual.isGreaterThan(maximum)) {
                notes.add(entry.getKey() + " > " + entry.getValue());
                passed = false;
            }
        }

        if (passed) {
            notes.add("holdout metrics satisfied all guardrails");
        }

        return new Report(candidateId, primaryHorizon, holdoutMetrics, passed, List.copyOf(notes));
    }

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
    public record Report(String candidateId, int horizonBars, Map<String, Num> metricValues, boolean passed,
            List<String> notes) {
    }

    private static Num normalizeThreshold(Num threshold, Num actual) {
        if (Num.isNaNOrNull(threshold) || Num.isNaNOrNull(actual)) {
            return NaN.NaN;
        }
        if (actual.getNumFactory().produces(threshold)) {
            return threshold;
        }
        return actual.getNumFactory().numOf(threshold.doubleValue());
    }
}
