/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

/**
 * Versioned native-kernel contract for
 * {@link org.ta4j.core.acceleration.AccelerationRuntime.Operation#MONTE_CARLO_SHOCK_PATHS_V1}.
 *
 * <p>
 * The core planner snapshots every kernel input into primitives; providers
 * execute this contract and return raw terminal prices. Scalar parity is
 * bitwise: the kernel must reproduce the scalar lane exactly for identical
 * inputs, including the per-path deterministic stream below, the shock-model
 * branch, within-path EWMA updates, and the terminal-price guard.
 *
 * <p>
 * Input buffers, in order:
 * <ol>
 * <li>{@code prices} — spot price per decision index ({@code [n]}).</li>
 * <li>{@code means} — starting log-return mean per decision index
 * ({@code [n]}).</li>
 * <li>{@code drifts} — forward drift assumption per decision index
 * ({@code [n]}).</li>
 * <li>{@code variances} — starting return variance per decision index
 * ({@code [n]}).</li>
 * <li>{@code windows} — historical log returns, row-major per decision index
 * ({@code [n][lookbackBarCount]}).</li>
 * </ol>
 *
 * <p>
 * Request parameters, in order: shock-model ordinal, volatility-update-mode
 * ordinal, horizon in bars, iteration count, lookback bar count, EWMA decay
 * factor. The base seed travels in the request seed field. Output is row-major
 * terminal prices ({@code [n][iterationCount]}). A non-finite output marks its
 * decision index unstable; the core decoder maps such slices to unstable
 * forecasts, matching the scalar lane.
 *
 * <p>
 * Per-path stream (RNG version 1): path {@code p} of decision index {@code i}
 * draws from the SplitMix-style stream seeded by
 * {@link #initialPathState(long, int, int, int)}. Each draw advances the state
 * by {@link #advanceState(long)} and interprets the mixed output with
 * {@link #toUnitDouble(long)} for uniforms, {@link #gaussian(double, double)}
 * for standard normals, and the {@code nextInt} rejection loop of the scalar
 * lane for bootstrap selection. Shock model and volatility update mode are
 * identified by their enum ordinals
 * ({@link MonteCarloReturnProjectionIndicator.ShockModel},
 * {@link MonteCarloReturnProjectionIndicator.VolatilityUpdateMode}). Terminal
 * prices apply the scalar guard: cumulative log-returns whose magnitude exceeds
 * {@code 700} map to non-finite output.
 *
 * @since 0.24.2
 */
public final class MonteCarloKernel {

    /** Operation buffer order: spot prices. */
    public static final int INPUT_PRICES = 0;

    /** Operation buffer order: starting means. */
    public static final int INPUT_MEANS = 1;

    /** Operation buffer order: forward drifts. */
    public static final int INPUT_DRIFTS = 2;

    /** Operation buffer order: starting variances. */
    public static final int INPUT_VARIANCES = 3;

    /** Operation buffer order: historical log-return windows. */
    public static final int INPUT_WINDOWS = 4;

    /** Number of input buffers. */
    public static final int INPUT_COUNT = 5;

    /**
     * Cumulative log-return magnitude above which terminal prices are non-finite.
     */
    public static final double MAX_EXPONENT = 700d;

    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    static final double DOUBLE_UNIT = 0x1.0p-53;

    private MonteCarloKernel() {
    }

    /**
     * Derives the initial stream state for one simulated path.
     *
     * @param seed          base seed
     * @param decisionIndex decision index
     * @param horizon       forecast horizon in bars
     * @param pathIndex     path ordinal within the decision index
     * @return initial stream state
     * @since 0.24.2
     */
    public static long initialPathState(long seed, int decisionIndex, int horizon, int pathIndex) {
        if (decisionIndex < 0) {
            throw new IllegalArgumentException("decisionIndex must be >= 0");
        }
        if (horizon < 1) {
            throw new IllegalArgumentException("horizon must be >= 1");
        }
        if (pathIndex < 0) {
            throw new IllegalArgumentException("pathIndex must be >= 0");
        }
        long value = seed;
        value = mix64(value ^ (Integer.toUnsignedLong(decisionIndex) * 0xD1B54A32D192ED03L));
        value = mix64(value ^ (Integer.toUnsignedLong(horizon) * 0x94D049BB133111EBL));
        value = mix64(value ^ (Integer.toUnsignedLong(pathIndex) * 0xDB4F0B9175AE2165L));
        return value;
    }

    /**
     * Advances a path stream and returns the mixed output (SplitMix64 increment
     * plus avalanche).
     *
     * @param state current stream state
     * @return mixed output; the next state is {@code state + GOLDEN_GAMMA}
     * @since 0.24.2
     */
    public static long advanceState(long state) {
        return mix64(state + GOLDEN_GAMMA);
    }

    /**
     * Maps mixed output bits to a {@code [0, 1)} uniform.
     *
     * @param mixed mixed output bits
     * @return uniform double
     * @since 0.24.2
     */
    public static double toUnitDouble(long mixed) {
        return (mixed >>> 11) * DOUBLE_UNIT;
    }

    /**
     * Box-Muller transform matching the scalar lane exactly (StrictMath, cosine
     * form, {@code 1 - u1} radius).
     *
     * @param first  first uniform
     * @param second second uniform
     * @return standard normal draw
     * @since 0.24.2
     */
    public static double gaussian(double first, double second) {
        double radius = StrictMath.sqrt(-2d * StrictMath.log(1d - first));
        return radius * StrictMath.cos(2d * StrictMath.PI * second);
    }

    static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
