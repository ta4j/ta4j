/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
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

class MetalAccelerationProviderTest {

    @TempDir
    Path temporary;

    @AfterEach
    void reset() {
        System.clearProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(MetalAccelerationProvider.APPROXIMATE_PROPERTY);
        System.clearProperty(MetalNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(ShockPathQualification.familyProperty(Backend.METAL));
        System.clearProperty(ShockPathQualification.minStepsProperty(Backend.METAL));
    }

    @Test
    void declinesExactRequestsWithoutTouchingNative() {
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridgeFailing());

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("metal");
        assertThat(assessment.diagnostic().detail()).contains("not exact-capable");
    }

    @Test
    void approximateRequestsRequireExplicitOptIn() {
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridgeFailing());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("opt-in");
    }

    @Test
    void absentLibraryDeclinesApproximate() {
        org.junit.jupiter.api.Assumptions.assumeFalse(MetalNativeLibrary.packagedResourcePresent());
        System.setProperty(MetalAccelerationProvider.APPROXIMATE_PROPERTY, "true");
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY, temporary.resolve("missing.dylib").toString());
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(false), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    void unqualifiedFamilyPredictsUnboundedCostForCoreCrossover() throws Exception {
        useLibraryFile();
        System.setProperty(MetalAccelerationProvider.APPROXIMATE_PROPERTY, "true");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.backend()).isEqualTo(Backend.METAL);
        assertThat(assessment.predictedTotalNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void qualifiedFamilyPredictsFiniteTotalCost() throws Exception {
        useLibraryFile();
        System.setProperty(MetalAccelerationProvider.APPROXIMATE_PROPERTY, "true");
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), "m5max");
        System.setProperty(ShockPathQualification.minStepsProperty(Backend.METAL), "8");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.backend()).isEqualTo(Backend.METAL);
        assertThat(assessment.deviceId()).isEqualTo("metal/m5max");
        assertThat(assessment.predictedTotalNanos()).isGreaterThan(0L).isLessThan(Long.MAX_VALUE);
        assertThat(assessment.deterministic()).isFalse();
    }

    @Test
    void executeMapsSamplesDecisionMajorAcrossChunks() throws Exception {
        useLibraryFile();
        System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, "400");
        AtomicInteger evaluations = new AtomicInteger();
        FakeBridge bridge = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                evaluations.incrementAndGet();
                return super.evaluate(request);
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridge);

        KernelResult result = provider.execute(request(Double.NaN));

        assertThat(evaluations.get()).isEqualTo(4);
        assertThat(result.nativeInitialized()).isTrue();
        assertThat(result.outputs()).containsExactly(0d, 1d, 100d, 101d, 200d, 201d, 300d, 301d);
    }

    @Test
    void executeRejectsShortSamples() throws Exception {
        useLibraryFile();
        MetalNativeBridge shortBridge = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                return new MetalEvaluationResult(1d, 0d, 1d, new float[] { 1f });
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), shortBridge);

        assertThrows(NativeProviderException.class, () -> provider.execute(request(Double.NaN)));
    }

    @Test
    void executeFailureSurfacesNativeCause() throws Exception {
        useLibraryFile();
        MetalNativeBridge failing = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                throw new IllegalStateException("kernel fault");
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), failing);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(Double.NaN)));
        assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void probeFailureSurfacesAsNativeError() throws Exception {
        useLibraryFile();
        MetalNativeBridge unavailable = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                return new MetalProbeResult(false, "", 0L, "no device");
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), unavailable);

        assertThrows(NativeProviderException.class, () -> provider.execute(request(Double.NaN)));
    }

    private void useLibraryFile() throws Exception {
        Path library = temporary.resolve("libta4j-metal-accelerator.dylib");
        java.nio.file.Files.write(library, new byte[] { 0 });
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY, library.toString());
    }

    private static java.util.function.Supplier<MetalNativeLibrary.LoadResult> loaderWith(boolean loaded) {
        return () -> new MetalNativeLibrary.LoadResult(loaded, null, loaded ? "test" : "missing");
    }

    private static MetalNativeBridge bridgeFailing() {
        return new MetalNativeBridge() {
            @Override
            public MetalProbeResult probe() {
                throw new AssertionError("assessment must not probe");
            }

            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("assessment must not evaluate");
            }
        };
    }

    private static KernelRequest request(double tolerance) {
        int decisions = 4;
        int horizon = 2;
        int iterations = 2;
        int lookback = 4;
        double[] params = { 0d, 0d, (double) horizon, (double) iterations, (double) lookback, 0.94d };
        List<double[]> inputs = List.of(new double[decisions], new double[decisions], new double[decisions],
                new double[decisions], new double[decisions * lookback]);
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 13, iterations, NumericEncoding.FLOAT64,
                Determinism.BITWISE_IDENTICAL, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
    }

    private static class FakeBridge implements MetalNativeBridge {

        @Override
        public MetalProbeResult probe() {
            return new MetalProbeResult(true, "Apple M5 Max", 1L << 30, "test");
        }

        @Override
        public MetalEvaluationResult evaluate(NativeForecastRequest request) {
            float[] samples = new float[request.decisionCount() * request.iterationCount()];
            for (int decision = 0; decision < request.decisionCount(); decision++) {
                for (int path = 0; path < request.iterationCount(); path++) {
                    samples[decision * request.iterationCount() + path] = (request.fromInclusive() - 10) * 100f
                            + decision * 100f + path;
                }
            }
            return new MetalEvaluationResult(1000d, 100d, 900d, samples);
        }
    }
}
