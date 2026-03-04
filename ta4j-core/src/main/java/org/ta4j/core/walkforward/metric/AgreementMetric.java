/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.metric;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardMetric;
import org.ta4j.core.walkforward.WalkForwardObservation;

/**
 * Generic agreement ratio metric at a selected rank.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class AgreementMetric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int rank;
    private final BiPredicate<RankedPrediction<P>, O> agreementPredicate;

    /**
     * Creates an agreement metric.
     *
     * @param name               metric name
     * @param rank               prediction rank to evaluate
     * @param agreementPredicate agreement predicate for prediction/outcome pairs
     * @since 0.22.4
     */
    public AgreementMetric(String name, int rank, BiPredicate<RankedPrediction<P>, O> agreementPredicate) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        this.rank = rank;
        this.agreementPredicate = Objects.requireNonNull(agreementPredicate, "agreementPredicate");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double compute(List<WalkForwardObservation<P, O>> observations) {
        int matches = 0;
        int count = 0;
        for (WalkForwardObservation<P, O> observation : observations) {
            if (observation.prediction().rank() != rank) {
                continue;
            }
            if (agreementPredicate.test(observation.prediction(), observation.realizedOutcome())) {
                matches++;
            }
            count++;
        }
        return count == 0 ? Double.NaN : (double) matches / count;
    }
}
