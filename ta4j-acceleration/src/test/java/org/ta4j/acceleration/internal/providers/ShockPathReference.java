/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;

import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.indicators.forecast.MonteCarloKernel;

/**
 * Constant-volatility {@code MONTE_CARLO_SHOCK_PATHS_V1} path simulation in
 * double precision (the scalar contract) or emulated single precision (the
 * Metal lane: inputs narrowed to {@code float}, float arithmetic, 53-bit RNG
 * state).
 */
final class ShockPathReference {

    private ShockPathReference() {
    }

    /**
     * Returns the cumulative log-return of one path.
     *
     * @param window shock history of this decision row
     */
    static double cumulativeLogReturn(boolean singlePrecision, int shockModel, double mean, double drift,
            double variance, double[] window, int horizon, long seed, int decisionIndex, int path) {
        long state = MonteCarloKernel.initialPathState(seed, decisionIndex, horizon, path);
        if (singlePrecision) {
            float m = (float) mean;
            float d = (float) drift;
            float volatility = (float) Math.sqrt(Math.max(0f, (float) variance));
            float cumulative = 0f;
            for (int step = 0; step < horizon; step++) {
                float stepReturn;
                if (shockModel == MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL && volatility == 0f) {
                    stepReturn = d + volatility * 0f;
                } else {
                    state = MonteCarloKernel.advanceState(state);
                    float w = (float) window[boundedIndex(MonteCarloKernel.mix64(state), window.length)];
                    stepReturn = shockModel == MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP ? w
                            : d + volatility * ((w - m) / volatility);
                }
                cumulative += stepReturn;
            }
            return cumulative;
        }
        double volatility = variance == 0d ? 0d : Math.sqrt(variance);
        double cumulative = 0d;
        for (int step = 0; step < horizon; step++) {
            double stepReturn;
            if (shockModel == MonteCarloKernel.SHOCK_NORMAL) {
                state = MonteCarloKernel.advanceState(state);
                double first = MonteCarloKernel.toUnitDouble(MonteCarloKernel.mix64(state));
                state = MonteCarloKernel.advanceState(state);
                double second = MonteCarloKernel.toUnitDouble(MonteCarloKernel.mix64(state));
                stepReturn = drift + volatility * MonteCarloKernel.gaussian(first, second);
            } else if (shockModel == MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL && volatility == 0d) {
                stepReturn = drift + volatility * 0d;
            } else {
                state = MonteCarloKernel.advanceState(state);
                double w = window[boundedIndex(MonteCarloKernel.mix64(state), window.length)];
                stepReturn = shockModel == MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP ? w
                        : drift + volatility * ((w - mean) / volatility);
            }
            cumulative += stepReturn;
        }
        return cumulative;
    }

    /**
     * Executes a three-row constant-volatility request per supported shock model on
     * a native FP64 lane and requires every cumulative log-return to match the
     * double-precision reference, including the shared returns buffer layout and
     * the per-row decision index of the path stream.
     */
    static void assertFp64LaneMatchesReference(Provider provider, double absoluteTolerance) {
        int decisions = 3;
        int lookback = 4;
        int horizon = 5;
        int iterations = 64;
        int fromInclusive = 17;
        double[] means = { 0.001d, -0.002d, 0.0005d };
        double[] drifts = { 0.0003d, 0.0001d, -0.0002d };
        double[] variances = { 0.0004d, 0.0009d, 0.0001d };
        double[] returns = { 0.01d, -0.02d, 0.015d, -0.005d, 0.03d, -0.01d };
        for (int shockModel : new int[] { MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP,
                MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL, MonteCarloKernel.SHOCK_NORMAL }) {
            double[] params = new double[MonteCarloKernel.PARAM_COUNT];
            params[MonteCarloKernel.PARAM_SHOCK_MODEL] = shockModel;
            params[MonteCarloKernel.PARAM_VOLATILITY_MODE] = MonteCarloKernel.VOLATILITY_CONSTANT;
            params[MonteCarloKernel.PARAM_HORIZON] = horizon;
            params[MonteCarloKernel.PARAM_ITERATIONS] = iterations;
            params[MonteCarloKernel.PARAM_LOOKBACK] = lookback;
            params[MonteCarloKernel.PARAM_DECAY] = 0.94d;
            params[MonteCarloKernel.PARAM_SMOOTHING_FACTOR] = MonteCarloKernel.smoothingBandwidthFactor(lookback);
            double[] prices = new double[decisions];
            Arrays.fill(prices, 100d);
            KernelRequest request = new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, fromInclusive,
                    fromInclusive + decisions - 1, iterations, NumericEncoding.FLOAT64, Determinism.APPROXIMATE, 42L,
                    1e-9d, params, List.of(prices, means, drifts, variances, returns), 1_000_000L, 1_000_000L);

            double[] outputs = provider.execute(request).outputs();

            assertThat(outputs).hasSize(decisions * iterations);
            for (int row = 0; row < decisions; row++) {
                double[] window = Arrays.copyOfRange(returns, row, row + lookback);
                for (int path = 0; path < iterations; path++) {
                    double expected = cumulativeLogReturn(false, shockModel, means[row], drifts[row], variances[row],
                            window, horizon, 42L, fromInclusive + row, path);
                    assertThat(outputs[row * iterations + path])
                            .as("shock model %d, row %d, path %d", shockModel, row, path)
                            .isCloseTo(expected, within(absoluteTolerance));
                }
            }
        }
    }

    private static int boundedIndex(long firstBits, int bound) {
        long bits = firstBits >>> 1;
        if (bound == 1) {
            return 0;
        }
        long remainder = bits % bound;
        if (bits - remainder + bound - 1 < 0) {
            throw new IllegalStateException("fixture draw needs a redraw; choose another seed");
        }
        return (int) remainder;
    }
}
