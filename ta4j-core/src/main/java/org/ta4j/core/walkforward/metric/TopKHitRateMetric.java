/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.metric;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardMetric;
import org.ta4j.core.walkforward.WalkForwardObservation;

/**
 * Top-k hit-rate metric.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class TopKHitRateMetric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int k;
    private final BiPredicate<RankedPrediction<P>, O> hitPredicate;

    /**
     * Creates a top-k hit-rate metric.
     *
     * @param name         metric name
     * @param k            top-k depth
     * @param hitPredicate hit predicate for prediction/outcome pairs
     * @since 0.22.4
     */
    public TopKHitRateMetric(String name, int k, BiPredicate<RankedPrediction<P>, O> hitPredicate) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        this.k = k;
        this.hitPredicate = Objects.requireNonNull(hitPredicate, "hitPredicate");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double compute(List<WalkForwardObservation<P, O>> observations) {
        Map<String, List<WalkForwardObservation<P, O>>> grouped = WalkForwardMetric.groupBySnapshot(observations);
        if (grouped.isEmpty()) {
            return Double.NaN;
        }

        int hits = 0;
        for (List<WalkForwardObservation<P, O>> snapshotRows : grouped.values()) {
            boolean hit = false;
            for (WalkForwardObservation<P, O> row : snapshotRows) {
                if (row.prediction().rank() <= k && hitPredicate.test(row.prediction(), row.realizedOutcome())) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                hits++;
            }
        }
        return (double) hits / grouped.size();
    }
}
