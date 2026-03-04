/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward.metric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardMetric;
import org.ta4j.core.walkforward.WalkForwardObservation;

/**
 * Normalized discounted cumulative gain (NDCG) at rank {@code k}.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class NdcgMetric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int k;
    private final BiFunction<RankedPrediction<P>, O, Double> relevanceFunction;

    /**
     * Creates an NDCG metric.
     *
     * @param name              metric name
     * @param k                 ranking depth
     * @param relevanceFunction relevance scoring function
     * @since 0.22.4
     */
    public NdcgMetric(String name, int k, BiFunction<RankedPrediction<P>, O, Double> relevanceFunction) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        this.k = k;
        this.relevanceFunction = Objects.requireNonNull(relevanceFunction, "relevanceFunction");
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

        double ndcgSum = 0.0;
        int count = 0;

        for (List<WalkForwardObservation<P, O>> snapshotRows : grouped.values()) {
            List<WalkForwardObservation<P, O>> ranked = snapshotRows.stream()
                    .filter(row -> row.prediction().rank() <= k)
                    .sorted(Comparator.comparingInt(row -> row.prediction().rank()))
                    .toList();
            if (ranked.isEmpty()) {
                continue;
            }

            List<Double> relevance = new ArrayList<>(ranked.size());
            for (WalkForwardObservation<P, O> row : ranked) {
                relevance.add(Math.max(0.0, relevanceFunction.apply(row.prediction(), row.realizedOutcome())));
            }

            double dcg = discountedGain(relevance);
            List<Double> ideal = relevance.stream().sorted(Comparator.reverseOrder()).toList();
            double idcg = discountedGain(ideal);
            ndcgSum += idcg == 0.0 ? 0.0 : dcg / idcg;
            count++;
        }

        return count == 0 ? Double.NaN : ndcgSum / count;
    }

    private static double discountedGain(List<Double> relevance) {
        double dcg = 0.0;
        for (int i = 0; i < relevance.size(); i++) {
            double gain = Math.pow(2.0, relevance.get(i)) - 1.0;
            dcg += gain / WalkForwardMetric.log2(i + 2.0);
        }
        return dcg;
    }
}
