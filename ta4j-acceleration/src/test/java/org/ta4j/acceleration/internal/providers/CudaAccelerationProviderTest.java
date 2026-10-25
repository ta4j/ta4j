/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

    @Test
    public void rejectsMissingOrMalformedProbeMetadataWithoutNativeInitialization() {
        for (String payload : new String[] { null, "", " \t", "OK|device|8" }) {
            CudaProbeResult result = JniCudaNativeBridge.parseProbePayload(payload);

            assertThat(result.available()).as("payload %s", payload).isFalse();
            assertThat(result.detail()).contains(payload == null || payload.isBlank() ? "no metadata" : "Malformed");
        }
    }

    @Test
    public void anchorsCudaProbeNumbersAfterDeviceNamesContainingSeparators() {
        CudaProbeResult result = JniCudaNativeBridge
                .parseProbePayload("OK|NVIDIA|device|9|1|4294967296|8589934592|13030|13020|ready");

        assertThat(result.available()).isTrue();
        assertThat(result.deviceName()).isEqualTo("NVIDIA|device");
        assertThat(result.computeMajor()).isEqualTo(9);
        assertThat(result.computeMinor()).isEqualTo(1);
        assertThat(result.freeMemoryBytes()).isEqualTo(4_294_967_296L);
        assertThat(result.totalMemoryBytes()).isEqualTo(8_589_934_592L);
        assertThat(result.driverVersion()).isEqualTo(13_030);
        assertThat(result.runtimeVersion()).isEqualTo(13_020);
        assertThat(result.detail()).isEqualTo("ready");
    }

    @Test
    public void preservesCudaDriverFailureInsteadOfParsingEmptyNumbers() {
        CudaProbeResult result = JniCudaNativeBridge.parseProbePayload("ERROR|NVIDIA|device|||||||driver unavailable");

        assertThat(result.available()).isFalse();
        assertThat(result.deviceName()).isEqualTo("NVIDIA|device");
        assertThat(result.detail()).isEqualTo("driver unavailable");
    }

    @Test
    public void rejectsEachMalformedCudaProbeNumber() {
        String[] fields = { "OK", "NVIDIA", "9", "1", "4294967296", "8589934592", "13030", "13020", "ready" };
        for (int number = 2; number <= 7; number++) {
            String[] malformed = fields.clone();
            malformed[number] = "not-a-number";

            CudaProbeResult result = JniCudaNativeBridge.parseProbePayload(String.join("|", malformed));

            assertThat(result.available()).as("field %d", number).isFalse();
            assertThat(result.detail()).startsWith("Malformed CUDA probe number:");
        }
    }

    @Test
    public void rejectsMissingAndTruncatedCudaEvaluationHeaders() {
        assertThrows(IllegalStateException.class, () -> JniCudaNativeBridge.parseEvaluationPayload(null));
        for (int length = 0; length < 4; length++) {
            double[] truncated = new double[length];
            assertThat(assertThrows(IllegalStateException.class,
                    () -> JniCudaNativeBridge.parseEvaluationPayload(truncated)))
                    .hasMessage("CUDA evaluation returned no result payload");
        }
    }

    @Test
    public void separatesCudaTimingsFromOwnedFp64Samples() {
        double[] payload = { 11d, 2d, 7d, 1d, 1e-50d, 1e40d, -0.25d };

        CudaEvaluationResult result = JniCudaNativeBridge.parseEvaluationPayload(payload);
        payload[4] = 42d;

        assertThat(result.totalMicros()).isEqualTo(11d);
        assertThat(result.transferMicros()).isEqualTo(2d);
        assertThat(result.kernelMicros()).isEqualTo(7d);
        assertThat(result.reductionMicros()).isEqualTo(1d);
        assertThat(result.logReturns()).containsExactly(1e-50d, 1e40d, -0.25d);
    }

    @Test
    public void rejectsRelativeAndInvalidCudaLibraryPathsBeforeLoading() {
        for (String path : new String[] { "relative-cuda.dll", "invalid\0cuda.dll" }) {
            System.setProperty(CudaNativeLibrary.LIBRARY_PROPERTY, path);

            Assessment assessment = new CudaAccelerationProvider().assess(request(0.01d));

            assertThat(assessment.supported()).as("configured path %s", path).isFalse();
            assertThat(assessment.diagnostic().detail()).contains("CUDA library not found");
        }
    }

    @Test
    public void nativeFailureDiagnosticsKeepTheCauseWithoutEmptyMessageNoise() {
        for (String message : new String[] { null, "", " \t", "driver failed" }) {
            IllegalStateException cause = new IllegalStateException(message);

            NativeProviderException failure = new NativeProviderException("cuda", cause);

            assertThat(failure).hasCause(cause)
                    .hasMessage("cuda native execution failed: IllegalStateException"
                            + (message == null || message.isBlank() ? "" : ": driver failed"));
        }
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
        double[] prices = new double[decisions];
        Arrays.fill(prices, 1d);
        List<double[]> inputs = List.of(prices, new double[decisions], new double[decisions], new double[decisions],
                new double[decisions + lookback - 1]);
        Determinism determinism = Double.isNaN(tolerance) ? Determinism.BITWISE_IDENTICAL : Determinism.APPROXIMATE;
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 13, iterations, NumericEncoding.FLOAT64,
                determinism, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
    }
}
