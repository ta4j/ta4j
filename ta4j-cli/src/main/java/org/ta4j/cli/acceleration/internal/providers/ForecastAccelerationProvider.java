/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.projection.Forecast;

interface ForecastAccelerationProvider {

    Capability capability();

    double predictedSpeedup(Request<Forecast> request);

    Result<Forecast> evaluate(Request<Forecast> request);
}

record Capability(String providerId, Backend backend, boolean available, boolean nativeInitialized, String deviceName,
        String detail) {

    Capability {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(backend, "backend must not be null");
        Objects.requireNonNull(deviceName, "deviceName must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
    }
}

final class UnavailableForecastProvider implements ForecastAccelerationProvider {

    private final Capability capability;

    UnavailableForecastProvider(Capability capability) {
        this.capability = capability;
    }

    @Override
    public Capability capability() {
        return capability;
    }

    @Override
    public double predictedSpeedup(Request<Forecast> request) {
        return 0d;
    }

    @Override
    public Result<Forecast> evaluate(Request<Forecast> request) {
        Diagnostic diagnostic = new Diagnostic(DiagnosticCode.PROVIDER_UNAVAILABLE, capability.providerId(),
                capability.detail());
        return new Result<>(Status.UNAVAILABLE, capability.backend(), List.of(), capability.nativeInitialized(), 0L,
                diagnostic);
    }
}

final class NativeProviderException extends RuntimeException {

    NativeProviderException(String backend, Throwable cause) {
        super(backend + " native execution failed: " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null || cause.getMessage().isBlank() ? "" : ": " + cause.getMessage()), cause);
    }
}
