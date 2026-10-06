/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.ta4j.core.acceleration.AccelerationPlan;
import org.ta4j.core.acceleration.AccelerationPlans;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.indicators.forecast.projection.Forecast;

/**
 * Measures native execution before a device has automatic-selection
 * qualification.
 */
final class NativeBenchmarkSupport {
    private NativeBenchmarkSupport() {
    }

    static long evaluate(Provider provider, MonteCarloPriceForecastIndicator forecast, int from, int to) {
        long started = System.nanoTime();
        AccelerationPlan<Forecast> plan = MonteCarloShockPathPlanner.plan(forecast, from, to, Long.MAX_VALUE);
        assertThat(AccelerationPlans.isPlanned(plan)).as("planner declined: %s", AccelerationPlans.reason(plan))
                .isTrue();
        KernelRequest request = AccelerationPlans.request(plan);
        AccelerationPlan.Decoder<Forecast> decoder = AccelerationPlans.decoder(plan);
        int first = request.fromInclusive();
        assertThat(request.determinism()).as("benchmark requires an explicit approximate tolerance")
                .isEqualTo(Determinism.APPROXIMATE);
        KernelResult result = provider.execute(request);
        assertThat(result.nativeInitialized()).isTrue();
        double[] outputs = result.outputs();
        int width = request.outputsPerIndex();
        assertThat(outputs).hasSize(request.expectedOutputLength());
        for (int offset = 0; offset < request.size(); offset++) {
            double[] slice = Arrays.copyOfRange(outputs, offset * width, (offset + 1) * width);
            Forecast value = decoder.decode(slice, first + offset);
            assertThat(value.isStable()).isTrue();
        }
        return System.nanoTime() - started;
    }
}
