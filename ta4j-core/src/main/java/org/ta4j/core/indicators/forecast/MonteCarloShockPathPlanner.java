/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.OperationDecoder;
import org.ta4j.core.acceleration.OperationPlanner;
import org.ta4j.core.acceleration.PlannedOperation;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
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
 * numerics, the explicit per-path RNG stream, per-index state stability and
 * window completeness. Any ineligible index declines the whole batch so the
 * scalar lane — which can mix unstable and stable forecasts — stays
 * authoritative outside the steady state.
 *
 * @since 0.24.2
 */
final class MonteCarloShockPathPlanner implements OperationPlanner {

    /** Order-of-magnitude scalar cost per simulated path step, in nanoseconds. */
    static final long NANOS_PER_PATH_STEP = 50L;

    /** Device bytes per float64 element. */
    private static final long BYTES_PER_ELEMENT = 8L;

    @Override
    public PlannedOperation plan(Indicator<?> indicator, int fromInclusive, int toInclusive, NumFactory factory) {
        Objects.requireNonNull(indicator, "indicator must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        if (!(indicator instanceof MonteCarloPriceForecastIndicator forecast)) {
            return null;
        }
        if (!forecast.kernelUsesStockShockPaths()) {
            return null;
        }
        if (!(factory instanceof DoubleNumFactory)) {
            return null;
        }
        if (!MonteCarloSimulation.isPerPathRngSelected()) {
            return null;
        }
        MonteCarloSettings settings = forecast.kernelSettings();
        if (settings.horizon() < 1 || settings.iterationCount() < 1 || settings.lookbackBarCount() < 1) {
            return null;
        }
        int size = toInclusive - fromInclusive + 1;
        if (size < 1) {
            return null;
        }
        int iterations = settings.iterationCount();
        int lookback = settings.lookbackBarCount();
        double[] prices = new double[size];
        double[] means = new double[size];
        double[] drifts = new double[size];
        double[] variances = new double[size];
        double[] windows = new double[size * lookback];
        Indicator<Num> priceIndicator = forecast.kernelPriceIndicator();
        ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator = forecast.kernelStateIndicator();
        ReturnIndicator returnIndicator = stateIndicator.getReturnIndicator();
        for (int row = 0; row < size; row++) {
            int index = fromInclusive + row;
            if (!snapshotRow(index, priceIndicator, returnIndicator, stateIndicator, factory, settings, prices, means,
                    drifts, variances, windows, row)) {
                return null;
            }
        }
        double[] params = { (double) forecast.kernelShockModel().ordinal(),
                (double) forecast.kernelVolatilityUpdateMode().ordinal(), (double) settings.horizon(),
                (double) iterations, (double) lookback, forecast.kernelVolatilityDecayFactor() };
        List<double[]> inputs = List.of(prices, means, drifts, variances, windows);
        long steps = (long) size * iterations * settings.horizon();
        long estimatedScalarNanos = steps * NANOS_PER_PATH_STEP;
        long inputBytes = ((long) size * 4 + (long) windows.length) * BYTES_PER_ELEMENT;
        long outputBytes = (long) size * iterations * BYTES_PER_ELEMENT;
        long peakBytes = inputBytes + outputBytes + steps * BYTES_PER_ELEMENT;
        double tolerance = AccelerationRuntime.approximateTolerance();
        boolean approximate = !Double.isNaN(tolerance);
        AccelerationRuntime.Determinism determinism = approximate ? AccelerationRuntime.Determinism.APPROXIMATE
                : AccelerationRuntime.Determinism.BITWISE_IDENTICAL;
        AccelerationRuntime.KernelRequest request = new AccelerationRuntime.KernelRequest(
                AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1, fromInclusive, toInclusive, iterations,
                AccelerationRuntime.NumericEncoding.FLOAT64, determinism, settings.seed(), tolerance, params, inputs,
                estimatedScalarNanos, peakBytes);
        List<Double> quantiles = List.copyOf(settings.quantileProbabilities());
        int horizon = settings.horizon();
        OperationDecoder decoder = (slice, index, decodingFactory) -> {
            List<Num> samples = new ArrayList<>(slice.length);
            for (double raw : slice) {
                if (!Double.isFinite(raw)) {
                    return Forecast.unstable(index, horizon);
                }
                samples.add(decodingFactory.numOf(raw));
            }
            return Forecast.ofSamples(index, horizon, samples, quantiles);
        };
        return new PlannedOperation(request, decoder);
    }

    private boolean snapshotRow(int index, Indicator<Num> priceIndicator, ReturnIndicator returnIndicator,
            ReturnForecastStateIndicator<? extends ReturnMomentState> stateIndicator, NumFactory factory,
            MonteCarloSettings settings, double[] prices, double[] means, double[] drifts, double[] variances,
            double[] windows, int row) {
        Num price = priceIndicator.getValue(index);
        if (!Num.isFinite(price) || !price.isPositive()) {
            return false;
        }
        prices[row] = price.doubleValue();
        ReturnMomentState rawState = stateIndicator.getValue(index);
        if (rawState == null) {
            return false;
        }
        ReturnMoments moments = rawState.moments();
        if (moments == null || moments.index() != index || !moments.isStable()
                || moments.representation() != ReturnRepresentation.LOG || moments.observationCount() <= 0) {
            return false;
        }
        Num mean = normalize(moments.mean(), factory);
        Num drift = normalize(moments.drift(), factory);
        Num variance = normalize(moments.variance(), factory);
        int lookback = settings.lookbackBarCount();
        int startIndex = index - lookback + 1;
        if (startIndex < returnIndicator.getBarSeries().getBeginIndex()) {
            return false;
        }
        means[row] = mean.doubleValue();
        drifts[row] = drift.doubleValue();
        variances[row] = variance.doubleValue();
        for (int offset = 0; offset < lookback; offset++) {
            Num value = returnIndicator.getValue(startIndex + offset);
            if (!Num.isFinite(value)) {
                return false;
            }
            Num normalized = normalize(value, factory);
            if (!Num.isFinite(normalized)) {
                return false;
            }
            windows[row * lookback + offset] = normalized.doubleValue();
        }
        return true;
    }

    private static Num normalize(Num value, NumFactory factory) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = factory.numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
    }
}
