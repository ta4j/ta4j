/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
    Score evaluate(Map<String, Num> globalMetrics, Map<String, Map<String, Num>> foldMetrics);

    /**
     * Creates a weighted multi-metric objective with guardrails and fold-variance
     * penalty.
     *
     * @param metricWeights       metric weights used for objective aggregation
     * @param minimumMetricValues minimum guardrails (metric {@code >= min})
     * @param maximumMetricValues maximum guardrails (metric {@code <= max})
     * @param foldVariancePenalty fold objective variance penalty multiplier
     * @return weighted objective
     * @since 0.22.4
     */
    static WalkForwardObjective weighted(Map<String, Num> metricWeights, Map<String, Num> minimumMetricValues,
            Map<String, Num> maximumMetricValues, Num foldVariancePenalty) {
        Map<String, Num> immutableMetricWeights = Map.copyOf(Objects.requireNonNull(metricWeights, "metricWeights"));
        Map<String, Num> immutableMinimumMetricValues = Map
                .copyOf(minimumMetricValues == null ? Map.of() : minimumMetricValues);
        Map<String, Num> immutableMaximumMetricValues = Map
                .copyOf(maximumMetricValues == null ? Map.of() : maximumMetricValues);
        Num immutableFoldVariancePenalty = Objects.requireNonNull(foldVariancePenalty, "foldVariancePenalty");
        if (Num.isNaNOrNull(immutableFoldVariancePenalty) || immutableFoldVariancePenalty.isNegative()) {
            throw new IllegalArgumentException("foldVariancePenalty must be finite and >= 0.0");
        }

        return (globalMetrics, foldMetrics) -> {
            Objects.requireNonNull(globalMetrics, "globalMetrics");
            Objects.requireNonNull(foldMetrics, "foldMetrics");
            NumFactory factory = resolveFactory(globalMetrics, foldMetrics);

            List<String> violations = new ArrayList<>();

            for (Map.Entry<String, Num> entry : immutableMinimumMetricValues.entrySet()) {
                Num actual = normalizeMetric(globalMetrics.get(entry.getKey()), factory);
                Num minimum = normalizeMetric(entry.getValue(), factory);
                if (Num.isNaNOrNull(actual) || actual.isLessThan(minimum)) {
                    violations.add(entry.getKey() + " < " + entry.getValue());
                }
            }
            for (Map.Entry<String, Num> entry : immutableMaximumMetricValues.entrySet()) {
                Num actual = normalizeMetric(globalMetrics.get(entry.getKey()), factory);
                Num maximum = normalizeMetric(entry.getValue(), factory);
                if (Num.isNaNOrNull(actual) || actual.isGreaterThan(maximum)) {
                    violations.add(entry.getKey() + " > " + entry.getValue());
                }
            }

            Num weightedScore = weightedSum(immutableMetricWeights, globalMetrics, factory);

            List<Num> foldScores = new ArrayList<>();
            for (Map<String, Num> foldMetricMap : foldMetrics.values()) {
                foldScores.add(weightedSum(immutableMetricWeights, foldMetricMap, factory));
            }
            Num variance = variance(foldScores, factory);
            Num total = weightedScore
                    .minus(normalizeMetric(immutableFoldVariancePenalty, factory).multipliedBy(variance));

            boolean guardrailPassed = violations.isEmpty();
            if (!guardrailPassed) {
                total = NaN.NaN;
            }

            return new Score(total, weightedScore, variance, guardrailPassed, List.copyOf(violations),
                    Map.copyOf(globalMetrics));
        };
    }

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
    record Score(Num totalScore, Num weightedScore, Num foldVariance, boolean guardrailPassed, List<String> violations,
            Map<String, Num> metricValues) {
    }

    private static Num weightedSum(Map<String, Num> metricWeights, Map<String, Num> values, NumFactory factory) {
        Num sum = factory.zero();
        for (Map.Entry<String, Num> weightEntry : metricWeights.entrySet()) {
            Num metricValue = normalizeMetric(values.get(weightEntry.getKey()), factory);
            if (Num.isNaNOrNull(metricValue)) {
                continue;
            }
            Num weight = normalizeMetric(weightEntry.getValue(), factory);
            if (Num.isNaNOrNull(weight)) {
                continue;
            }
            sum = sum.plus(weight.multipliedBy(metricValue));
        }
        return sum;
    }

    private static Num variance(List<Num> values, NumFactory factory) {
        if (values.isEmpty()) {
            return NaN.NaN;
        }
        Num mean = factory.zero();
        for (Num value : values) {
            mean = mean.plus(value);
        }
        mean = mean.dividedBy(factory.numOf(values.size()));

        Num squared = factory.zero();
        for (Num value : values) {
            Num delta = value.minus(mean);
            squared = squared.plus(delta.multipliedBy(delta));
        }
        return squared.dividedBy(factory.numOf(values.size()));
    }

    private static Num normalizeMetric(Num value, NumFactory factory) {
        if (value == null || value.isNaN()) {
            return NaN.NaN;
        }
        if (factory.produces(value)) {
            return value;
        }
        return factory.numOf(value.doubleValue());
    }

    private static NumFactory resolveFactory(Map<String, Num> globalMetrics,
            Map<String, Map<String, Num>> foldMetrics) {
        for (Num value : globalMetrics.values()) {
            if (!Num.isNaNOrNull(value)) {
                return value.getNumFactory();
            }
        }
        for (Map<String, Num> foldMetricMap : foldMetrics.values()) {
            for (Num value : foldMetricMap.values()) {
                if (!Num.isNaNOrNull(value)) {
                    return value.getNumFactory();
                }
            }
        }
        for (Num value : globalMetrics.values()) {
            if (value != null) {
                return value.getNumFactory();
            }
        }
        return DoubleNumFactory.getInstance();
    }
}
