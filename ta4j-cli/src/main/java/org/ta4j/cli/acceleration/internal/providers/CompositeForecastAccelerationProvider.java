/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;
import java.util.function.Supplier;

import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.projection.Forecast;

/**
 * Ordered fallback chain of native forecast providers.
 *
 * <p>
 * Used only for automatic Linux selection: members are probed eagerly at
 * construction, the capability of the first available member is advertised, and
 * evaluation executes the first member whose crossover prediction clears the
 * service threshold. Native failures propagate so the service quarantines the
 * composite under its own provider id.
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
        for (ForecastAccelerationProvider member : members) {
            if (member.capability().available()) {
                return member.capability();
            }
        }
        return members.get(0).capability();
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
        for (ForecastAccelerationProvider member : members) {
            if (!member.capability().available()) {
                continue;
            }
            double predicted = member.predictedSpeedup(request);
            if (!Double.isFinite(predicted) || predicted < CliIndicatorAccelerationService.MINIMUM_SPEEDUP) {
                continue;
            }
            Result<Forecast> result = member.evaluate(request);
            lastResult = result;
            if (result.status() == Status.EXECUTED || result.status() == Status.FAILED) {
                return result;
            }
        }
        if (lastResult != null) {
            return lastResult;
        }
        return members.get(0).evaluate(request);
    }
}
