/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.indicators.forecast.MonteCarloKernel;

class OpenClAccelerationProviderTest {

    @AfterEach
    public void reset() {
        System.clearProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(OpenClNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(ShockPathQualification.familyProperty(Backend.OPENCL));
    }

    @Test
    public void declinesBitwiseIdentity() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("opencl");
        assertThat(assessment.diagnostic().detail()).contains("not exact-capable");
        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(Double.NaN)));
        assertThat(failure.getMessage()).contains("not exact-capable");
    }

    @Test
    public void assessesApproximateRequestsWithoutLoadingLibrary(@TempDir Path directory) throws IOException {
        System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY,
                Files.createFile(directory.resolve("unloaded.so")).toString());

        Assessment assessment = new OpenClAccelerationProvider().assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
    }

    @Test
    public void rejectsConfiguredDirectoryAsLibrary(@TempDir Path directory) throws IOException {
        System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY,
                Files.createDirectory(directory.resolve("not-a-library")).toString());

        Assessment assessment = new OpenClAccelerationProvider().assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    public void reportsMissingLibraryOnExecution(@TempDir Path directory) {
        System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY, directory.resolve("missing.so").toString());
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider();

        assertThrows(NativeProviderException.class, () -> provider.execute(request(0.01d)));
    }

    @Test
    public void probeLinkageErrorSurfacesAsNativeError() {
        OpenClNativeBridge failingProbe = new OpenClNativeBridge() {
            @Override
            public OpenClProbeResult probe() {
                throw new UnsatisfiedLinkError("missing OpenCL JNI symbol");
            }

            @Override
            public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("probe must fail before evaluation");
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), failingProbe);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getCause()).isInstanceOf(UnsatisfiedLinkError.class);
    }

    @Test
    public void unavailableProbeRejectsExecutionBeforeEvaluation() {
        OpenClNativeBridge unavailableProbe = new OpenClNativeBridge() {
            @Override
            public OpenClProbeResult probe() {
                return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, false, "device lost");
            }

            @Override
            public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("unavailable device must not evaluate");
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), unavailableProbe);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).contains("device lost");
    }

    @Test
    public void assessmentRejectsRelativeAndMalformedLibraryPathsWithoutNativeWork() {
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(() -> {
            throw new AssertionError("assessment must not load native code");
        }, new FakeBridge());
        for (String path : new String[] { "relative-opencl.so", "invalid\0opencl.so" }) {
            System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY, path);

            Assessment assessment = provider.assess(request(0.01d));

            assertThat(assessment.supported()).isFalse();
            assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
            assertThat(assessment.diagnostic().detail()).contains("OpenCL library not found");
        }
    }

    @Test
    public void loadFailureRejectsExecutionBeforeProbing() {
        FakeBridge bridge = new FakeBridge() {
            @Override
            public OpenClProbeResult probe() {
                throw new AssertionError("a failed library load must not probe");
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                () -> new OpenClNativeLibrary.LoadResult(false, null, "incompatible OpenCL ABI"), bridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).contains("opencl native execution failed", "incompatible OpenCL ABI");
    }

    @Test
    public void probeRuntimeFailureRetainsCauseWithoutInventingDetail() {
        IllegalStateException cause = new IllegalStateException();
        FakeBridge bridge = new FakeBridge() {
            @Override
            public OpenClProbeResult probe() {
                throw cause;
            }

            @Override
            public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("a failed probe must not evaluate");
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), bridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getMessage()).isEqualTo("opencl native execution failed: IllegalStateException");
    }

    @Test
    public void evaluationLinkageErrorRetainsNativeCause() {
        UnsatisfiedLinkError cause = new UnsatisfiedLinkError("missing OpenCL evaluation symbol");
        FakeBridge bridge = new FakeBridge() {
            @Override
            public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                throw cause;
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), bridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getMessage()).contains("opencl native execution failed", "missing OpenCL evaluation symbol");
    }

    @Test
    public void executionMapsOverlappingChunksWithinProbedMemoryAndReusesKernel(@TempDir Path directory)
            throws IOException {
        System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY,
                Files.createFile(directory.resolve("fake-opencl.so")).toString());
        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, "1000");
        System.setProperty(ShockPathQualification.familyProperty(Backend.OPENCL), " PORTABLE ");
        KernelRequest baseRequest = request(0.01d, 5);
        double[] params = baseRequest.params();
        params[MonteCarloKernel.PARAM_SHOCK_MODEL] = MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL;
        double[] means = { 0.01d, 0.02d, 0.03d, 0.04d, 0.05d };
        double[] drifts = { 0.001d, 0.002d, 0.003d, 0.004d, 0.005d };
        double[] variances = { 0.0001d, 0.0002d, 0.0003d, 0.0004d, 0.0005d };
        double[] returns = { 0.001d, 0.002d, 0.003d, 0.004d, 0.005d, 0.006d, 0.007d, 0.008d };
        List<double[]> inputs = List.of(baseRequest.inputs().get(MonteCarloKernel.INPUT_PRICES), means, drifts,
                variances, returns);
        KernelRequest request = new KernelRequest(baseRequest.operation(), baseRequest.fromInclusive(),
                baseRequest.toInclusive(), baseRequest.outputsPerIndex(), baseRequest.numeric(),
                baseRequest.determinism(), baseRequest.seed(), baseRequest.tolerance(), params, inputs,
                baseRequest.estimatedScalarNanos(), baseRequest.peakDeviceBytesEstimate());
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        List<Integer> starts = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        FakeBridge bridge = new FakeBridge() {
            @Override
            public OpenClProbeResult probe() {
                probes.incrementAndGet();
                return new OpenClProbeResult(true, "Portable FP64 GPU", 3, 0, 480L, 1L << 30, 1, 1, true, "test");
            }

            @Override
            public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                int base = request.fromInclusive() - 10;
                int count = request.decisionCount();
                starts.add(request.fromInclusive());
                sizes.add(count);
                assertThat(request.horizon()).isEqualTo(2);
                assertThat(request.iterationCount()).isEqualTo(2);
                assertThat(request.lookbackBarCount()).isEqualTo(4);
                assertThat(request.seed()).isEqualTo(42L);
                assertThat(request.shockModel()).isEqualTo(1);
                assertThat(request.volatilityMode()).isEqualTo(MonteCarloKernel.VOLATILITY_CONSTANT);
                assertThat(request.volatilityDecayFactor()).isEqualTo(0.94d);
                assertThat(request.means()).containsExactly(Arrays.copyOfRange(means, base, base + count));
                assertThat(request.drifts()).containsExactly(Arrays.copyOfRange(drifts, base, base + count));
                assertThat(request.variances()).containsExactly(Arrays.copyOfRange(variances, base, base + count));
                assertThat(request.historicalReturns())
                        .containsExactly(Arrays.copyOfRange(returns, base, base + count + 3));
                return super.evaluate(request);
            }
        };
        OpenClAccelerationProvider provider = new OpenClAccelerationProvider(() -> {
            loads.incrementAndGet();
            return new OpenClNativeLibrary.LoadResult(true, null, "test");
        }, bridge);

        Assessment cold = provider.assess(request);
        KernelResult first = provider.execute(request);
        Assessment warm = provider.assess(request);
        KernelResult second = provider.execute(request);

        assertThat(cold.supported()).isTrue();
        assertThat(cold.deviceId()).isEqualTo("opencl/portable");
        assertThat(cold.peakDeviceBytes()).isEqualTo(704L);
        assertThat(cold.predictedTotalNanos()).isEqualTo(Long.MAX_VALUE);
        assertThat(first.nativeInitialized()).isTrue();
        assertThat(first.outputs()).containsExactly(0d, 1d, 100d, 101d, 200d, 201d, 300d, 301d, 400d, 401d);
        assertThat(second.outputs()).containsExactly(first.outputs());
        assertThat(warm.supported()).isTrue();
        assertThat(warm.deviceId()).isEqualTo("opencl/Portable FP64 GPU");
        assertThat(warm.peakDeviceBytes()).isEqualTo(480L);
        assertThat(warm.predictedTotalNanos()).isEqualTo(Long.MAX_VALUE);
        assertThat(starts).containsExactly(10, 12, 14, 10, 12, 14);
        assertThat(sizes).containsExactly(2, 2, 1, 2, 2, 1);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(probes.get()).isEqualTo(1);
    }

    @Test
    public void probeBudgetRejectsExecutionAndLaterAssessmentBeforeEvaluation(@TempDir Path directory)
            throws IOException {
        System.setProperty(OpenClNativeLibrary.LIBRARY_PROPERTY,
                Files.createFile(directory.resolve("fake-opencl.so")).toString());
        for (long freeBytes : new long[] { 0L, 263L }) {
            FakeBridge bridge = new FakeBridge() {
                @Override
                public OpenClProbeResult probe() {
                    return new OpenClProbeResult(true, "Memory-constrained GPU", 3, 0, freeBytes, 1L << 30, 1, 1, true,
                            "test");
                }

                @Override
                public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                    throw new AssertionError("a request that cannot fit one decision must not evaluate");
                }
            };
            OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                    () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), bridge);
            Assessment beforeProbe = provider.assess(request(0.01d));

            NativeProviderException failure = assertThrows(NativeProviderException.class,
                    () -> provider.execute(request(0.01d)));
            Assessment afterProbe = provider.assess(request(0.01d));

            String ceiling = Math.max(1L, freeBytes) + "-byte";
            assertThat(beforeProbe.supported()).isTrue();
            assertThat(failure.getMessage()).contains("216 bytes per decision plus 48 shared bytes", ceiling);
            assertThat(afterProbe.supported()).isFalse();
            assertThat(afterProbe.deviceId()).isEqualTo("opencl/Memory-constrained GPU");
            assertThat(afterProbe.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
            assertThat(afterProbe.diagnostic().detail()).contains(ceiling);
        }
    }

    @Test
    public void executionRejectsShortAndLongSampleChunksWithoutContinuing() {
        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, "480");
        for (int length : new int[] { 3, 5 }) {
            AtomicInteger evaluations = new AtomicInteger();
            FakeBridge bridge = new FakeBridge() {
                @Override
                public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
                    evaluations.incrementAndGet();
                    return new OpenClEvaluationResult(1d, 0d, 1d, 0d, new double[length]);
                }
            };
            OpenClAccelerationProvider provider = new OpenClAccelerationProvider(
                    () -> new OpenClNativeLibrary.LoadResult(true, null, "test"), bridge);

            NativeProviderException failure = assertThrows(NativeProviderException.class,
                    () -> provider.execute(request(0.01d)));

            assertThat(failure.getMessage())
                    .isEqualTo("opencl returned " + length + " samples, expected 4 for 2 decisions");
            assertThat(evaluations.get()).isEqualTo(1);
        }
    }

    private static KernelRequest request(double tolerance) {
        return request(tolerance, 4);
    }

    private static KernelRequest request(double tolerance, int decisions) {
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
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 10 + decisions - 1, iterations,
                NumericEncoding.FLOAT64, determinism, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
    }

    private static class FakeBridge implements OpenClNativeBridge {

        @Override
        public OpenClProbeResult probe() {
            return new OpenClProbeResult(true, "Portable FP64 GPU", 3, 0, 1L << 30, 1L << 30, 1, 1, true, "test");
        }

        @Override
        public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
            double[] samples = new double[request.decisionCount() * request.iterationCount()];
            for (int decision = 0; decision < request.decisionCount(); decision++) {
                for (int path = 0; path < request.iterationCount(); path++) {
                    samples[decision * request.iterationCount() + path] = (request.fromInclusive() - 10) * 100d
                            + decision * 100d + path;
                }
            }
            return new OpenClEvaluationResult(1000d, 100d, 900d, 0d, samples);
        }
    }
}
