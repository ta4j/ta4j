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

    /**
     * Smallest per-decision path count measured in the RTX 5090 crossover matrix.
     */
    private static final long MIN_QUALIFIED_ITERATION_COUNT = 64L;

    @Override
    public double predictedSpeedup(Request<Forecast> request) {
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long decisions = (long) request.toInclusive() - request.fromInclusive() + 1L;
        // The measured RTX 5090 crossover matrix never qualified shapes with
        // fewer paths than this: below it a decision exposes too few path
        // threads to amortize transfer, launch, reduction, and sort overhead,
        // even when decisions x paths x horizon clears the work floor.
        if (spec.iterationCount() < MIN_QUALIFIED_ITERATION_COUNT) {
            return 0d;
        }
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
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long decisions = (long) request.toInclusive() - request.fromInclusive() + 1L;
        // estimatedPeakBytes counts one host snapshot plus one device staging
        // copy of the per-decision inputs. The batched pipeline additionally
        // uploads the full decision x lookback return matrix as its own
        // contiguous device buffer, so account for that third copy here.
        validateMemoryCeiling(Math.addExact(
                ForecastSnapshot.estimatedPeakBytes(decisions, spec.lookbackBarCount(), spec.iterationCount(),
                        spec.quantileProbabilities().size(), false, decisions),
                Math.multiplyExact(Math.multiplyExact(decisions, (long) spec.lookbackBarCount()), Double.BYTES)));
        ForecastSnapshot snapshot = ForecastSnapshot.capture(forecast, request.fromInclusive(), request.toInclusive(),
                "CUDA");
        CudaEvaluationResult nativeResult;
        try {
            nativeResult = nativeBridge.evaluate(snapshot.nativeRequest());
        } catch (LinkageError | RuntimeException exception) {
            throw new NativeProviderException("CUDA", exception);
        }
        List<Forecast> values = snapshot.materializeRows(nativeResult.rows(), "CUDA");
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

    /**
     * Minimum total Monte Carlo samples before the CUDA lane is predicted to win.
     * Windows x86_64 qualification on an RTX 5090 (compute capability 12.0, CUDA
     * 13.3) measured scalar parity below this workload and CUDA wins from
     * {@code 3.4x} up to {@code 68.9x} at or above it once decision batches execute
     * as a single enqueued pipeline.
     */
    private static final long QUALIFIED_MINIMUM_WORK = 262_144L;

    private CudaCrossoverModel() {
    }

    static double predictedSpeedup(CudaProbeResult probe, long work) {
        if (probe.computeMajor() != 12 || probe.computeMinor() != 0 || work < QUALIFIED_MINIMUM_WORK) {
            return 0d;
        }
        return 0.25d;
    }

}
