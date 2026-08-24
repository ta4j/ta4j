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
 * Method-specific configuration lives on the
 * {@link org.ta4j.core.indicators.forecast.method.MonteCarloMethod
 * MonteCarloMethod} implementation chosen by the caller.
 */
record MonteCarloSettings(int horizon, int iterationCount, int lookbackBarCount, long seed,
        List<Double> quantileProbabilities) {

    MonteCarloSettings {
        if (horizon < 1 || iterationCount < 1 || lookbackBarCount < 1) {
            throw new IllegalArgumentException("horizon, iterationCount, and lookbackBarCount must be >= 1");
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
