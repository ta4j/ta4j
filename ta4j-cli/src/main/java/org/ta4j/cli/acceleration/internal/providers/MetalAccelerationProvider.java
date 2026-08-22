/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;

final class MetalAccelerationProvider implements ForecastAccelerationProvider {

    static final String MAX_MEMORY_PROPERTY = "ta4j.acceleration.metal.maxBytes";

    static final String APPROXIMATE_PROPERTY = "ta4j.cli.acceleration.metal.approximate";

    private static final long DEFAULT_MAX_MEMORY_BYTES = 512L * 1024L * 1024L;

    private final Capability capability;
    private final MetalNativeBridge nativeBridge;
    private final MetalProbeResult probe;
    private final boolean approximateEnabled;

    MetalAccelerationProvider(Capability capability, MetalNativeBridge nativeBridge, MetalProbeResult probe,
            boolean approximateEnabled) {
        this.capability = capability;
        this.nativeBridge = nativeBridge;
        this.probe = probe;
        this.approximateEnabled = approximateEnabled;
    }

    @Override
    public Capability capability() {
        return capability;
    }

    @Override
    public double predictedSpeedup(Request<Forecast> request) {
        if (!approximateEnabled) {
            return 0d;
        }
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long decisions = (long) request.toInclusive() - request.fromInclusive() + 1L;
        long work;
        try {
            work = Math.multiplyExact(Math.multiplyExact(decisions, spec.iterationCount()), spec.horizon());
        } catch (ArithmeticException exception) {
            return 0d;
        }
        return MetalCrossoverModel.predictedSpeedup(probe, work);
    }

    @Override
    public Result<Forecast> evaluate(Request<Forecast> request) {
        if (!approximateEnabled) {
            Diagnostic diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_UNAVAILABLE, capability.providerId(),
                    "approximate fp32 results require opt-in via -D" + APPROXIMATE_PROPERTY + "=true");
            return Result.notExecuted(Status.UNAVAILABLE, Backend.METAL, diagnostic);
        }
        long started = System.nanoTime();
        MonteCarloPriceForecastIndicator forecast = (MonteCarloPriceForecastIndicator) request.indicator();
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        long ceiling = memoryCeiling();
        int decisionsPerChunk = decisionsPerChunk(spec, ceiling);
        SeriesStamp requestStamp = SeriesStamp.capture(request.series());
        List<Forecast> values = new ArrayList<>(request.size());
        double totalMicros = 0d;
        double transferMicros = 0d;
        double kernelMicros = 0d;
        int chunks = 0;
        for (int from = request.fromInclusive(); from <= request.toInclusive();) {
            int to = (int) Math.min(request.toInclusive(), (long) from + decisionsPerChunk - 1L);
            ForecastSnapshot snapshot = ForecastSnapshot.capture(forecast, from, to, "Metal");
            MetalEvaluationResult nativeResult;
            try {
                nativeResult = nativeBridge.evaluate(snapshot.nativeRequest());
            } catch (LinkageError | RuntimeException exception) {
                throw new NativeProviderException("Metal", exception);
            }
            values.addAll(snapshot.materializeSamples(nativeResult.terminalPrices(), "Metal"));
            totalMicros += nativeResult.totalMicros();
            transferMicros += nativeResult.transferMicros();
            kernelMicros += nativeResult.kernelMicros();
            chunks++;
            if (to == request.toInclusive()) {
                break;
            }
            from = Math.addExact(to, 1);
        }
        requestStamp.requireCurrent(request.series(), "during chunked Metal evaluation");
        Diagnostic timing = new Diagnostic(DiagnosticCode.ACCELERATED, capability.providerId(),
                "Metal chunks=%d approx=fp32 timings total=%.3fms transfer=%.3fms kernel=%.3fms".formatted(chunks,
                        totalMicros / 1_000d, transferMicros / 1_000d, kernelMicros / 1_000d));
        return Result.executed(Backend.METAL, List.copyOf(values), true, System.nanoTime() - started, timing);
    }

    private long memoryCeiling() {
        long configured = Long.getLong(MAX_MEMORY_PROPERTY, DEFAULT_MAX_MEMORY_BYTES);
        if (configured <= 0L) {
            throw new IllegalArgumentException(MAX_MEMORY_PROPERTY + " must be > 0");
        }
        long deviceCeiling = Math.max(1L, probe.recommendedMaxWorkingSetBytes() / 2L);
        return Math.min(configured, deviceCeiling);
    }

    static int decisionsPerChunk(MonteCarloPriceForecastSpec spec, long ceiling) {
        // Per decision: 5 double parameters + 1 int flag + lookback history
        // doubles, staged once in Java and copied once to the device (factor
        // 2), plus 68 bytes per sample while Forecast.ofSamples normalizes and
        // sorts: the two defensive terminal-price float-array copies (8 bytes),
        // the original DoubleNum and its normalized copy (2 x 24 bytes), and
        // the three live reference arrays (input, normalized, sorted samples:
        // 3 x 4 bytes).
        long inputs = Math.multiplyExact(Math.addExact(5L * Double.BYTES + Integer.BYTES,
                Math.multiplyExact((long) spec.lookbackBarCount(), Double.BYTES)), 2L);
        long outputs = Math.multiplyExact((long) spec.iterationCount(), 68L);
        long bytesPerDecision = Math.addExact(inputs, outputs);
        long capacity = ceiling / bytesPerDecision;
        long nativeCellCapacity = Integer.MAX_VALUE / (long) spec.iterationCount();
        long nativeHistoryCapacity = Integer.MAX_VALUE / (long) spec.lookbackBarCount();
        capacity = Math.min(capacity, Math.min(nativeCellCapacity, nativeHistoryCapacity));
        if (capacity < 1L) {
            throw new IllegalArgumentException("Metal needs %,d bytes per decision, above the %,d-byte provider ceiling"
                    .formatted(bytesPerDecision, ceiling));
        }
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }
}

final class MetalCrossoverModel {

    private static final long QUALIFIED_MINIMUM_WORK = 16_777_216L;

    private MetalCrossoverModel() {
    }

    static double predictedSpeedup(MetalProbeResult probe, long work) {
        if (!probe.deviceName().contains("M5 Max") || work < QUALIFIED_MINIMUM_WORK) {
            return 0d;
        }
        return 0.25d;
    }
}
