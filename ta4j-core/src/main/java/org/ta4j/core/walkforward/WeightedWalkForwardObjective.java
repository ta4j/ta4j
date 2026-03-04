/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Weighted multi-metric objective with guardrails and fold-variance penalty.
 *
 * @since 0.22.4
 */
public final class WeightedWalkForwardObjective implements WalkForwardObjective {

    private final Map<String, Double> metricWeights;
    private final Map<String, Double> minimumMetricValues;
    private final Map<String, Double> maximumMetricValues;
    private final double foldVariancePenalty;

    /**
     * Creates a weighted objective.
     *
     * @param metricWeights       metric weights used for objective aggregation
     * @param minimumMetricValues minimum guardrails (metric {@code >= min})
     * @param maximumMetricValues maximum guardrails (metric {@code <= max})
     * @param foldVariancePenalty fold objective variance penalty multiplier
     * @since 0.22.4
     */
    public WeightedWalkForwardObjective(Map<String, Double> metricWeights, Map<String, Double> minimumMetricValues,
            Map<String, Double> maximumMetricValues, double foldVariancePenalty) {
        this.metricWeights = Map.copyOf(Objects.requireNonNull(metricWeights, "metricWeights"));
        this.minimumMetricValues = Map.copyOf(minimumMetricValues == null ? Map.of() : minimumMetricValues);
        this.maximumMetricValues = Map.copyOf(maximumMetricValues == null ? Map.of() : maximumMetricValues);
        if (foldVariancePenalty < 0.0) {
            throw new IllegalArgumentException("foldVariancePenalty must be >= 0.0");
        }
        this.foldVariancePenalty = foldVariancePenalty;
    }

    @Override
    public WalkForwardObjective.Score evaluate(Map<String, Double> globalMetrics,
            Map<String, Map<String, Double>> foldMetrics) {
        Objects.requireNonNull(globalMetrics, "globalMetrics");
        Objects.requireNonNull(foldMetrics, "foldMetrics");

        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, Double> entry : minimumMetricValues.entrySet()) {
            double actual = globalMetrics.getOrDefault(entry.getKey(), Double.NaN);
            if (Double.isNaN(actual) || actual < entry.getValue()) {
                violations.add(entry.getKey() + " < " + entry.getValue());
            }
        }
        for (Map.Entry<String, Double> entry : maximumMetricValues.entrySet()) {
            double actual = globalMetrics.getOrDefault(entry.getKey(), Double.NaN);
            if (Double.isNaN(actual) || actual > entry.getValue()) {
                violations.add(entry.getKey() + " > " + entry.getValue());
            }
        }

        double weightedScore = weightedSum(globalMetrics);

        List<Double> foldScores = new ArrayList<>();
        for (Map<String, Double> foldMetricMap : foldMetrics.values()) {
            foldScores.add(weightedSum(foldMetricMap));
        }
        double variance = variance(foldScores);
        double total = weightedScore - (foldVariancePenalty * variance);

        boolean guardrailPassed = violations.isEmpty();
        if (!guardrailPassed) {
            total = Double.NEGATIVE_INFINITY;
        }

        return new WalkForwardObjective.Score(total, weightedScore, variance, guardrailPassed, List.copyOf(violations),
                Map.copyOf(globalMetrics));
    }

    private double weightedSum(Map<String, Double> values) {
        double sum = 0.0;
        for (Map.Entry<String, Double> weightEntry : metricWeights.entrySet()) {
            double metricValue = values.getOrDefault(weightEntry.getKey(), Double.NaN);
            if (Double.isNaN(metricValue)) {
                continue;
            }
            sum += weightEntry.getValue() * metricValue;
        }
        return sum;
    }

    private static double variance(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.size();

        double squared = 0.0;
        for (double value : values) {
            double delta = value - mean;
            squared += delta * delta;
        }
        return squared / values.size();
    }
}
