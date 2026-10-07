/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.acceleration.Kernel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;

/**
 * The {@link Kernel} of {@link MonteCarloPriceForecastIndicator}: the versioned
 * contract of {@link Operation#MONTE_CARLO_SHOCK_PATHS_V1} and its CPU
 * implementation.
 *
 * <p>
 * The indicator snapshots every kernel input into primitives; the kernel
 * simulates the paths and returns raw cumulative log-returns. The indicator
 * maps them to terminal prices through its own guards and exponential, so no
 * provider ever evaluates {@code exp}, {@code pow}, or the terminal guards. A
 * provider must reproduce {@link #run(KernelRequest, int, double[])} bit for
 * bit.
 *
 * <p>
 * Input buffers, indexed by the {@code INPUT_*} constants:
 * <ol>
 * <li>{@code prices} — spot price per decision index ({@code [n]}); the kernel
 * ignores it.</li>
 * <li>{@code means} — starting log-return mean per decision index
 * ({@code [n]}).</li>
 * <li>{@code drifts} — forward drift assumption per decision index
 * ({@code [n]}).</li>
 * <li>{@code variances} — starting return variance per decision index
 * ({@code [n]}).</li>
 * <li>{@code returns} — contiguous historical log returns
 * ({@code [n + lookback - 1]}); decision row {@code r} uses the window
 * {@code returns[r .. r + lookback - 1]}.</li>
 * </ol>
 *
 * <p>
 * Request parameters are indexed by the {@code PARAM_*} constants. The base
 * seed travels in the request seed field. Output is row-major cumulative
 * log-returns ({@code [n][iterationCount]}). A non-finite value means the
 * decision index is reported unstable; the runtime cannot tell it from a faulty
 * provider, so a batch containing one is rejected, the provider is quarantined
 * for this operation, and the indicator is recomputed on the CPU.
 *
 * <p>
 * Path algorithm for decision row {@code r} at decision index {@code i} and
 * path {@code p}, where every arithmetic operation is one separately rounded
 * IEEE-754 binary64 operation evaluated in the order written — fused
 * multiply-add, contraction, reassociation, and extended precision are not
 * allowed:
 * <ol>
 * <li>{@code vol = variance == 0 ? 0 : sqrt(variance)}.</li>
 * <li>Shock table: {@link #SHOCK_HISTORICAL_BOOTSTRAP} uses the window as is.
 * The standardized models use {@code (w - mean) / vol} for each window value
 * when {@code vol != 0}; when {@code vol == 0} every shock is {@code 0} and no
 * draw is consumed. {@link #SHOCK_SMOOTHED_EMPIRICAL} adds a bandwidth
 * {@code sd * PARAM_SMOOTHING_FACTOR}, where {@code sd} is the sample standard
 * deviation of the standardized shocks (mean as a left-to-right sum divided by
 * {@code lookback}, squared deviations summed left to right and divided by
 * {@code lookback - 1}); the bandwidth is {@code 0} when {@code lookback < 2}
 * or that variance is not finite and positive.</li>
 * <li>Each of {@code horizon} steps draws one shock: bootstrap models pick
 * {@code table[nextInt(lookback)]}; the smoothed model with a nonzero bandwidth
 * then adds {@code gaussian * bandwidth}, drawing the index first;
 * {@link #SHOCK_NORMAL} draws {@code gaussian}. {@code step} is the shock
 * itself for bootstrap, otherwise {@code drift + vol * shock}, and
 * {@code cumulative += step}.</li>
 * <li>With {@link #VOLATILITY_EWMA}, after each step:
 * {@code dev = step - mean; mean = mean * decay + step * (1 - decay);
 * variance = variance * decay + (dev * dev) * (1 - decay);} and {@code vol} is
 * recomputed as in step 1.</li>
 * </ol>
 *
 * <p>
 * Per-path stream: path {@code p} of decision index {@code i} draws from the
 * SplitMix-style stream seeded by
 * {@link #initialPathState(long, int, int, int)}. Each draw advances the state
 * with {@code state = advanceState(state)} and obtains output bits with
 * {@code mix64(state)}. Uniforms use {@link #toUnitDouble(long)}; a standard
 * normal is {@link #gaussian(double, double)} of two uniforms drawn in argument
 * order; {@code nextInt(bound)} takes {@code bits >>> 1}, returns
 * {@code bits % bound}, and redraws while {@code bits - rem + bound - 1}
 * overflows. {@code gaussian} uses {@code log} and {@code cos} with
 * {@link StrictMath} (fdlibm) semantics, so native kernels must use a bitwise
 * port of those two functions; {@code sqrt} is correctly rounded everywhere.
 *
 * @since 0.26.1
 */
public final class MonteCarloKernel implements Kernel {

    /** Operation buffer order: spot prices. */
    public static final int INPUT_PRICES = 0;

    /** Operation buffer order: starting means. */
    public static final int INPUT_MEANS = 1;

    /** Operation buffer order: forward drifts. */
    public static final int INPUT_DRIFTS = 2;

    /** Operation buffer order: starting variances. */
    public static final int INPUT_VARIANCES = 3;

    /** Operation buffer order: contiguous historical log returns. */
    public static final int INPUT_RETURNS = 4;

    /** Number of input buffers. */
    public static final int INPUT_COUNT = 5;

    /** Shock-model code: bootstrap historical log returns. */
    public static final int SHOCK_HISTORICAL_BOOTSTRAP = 0;

    /** Shock-model code: bootstrap standardized historical returns. */
    public static final int SHOCK_STANDARDIZED_EMPIRICAL = 1;

    /** Shock-model code: kernel-smoothed standardized historical returns. */
    public static final int SHOCK_SMOOTHED_EMPIRICAL = 2;

    /** Shock-model code: standard normal shocks. */
    public static final int SHOCK_NORMAL = 3;

    /** Volatility-mode code: constant volatility along each path. */
    public static final int VOLATILITY_CONSTANT = 0;

    /** Volatility-mode code: EWMA volatility updates along each path. */
    public static final int VOLATILITY_EWMA = 1;

    /** Parameter index: shock-model code. */
    public static final int PARAM_SHOCK_MODEL = 0;

    /** Parameter index: volatility-mode code. */
    public static final int PARAM_VOLATILITY_MODE = 1;

    /** Parameter index: horizon in bars. */
    public static final int PARAM_HORIZON = 2;

    /** Parameter index: simulated paths per decision index. */
    public static final int PARAM_ITERATIONS = 3;

    /** Parameter index: historical lookback bar count. */
    public static final int PARAM_LOOKBACK = 4;

    /** Parameter index: EWMA decay factor. */
    public static final int PARAM_DECAY = 5;

    /**
     * Parameter index: smoothed-empirical bandwidth factor
     * {@link #smoothingBandwidthFactor(int)} of the lookback.
     */
    public static final int PARAM_SMOOTHING_FACTOR = 6;

    /** Number of request parameters. */
    public static final int PARAM_COUNT = 7;

    /**
     * Cumulative log-return magnitude above which the core decoder and the scalar
     * lane report an unstable forecast instead of a terminal price.
     */
    public static final double MAX_EXPONENT = 700d;

    /**
     * Silverman reference-bandwidth factor {@code 1.06 * count^(-1/5)} shared by
     * the scalar smoothed-empirical sampler and the kernel request, so the
     * platform-dependent {@code pow} is evaluated once, on the JVM.
     *
     * @param observationCount number of standardized residuals
     * @return bandwidth factor multiplied by the residual standard deviation
     * @since 0.26.1
     */
    public static double smoothingBandwidthFactor(int observationCount) {
        return 1.06d * Math.pow(observationCount, -0.2d);
    }

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    static final double DOUBLE_UNIT = 0x1.0p-53;

    /** Order-of-magnitude CPU cost per simulated path step, in nanoseconds. */
    private static final long NANOS_PER_PATH_STEP = 50L;

    /** Per-row inputs: price, mean, drift and variance. */
    private static final int INPUTS_PER_ROW = 4;

    private final MonteCarloSettings settings;
    private final int shockModel;
    private final int volatilityUpdateMode;
    private final double volatilityDecayFactor;

    MonteCarloKernel(MonteCarloSettings settings, ShockModel shockModel, VolatilityUpdateMode volatilityUpdateMode,
            double volatilityDecayFactor) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.shockModel = switch (Objects.requireNonNull(shockModel, "shockModel must not be null")) {
        case HISTORICAL_BOOTSTRAP -> SHOCK_HISTORICAL_BOOTSTRAP;
        case STANDARDIZED_EMPIRICAL -> SHOCK_STANDARDIZED_EMPIRICAL;
        case SMOOTHED_EMPIRICAL -> SHOCK_SMOOTHED_EMPIRICAL;
        case NORMAL -> SHOCK_NORMAL;
        };
        this.volatilityUpdateMode = switch (Objects.requireNonNull(volatilityUpdateMode,
                "volatilityUpdateMode must not be null")) {
        case CONSTANT -> VOLATILITY_CONSTANT;
        case EWMA -> VOLATILITY_EWMA;
        };
        if (Double.isNaN(volatilityDecayFactor) || volatilityDecayFactor <= 0d || volatilityDecayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        this.volatilityDecayFactor = volatilityDecayFactor;
    }

    MonteCarloSettings settings() {
        return settings;
    }

    @Override
    public Operation operation() {
        return Operation.MONTE_CARLO_SHOCK_PATHS_V1;
    }

    @Override
    public long seed() {
        return settings.seed();
    }

    @Override
    public double[] params() {
        double[] params = new double[PARAM_COUNT];
        params[PARAM_SHOCK_MODEL] = shockModel;
        params[PARAM_VOLATILITY_MODE] = volatilityUpdateMode;
        params[PARAM_HORIZON] = settings.horizon();
        params[PARAM_ITERATIONS] = settings.iterationCount();
        params[PARAM_LOOKBACK] = settings.lookbackBarCount();
        params[PARAM_DECAY] = volatilityDecayFactor;
        params[PARAM_SMOOTHING_FACTOR] = smoothingBandwidthFactor(settings.lookbackBarCount());
        return params;
    }

    @Override
    public int inputsPerRow() {
        return INPUTS_PER_ROW;
    }

    @Override
    public int windowLength() {
        return settings.lookbackBarCount();
    }

    @Override
    public int outputsPerRow() {
        return settings.iterationCount();
    }

    /**
     * Four scalar inputs and one new window return, the output paths, and one
     * per-step workspace value per path.
     */
    @Override
    public long deviceBytesPerRow() {
        try {
            long outputBytes = Math.multiplyExact((long) settings.iterationCount(), Double.BYTES);
            return Math.addExact((INPUTS_PER_ROW + 1L) * Double.BYTES,
                    Math.addExact(outputBytes, Math.multiplyExact(outputBytes, settings.horizon())));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long scalarNanosPerRow() {
        long steps = (long) settings.iterationCount() * settings.horizon();
        return steps > Long.MAX_VALUE / NANOS_PER_PATH_STEP ? Long.MAX_VALUE : steps * NANOS_PER_PATH_STEP;
    }

    @Override
    public void run(KernelRequest request, int row, double[] outputs) {
        int model = (int) request.param(PARAM_SHOCK_MODEL);
        boolean ewma = (int) request.param(PARAM_VOLATILITY_MODE) == VOLATILITY_EWMA;
        int horizon = (int) request.param(PARAM_HORIZON);
        int iterations = (int) request.param(PARAM_ITERATIONS);
        int lookback = (int) request.param(PARAM_LOOKBACK);
        double decay = request.param(PARAM_DECAY);
        double oneMinusDecay = 1d - decay;
        int decisionIndex = request.fromInclusive() + row;
        double startMean = request.input(INPUT_MEANS, row);
        double drift = request.input(INPUT_DRIFTS, row);
        double startVariance = request.input(INPUT_VARIANCES, row);
        boolean bootstrap = model == SHOCK_HISTORICAL_BOOTSTRAP;
        boolean normal = model == SHOCK_NORMAL;
        double startVolatility = startVariance == 0d ? 0d : Math.sqrt(startVariance);
        boolean zeroShocks = !bootstrap && !normal && startVolatility == 0d;
        double[] table = null;
        double bandwidth = 0d;
        if (!normal && !zeroShocks) {
            table = new double[lookback];
            for (int offset = 0; offset < lookback; offset++) {
                double value = request.input(INPUT_RETURNS, row + offset);
                table[offset] = bootstrap ? value : (value - startMean) / startVolatility;
            }
            if (model == SHOCK_SMOOTHED_EMPIRICAL) {
                bandwidth = bandwidth(table, request.param(PARAM_SMOOTHING_FACTOR));
            }
        }
        MonteCarloSimulation.DeterministicRandom random = MonteCarloSimulation.DeterministicRandom
                .forPath(request.seed(), decisionIndex, horizon, 0);
        for (int path = 0; path < iterations; path++) {
            random.resetToPath(request.seed(), decisionIndex, horizon, path);
            double cumulative = 0d;
            double mean = startMean;
            double variance = startVariance;
            double volatility = startVolatility;
            for (int step = 0; step < horizon; step++) {
                double shock;
                if (normal) {
                    shock = random.nextGaussian();
                } else if (zeroShocks) {
                    shock = 0d;
                } else {
                    shock = table[random.nextInt(lookback)];
                    if (bandwidth != 0d) {
                        shock = shock + random.nextGaussian() * bandwidth;
                    }
                }
                double stepReturn = bootstrap ? shock : drift + volatility * shock;
                cumulative = cumulative + stepReturn;
                if (ewma) {
                    double deviation = stepReturn - mean;
                    mean = mean * decay + stepReturn * oneMinusDecay;
                    variance = variance * decay + deviation * deviation * oneMinusDecay;
                    volatility = variance == 0d ? 0d : Math.sqrt(variance);
                }
            }
            outputs[path] = cumulative;
        }
    }

    /**
     * Silverman bandwidth of the standardized shocks, {@code 0} when their sample
     * variance is degenerate.
     */
    private static double bandwidth(double[] shocks, double factor) {
        if (shocks.length < 2) {
            return 0d;
        }
        double sum = 0d;
        for (double shock : shocks) {
            sum = sum + shock;
        }
        double mean = sum / shocks.length;
        double squaredDeviationSum = 0d;
        for (double shock : shocks) {
            double deviation = shock - mean;
            squaredDeviationSum = squaredDeviationSum + deviation * deviation;
        }
        double variance = squaredDeviationSum / (shocks.length - 1L);
        if (!Double.isFinite(variance) || variance <= 0d) {
            return 0d;
        }
        return Math.sqrt(variance) * factor;
    }

    /**
     * Derives the initial stream state for one simulated path.
     *
     * @param seed          base seed
     * @param decisionIndex decision index
     * @param horizon       forecast horizon in bars
     * @param pathIndex     path ordinal within the decision index
     * @return initial stream state
     * @since 0.26.1
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
     * Advances the raw path-stream state by the SplitMix64 increment. Keep this
     * returned state for the next draw; use {@link #mix64(long)} separately to
     * obtain output bits without altering the raw state.
     *
     * @param state current raw stream state
     * @return next raw stream state
     * @since 0.26.1
     */
    public static long advanceState(long state) {
        return state + GOLDEN_GAMMA;
    }

    /**
     * Maps mixed output bits to a {@code [0, 1)} uniform.
     *
     * @param mixed mixed output bits
     * @return uniform double
     * @since 0.26.1
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
     * @since 0.26.1
     */
    public static double gaussian(double first, double second) {
        double radius = StrictMath.sqrt(-2d * StrictMath.log(1d - first));
        return radius * StrictMath.cos(2d * StrictMath.PI * second);
    }

    /**
     * Applies the SplitMix64 avalanche to a raw stream state without advancing it.
     *
     * @param value raw stream state
     * @return mixed output bits, not the state for the next draw
     * @since 0.26.1
     */
    public static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
