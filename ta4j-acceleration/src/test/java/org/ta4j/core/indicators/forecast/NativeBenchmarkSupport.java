/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.acceleration.PlannedOperation;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.num.NumFactory;

/**
 * Measures native execution before a device has automatic-selection
 * qualification.
 */
final class NativeBenchmarkSupport {
    private NativeBenchmarkSupport() {
    }

    static long evaluate(Provider provider, MonteCarloPriceForecastIndicator forecast, int from, int to) {
        NumFactory factory = forecast.getBarSeries().numFactory();
        long started = System.nanoTime();
        PlannedOperation planned = new MonteCarloShockPathPlanner().plan(forecast, from, to, factory,
                org.ta4j.core.acceleration.AccelerationRuntime.maxDeviceBytes());
        assertThat(planned).isNotNull();
        KernelRequest request = planned.request();
        assertThat(request.determinism()).as("benchmark requires an explicit approximate tolerance")
                .isEqualTo(Determinism.APPROXIMATE);
        KernelResult result = provider.execute(request);
        assertThat(result.nativeInitialized()).isTrue();
        double[] outputs = result.outputs();
        int width = request.outputsPerIndex();
        assertThat(outputs).hasSize(request.expectedOutputLength());
        for (int offset = 0; offset < request.size(); offset++) {
            double[] slice = Arrays.copyOfRange(outputs, offset * width, (offset + 1) * width);
            Forecast value = (Forecast) planned.decoder().decode(slice, from + offset, factory);
            assertThat(value.isStable()).isTrue();
        }
        return System.nanoTime() - started;
    }
}
