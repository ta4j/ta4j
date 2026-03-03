/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Expected calibration error (ECE) for probabilistic predictions.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public final class ExpectedCalibrationErrorMetric<P, O> implements WalkForwardMetric<P, O> {

    private final String name;
    private final int rank;
    private final int bins;
    private final Function<O, Double> actualProbabilityExtractor;

    /**
     * Creates an ECE metric.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param bins                       number of equal-width probability bins
     * @param actualProbabilityExtractor outcome to probability mapper in
     *                                   {@code [0,1]}
     * @since 0.22.4
     */
    public ExpectedCalibrationErrorMetric(String name, int rank, int bins,
            Function<O, Double> actualProbabilityExtractor) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        if (bins <= 0) {
            throw new IllegalArgumentException("bins must be > 0");
        }
        this.rank = rank;
        this.bins = bins;
        this.actualProbabilityExtractor = Objects.requireNonNull(actualProbabilityExtractor,
                "actualProbabilityExtractor");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double compute(List<WalkForwardObservation<P, O>> observations) {
        double[] predictedSums = new double[bins];
        double[] actualSums = new double[bins];
        int[] counts = new int[bins];
        int total = 0;

        for (WalkForwardObservation<P, O> observation : observations) {
            if (observation.prediction().rank() != rank) {
                continue;
            }
            double predicted = WalkForwardMetricSupport.clamp01(observation.prediction().probability());
            double actual = WalkForwardMetricSupport
                    .clamp01(actualProbabilityExtractor.apply(observation.realizedOutcome()));
            int index = Math.min(bins - 1, (int) Math.floor(predicted * bins));
            predictedSums[index] += predicted;
            actualSums[index] += actual;
            counts[index]++;
            total++;
        }

        if (total == 0) {
            return Double.NaN;
        }

        double ece = 0.0;
        for (int i = 0; i < bins; i++) {
            if (counts[i] == 0) {
                continue;
            }
            double meanPredicted = predictedSums[i] / counts[i];
            double meanActual = actualSums[i] / counts[i];
            ece += (Math.abs(meanPredicted - meanActual) * counts[i]) / total;
        }
        return ece;
    }
}
