/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

/**
 * Chunked native request for the versioned Monte Carlo shock-path kernel. The
 * core owns eligibility and snapshotting, so every row handed here is stable.
 * {@code historicalReturns} is the contiguous shared buffer of
 * {@code decisionCount + lookbackBarCount - 1} returns: decision row {@code r}
 * samples {@code historicalReturns[r .. r + lookbackBarCount - 1]}.
 * {@code shockModel} uses the native codes 0 (historical bootstrap), 1
 * (standardized empirical) and 2 (normal).
 *
 * @since 0.25.1
 */
record NativeForecastRequest(int fromInclusive, int decisionCount, int horizon, int iterationCount,
        int lookbackBarCount, long seed, int shockModel, int volatilityMode, double volatilityDecayFactor,
        double[] means, double[] drifts, double[] variances, double[] historicalReturns) {
}
