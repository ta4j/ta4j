/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

/**
 * Sample-output native kernel seam. Implementations translate one chunked
 * {@link NativeForecastRequest} into per-sample terminal prices without
 * touching indicators, forecasts, or crossover policy.
 *
 * @since 0.24.2
 */
interface SampleKernel {

    /**
     * Evaluates one chunk and returns per-sample terminal prices.
     *
     * @param request chunked native request with an all-stable flag vector
     * @return terminal prices and the native-measured total microseconds
     */
    SampleResult evaluateSamples(NativeForecastRequest request);

    /**
     * Native sample output with its measured cost.
     *
     * @param terminalPrices per-sample terminal prices, decision-major order
     * @param totalMicros    native-measured total microseconds for the chunk
     */
    record SampleResult(float[] terminalPrices, double totalMicros) {
        public SampleResult {
            terminalPrices = terminalPrices.clone();
        }

        @Override
        public float[] terminalPrices() {
            return terminalPrices.clone();
        }
    }
}
