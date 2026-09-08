/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;

class CudaAccelerationProviderTest {

    @AfterEach
    void reset() {
        System.clearProperty(CudaAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(CudaNativeLibrary.LIBRARY_PROPERTY);
    }

    @Test
    void declinesBitwiseIdentity() {
        CudaAccelerationProvider provider = new CudaAccelerationProvider();

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("cuda");
    }

    @Test
    void assessesApproximateRequestsWithoutLoadingLibrary(@TempDir Path directory) throws IOException {
        System.setProperty(CudaNativeLibrary.LIBRARY_PROPERTY,
                Files.createFile(directory.resolve("unloaded.dll")).toString());

        Assessment assessment = new CudaAccelerationProvider().assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
    }

    @Test
    void reportsMissingLibraryOnExecution(@TempDir Path directory) {
        System.setProperty(CudaNativeLibrary.LIBRARY_PROPERTY, directory.resolve("missing.dll").toString());
        CudaAccelerationProvider provider = new CudaAccelerationProvider();

        assertThrows(NativeProviderException.class, () -> provider.execute(request(0.01d)));
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
