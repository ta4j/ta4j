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
import org.ta4j.core.indicators.forecast.MonteCarloKernel;

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
    void rejectsConfiguredDirectoryAsLibrary(@TempDir Path directory) throws IOException {
        System.setProperty(CudaNativeLibrary.LIBRARY_PROPERTY,
                Files.createDirectory(directory.resolve("not-a-library")).toString());

        Assessment assessment = new CudaAccelerationProvider().assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    void reportsMissingLibraryOnExecution(@TempDir Path directory) {
        System.setProperty(CudaNativeLibrary.LIBRARY_PROPERTY, directory.resolve("missing.dll").toString());
        CudaAccelerationProvider provider = new CudaAccelerationProvider();

        assertThrows(NativeProviderException.class, () -> provider.execute(request(0.01d)));
    }

    @Test
    void sampleTransportPreservesEveryFp64Value() {
        // Values that single precision would flush to zero, overflow, or round.
        double[] native64 = { 1e-50d, 1e40d, -1e-50d, Float.MIN_VALUE / 3d, 0x1.fffffffffffffp-127d,
                Float.MAX_VALUE * 2d, -700.25d, 0.1234567890123456789d };
        CudaNativeBridge bridge = new CudaNativeBridge() {
            @Override
            public CudaProbeResult probe() {
                throw new AssertionError("the adapter must not probe");
            }

            @Override
            public CudaEvaluationResult evaluate(NativeForecastRequest request) {
                return new CudaEvaluationResult(1d, 0d, 1d, 0d, native64);
            }
        };

        double[] published = CudaAccelerationProvider.sampleKernel(bridge)
                .evaluateSamples(new NativeForecastRequest(0, 1, 1, native64.length, 1, 42L, 0, 0, 0.94d, new double[1],
                        new double[1], new double[1], new double[1]))
                .logReturns();

        assertThat(published).containsExactly(native64);
    }

    private static KernelRequest request(double tolerance) {
        int decisions = 4;
        int iterations = 2;
        int lookback = 4;
        double[] params = new double[MonteCarloKernel.PARAM_COUNT];
        params[MonteCarloKernel.PARAM_HORIZON] = 2;
        params[MonteCarloKernel.PARAM_ITERATIONS] = iterations;
        params[MonteCarloKernel.PARAM_LOOKBACK] = lookback;
        params[MonteCarloKernel.PARAM_DECAY] = 0.94d;
        List<double[]> inputs = List.of(new double[decisions], new double[decisions], new double[decisions],
                new double[decisions], new double[decisions + lookback - 1]);
        Determinism determinism = Double.isNaN(tolerance) ? Determinism.BITWISE_IDENTICAL : Determinism.APPROXIMATE;
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 13, iterations, NumericEncoding.FLOAT64,
                determinism, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
    }
}
