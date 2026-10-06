/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.indicators.forecast.MonteCarloKernel;

class MetalAccelerationProviderTest {

    @TempDir
    Path temporary;

    @AfterEach
    void reset() {
        System.clearProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(AccelerationRuntime.APPROXIMATE_TOLERANCE_PROPERTY);
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
    void absentLibraryDeclinesApproximate() {
        org.junit.jupiter.api.Assumptions.assumeFalse(MetalNativeLibrary.packagedResourcePresent());
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY, temporary.resolve("missing.dylib").toString());
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(false), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    void rejectsConfiguredDirectoryAsLibrary() throws Exception {
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY,
                Files.createDirectory(temporary.resolve("not-a-library")).toString());

        Assessment assessment = new MetalAccelerationProvider(loaderWith(true), bridgeFailing()).assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    void executeRejectsInfeasibleMemoryCeilingBeforeNativeInitialization() {
        System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, "0");
        AtomicInteger loads = new AtomicInteger();
        MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
            loads.incrementAndGet();
            return new MetalNativeLibrary.LoadResult(true, null, "test");
        }, bridgeFailing());

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).contains("must be > 0");
        assertThat(loads.get()).isZero();
    }

    @Test
    void unqualifiedFamilyPredictsUnboundedCostForCoreCrossover() throws Exception {
        useLibraryFile();
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), "generic");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.backend()).isEqualTo(Backend.METAL);
        assertThat(assessment.predictedTotalNanos()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void measuredFamilyPredictsFiniteTotalCost() throws Exception {
        useLibraryFile();
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), "m5max");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge(),
                ShockPathQualification.of(Backend.METAL, 1, "m5max",
                        new ShockPathQualification.Coefficients(1_000L, 100L, 1d, 0d, 8L)));

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.backend()).isEqualTo(Backend.METAL);
        assertThat(assessment.deviceId()).isEqualTo("metal/m5max");
        assertThat(assessment.predictedTotalNanos()).isGreaterThan(0L).isLessThan(Long.MAX_VALUE);
        assertThat(assessment.deterministic()).isTrue();
    }

    @Test
    void fp32InputRoundingCounterexampleIsDeclinedAtAMicroTolerance() throws Exception {
        useLibraryFile();
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge());
        // One decision, horizon 1, standardized empirical: mean 32 and history
        // 32.0000015 narrow to the same float, a relative price error of 1.5e-6.
        KernelRequest tight = singleRow(1e-6d, MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL,
                MonteCarloKernel.VOLATILITY_CONSTANT, 32d, 1d, 32.0000015d);
        KernelRequest loose = singleRow(1e-3d, MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL,
                MonteCarloKernel.VOLATILITY_CONSTANT, 32d, 1d, 32.0000015d);

        Assessment declined = provider.assess(tight);
        Assessment admitted = provider.assess(loose);

        assertThat(declined.supported()).isFalse();
        assertThat(declined.diagnostic().code()).isEqualTo(DiagnosticCode.UNSUPPORTED);
        assertThat(declined.diagnostic().detail()).contains("cannot certify approximate tolerance 1.0E-6");
        assertThat(admitted.supported()).isTrue();
        assertThrows(NativeProviderException.class, () -> provider.execute(tight));
    }

    @Test
    void ewmaAndFp32NormalRequestsAreNotCertified() throws Exception {
        useLibraryFile();
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), new FakeBridge());

        Assessment ewma = provider.assess(singleRow(0.5d, MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP,
                MonteCarloKernel.VOLATILITY_EWMA, 0d, 1e-4d, 0.01d));
        Assessment normal = provider.assess(
                singleRow(0.5d, MonteCarloKernel.SHOCK_NORMAL, MonteCarloKernel.VOLATILITY_CONSTANT, 0d, 1e-4d, 0.01d));

        assertThat(ewma.supported()).isFalse();
        assertThat(ewma.diagnostic().detail()).contains("EWMA");
        assertThat(normal.supported()).isFalse();
        assertThat(normal.diagnostic().detail()).contains("Box-Muller");
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
                // Row r of the chunk samples the shared returns [r, r + lookback).
                int base = request.fromInclusive() - 10;
                assertThat(request.historicalReturns()).hasSize(request.decisionCount() + 3);
                assertThat(request.historicalReturns()[0]).isEqualTo(base);
                assertThat(request.means()).containsExactly(1000d + base);
                return super.evaluate(request);
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridge);

        KernelResult result = provider.execute(request(0.01d));

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

        assertThrows(NativeProviderException.class, () -> provider.execute(request(0.01d)));
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
                () -> provider.execute(request(0.01d)));
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

        assertThrows(NativeProviderException.class, () -> provider.execute(request(0.01d)));
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
        return request(Double.isNaN(tolerance) ? Determinism.BITWISE_IDENTICAL : Determinism.APPROXIMATE, tolerance);
    }

    private static KernelRequest request(Determinism determinism, double tolerance) {
        int decisions = 4;
        int iterations = 2;
        int lookback = 4;
        double[] means = new double[decisions];
        for (int row = 0; row < decisions; row++) {
            means[row] = 1000d + row;
        }
        double[] returns = new double[decisions + lookback - 1];
        for (int index = 0; index < returns.length; index++) {
            returns[index] = index;
        }
        // Bootstrap shocks with a loose tolerance keep the fp32 bound satisfiable.
        return request(determinism, tolerance, MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP,
                MonteCarloKernel.VOLATILITY_CONSTANT, 2, lookback, iterations, means, new double[decisions], returns);
    }

    private static KernelRequest singleRow(double tolerance, int shockModel, int volatilityMode, double mean,
            double variance, double historicalReturn) {
        return request(Determinism.APPROXIMATE, tolerance, shockModel, volatilityMode, 1, 1, 2, new double[] { mean },
                new double[] { variance }, new double[] { historicalReturn });
    }

    private static KernelRequest request(Determinism determinism, double tolerance, int shockModel, int volatilityMode,
            int horizon, int lookback, int iterations, double[] means, double[] variances, double[] returns) {
        int decisions = means.length;
        double[] params = new double[MonteCarloKernel.PARAM_COUNT];
        params[MonteCarloKernel.PARAM_SHOCK_MODEL] = shockModel;
        params[MonteCarloKernel.PARAM_VOLATILITY_MODE] = volatilityMode;
        params[MonteCarloKernel.PARAM_HORIZON] = horizon;
        params[MonteCarloKernel.PARAM_ITERATIONS] = iterations;
        params[MonteCarloKernel.PARAM_LOOKBACK] = lookback;
        params[MonteCarloKernel.PARAM_DECAY] = 0.94d;
        params[MonteCarloKernel.PARAM_SMOOTHING_FACTOR] = MonteCarloKernel.smoothingBandwidthFactor(lookback);
        double[] prices = new double[decisions];
        java.util.Arrays.fill(prices, 1d);
        List<double[]> inputs = List.of(prices, means, new double[decisions], variances, returns);
        return new KernelRequest(Operation.MONTE_CARLO_SHOCK_PATHS_V1, 10, 10 + decisions - 1, iterations,
                NumericEncoding.FLOAT64, determinism, 42L, tolerance, params, inputs, 1_000_000_000L, 1_000_000L);
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
