/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class OpenClAccelerationProviderTest {

    private String previousLibrary;
    private String previousMaxMemory;

    @BeforeEach
    void captureProperties() {
        previousLibrary = System.getProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY);
        previousMaxMemory = System.getProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY);
    }

    @AfterEach
    void restorePropertiesAndCache() {
        restoreProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, previousMaxMemory);
        restoreProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY, previousLibrary);
        OpenClAccelerationProviderFactory.clearProbeCacheForTests();
    }

    @Test
    void fakeBridgeMaterializesValidatedOrderedForecasts() {
        MonteCarloPriceForecastIndicator forecast = forecast(doubleSeries());
        Request<Forecast> request = request(forecast);

        Result<Forecast> result = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult))
                .evaluate(request);

        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
        assertThat(result.values()).hasSize(request.size()).allSatisfy(value -> {
            assertThat(value.isStable()).isTrue();
            assertThat(value.mean().doubleValue()).isEqualTo(100d);
            assertThat(value.standardDeviation().doubleValue()).isZero();
        });
    }

    @Test
    void staleSnapshotFailsBeforePublication() {
        BarSeries series = doubleSeries();
        MonteCarloPriceForecastIndicator forecast = forecast(series);
        FakeBridge bridge = new FakeBridge(request -> {
            series.addPrice(999d);
            return constantResult(request);
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> provider(bridge).evaluate(request(forecast)));

        assertThat(exception).hasMessageContaining("BarSeries changed");
    }

    @Test
    void invalidNativeQuantilesAreRejectedAtomically() {
        MonteCarloPriceForecastIndicator forecast = forecast(doubleSeries());
        FakeBridge bridge = new FakeBridge(request -> {
            OpenClEvaluationResult valid = constantResult(request);
            double[] rows = valid.rows();
            rows[5] = 101d;
            rows[6] = 99d;
            return new OpenClEvaluationResult(1d, 1d, 1d, 1d, rows);
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> provider(bridge).evaluate(request(forecast)));

        assertThat(exception).hasMessageContaining("quantiles are not monotone");
    }

    @Test
    void memoryCeilingRejectsBeforeAnyCaptureWork() {
        // A request above the configured ceiling must be rejected up front. It must
        // not first run the full snapshot capture (which allocates
        // decisionCount x lookbackBarCount doubles and reads every history cell),
        // because for large backtests that allocation itself exhausts the heap and
        // turns a documented scalar fallback into an OutOfMemoryError.
        AtomicInteger reads = new AtomicInteger();
        BarSeries series = doubleSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        CountingReturnIndicator returns = new CountingReturnIndicator(new LogReturnIndicator(close), reads);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(3)
                .iterationCount(64)
                .lookbackBarCount(16)
                .seed(17L)
                .build();

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, "1");
        OpenClAccelerationProvider provider = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult));

        assertThrows(IllegalArgumentException.class, () -> provider.evaluate(request(forecast)));
        assertThat(reads).hasValue(0);
    }

    @Test
    void memoryCeilingCountsHostAndDeviceSortBuffers() {
        // The OpenCL kernels hold the padded samples twice at once: the host
        // staging array (padded_host) and the device sort buffer
        // (device_samples), each at nextPowerOfTwo(iterationCount) doubles. A
        // ceiling that counts only one buffer accepts requests whose two padded
        // buffers alone exceed it, before inputs and result storage.
        int iterationCount = (1 << 20) + 1; // just above a power of two
        BarSeries series = doubleSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(2)
                .iterationCount(iterationCount)
                .lookbackBarCount(16)
                .seed(17L)
                .quantiles(0.5)
                .build();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end, end);
        long singleBufferEstimate = ForecastSnapshot.estimatedPeakBytes(1L, 16L, iterationCount, 1L, false, 1L);
        long bothBuffersEstimate = ForecastSnapshot.estimatedPeakBytes(1L, 16L, 2L * nextPowerOfTwo(iterationCount), 1L,
                false, 1L);
        long ceiling = singleBufferEstimate + (bothBuffersEstimate - singleBufferEstimate) / 2L;

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(ceiling));
        OpenClAccelerationProvider provider = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> provider.evaluate(request));
        assertThat(exception).hasMessageContaining("above the");
    }

    @Test
    void powerOfTwoIterationCountIsUnaffectedByThePaddedCeiling() {
        // Negative control: for an exact power-of-two iteration count the native
        // padded buffer equals the raw count, so the padded estimate must not
        // reject requests the raw estimate accepts.
        int iterationCount = 1 << 20;
        BarSeries series = doubleSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(2)
                .iterationCount(iterationCount)
                .lookbackBarCount(16)
                .seed(17L)
                .quantiles(0.5)
                .build();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end, end);

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(1L << 31));
        OpenClAccelerationProvider provider = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult));

        Result<Forecast> result = provider.evaluate(request);
        assertThat(result.status()).isEqualTo(org.ta4j.core.acceleration.AccelerationRuntime.Status.EXECUTED);
    }

    private static long nextPowerOfTwo(long value) {
        long power = 1L;
        while (power < value) {
            power <<= 1;
        }
        return power;
    }


    @Test
    void memoryCeilingIncludesMomentPartialBuffers() {
        // The native moments reduction stages two device-side partial buffers
        // at ceil(iterationCount / MOMENT_THREADS) doubles each (MOMENT_THREADS
        // is 256). A request whose full estimate - partials included - exceeds
        // the configured ceiling must be rejected in preflight, and the same
        // request must be admitted once the ceiling covers the estimate.
        int iterationCount = 3 * 256 + 7;
        long expectedBytes = ForecastSnapshot.estimatedPeakBytes(1L, 16L, 2L * nextPowerOfTwo(iterationCount), 1L,
                false, 1L)
                + (1L * 16L * Double.BYTES) // device_history: one decision x 16 bars
                + 128L // profiling events for one decision
                + (((iterationCount + 255L) / 256L) * 2L * Double.BYTES); // moment partials

        BarSeries series = doubleSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(2)
                .iterationCount(iterationCount)
                .lookbackBarCount(16)
                .seed(17L)
                .quantiles(0.5)
                .build();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end, end);

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(expectedBytes - 1L));
        OpenClAccelerationProvider rejecting = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> rejecting.evaluate(request));
        assertThat(exception).hasMessageContaining("above the");

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(expectedBytes));
        OpenClAccelerationProvider admitting = provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult));
        Result<Forecast> result = admitting.evaluate(request);
        assertThat(result.status()).isEqualTo(org.ta4j.core.acceleration.AccelerationRuntime.Status.EXECUTED);
    }
    @Test
    void decimalPrecisionAndMemoryCeilingFailBeforeNativeExecution() {
        AtomicInteger evaluations = new AtomicInteger();
        FakeBridge bridge = new FakeBridge(request -> {
            evaluations.incrementAndGet();
            return constantResult(request);
        });
        MonteCarloPriceForecastIndicator decimalForecast = forecast(
                new MockBarSeriesBuilder().withData(prices()).build());
        assertThrows(IllegalArgumentException.class, () -> provider(bridge).evaluate(request(decimalForecast)));

        System.setProperty(OpenClAccelerationProvider.MAX_MEMORY_PROPERTY, "1");
        MonteCarloPriceForecastIndicator doubleForecast = forecast(doubleSeries());
        assertThrows(IllegalArgumentException.class, () -> provider(bridge).evaluate(request(doubleForecast)));
        assertThat(evaluations).hasValue(0);
    }

    @Test
    void productionProbeCachesSuccessAndRejectsUnavailableDevice() {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        FakeBridge bridge = new FakeBridge(OpenClAccelerationProviderTest::constantResult) {
            @Override
            public OpenClProbeResult probe() {
                probes.incrementAndGet();
                return qualifiedProbe();
            }
        };
        System.setProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY, "/qualified/libta4j-opencl.so");
        OpenClAccelerationProviderFactory factory = new OpenClAccelerationProviderFactory(() -> {
            loads.incrementAndGet();
            return new OpenClNativeLibrary.LoadResult(true, Path.of("/qualified/libta4j-opencl.so"), "");
        }, bridge, true);

        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(loads).hasValue(1);
        assertThat(probes).hasValue(1);

        OpenClAccelerationProviderFactory rejected = new OpenClAccelerationProviderFactory(
                () -> new OpenClNativeLibrary.LoadResult(true, Path.of("/unavailable/libta4j-opencl.so"), ""),
                new FakeBridge(OpenClAccelerationProviderTest::constantResult) {
                    @Override
                    public OpenClProbeResult probe() {
                        return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, false, "device lacks FP64");
                    }
                }, false);
        assertThat(rejected.probe().capability().available()).isFalse();
        assertThat(rejected.probe().capability().detail()).contains("device lacks FP64");

        OpenClAccelerationProviderFactory throwing = new OpenClAccelerationProviderFactory(
                () -> new OpenClNativeLibrary.LoadResult(true, Path.of("/throwing/libta4j-opencl.so"), ""),
                new FakeBridge(OpenClAccelerationProviderTest::constantResult) {
                    @Override
                    public OpenClProbeResult probe() {
                        throw new IllegalStateException("context creation failed");
                    }
                }, false);
        assertThat(throwing.probe().capability().detail()).contains("self-test failed", "context creation failed");
    }

    @Test
    void unqualifiedOpenClDevicesStayOnScalarExecution() {
        MonteCarloPriceForecastIndicator small = forecast(doubleSeries());
        Request<Forecast> smallRequest = request(small);
        assertThat(
                provider(new FakeBridge(OpenClAccelerationProviderTest::constantResult)).predictedSpeedup(smallRequest))
                .isZero();

        MonteCarloPriceForecastIndicator large = largeForecast();
        Request<Forecast> largeRequest = request(large);
        OpenClAccelerationProvider cpuProvider = provider(
                new FakeBridge(OpenClAccelerationProviderTest::constantResult), cpuProbe());
        assertThat(cpuProvider.predictedSpeedup(largeRequest)).isZero();

        // Even a qualified-looking GPU predicts no speedup until real-GPU
        // measurement qualifies the OpenCL lane, mirroring the CUDA model's
        // measured-only approach.
        OpenClAccelerationProvider gpuProvider = provider(
                new FakeBridge(OpenClAccelerationProviderTest::constantResult), qualifiedProbe());
        assertThat(gpuProvider.predictedSpeedup(largeRequest)).isZero();
    }

    @Test
    void autoCrossoverDoesNotEngageDevicesWithoutQualifyingCapability() {
        // The automatic selection contract promises a qualified GPU that is
        // expected to beat scalar execution. The crossover model must therefore
        // distinguish devices by their reported capability. A first-generation
        // integrated GPU with 512MB of global memory cannot beat a JIT scalar
        // lane on the FP64-heavy forecast kernels, so it must never be
        // auto-selected.
        MonteCarloPriceForecastIndicator large = largeForecast();
        Request<Forecast> largeRequest = request(large);

        OpenClProbeResult weakIntegratedGpu = new OpenClProbeResult(true, "Weak Integrated GPU", 1, 2,
                512L * 1024 * 1024, 1L * 1024 * 1024 * 1024, 0, 0, true, "self-test passed");
        OpenClAccelerationProvider weakProvider = provider(
                new FakeBridge(OpenClAccelerationProviderTest::constantResult), weakIntegratedGpu);

        assertThat(weakProvider.predictedSpeedup(largeRequest)).isZero();
    }

    private static OpenClAccelerationProvider provider(OpenClNativeBridge bridge) {
        return provider(bridge, qualifiedProbe());
    }

    private static OpenClAccelerationProvider provider(OpenClNativeBridge bridge, OpenClProbeResult probe) {
        Capability capability = new Capability("opencl", Backend.OPENCL, true, true, probe.deviceName(), "");
        return new OpenClAccelerationProvider(capability, bridge, probe);
    }

    private static OpenClProbeResult qualifiedProbe() {
        return new OpenClProbeResult(true, "OpenCL GPU", 3, 0, 16L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 0, 0,
                true, "self-test passed");
    }

    private static OpenClProbeResult cpuProbe() {
        return new OpenClProbeResult(true, "PoCL CPU", 3, 0, 16L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 0, 0,
                false, "self-test passed");
    }

    private static OpenClEvaluationResult constantResult(NativeForecastRequest request) {
        int rowLength = 4 + request.quantiles().length;
        double[] rows = new double[request.decisionCount() * rowLength];
        int[] stable = request.stable();
        for (int decision = 0; decision < request.decisionCount(); decision++) {
            int offset = decision * rowLength;
            if (stable[decision] == 0) {
                rows[offset] = 1d;
                continue;
            }
            rows[offset + 1] = 100d;
            rows[offset + 2] = 100d;
            rows[offset + 3] = 0d;
            for (int quantile = 0; quantile < request.quantiles().length; quantile++) {
                rows[offset + 4 + quantile] = 100d;
            }
        }
        return new OpenClEvaluationResult(4d, 1d, 1d, 1d, rows);
    }

    private static Request<Forecast> request(MonteCarloPriceForecastIndicator forecast) {
        int toInclusive = forecast.getBarSeries().getEndIndex();
        return new Request<>(forecast, toInclusive - 2, toInclusive);
    }

    private static MonteCarloPriceForecastIndicator largeForecast() {
        BarSeries series = doubleSeries();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(1024)
                .iterationCount(16384)
                .lookbackBarCount(16)
                .seed(17L)
                .build();
    }

    private static MonteCarloPriceForecastIndicator forecast(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(3)
                .iterationCount(64)
                .lookbackBarCount(16)
                .seed(17L)
                .build();
    }

    private static BarSeries doubleSeries() {
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices()).build();
    }

    private static double[] prices() {
        double[] prices = new double[80];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.1d;
        }
        return prices;
    }

    private static final class CountingReturnIndicator implements ReturnIndicator {

        private final ReturnIndicator delegate;
        private final AtomicInteger reads;

        private CountingReturnIndicator(ReturnIndicator delegate, AtomicInteger reads) {
            this.delegate = delegate;
            this.reads = reads;
        }

        @Override
        public Num getValue(int index) {
            reads.incrementAndGet();
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public BarSeries getBarSeries() {
            return delegate.getBarSeries();
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return delegate.getReturnRepresentation();
        }
    }

    private static class FakeBridge implements OpenClNativeBridge {

        private final Function<NativeForecastRequest, OpenClEvaluationResult> evaluation;

        private FakeBridge(Function<NativeForecastRequest, OpenClEvaluationResult> evaluation) {
            this.evaluation = evaluation;
        }

        @Override
        public OpenClProbeResult probe() {
            return qualifiedProbe();
        }

        @Override
        public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
            return evaluation.apply(request);
        }
    }

    private static void restoreProperty(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
