/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationDiagnostic;
import org.ta4j.core.acceleration.AccelerationDiagnosticCode;
import org.ta4j.core.acceleration.AccelerationDiagnostics;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndexedIndicatorValue;
import org.ta4j.core.acceleration.IndicatorBatchEvaluator;
import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class CudaAccelerationProvider implements IndicatorAccelerationProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.cuda.maxBytes";
    static final String HYBRID_TIMEOUT_SECONDS_PROPERTY = "ta4j.acceleration.cuda.hybridTimeoutSeconds";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    private final ProviderCapability capability;
    private final CudaNativeBridge nativeBridge;
    private final CudaProbeResult probe;

    CudaAccelerationProvider(ProviderCapability capability, CudaNativeBridge nativeBridge, CudaProbeResult probe) {
        this.capability = capability;
        this.nativeBridge = nativeBridge;
        this.probe = probe;
    }

    @Override
    public ProviderCapability capability() {
        return capability;
    }

    @Override
    public <T> double predictedSpeedup(IndicatorBatchRequest<T> request, AdapterMatch<T> match) {
        if (!ForecastBatchAdapter.OPERATION_ID.equals(match.operationId())
                || !(request.indicator() instanceof MonteCarloPriceForecastIndicator forecast)) {
            return 0d;
        }
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long decisions = (long) request.toInclusive() - request.fromInclusive() + 1L;
        long work;
        try {
            work = Math.multiplyExact(Math.multiplyExact(decisions, spec.iterationCount()), spec.horizon());
        } catch (ArithmeticException exception) {
            return 0d;
        }
        return CudaCrossoverModel.predictedSpeedup(probe, work);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<IndicatorBatchResult<T>> evaluate(IndicatorBatchRequest<T> request, AdapterMatch<T> match) {
        if (!ForecastBatchAdapter.OPERATION_ID.equals(match.operationId())
                || !(request.indicator() instanceof MonteCarloPriceForecastIndicator forecast)) {
            return Optional.empty();
        }
        if (request.config().mode() == AccelerationMode.HYBRID && request.toInclusive() > request.fromInclusive()) {
            return evaluateHybrid(request, match, forecast);
        }
        return evaluateCuda(request, match, forecast);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<IndicatorBatchResult<T>> evaluateCuda(IndicatorBatchRequest<T> request, AdapterMatch<T> match,
            MonteCarloPriceForecastIndicator forecast) {
        CudaForecastSnapshot snapshot = CudaForecastSnapshot.capture(forecast, request.fromInclusive(),
                request.toInclusive(), probe);
        CudaEvaluationResult nativeResult = nativeBridge.evaluate(snapshot.nativeRequest());
        List<IndexedIndicatorValue<Forecast>> values = snapshot.materialize(nativeResult);
        AccelerationDiagnostic timing = new AccelerationDiagnostic(
                AccelerationDiagnosticCode.NATIVE_PROVIDER_INITIALIZED,
                "CUDA timings total=%.3fms transfer=%.3fms kernel=%.3fms reduction=%.3fms".formatted(
                        nativeResult.totalMicros() / 1_000d, nativeResult.transferMicros() / 1_000d,
                        nativeResult.kernelMicros() / 1_000d, nativeResult.reductionMicros() / 1_000d),
                capability.providerId(), match.operationId());
        AccelerationDiagnostics diagnostics = new AccelerationDiagnostics(request.config().mode(),
                AccelerationMode.CUDA, capability.providerId(), match.operationId(), true, List.of(timing));
        return Optional.of((IndicatorBatchResult<T>) new IndicatorBatchResult<>(values, diagnostics));
    }

    private <T> Optional<IndicatorBatchResult<T>> evaluateHybrid(IndicatorBatchRequest<T> request,
            AdapterMatch<T> match, MonteCarloPriceForecastIndicator forecast) {
        int decisionCount = Math.addExact(Math.subtractExact(request.toInclusive(), request.fromInclusive()), 1);
        int cpuCount = Math.max(1,
                Math.min(decisionCount - 1, (int) Math.ceil(decisionCount * CudaCrossoverModel.hybridCpuFraction())));
        int cpuToInclusive = Math.addExact(request.fromInclusive(), cpuCount - 1);
        int cudaFromInclusive = Math.addExact(cpuToInclusive, 1);
        IndicatorBatchRequest<T> cudaRequest = new IndicatorBatchRequest<>(request.indicator(), cudaFromInclusive,
                request.toInclusive(), request.config());
        long timeoutSeconds = Long.getLong(HYBRID_TIMEOUT_SECONDS_PROPERTY, 300L);
        if (timeoutSeconds < 1L) {
            throw new IllegalArgumentException(HYBRID_TIMEOUT_SECONDS_PROPERTY + " must be >= 1");
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<IndicatorBatchResult<T>> cpu = executor.submit(() -> IndicatorBatchEvaluator
                .evaluate(request.indicator(), request.fromInclusive(), cpuToInclusive, AccelerationConfig.cpu()));
        Future<IndicatorBatchResult<T>> cuda = executor
                .submit(() -> evaluateCuda(cudaRequest, match, forecast).orElseThrow());
        try {
            IndicatorBatchResult<T> cpuResult = cpu.get(timeoutSeconds, TimeUnit.SECONDS);
            IndicatorBatchResult<T> cudaResult = cuda.get(timeoutSeconds, TimeUnit.SECONDS);
            List<IndexedIndicatorValue<T>> values = new ArrayList<>(decisionCount);
            values.addAll(cpuResult.values());
            values.addAll(cudaResult.values());
            validateHybridCoverage(values, request.fromInclusive(), request.toInclusive());
            List<AccelerationDiagnostic> diagnostics = new ArrayList<>(cudaResult.diagnostics().diagnostics());
            diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.NATIVE_PROVIDER_INITIALIZED,
                    "HYBRID completed CPU indices [%d, %d] and CUDA indices [%d, %d]".formatted(request.fromInclusive(),
                            cpuToInclusive, cudaFromInclusive, request.toInclusive()),
                    capability.providerId(), match.operationId()));
            AccelerationDiagnostics execution = new AccelerationDiagnostics(AccelerationMode.HYBRID,
                    AccelerationMode.HYBRID, "cpu+cuda", match.operationId(), true, diagnostics);
            return Optional.of(new IndicatorBatchResult<>(values, execution));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HYBRID execution was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("HYBRID execution failed", cause);
        } catch (TimeoutException exception) {
            cpu.cancel(true);
            cuda.cancel(true);
            throw new IllegalStateException("HYBRID execution exceeded " + timeoutSeconds + " seconds", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void validateHybridCoverage(List<? extends IndexedIndicatorValue<?>> values, int fromInclusive,
            int toInclusive) {
        int expectedCount = Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
        if (values.size() != expectedCount) {
            throw new IllegalStateException("HYBRID partitions did not cover the complete request");
        }
        for (int offset = 0; offset < expectedCount; offset++) {
            if (values.get(offset).index() != fromInclusive + offset) {
                throw new IllegalStateException("HYBRID partitions returned missing, duplicate, or unordered indices");
            }
        }
    }

    private record CudaForecastSnapshot(BarSeries series, SeriesStamp stamp, NumFactory numFactory, int fromInclusive,
            int decisionCount, int horizon, int iterationCount, double[] quantiles, CudaNativeRequest nativeRequest) {

        private static CudaForecastSnapshot capture(MonteCarloPriceForecastIndicator indicator, int fromInclusive,
                int toInclusive, CudaProbeResult probe) {
            MonteCarloPriceForecastSpec spec = indicator.accelerationSpec();
            BarSeries series = indicator.getBarSeries();
            if (series.numFactory() != DoubleNumFactory.getInstance()) {
                throw new IllegalArgumentException("CUDA forecast provider requires DoubleNum precision");
            }
            if (!IndicatorUtils.isSameSeries(spec.priceIndicator().getBarSeries(), series)
                    || !IndicatorUtils.isSameSeries(spec.stateIndicator().getBarSeries(), series) || !IndicatorUtils
                            .isSameSeries(spec.stateIndicator().getReturnIndicator().getBarSeries(), series)) {
                throw new IllegalArgumentException("CUDA forecast sources must share one BarSeries instance");
            }
            int decisionCount = Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
            int historyLength = Math.multiplyExact(decisionCount, spec.lookbackBarCount());
            validateMemoryCeiling(decisionCount, historyLength, spec, probe);

            SeriesStamp before = SeriesStamp.capture(series);
            int[] stable = new int[decisionCount];
            double[] prices = new double[decisionCount];
            double[] means = new double[decisionCount];
            double[] drifts = new double[decisionCount];
            double[] variances = new double[decisionCount];
            double[] historicalReturns = new double[historyLength];
            ReturnIndicator returnIndicator = spec.stateIndicator().getReturnIndicator();
            for (int offset = 0; offset < decisionCount; offset++) {
                int index = Math.addExact(fromInclusive, offset);
                if (!captureDecision(indicator, spec, returnIndicator, index, offset, stable, prices, means, drifts,
                        variances, historicalReturns)) {
                    stable[offset] = 0;
                }
            }
            before.requireCurrent(series, "during CUDA snapshot capture");
            double[] quantiles = spec.quantileProbabilities().stream().mapToDouble(Double::doubleValue).toArray();
            CudaNativeRequest nativeRequest = new CudaNativeRequest(fromInclusive, decisionCount, spec.horizon(),
                    spec.iterationCount(), spec.lookbackBarCount(), spec.seed(), spec.shockModel().ordinal(),
                    spec.volatilityUpdateMode().ordinal(), spec.volatilityDecayFactor(), quantiles, stable, prices,
                    means, drifts, variances, historicalReturns);
            return new CudaForecastSnapshot(series, before, series.numFactory(), fromInclusive, decisionCount,
                    spec.horizon(), spec.iterationCount(), quantiles, nativeRequest);
        }

        private static boolean captureDecision(MonteCarloPriceForecastIndicator indicator,
                MonteCarloPriceForecastSpec spec, ReturnIndicator returnIndicator, int index, int offset, int[] stable,
                double[] prices, double[] means, double[] drifts, double[] variances, double[] historicalReturns) {
            if (index < indicator.getCountOfUnstableBars() || index < indicator.getBarSeries().getRemovedBarsCount()) {
                return false;
            }
            Num price = spec.priceIndicator().getValue(index);
            ReturnMomentState state = spec.stateIndicator().getValue(index);
            ReturnMoments moments = state == null ? null : state.moments();
            if (!finitePositive(price) || moments == null || moments.index() != index || !moments.isStable()
                    || moments.representation() != ReturnRepresentation.LOG || !finite(moments.mean())
                    || !finite(moments.drift()) || !finiteNonNegative(moments.variance())) {
                return false;
            }
            int historyStart = index - spec.lookbackBarCount() + 1;
            if (historyStart < indicator.getBarSeries().getRemovedBarsCount()) {
                return false;
            }
            int historyOffset = Math.multiplyExact(offset, spec.lookbackBarCount());
            for (int i = 0; i < spec.lookbackBarCount(); i++) {
                Num value = returnIndicator.getValue(historyStart + i);
                if (!finite(value)) {
                    return false;
                }
                historicalReturns[historyOffset + i] = value.doubleValue();
            }
            stable[offset] = 1;
            prices[offset] = price.doubleValue();
            means[offset] = moments.mean().doubleValue();
            drifts[offset] = moments.drift().doubleValue();
            variances[offset] = moments.variance().doubleValue();
            return true;
        }

        private List<IndexedIndicatorValue<Forecast>> materialize(CudaEvaluationResult result) {
            stamp.requireCurrent(series, "before CUDA result publication");
            int rowLength = 4 + quantiles.length;
            double[] rows = result.rows();
            if (rows.length != Math.multiplyExact(decisionCount, rowLength)) {
                throw new IllegalStateException("CUDA result length does not match the immutable request");
            }
            List<IndexedIndicatorValue<Forecast>> values = new ArrayList<>(decisionCount);
            for (int offset = 0; offset < decisionCount; offset++) {
                int rowOffset = offset * rowLength;
                int status = (int) rows[rowOffset];
                int index = fromInclusive + offset;
                Forecast forecast = switch (status) {
                case 0 -> stableForecast(index, rowOffset, rows);
                case 1, 2 -> Forecast.unstable(index, horizon);
                default -> throw new IllegalStateException("CUDA decision " + index + " failed with status " + status);
                };
                values.add(new IndexedIndicatorValue<>(index, forecast));
            }
            stamp.requireCurrent(series, "while publishing CUDA results");
            return List.copyOf(values);
        }

        private Forecast stableForecast(int index, int rowOffset, double[] rows) {
            double mean = requireFinite(rows[rowOffset + 1], "mean", index);
            double median = requireFinite(rows[rowOffset + 2], "median", index);
            double standardDeviation = requireFinite(rows[rowOffset + 3], "standard deviation", index);
            if (standardDeviation < 0d) {
                throw new IllegalStateException("CUDA standard deviation is negative at index " + index);
            }
            Map<Double, Num> mappedQuantiles = new LinkedHashMap<>();
            double previous = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < quantiles.length; i++) {
                double value = requireFinite(rows[rowOffset + 4 + i], "quantile", index);
                if (value < previous) {
                    throw new IllegalStateException("CUDA quantiles are not monotone at index " + index);
                }
                previous = value;
                mappedQuantiles.put(quantiles[i], numFactory.numOf(value));
                if (Double.doubleToLongBits(quantiles[i]) == Double.doubleToLongBits(0.5d)) {
                    median = value;
                }
            }
            return Forecast.builder(index, horizon, numFactory, ForecastSupport.empirical(iterationCount))
                    .mean(numFactory.numOf(mean))
                    .median(numFactory.numOf(median))
                    .standardDeviation(numFactory.numOf(standardDeviation))
                    .quantiles(mappedQuantiles)
                    .build();
        }

        private static void validateMemoryCeiling(int decisionCount, int historyLength,
                MonteCarloPriceForecastSpec spec, CudaProbeResult probe) {
            long configured = Long.getLong(MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES);
            if (configured <= 0L) {
                throw new IllegalArgumentException(MAX_MEMORY_PROPERTY + " must be > 0");
            }
            long deviceCeiling = Math.max(1L, probe.freeMemoryBytes() / 2L);
            long ceiling = Math.min(configured, deviceCeiling);
            long inputDoubles = Math.addExact(Math.multiplyExact((long) decisionCount, 4L), historyLength);
            long outputDoubles = Math.addExact(spec.iterationCount(),
                    Math.multiplyExact((long) decisionCount, 4L + spec.quantileProbabilities().size()));
            long bytes = Math.multiplyExact(Math.addExact(inputDoubles, outputDoubles), Double.BYTES);
            if (bytes > ceiling) {
                throw new IllegalArgumentException(
                        "CUDA request needs %,d bytes, above the %,d-byte provider ceiling".formatted(bytes, ceiling));
            }
        }

        private static boolean finite(Num value) {
            return Num.isFinite(value) && Double.isFinite(value.doubleValue());
        }

        private static boolean finitePositive(Num value) {
            return finite(value) && value.isPositive();
        }

        private static boolean finiteNonNegative(Num value) {
            return finite(value) && !value.isNegative();
        }

        private static double requireFinite(double value, String field, int index) {
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("CUDA " + field + " is non-finite at index " + index);
            }
            return value;
        }
    }

    private record SeriesStamp(int beginIndex, int endIndex, int removedBars, int barCount, long historyRevision,
            long dataFingerprint) {

        private static SeriesStamp capture(BarSeries series) {
            return new SeriesStamp(series.getBeginIndex(), series.getEndIndex(), series.getRemovedBarsCount(),
                    series.getBarCount(), series.getBarHistoryRevision(), fingerprint(series));
        }

        private void requireCurrent(BarSeries series, String phase) {
            if (!equals(capture(series))) {
                throw new IllegalStateException("BarSeries changed " + phase);
            }
        }

        private static long fingerprint(BarSeries series) {
            long value = 0xCBF29CE484222325L;
            for (Bar bar : series.getBarData()) {
                value = mix(value, bar.getBeginTime().getEpochSecond());
                value = mix(value, bar.getBeginTime().getNano());
                value = mix(value, bar.getEndTime().getEpochSecond());
                value = mix(value, bar.getEndTime().getNano());
                value = mix(value, bits(bar.getOpenPrice()));
                value = mix(value, bits(bar.getHighPrice()));
                value = mix(value, bits(bar.getLowPrice()));
                value = mix(value, bits(bar.getClosePrice()));
                value = mix(value, bits(bar.getVolume()));
                value = mix(value, bits(bar.getAmount()));
                value = mix(value, bar.getTrades());
            }
            return value;
        }

        private static long mix(long hash, long value) {
            return (hash ^ value) * 0x100000001B3L;
        }

        private static long bits(Num value) {
            return value == null ? 0x7FF8000000000001L : Double.doubleToLongBits(value.doubleValue());
        }
    }
}

final class CudaCrossoverModel {

    private static final long QUALIFIED_MINIMUM_WORK = Long.MAX_VALUE;

    private CudaCrossoverModel() {
    }

    static double predictedSpeedup(CudaProbeResult probe, long work) {
        if (probe.computeMajor() != 12 || probe.computeMinor() != 0 || work < QUALIFIED_MINIMUM_WORK) {
            return 0d;
        }
        return 0.25d;
    }

    static double hybridCpuFraction() {
        return 0.10d;
    }
}
