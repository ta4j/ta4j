/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationPlan;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Lowers {@link MonteCarloPriceForecastIndicator} batches into
 * {@link AccelerationRuntime.Operation#MONTE_CARLO_SHOCK_PATHS_V1} kernel
 * requests.
 *
 * <p>
 * The planner replicates the scalar eligibility gates exactly: double-only
 * numerics, the scalar first-stable forecast index, per-index state stability
 * and window completeness. Both lanes draw each path from the same per-path
 * stream, so the kernel needs no RNG state beyond the seed. The unavailable
 * prefix before the first stable index stays on the scalar lane and the planner
 * lowers the eligible suffix instead. A range that exceeds the device or host
 * budget is lowered as the longest prefix that fits, and a row that is not
 * eligible truncates the batch before it; the runtime plans the next chunk when
 * a later index is read. Only a decision index that can never be lowered is
 * declined permanently, with the specific reason.
 *
 * @since 0.26.1
 */
final class MonteCarloShockPathPlanner {

    /** Order-of-magnitude scalar cost per simulated path step, in nanoseconds. */
    static final long NANOS_PER_PATH_STEP = 50L;

    /** Device bytes per float64 element. */
    private static final long BYTES_PER_ELEMENT = 8L;

    /** Per-row scalar inputs: price, mean, drift and variance. */
    private static final long SCALAR_INPUTS_PER_ROW = 4L;

    private MonteCarloShockPathPlanner() {
    }

    static AccelerationPlan<Forecast> plan(MonteCarloPriceForecastIndicator forecast, int fromInclusive,
            int toInclusive, long memoryLimitBytes) {
        // Keep host staging below a quarter of the maximum heap, independently of
        // the device budget, leaving headroom for series caches and decoded values.
        return plan(forecast, fromInclusive, toInclusive, memoryLimitBytes, Runtime.getRuntime().maxMemory() / 4L);
    }

    static AccelerationPlan<Forecast> plan(MonteCarloPriceForecastIndicator forecast, int fromInclusive,
            int toInclusive, long memoryLimitBytes, long hostMemoryLimitBytes) {
        Objects.requireNonNull(forecast, "forecast must not be null");
        NumFactory factory = forecast.getBarSeries().numFactory();
        if (!(factory instanceof DoubleNumFactory)) {
            return AccelerationPlan
                    .unsupported("requires DoubleNumFactory; the series uses " + factory.getClass().getSimpleName());
        }
        MonteCarloPriceForecastIndicator.ShockPathKernelConfig config = forecast.shockPathKernelConfig();
        if (config == null) {
            return AccelerationPlan
                    .unsupported("a custom MonteCarloMethod is not lowered; only the default shock-path method is");
        }
        if (fromInclusive < 0 || toInclusive < fromInclusive) {
            return AccelerationPlan.unsupported("invalid request range [" + fromInclusive + ", " + toInclusive + "]");
        }
        MonteCarloSettings settings = config.settings();
        int iterations = settings.iterationCount();
        int lookback = settings.lookbackBarCount();
        int horizon = settings.horizon();
        // Per decision row: four scalar inputs, one new shared return, the output
        // paths and a per-step workspace. The lookback - 1 leading returns of the
        // shared buffer are paid once per batch.
        long rowDeviceBytes;
        long rowHostBytes;
        long fixedDeviceBytes;
        long fixedHostBytes;
        try {
            long rowInputBytes = Math.multiplyExact(SCALAR_INPUTS_PER_ROW + 1L, BYTES_PER_ELEMENT);
            long rowOutputBytes = Math.multiplyExact((long) iterations, BYTES_PER_ELEMENT);
            long rowWorkspaceBytes = Math.multiplyExact(rowOutputBytes, horizon);
            rowDeviceBytes = Math.addExact(rowInputBytes, Math.addExact(rowOutputBytes, rowWorkspaceBytes));
            fixedDeviceBytes = Math.multiplyExact(lookback - 1L, BYTES_PER_ELEMENT);
            // Planner snapshots, immutable request copies and provider-accessor
            // copies may overlap. Allow four output buffers for native handoff,
            // result ownership and decoding before any large array is allocated.
            rowHostBytes = Math.addExact(Math.multiplyExact(rowInputBytes, 3L), Math.multiplyExact(rowOutputBytes, 4L));
            fixedHostBytes = Math.multiplyExact(fixedDeviceBytes, 3L);
        } catch (ArithmeticException exception) {
            return AccelerationPlan.unsupported("one decision index overflows batch dimensions (lookback " + lookback
                    + ", iterations " + iterations + ", horizon " + horizon + ")");
        }
        // Returns and output buffers are int-indexed arrays.
        long rowsThatFit = Math.min(
                Math.min(Math.max(0L, memoryLimitBytes - fixedDeviceBytes) / rowDeviceBytes,
                        Math.max(0L, hostMemoryLimitBytes - fixedHostBytes) / rowHostBytes),
                Math.min(Integer.MAX_VALUE - (lookback - 1L), Integer.MAX_VALUE / iterations));
        if (rowsThatFit < 1L) {
            return AccelerationPlan.unsupported("one decision index needs " + (fixedDeviceBytes + rowDeviceBytes)
                    + " device bytes and " + (fixedHostBytes + rowHostBytes) + " host bytes, above the "
                    + memoryLimitBytes + "-byte device or " + hostMemoryLimitBytes + "-byte host budget");
        }
        // Scalar simulation reports an unstable forecast before its own first
        // stable index, so that prefix must stay on the scalar lane rather than be
        // lowered from a shorter window: acceleration must never publish a stable
        // forecast the scalar lane still reports as unstable.
        int firstStableIndex = Math.max(fromInclusive, forecast.getCountOfUnstableBars());
        if (firstStableIndex > toInclusive) {
            return AccelerationPlan.retryFrom(firstStableIndex,
                    "index " + fromInclusive + " precedes the first stable forecast index " + firstStableIndex);
        }
        ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator = config.stateIndicator();
        ReturnIndicator returnIndicator = stateIndicator.getReturnIndicator();
        int windowStart = firstStableIndex - lookback + 1;
        long firstRetainedOrigin = (long) returnIndicator.getBarSeries().getBeginIndex() + lookback - 1L;
        if (windowStart < returnIndicator.getBarSeries().getBeginIndex()) {
            int retryFromIndex = (int) Math.min(Integer.MAX_VALUE, firstRetainedOrigin);
            return AccelerationPlan.retryFrom(retryFromIndex,
                    "index " + firstStableIndex + " needs returns before the first retained bar");
        }
        Indicator<Num> priceIndicator = config.priceIndicator();
        // The scalar lane maps terminal prices in the price's own factory; the
        // decoder rebuilds prices through the series' double factory, so only
        // DoubleNum prices keep both lanes in the same numeric domain.
        Num firstPrice = priceIndicator.getValue(firstStableIndex);
        if (Num.isFinite(firstPrice) && !(firstPrice instanceof DoubleNum)) {
            return AccelerationPlan.unsupported(
                    "requires DoubleNum prices; the price indicator returns " + firstPrice.getClass().getSimpleName());
        }
        int rows = (int) Math.min((long) toInclusive - firstStableIndex + 1L, rowsThatFit);
        double[] prices = new double[rows];
        double[] means = new double[rows];
        double[] drifts = new double[rows];
        double[] variances = new double[rows];
        int stateRows = 0;
        while (stateRows < rows && snapshotState(firstStableIndex + stateRows, priceIndicator, stateIndicator, prices,
                means, drifts, variances, stateRows)) {
            stateRows++;
        }
        if (stateRows == 0) {
            return AccelerationPlan.retryFrom(firstStableIndex + 1,
                    "index " + firstStableIndex + " has no stable forecast state or positive finite price");
        }
        rows = stateRows;
        // Consecutive windows overlap in lookback - 1 returns: read each return once
        // and ship the shared buffer instead of one window per decision row.
        double[] returns = new double[rows + lookback - 1];
        for (int offset = 0; offset < returns.length; offset++) {
            int barIndex = windowStart + offset;
            double value = snapshot(returnIndicator.getValue(barIndex));
            if (Double.isNaN(value)) {
                // Every origin whose window contains barIndex is ineligible; origins
                // before it are complete, so lower them and let the runtime retry
                // after the last affected window.
                int affectedRow = barIndex - firstStableIndex;
                if (affectedRow <= 0) {
                    int retryFromIndex = (int) Math.min(Integer.MAX_VALUE, (long) barIndex + lookback);
                    return AccelerationPlan.retryFrom(retryFromIndex, "return at index " + barIndex + " is not finite");
                }
                rows = affectedRow;
                break;
            }
            returns[offset] = value;
        }
        if (rows < prices.length) {
            prices = Arrays.copyOf(prices, rows);
            means = Arrays.copyOf(means, rows);
            drifts = Arrays.copyOf(drifts, rows);
            variances = Arrays.copyOf(variances, rows);
            returns = Arrays.copyOf(returns, rows + lookback - 1);
        }
        int toIndex = firstStableIndex + rows - 1;
        long steps = (long) rows * iterations * horizon;
        long estimatedScalarNanos = steps > Long.MAX_VALUE / NANOS_PER_PATH_STEP ? Long.MAX_VALUE
                : steps * NANOS_PER_PATH_STEP;
        long peakBytes = fixedDeviceBytes + rowDeviceBytes * rows;
        double[] params = new double[MonteCarloKernel.PARAM_COUNT];
        params[MonteCarloKernel.PARAM_SHOCK_MODEL] = shockModelCode(config.shockModel());
        params[MonteCarloKernel.PARAM_VOLATILITY_MODE] = volatilityUpdateModeCode(config.volatilityUpdateMode());
        params[MonteCarloKernel.PARAM_HORIZON] = horizon;
        params[MonteCarloKernel.PARAM_ITERATIONS] = iterations;
        params[MonteCarloKernel.PARAM_LOOKBACK] = lookback;
        params[MonteCarloKernel.PARAM_DECAY] = config.volatilityDecayFactor();
        params[MonteCarloKernel.PARAM_SMOOTHING_FACTOR] = MonteCarloKernel.smoothingBandwidthFactor(lookback);
        double[][] inputs = new double[MonteCarloKernel.INPUT_COUNT][];
        inputs[MonteCarloKernel.INPUT_PRICES] = prices;
        inputs[MonteCarloKernel.INPUT_MEANS] = means;
        inputs[MonteCarloKernel.INPUT_DRIFTS] = drifts;
        inputs[MonteCarloKernel.INPUT_VARIANCES] = variances;
        inputs[MonteCarloKernel.INPUT_RETURNS] = returns;
        double tolerance = AccelerationRuntime.approximateTolerance();
        AccelerationRuntime.Determinism determinism = Double.isNaN(tolerance)
                ? AccelerationRuntime.Determinism.BITWISE_IDENTICAL
                : AccelerationRuntime.Determinism.APPROXIMATE;
        AccelerationRuntime.KernelRequest request = new AccelerationRuntime.KernelRequest(
                AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1, firstStableIndex, toIndex, iterations,
                AccelerationRuntime.NumericEncoding.FLOAT64, determinism, settings.seed(), tolerance, params,
                List.of(inputs), estimatedScalarNanos, peakBytes);
        List<Double> quantiles = List.copyOf(settings.quantileProbabilities());
        double[] spotPrices = prices;
        int firstRowIndex = firstStableIndex;
        AccelerationPlan.Decoder<Forecast> decoder = (slice, index) -> {
            // Kernels return cumulative log-returns; the scalar lane's own mapping
            // applies the exponential and every terminal guard.
            Num price = factory.numOf(spotPrices[index - firstRowIndex]);
            Num exponentLimit = factory.numOf(MonteCarloKernel.MAX_EXPONENT);
            List<Num> samples = new ArrayList<>(slice.length);
            for (double raw : slice) {
                if (!Double.isFinite(raw)) {
                    return Forecast.unstable(index, horizon);
                }
                Num terminal;
                try {
                    terminal = MonteCarloPriceForecastIndicator.terminalPrice(price, factory.numOf(raw), exponentLimit);
                } catch (ArithmeticException exception) {
                    return Forecast.unstable(index, horizon);
                }
                if (!Num.isFinite(terminal)) {
                    return Forecast.unstable(index, horizon);
                }
                samples.add(terminal);
            }
            return Forecast.ofSamples(index, horizon, samples, quantiles);
        };
        return AccelerationPlan.of(request, decoder);
    }

    private static boolean snapshotState(int index, Indicator<Num> priceIndicator,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, double[] prices, double[] means,
            double[] drifts, double[] variances, int row) {
        Num price = priceIndicator.getValue(index);
        if (!(price instanceof DoubleNum) || !Num.isFinite(price) || !price.isPositive()) {
            return false;
        }
        ReturnMomentState rawState = stateIndicator.getValue(index);
        if (rawState == null) {
            return false;
        }
        ReturnMoments moments = rawState.moments();
        if (moments == null || moments.index() != index || !moments.isStable()
                || moments.representation() != ReturnRepresentation.LOG || moments.observationCount() <= 0) {
            return false;
        }
        double mean = snapshot(moments.mean());
        double drift = snapshot(moments.drift());
        double variance = snapshot(moments.variance());
        if (Double.isNaN(mean) || Double.isNaN(drift) || Double.isNaN(variance)) {
            return false;
        }
        prices[row] = price.doubleValue();
        means[row] = mean;
        drifts[row] = drift;
        variances[row] = variance;
        return true;
    }

    /**
     * Snapshots a series value exactly as the scalar lane normalizes it through the
     * double factory, or returns NaN when the scalar lane would reject it. For a
     * {@link DoubleNum} the decimal round trip is the identity except that
     * {@code -0.0} becomes {@code +0.0}, which adding {@code 0.0} reproduces
     * without the per-value decimal conversion.
     */
    private static double snapshot(Num value) {
        if (value instanceof DoubleNum) {
            double raw = value.doubleValue();
            return Double.isFinite(raw) ? raw + 0.0d : Double.NaN;
        }
        if (!Num.isFinite(value)) {
            return Double.NaN;
        }
        Num normalized = DoubleNumFactory.getInstance().numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized.doubleValue()
                : Double.NaN;
    }

    private static double shockModelCode(MonteCarloReturnProjectionIndicator.ShockModel model) {
        return switch (model) {
        case HISTORICAL_BOOTSTRAP -> MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP;
        case STANDARDIZED_EMPIRICAL -> MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL;
        case SMOOTHED_EMPIRICAL -> MonteCarloKernel.SHOCK_SMOOTHED_EMPIRICAL;
        case NORMAL -> MonteCarloKernel.SHOCK_NORMAL;
        };
    }

    private static double volatilityUpdateModeCode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode mode) {
        return switch (mode) {
        case CONSTANT -> MonteCarloKernel.VOLATILITY_CONSTANT;
        case EWMA -> MonteCarloKernel.VOLATILITY_EWMA;
        };
    }
}
