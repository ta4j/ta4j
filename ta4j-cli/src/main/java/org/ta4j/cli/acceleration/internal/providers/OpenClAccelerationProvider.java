/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
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
        // No measured device-specific qualification exists for the OpenCL lane:
        // unlike CUDA (whose crossover model is fit to RTX 5090 measurements),
        // any predicted speedup here would be fabricated and could select
        // devices that are substantially slower than the JIT scalar path.
        // Automatic selection therefore keeps eligible-but-unqualified devices
        // on scalar execution; forcing this provider through the qualification
        // property still bypasses the speedup gate for measurement runs.
        return 0d;
    }

    @Override
    public Result<Forecast> evaluate(Request<Forecast> request) {
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        return evaluateOpenCl(request, forecast);
    }

    private Result<Forecast> evaluateOpenCl(Request<Forecast> request, MonteCarloPriceForecastIndicator forecast) {
        long started = System.nanoTime();
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long decisions = (long) request.toInclusive() - request.fromInclusive() + 1L;
        // The native kernels sort samples padded to the next power of two
        // (bitonic sort) and hold both the host staging array (padded_host) and
        // the device sort buffer (device_samples) at paddedIterations doubles,
        // so the ceiling must count two padded sample buffers — otherwise a
        // request just above a power-of-two boundary allocates nearly four
        // times its raw iteration estimate on host and device.
        long paddedIterations = nextPowerOfTwo(spec.iterationCount());
        long sampleBufferIterations = Math.multiplyExact(paddedIterations, 2L);
        // The batched pipeline also keeps one contiguous device-side copy of
        // every decision's lookback history (device_history), which
        // estimatedPeakBytes does not model because it assumes per-decision
        // staging only.
        long deviceHistoryBytes = Math.multiplyExact(Math.multiplyExact(decisions, spec.lookbackBarCount()),
                Double.BYTES);
        // Profiling keeps up to eight retained events per stable decision
        // (cl_event handle plus kind marker) until the single final clFinish,
        // an O(decisions) host-side cost the per-decision estimate cannot see.
        long profilingBytes = Math.multiplyExact(decisions, 128L);
        validateMemoryCeiling(Math.addExact(
                Math.addExact(ForecastSnapshot.estimatedPeakBytes(decisions, spec.lookbackBarCount(),
                        sampleBufferIterations, spec.quantileProbabilities().size(), false, 1L), deviceHistoryBytes),
                profilingBytes));
        ForecastSnapshot snapshot = ForecastSnapshot.capture(forecast, request.fromInclusive(), request.toInclusive(),
                "OpenCL");
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

    /**
     * Mirrors the native {@code next_power_of_two} used to size the bitonic-sort
     * sample buffer.
     */
    private static long nextPowerOfTwo(long value) {
        long power = 1L;
        while (power < value) {
            power <<= 1;
        }
        return power;
    }

}

