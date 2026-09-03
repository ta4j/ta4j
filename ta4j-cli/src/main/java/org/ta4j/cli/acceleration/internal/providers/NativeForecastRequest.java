/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

/**
 * Chunked native request for the versioned Monte Carlo shock-path kernel.
 * Carries one contiguous decision chunk with an all-stable flag vector; the
 * core owns eligibility and snapshotting, so every row handed here is stable.
 *
 * @since 0.24.2
 */
record NativeForecastRequest(int fromInclusive, int decisionCount, int horizon, int iterationCount,
        int lookbackBarCount, long seed, int shockModel, int volatilityMode, double volatilityDecayFactor,
        double[] quantiles, int[] stable, double[] prices, double[] means, double[] drifts, double[] variances,
        double[] historicalReturns) {
}
