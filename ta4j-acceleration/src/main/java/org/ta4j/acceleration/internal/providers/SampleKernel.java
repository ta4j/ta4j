/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

/**
 * Sample-output native kernel seam. Implementations translate one chunked
 * {@link NativeForecastRequest} into per-sample cumulative log-returns without
 * touching indicators, forecasts, or crossover policy.
 *
 * @since 0.25.1
 */
interface SampleKernel {

    /**
     * Evaluates one chunk and returns per-sample cumulative log-returns.
     *
     * @param request chunked native request
     * @return cumulative log-returns and the native-measured total microseconds
     */
    SampleResult evaluateSamples(NativeForecastRequest request);

    /**
     * Native sample output with its measured cost. Samples travel as
     * {@code double}, so FP64 lanes publish exactly what their kernels computed;
     * reduced-precision lanes widen at their own boundary.
     *
     * @param logReturns  per-sample cumulative log-returns, decision-major order
     * @param totalMicros native-measured total microseconds for the chunk
     */
    record SampleResult(double[] logReturns, double totalMicros) {
        public SampleResult {
            logReturns = logReturns.clone();
        }

        @Override
        public double[] logReturns() {
            return logReturns.clone();
        }
    }
}
