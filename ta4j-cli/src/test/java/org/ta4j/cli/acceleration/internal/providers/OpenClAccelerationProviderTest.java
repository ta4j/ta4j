/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;

class OpenClAccelerationProviderTest {

    @AfterEach
    void reset() {
        System.clearProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(OpenClNativeLibrary.LIBRARY_PROPERTY);
    }

    @Test
    void declinesExactWithReducedRowReason() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("opencl");
        assertThat(assessment.diagnostic().detail()).contains("reduced forecast rows");
    }

    @Test
    void declinesApproximateWithReducedRowReason() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("reduced forecast rows");
    }

    @Test
    void assessmentNeverInitializesNative() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
    }

    @Test
    void executeIsUnreachable() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        assertThrows(NativeProviderException.class, () -> provider.execute(request(Double.NaN)));
    }

    private static KernelRequest request(double tolerance) {
        int decisions = 4;
        int horizon = 2;
        int iterations = 2;
        int lookback = 4;
        double[] params = { 0d, 0d, (double) horizon, (double) iterations, (double) lookback, 0.94d };
        List<double[]> inputs = List.of(new double[decisions], new double[decisions], new double[decisions],
                new double[decisions], new double[decisions * lookback]);
        Determinism determinism = Double.isNaN(tolerance) ? Determinism.BITWISE_IDENTICAL : Determinism.APPROXIMATE;
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 13, iterations, NumericEncoding.FLOAT64,
                determinism, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
    }
}
