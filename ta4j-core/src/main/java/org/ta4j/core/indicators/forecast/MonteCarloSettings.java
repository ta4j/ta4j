/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Technique-independent Monte Carlo forecast settings.
 *
 * <p>
 * Every forecast simulates {@code iterationCount} paths of {@code horizon}
 * steps and holds one terminal value per path, so both settings are bounded:
 * at most {@value #MAX_HORIZON} bars, {@value #MAX_ITERATION_COUNT} paths and
 * {@value #MAX_PATH_STEPS} simulated steps per forecast.
 *
 * <p>
 * Method-specific configuration lives on the
 * {@link org.ta4j.core.analysis.montecarlo.MonteCarloMethod MonteCarloMethod}
 * implementation chosen by the caller.
 */
record MonteCarloSettings(int horizon, int iterationCount, int lookbackBarCount, long seed,
        List<Double> quantileProbabilities) {

    static final int MAX_HORIZON = 10_000;
    static final int MAX_ITERATION_COUNT = 1_000_000;
    static final long MAX_PATH_STEPS = 1_000_000_000L;

    MonteCarloSettings {
        if (horizon < 1 || iterationCount < 1 || lookbackBarCount < 1) {
            throw new IllegalArgumentException("horizon, iterationCount, and lookbackBarCount must be >= 1");
        }
        if (horizon > MAX_HORIZON) {
            throw new IllegalArgumentException("horizon must be <= " + MAX_HORIZON);
        }
        if (iterationCount > MAX_ITERATION_COUNT) {
            throw new IllegalArgumentException("iterationCount must be <= " + MAX_ITERATION_COUNT);
        }
        if ((long) horizon * iterationCount > MAX_PATH_STEPS) {
            throw new IllegalArgumentException("horizon * iterationCount must be <= " + MAX_PATH_STEPS);
        }
        List<Double> input = Objects.requireNonNull(quantileProbabilities, "quantileProbabilities must not be null");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        TreeSet<Double> sorted = new TreeSet<>();
        for (Double probability : input) {
            Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
            if (Double.isNaN(value) || value < 0d || value > 1d) {
                throw new IllegalArgumentException("quantile probability must be in [0, 1]");
            }
            sorted.add(value);
        }
        quantileProbabilities = List.copyOf(sorted);
    }
}
