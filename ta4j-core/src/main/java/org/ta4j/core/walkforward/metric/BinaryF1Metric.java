/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.metric;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardMetric;
import org.ta4j.core.walkforward.WalkForwardObservation;

/**
 * Binary F1 score metric for selected prediction rank.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class BinaryF1Metric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int rank;
    private final BiPredicate<RankedPrediction<P>, O> predictedPositivePredicate;
    private final Predicate<O> actualPositivePredicate;

    /**
     * Creates a binary F1 metric.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param predictedPositivePredicate prediction-side positive predicate
     * @param actualPositivePredicate    outcome-side positive predicate
     * @since 0.22.4
     */
    public BinaryF1Metric(String name, int rank, BiPredicate<RankedPrediction<P>, O> predictedPositivePredicate,
            Predicate<O> actualPositivePredicate) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        this.rank = rank;
        this.predictedPositivePredicate = Objects.requireNonNull(predictedPositivePredicate,
                "predictedPositivePredicate");
        this.actualPositivePredicate = Objects.requireNonNull(actualPositivePredicate, "actualPositivePredicate");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double compute(List<WalkForwardObservation<P, O>> observations) {
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;

        for (WalkForwardObservation<P, O> observation : observations) {
            if (observation.prediction().rank() != rank) {
                continue;
            }
            boolean predicted = predictedPositivePredicate.test(observation.prediction(),
                    observation.realizedOutcome());
            boolean actual = actualPositivePredicate.test(observation.realizedOutcome());

            if (predicted && actual) {
                truePositive++;
            } else if (predicted) {
                falsePositive++;
            } else if (actual) {
                falseNegative++;
            }
        }

        double precisionDenominator = truePositive + falsePositive;
        double recallDenominator = truePositive + falseNegative;
        if (precisionDenominator == 0.0 || recallDenominator == 0.0) {
            return 0.0;
        }
        double precision = truePositive / precisionDenominator;
        double recall = truePositive / recallDenominator;
        double denominator = precision + recall;
        return denominator == 0.0 ? 0.0 : (2.0 * precision * recall) / denominator;
    }
}
