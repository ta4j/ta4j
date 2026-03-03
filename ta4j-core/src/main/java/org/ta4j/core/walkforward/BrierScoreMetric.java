/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Brier score metric for probabilistic predictions.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class BrierScoreMetric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int rank;
    private final Function<O, Double> actualProbabilityExtractor;

    /**
     * Creates a Brier score metric for a selected prediction rank.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param actualProbabilityExtractor outcome to probability mapper in
     *                                   {@code [0,1]}
     * @since 0.22.4
     */
    public BrierScoreMetric(String name, int rank, Function<O, Double> actualProbabilityExtractor) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        this.rank = rank;
        this.actualProbabilityExtractor = Objects.requireNonNull(actualProbabilityExtractor,
                "actualProbabilityExtractor");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double compute(List<WalkForwardObservation<P, O>> observations) {
        double sum = 0.0;
        int count = 0;
        for (WalkForwardObservation<P, O> observation : observations) {
            if (observation.prediction().rank() != rank) {
                continue;
            }
            double predicted = WalkForwardMetricSupport.clamp01(observation.prediction().probability());
            double actual = WalkForwardMetricSupport
                    .clamp01(actualProbabilityExtractor.apply(observation.realizedOutcome()));
            double error = predicted - actual;
            sum += error * error;
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }
}
