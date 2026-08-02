/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;

import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;

final class CudaAccelerationProvider implements ForecastAccelerationProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.cuda.maxBytes";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    private final Capability capability;
    private final CudaNativeBridge nativeBridge;
    private final CudaProbeResult probe;

    CudaAccelerationProvider(Capability capability, CudaNativeBridge nativeBridge, CudaProbeResult probe) {
        this.capability = capability;
        this.nativeBridge = nativeBridge;
        this.probe = probe;
    }

    @Override
    public Capability capability() {
        return capability;
    }

    @Override
    public double predictedSpeedup(Request<Forecast> request) {
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
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
    public Result<Forecast> evaluate(Request<Forecast> request) {
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        return evaluateCuda(request, forecast);
    }

    private Result<Forecast> evaluateCuda(Request<Forecast> request, MonteCarloPriceForecastIndicator forecast) {
        long started = System.nanoTime();
        ForecastSnapshot snapshot = ForecastSnapshot.capture(forecast, request.fromInclusive(), request.toInclusive(),
                "CUDA");
        validateMemoryCeiling(snapshot.estimatedCudaBytes());
        CudaEvaluationResult nativeResult;
        try {
            nativeResult = nativeBridge.evaluate(snapshot.nativeRequest());
        } catch (LinkageError | RuntimeException exception) {
            throw new NativeProviderException("CUDA", exception);
        }
        List<Forecast> values = snapshot.materializeRows(nativeResult, "CUDA");
        Diagnostic timing = new Diagnostic(DiagnosticCode.ACCELERATED, capability.providerId(),
                "CUDA timings total=%.3fms transfer=%.3fms kernel=%.3fms reduction=%.3fms".formatted(
                        nativeResult.totalMicros() / 1_000d, nativeResult.transferMicros() / 1_000d,
                        nativeResult.kernelMicros() / 1_000d, nativeResult.reductionMicros() / 1_000d));
        return Result.executed(Backend.CUDA, values, true, System.nanoTime() - started, timing);
    }

    private void validateMemoryCeiling(long requiredBytes) {
        long configured = Long.getLong(MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES);
        if (configured <= 0L) {
            throw new IllegalArgumentException(MAX_MEMORY_PROPERTY + " must be > 0");
        }
        long deviceCeiling = Math.max(1L, probe.freeMemoryBytes() / 2L);
        long ceiling = Math.min(configured, deviceCeiling);
        if (requiredBytes > ceiling) {
            throw new IllegalArgumentException("CUDA request needs %,d bytes, above the %,d-byte provider ceiling"
                    .formatted(requiredBytes, ceiling));
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

}
