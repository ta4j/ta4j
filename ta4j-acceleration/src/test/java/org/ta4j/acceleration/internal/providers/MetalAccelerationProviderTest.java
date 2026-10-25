/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
    public void reset() {
        System.clearProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(AccelerationRuntime.APPROXIMATE_TOLERANCE_PROPERTY);
        System.clearProperty(MetalNativeLibrary.LIBRARY_PROPERTY);
        System.clearProperty(ShockPathQualification.familyProperty(Backend.METAL));
        System.clearProperty(ShockPathQualification.minStepsProperty(Backend.METAL));
    }

    @Test
    public void declinesExactRequestsWithoutTouchingNative() {
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridgeFailing());

        Assessment assessment = provider.assess(request(Double.NaN));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("metal");
        assertThat(assessment.diagnostic().detail()).contains("not exact-capable");
    }

    @Test
    public void absentLibraryDeclinesApproximate() {
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY, temporary.resolve("missing.dylib").toString());
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(false), new FakeBridge());

        Assessment assessment = provider.assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    public void rejectsConfiguredDirectoryAsLibrary() throws Exception {
        System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY,
                Files.createDirectory(temporary.resolve("not-a-library")).toString());

        Assessment assessment = new MetalAccelerationProvider(loaderWith(true), bridgeFailing()).assess(request(0.01d));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("not found");
    }

    @Test
    public void rejectsInvalidParameterCountBeforeNativeWork() {
        KernelRequest valid = request(0.01d);
        double[] params = Arrays.copyOf(valid.params(), MonteCarloKernel.PARAM_COUNT - 1);

        assertRejectedBeforeNativeWork(request(valid, params, valid.inputs()),
                "expects " + MonteCarloKernel.PARAM_COUNT + " kernel params");
    }

    @Test
    public void rejectsUnsupportedAndFractionalShockCodesBeforeNativeWork() {
        for (double shock : new double[] { MonteCarloKernel.SHOCK_SMOOTHED_EMPIRICAL, 0.5d, Double.NaN }) {
            assertRejectedBeforeNativeWork(withParam(request(0.01d), MonteCarloKernel.PARAM_SHOCK_MODEL, shock),
                    "supports historical bootstrap, standardized empirical, and normal shocks");
        }
    }

    @Test
    public void rejectsInvalidVolatilityModesAndDecayBeforeNativeWork() {
        for (double mode : new double[] { 2d, 0.5d }) {
            assertRejectedBeforeNativeWork(withParam(request(0.01d), MonteCarloKernel.PARAM_VOLATILITY_MODE, mode),
                    "constant or EWMA volatility mode with a decay in (0, 1)");
        }
        for (double decay : new double[] { 0d, 1d, Double.NaN }) {
            assertRejectedBeforeNativeWork(withParam(request(0.01d), MonteCarloKernel.PARAM_DECAY, decay),
                    "constant or EWMA volatility mode with a decay in (0, 1)");
        }
    }

    @Test
    public void rejectsNonpositiveDimensionsBeforeNativeWork() {
        for (int parameter : new int[] { MonteCarloKernel.PARAM_HORIZON, MonteCarloKernel.PARAM_ITERATIONS,
                MonteCarloKernel.PARAM_LOOKBACK }) {
            assertRejectedBeforeNativeWork(withParam(request(0.01d), parameter, 0d), "positive");
        }
    }

    @Test
    public void rejectsFractionalNonfiniteAndOutOfRangeDimensionsBeforeNativeWork() {
        for (int parameter : new int[] { MonteCarloKernel.PARAM_HORIZON, MonteCarloKernel.PARAM_ITERATIONS,
                MonteCarloKernel.PARAM_LOOKBACK }) {
            for (double value : new double[] { 1.5d, Double.POSITIVE_INFINITY, Double.NaN,
                    (double) Integer.MAX_VALUE + 1d }) {
                assertRejectedBeforeNativeWork(withParam(request(0.01d), parameter, value), "positive integers");
            }
        }
    }

    @Test
    public void rejectsDimensionArithmeticOverflowBeforeInspectingBuffers() {
        KernelRequest valid = request(0.01d);
        double[] params = valid.params();
        params[MonteCarloKernel.PARAM_HORIZON] = Integer.MAX_VALUE;
        params[MonteCarloKernel.PARAM_ITERATIONS] = Integer.MAX_VALUE;

        assertRejectedBeforeNativeWork(request(valid, params, List.of()), "request dimensions overflow");

        KernelRequest overflowingRange = new KernelRequest(valid.operation(), Integer.MIN_VALUE, Integer.MAX_VALUE,
                valid.outputsPerIndex(), valid.numeric(), valid.determinism(), valid.seed(), valid.tolerance(),
                valid.params(), List.of(), valid.estimatedScalarNanos(), valid.peakDeviceBytesEstimate());

        assertRejectedBeforeNativeWork(overflowingRange, "request dimensions overflow");
    }

    @Test
    public void rejectsOutputVectorLengthOverflowBeforeNativeWork() {
        int decisions = 50_000;
        KernelRequest overflowingOutput = request(Determinism.APPROXIMATE, 0.01d,
                MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP, MonteCarloKernel.VOLATILITY_CONSTANT, 1, 1, 50_000,
                new double[decisions], new double[decisions], new double[decisions]);

        assertRejectedBeforeNativeWork(overflowingOutput, "request dimensions overflow");
    }

    @Test
    public void rejectsOutputArityBeforeNativeWork() {
        KernelRequest valid = request(0.01d);
        KernelRequest wrongArity = new KernelRequest(valid.operation(), valid.fromInclusive(), valid.toInclusive(), 3,
                valid.numeric(), valid.determinism(), valid.seed(), valid.tolerance(), valid.params(), valid.inputs(),
                valid.estimatedScalarNanos(), valid.peakDeviceBytesEstimate());

        assertRejectedBeforeNativeWork(wrongArity, "returns 2 outputs per index, not 3");
    }

    @Test
    public void rejectsMalformedStateAndSharedHistoryBuffersBeforeNativeWork() {
        KernelRequest valid = request(0.01d);
        List<double[]> inputs = valid.inputs();

        assertRejectedBeforeNativeWork(request(valid, valid.params(), inputs.subList(0, inputs.size() - 1)),
                "expects 5 input buffers, not 4");
        for (int buffer : new int[] { MonteCarloKernel.INPUT_PRICES, MonteCarloKernel.INPUT_MEANS,
                MonteCarloKernel.INPUT_DRIFTS, MonteCarloKernel.INPUT_VARIANCES }) {
            List<double[]> malformed = new ArrayList<>(inputs);
            malformed.set(buffer, new double[valid.size() - 1]);
            assertRejectedBeforeNativeWork(request(valid, valid.params(), malformed),
                    "expects 4 values in input buffer " + buffer + ", not 3");
        }
        for (int length : new int[] { 6, 8 }) {
            List<double[]> malformed = new ArrayList<>(inputs);
            malformed.set(MonteCarloKernel.INPUT_RETURNS, new double[length]);
            assertRejectedBeforeNativeWork(request(valid, valid.params(), malformed),
                    "expects a shared returns buffer of 7 values, not " + length);
        }
    }

    @Test
    public void assessmentAndExecutionRejectUnusableMemoryCeilings() throws Exception {
        useLibraryFile();
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), "generic");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
            throw new AssertionError("an infeasible request must not load native code");
        }, bridgeFailing());
        for (long ceiling : new long[] { 0L, -1L, 263L }) {
            System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(ceiling));
            String expected = ceiling > 0L ? "needs 216 bytes per decision plus 48 shared bytes" : "must be > 0";

            Assessment assessment = provider.assess(request(0.01d));
            NativeProviderException failure = assertThrows(NativeProviderException.class,
                    () -> provider.execute(request(0.01d)));

            assertThat(assessment.supported()).isFalse();
            assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
            assertThat(assessment.diagnostic().detail()).contains(expected);
            assertThat(failure.getMessage()).contains(expected);
        }
    }

    @Test
    public void assessmentRejectsRelativeAndMalformedLibraryPathsWithoutLoading() {
        MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
            throw new AssertionError("assessment must not load native code");
        }, bridgeFailing());
        for (String path : new String[] { "relative-metal.dylib", "invalid\0metal.dylib" }) {
            System.setProperty(MetalNativeLibrary.LIBRARY_PROPERTY, path);

            Assessment assessment = provider.assess(request(0.01d));

            assertThat(assessment.supported()).isFalse();
            assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
            assertThat(assessment.diagnostic().detail()).contains("Metal library not found");
        }
    }

    @Test
    public void unidentifiedColdFamilyStaysUnqualifiedWithoutNativeLoading() throws Exception {
        useLibraryFile();
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), " ");
        String originalOs = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
                throw new AssertionError("an unidentified assessment must not initialize native code");
            }, bridgeFailing());

            for (int index = 0; index < 2; index++) {
                Assessment assessment = provider.assess(request(0.01d));

                assertThat(assessment.supported()).isTrue();
                assertThat(assessment.backend()).isEqualTo(Backend.METAL);
                assertThat(assessment.deviceId()).isEqualTo("metal/generic");
                assertThat(assessment.predictedTotalNanos()).isEqualTo(Long.MAX_VALUE);
            }
        } finally {
            if (originalOs == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOs);
            }
        }
    }

    @Test
    public void measuredFamilyPredictsFiniteTotalCost() throws Exception {
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
    public void fp32InputRoundingCounterexampleIsDeclinedAtAMicroTolerance() throws Exception {
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
    public void rejectsOverflowingFp32VarianceBeforeNativeWork() {
        KernelRequest overflowing = singleRow(1e-4d, MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL,
                MonteCarloKernel.VOLATILITY_CONSTANT, 0d, 1e40d, 0.01d);

        assertRejectedBeforeNativeWork(overflowing, "overflows single precision");
    }

    @Test
    public void ewmaAndFp32NormalRequestsAreNotCertified() throws Exception {
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
    public void executeMapsSamplesDecisionMajorAcrossChunks() throws Exception {
        useLibraryFile();
        System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, "264");
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
    public void executeRejectsShortSamples() throws Exception {
        useLibraryFile();
        MetalNativeBridge shortBridge = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                return new MetalEvaluationResult(1d, 0d, 1d, new float[] { 1f });
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), shortBridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).isEqualTo("metal returned 1 samples, expected 8 for 4 decisions");
    }

    @Test
    public void executeFailureSurfacesNativeCause() throws Exception {
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
    public void probeFailureSurfacesAsNativeError() throws Exception {
        useLibraryFile();
        MetalNativeBridge unavailable = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                return new MetalProbeResult(false, "", 0L, "no device");
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), unavailable);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).contains("metal", "no device");
    }

    @Test
    public void loadFailureRejectsExecutionBeforeProbing() {
        MetalAccelerationProvider provider = new MetalAccelerationProvider(
                () -> new MetalNativeLibrary.LoadResult(false, null, "incompatible Metal ABI"), bridgeFailing());

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure.getMessage()).contains("metal native execution failed", "incompatible Metal ABI");
    }

    @Test
    public void probeLinkageErrorRetainsNativeCause() {
        UnsatisfiedLinkError cause = new UnsatisfiedLinkError("missing Metal probe symbol");
        FakeBridge bridge = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                throw cause;
            }

            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("a failed probe must not evaluate");
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));

        assertThat(failure).hasCause(cause).hasMessageContaining("missing Metal probe symbol");
    }

    @Test
    public void probeMemoryCeilingRejectsExecutionBeforeStagingAnyChunk() throws Exception {
        useLibraryFile();
        FakeBridge bridge = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                return new MetalProbeResult(true, "Portable Metal GPU", 526L, "test");
            }

            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                throw new AssertionError("the probed budget cannot fit one decision");
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(loaderWith(true), bridge);

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider.execute(request(0.01d)));
        Assessment assessment = provider.assess(request(0.01d));

        assertThat(failure.getMessage()).contains("216 bytes per decision plus 48 shared bytes", "263-byte");
        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.deviceId()).isEqualTo("metal/Portable Metal GPU");
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(assessment.diagnostic().detail()).contains("263-byte");
        assertThat(provider.deviceFamily()).isEqualTo("portablemetalgpu");
    }

    @Test
    public void failedEvaluationStaysColdUntilSuccessfulRetryAndReusesKernel() throws Exception {
        useLibraryFile();
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), " M5MAX ");
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        IllegalStateException cause = new IllegalStateException("transient Metal command-buffer fault");
        FakeBridge bridge = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                probes.incrementAndGet();
                return new MetalProbeResult(true, "Apple M5 Max", 960L, "test");
            }

            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                if (evaluations.getAndIncrement() == 0) {
                    throw cause;
                }
                assertThat(request.decisionCount()).isEqualTo(2);
                return super.evaluate(request);
            }
        };
        MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
            loads.incrementAndGet();
            return new MetalNativeLibrary.LoadResult(true, null, "test");
        }, bridge, ShockPathQualification.of(Backend.METAL, 1, "m5max",
                new ShockPathQualification.Coefficients(1_000L, 100L, 1d, 0d, 8L)));
        KernelRequest request = request(0.01d);

        Assessment cold = provider.assess(request);
        NativeProviderException failure = assertThrows(NativeProviderException.class, () -> provider.execute(request));
        System.clearProperty(ShockPathQualification.familyProperty(Backend.METAL));
        Assessment failed = provider.assess(request);
        KernelResult first = provider.execute(request);
        Assessment warm = provider.assess(request);
        KernelResult second = provider.execute(request);

        assertThat(cold.supported()).isTrue();
        assertThat(cold.deviceId()).isEqualTo("metal/m5max");
        assertThat(cold.predictedTotalNanos()).isEqualTo(1_016L);
        assertThat(cold.peakDeviceBytes()).isEqualTo(624L);
        assertThat(failure).hasCause(cause);
        assertThat(failed.supported()).isTrue();
        assertThat(failed.deviceId()).isEqualTo("metal/Apple M5 Max");
        assertThat(failed.predictedTotalNanos()).isEqualTo(1_016L);
        assertThat(failed.peakDeviceBytes()).isEqualTo(480L);
        assertThat(warm.deviceId()).isEqualTo("metal/Apple M5 Max");
        assertThat(warm.predictedTotalNanos()).isEqualTo(116L);
        assertThat(warm.peakDeviceBytes()).isEqualTo(480L);
        assertThat(first.outputs()).containsExactly(0d, 1d, 100d, 101d, 200d, 201d, 300d, 301d);
        assertThat(second.outputs()).containsExactly(first.outputs());
        assertThat(loads.get()).isEqualTo(1);
        assertThat(probes.get()).isEqualTo(1);
        assertThat(evaluations.get()).isEqualTo(5);
    }

    private static void assertRejectedBeforeNativeWork(KernelRequest request, String detail) {
        System.setProperty(ShockPathQualification.familyProperty(Backend.METAL), "generic");
        MetalAccelerationProvider provider = new MetalAccelerationProvider(() -> {
            throw new AssertionError("request validation must precede native loading");
        }, bridgeFailing());

        Assessment assessment = provider.assess(request);
        NativeProviderException failure = assertThrows(NativeProviderException.class, () -> provider.execute(request));

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.diagnostic().code()).isEqualTo(DiagnosticCode.UNSUPPORTED);
        assertThat(assessment.diagnostic().providerId()).isEqualTo("metal");
        assertThat(assessment.diagnostic().detail()).contains(detail);
        assertThat(failure.getMessage()).contains(detail);
    }

    private static KernelRequest withParam(KernelRequest template, int parameter, double value) {
        double[] params = template.params();
        params[parameter] = value;
        return request(template, params, template.inputs());
    }

    private static KernelRequest request(KernelRequest template, double[] params, List<double[]> inputs) {
        return new KernelRequest(template.operation(), template.fromInclusive(), template.toInclusive(),
                template.outputsPerIndex(), template.numeric(), template.determinism(), template.seed(),
                template.tolerance(), params, inputs, template.estimatedScalarNanos(),
                template.peakDeviceBytesEstimate());
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
