/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.acceleration.Kernel;
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
        KernelRequest request = request(forecast, from, to);
        KernelResult result = provider.execute(request);
        assertThat(result.nativeInitialized()).isTrue();
        double[] outputs = result.outputs();
        int width = request.outputsPerIndex();
        assertThat(outputs).hasSize(request.expectedOutputLength());
        for (int offset = 0; offset < request.size(); offset++) {
            double[] slice = Arrays.copyOfRange(outputs, offset * width, (offset + 1) * width);
            Forecast value = forecast.decode(from + offset, slice);
            assertThat(value.isStable()).isTrue();
        }
        return System.nanoTime() - started;
    }

    /** Lays out the batch exactly as the runtime does: row buffers, then window. */
    private static KernelRequest request(MonteCarloPriceForecastIndicator forecast, int from, int to) {
        double tolerance = AccelerationRuntime.approximateTolerance();
        assertThat(tolerance).as("benchmark requires an explicit approximate tolerance").isFinite();
        Kernel kernel = forecast.kernel();
        int rows = to - from + 1;
        int windowLength = kernel.windowLength();
        double[][] columns = new double[kernel.inputsPerRow()][rows];
        double[] rowInputs = new double[kernel.inputsPerRow()];
        for (int row = 0; row < rows; row++) {
            assertThat(forecast.snapshot(from + row, rowInputs)).as("index %s is not eligible", from + row).isTrue();
            for (int buffer = 0; buffer < rowInputs.length; buffer++) {
                columns[buffer][row] = rowInputs[buffer];
            }
        }
        List<double[]> inputs = new ArrayList<>(Arrays.asList(columns));
        if (windowLength > 0) {
            double[] window = new double[rows + windowLength - 1];
            for (int offset = 0; offset < window.length; offset++) {
                window[offset] = forecast.windowValue(from - windowLength + 1 + offset);
            }
            inputs.add(window);
        }
        long peakDeviceBytes = Math.addExact(Math.max(0L, windowLength - 1L) * Double.BYTES,
                Math.multiplyExact(kernel.deviceBytesPerRow(), rows));
        return new KernelRequest(kernel.operation(), from, to, kernel.outputsPerRow(), NumericEncoding.FLOAT64,
                Determinism.APPROXIMATE, kernel.seed(), tolerance, kernel.params(), inputs,
                Math.multiplyExact(kernel.scalarNanosPerRow(), rows), peakDeviceBytes);
    }
}
