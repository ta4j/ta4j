/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;
import java.util.function.Supplier;

import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.projection.Forecast;

/**
 * Ordered fallback chain of native forecast providers.
 *
 * <p>
 * Used only for automatic Linux selection: members are probed eagerly at
 * construction, the capability of the first available member is advertised
 * under the composite's own provider id, and evaluation executes the first
 * member whose crossover prediction clears the service threshold. A member's
 * native failure falls through to the remaining members; when every member
 * fails, the last failure propagates so the service quarantines the composite
 * under its own provider id.
 *
 * @since 0.23.1
 */
final class CompositeForecastAccelerationProvider implements ForecastAccelerationProvider {

    private final List<ForecastAccelerationProvider> members;

    CompositeForecastAccelerationProvider(List<Supplier<ForecastAccelerationProvider>> factories) {
        this.members = List.copyOf(factories.stream().map(Supplier::get).toList());
    }

    @Override
    public Capability capability() {
        Capability source = null;
        for (ForecastAccelerationProvider member : members) {
            if (member.capability().available()) {
                source = member.capability();
                break;
            }
        }
        if (source == null) {
            source = members.get(0).capability();
        }
        // The composite is selected under its own provider id ("opencl"), and
        // the service keys failure quarantine and diagnostics on
        // capability().providerId(). Reporting a member's id ("cuda") here
        // would quarantine failures under an id the service never consults,
        // so the composite advertises its own identity while keeping the
        // first available member's availability, device, and failure detail.
        return new Capability("opencl", Backend.OPENCL, source.available(), source.nativeInitialized(),
                source.deviceName(), source.detail());
    }

    @Override
    public double predictedSpeedup(Request<Forecast> request) {
        double best = 0d;
        for (ForecastAccelerationProvider member : members) {
            if (!member.capability().available()) {
                continue;
            }
            double predicted = member.predictedSpeedup(request);
            if (Double.isFinite(predicted) && predicted > best) {
                best = predicted;
            }
        }
        return best;
    }

    @Override
    public Result<Forecast> evaluate(Request<Forecast> request) {
        Result<Forecast> lastResult = null;
        NativeProviderException lastFailure = null;
        for (ForecastAccelerationProvider member : members) {
            if (!member.capability().available()) {
                continue;
            }
            double predicted = member.predictedSpeedup(request);
            if (!Double.isFinite(predicted) || predicted < CliIndicatorAccelerationService.MINIMUM_SPEEDUP) {
                continue;
            }
            try {
                Result<Forecast> result = member.evaluate(request);
                lastResult = result;
                if (result.status() == Status.EXECUTED || result.status() == Status.FAILED) {
                    return result;
                }
            } catch (NativeProviderException exception) {
                // One member's native failure must not block the remaining
                // fallback members (for example a broken CUDA device on a host
                // with a healthy OpenCL device). When every member fails, the
                // last failure still propagates so the service quarantines the
                // composite under its own provider id.
                lastFailure = exception;
            }
        }
        if (lastResult != null) {
            return lastResult;
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        ForecastAccelerationProvider first = members.get(0);
        if (first.capability().available()) {
            // No member cleared the automatic speedup threshold. Executing
            // member 0 here would run a native kernel the crossover model
            // explicitly declined (for example the unqualified CUDA lane on
            // Linux), defeating the device-capability and workload gates.
            // Report the skip so the caller falls back to scalar execution.
            return new Result<>(Status.SKIPPED, Backend.OPENCL, List.of(), false, 0L,
                    new Diagnostic(DiagnosticCode.CPU_FASTER, "opencl",
                            "no provider member cleared the automatic speedup threshold"));
        }
        // All members are unavailable; surface the first member's unavailability
        // (its evaluate performs no native work).
        return first.evaluate(request);
    }
}
