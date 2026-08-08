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

final class OpenClAccelerationProvider implements ForecastAccelerationProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.opencl.maxBytes";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    private final Capability capability;
    private final OpenClNativeBridge nativeBridge;
    private final OpenClProbeResult probe;

    OpenClAccelerationProvider(Capability capability, OpenClNativeBridge nativeBridge, OpenClProbeResult probe) {
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
        return OpenClCrossoverModel.predictedSpeedup(probe, work);
    }

    @Override
    public Result<Forecast> evaluate(Request<Forecast> request) {
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        return evaluateOpenCl(request, forecast);
    }

    private Result<Forecast> evaluateOpenCl(Request<Forecast> request, MonteCarloPriceForecastIndicator forecast) {
        long started = System.nanoTime();
        ForecastSnapshot snapshot = ForecastSnapshot.capture(forecast, request.fromInclusive(), request.toInclusive(),
                "OpenCL");
        validateMemoryCeiling(snapshot.estimatedNativeBytes());
        OpenClEvaluationResult nativeResult;
        try {
            nativeResult = nativeBridge.evaluate(snapshot.nativeRequest());
        } catch (LinkageError | RuntimeException exception) {
            throw new NativeProviderException("OpenCL", exception);
        }
        List<Forecast> values = snapshot.materializeRows(nativeResult.rows(), "OpenCL");
        Diagnostic timing = new Diagnostic(DiagnosticCode.ACCELERATED, capability.providerId(),
                "OpenCL timings total=%.3fms transfer=%.3fms kernel=%.3fms reduction=%.3fms".formatted(
                        nativeResult.totalMicros() / 1_000d, nativeResult.transferMicros() / 1_000d,
                        nativeResult.kernelMicros() / 1_000d, nativeResult.reductionMicros() / 1_000d));
        return Result.executed(Backend.OPENCL, values, true, System.nanoTime() - started, timing);
    }

    private void validateMemoryCeiling(long requiredBytes) {
        long configured = Long.getLong(MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES);
        if (configured <= 0L) {
            throw new IllegalArgumentException(MAX_MEMORY_PROPERTY + " must be > 0");
        }
        long deviceCeiling = Math.max(1L, probe.freeMemoryBytes() / 2L);
        long ceiling = Math.min(configured, deviceCeiling);
        if (requiredBytes > ceiling) {
            throw new IllegalArgumentException("OpenCL request needs %,d bytes, above the %,d-byte provider ceiling"
                    .formatted(requiredBytes, ceiling));
        }
    }

}

final class OpenClCrossoverModel {

    /**
     * Conservative workload floor mirroring the Metal provider's qualified minimum;
     * placeholder pending real-GPU speedup measurement.
     */
    private static final long QUALIFIED_MINIMUM_WORK = 16_777_216L;

    private OpenClCrossoverModel() {
    }

    static double predictedSpeedup(OpenClProbeResult probe, long work) {
        if (!probe.gpuDevice() || work < QUALIFIED_MINIMUM_WORK) {
            return 0d;
        }
        return 0.25d;
    }

}
