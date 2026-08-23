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

class CudaAccelerationProviderTest {

    private String previousLibrary;
    private String previousMaxMemory;

    @BeforeEach
    void captureProperties() {
        previousLibrary = System.getProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
        previousMaxMemory = System.getProperty(CudaAccelerationProvider.MAX_MEMORY_PROPERTY);
    }

    @AfterEach
    void restorePropertiesAndCache() {
        restoreProperty(CudaAccelerationProvider.MAX_MEMORY_PROPERTY, previousMaxMemory);
        restoreProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY, previousLibrary);
        CudaAccelerationProviderFactory.clearProbeCacheForTests();
    }

    @Test
    void fakeBridgeMaterializesValidatedOrderedForecasts() {
        MonteCarloPriceForecastIndicator forecast = forecast(doubleSeries());
        Request<Forecast> request = request(forecast);

        Result<Forecast> result = provider(new FakeBridge(CudaAccelerationProviderTest::constantResult))
                .evaluate(request);

        assertThat(result.backend()).isEqualTo(Backend.CUDA);
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
            CudaEvaluationResult valid = constantResult(request);
            double[] rows = valid.rows();
            rows[5] = 101d;
            rows[6] = 99d;
            return new CudaEvaluationResult(1d, 1d, 1d, 1d, rows);
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> provider(bridge).evaluate(request(forecast)));

        assertThat(exception).hasMessageContaining("quantiles are not monotone");
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

        System.setProperty(CudaAccelerationProvider.MAX_MEMORY_PROPERTY, "1");
        MonteCarloPriceForecastIndicator doubleForecast = forecast(doubleSeries());
        assertThrows(IllegalArgumentException.class, () -> provider(bridge).evaluate(request(doubleForecast)));
        assertThat(evaluations).hasValue(0);
    }

    @Test
    void automaticSelectionRequiresQualifiedWorkloadAndComputeCapability() {
        MonteCarloPriceForecastIndicator forecast = forecast(doubleSeries());
        assertThat(provider(new FakeBridge(CudaAccelerationProviderTest::constantResult))
                .predictedSpeedup(request(forecast))).isZero();
        assertThat(CudaCrossoverModel.predictedSpeedup(qualifiedProbe(), 262_144L)).isEqualTo(0.25d);
        assertThat(CudaCrossoverModel.predictedSpeedup(qualifiedProbe(), 262_143L)).isZero();
        assertThat(CudaCrossoverModel.predictedSpeedup(new CudaProbeResult(true, "RTX 4090", 8, 9,
                16L * 1024 * 1024 * 1024, 24L * 1024 * 1024 * 1024, 12_800, 12_800, "self-test passed"),
                Long.MAX_VALUE)).isZero();
    }

    @Test
    void automaticSelectionRequiresMeasuredPathCountMinimum() {
        // 3 x 3 x 87,382 clears the 262,144-step work floor but exposes only
        // three path threads per decision; selection must stay scalar.
        assertThat(provider(new FakeBridge(CudaAccelerationProviderTest::constantResult))
                .predictedSpeedup(request(forecast(doubleSeries(), 3, 87_382)))).isZero();
        // The same product with qualified path count still selects CUDA.
        assertThat(provider(new FakeBridge(CudaAccelerationProviderTest::constantResult))
                .predictedSpeedup(request(forecast(doubleSeries(), 64, 4_096)))).isPositive();
    }
    @Test
    void productionProbeCachesSuccessAndRejectsWrongArchitecture() {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        FakeBridge bridge = new FakeBridge(CudaAccelerationProviderTest::constantResult) {
            @Override
            public CudaProbeResult probe() {
                probes.incrementAndGet();
                return qualifiedProbe();
            }
        };
        System.setProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY, "C:\\qualified\\cuda.dll");
        CudaAccelerationProviderFactory factory = new CudaAccelerationProviderFactory(() -> {
            loads.incrementAndGet();
            return new CudaNativeLibrary.LoadResult(true, Path.of("C:\\qualified\\cuda.dll"), "");
        }, bridge, true);

        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(loads).hasValue(1);
        assertThat(probes).hasValue(1);

        CudaProbeResult wrongArchitecture = new CudaProbeResult(true, "other", 8, 9, 1_000_000L, 2_000_000L, 13_300,
                13_300, "");
        CudaAccelerationProviderFactory rejected = new CudaAccelerationProviderFactory(
                () -> new CudaNativeLibrary.LoadResult(true, Path.of("C:\\other\\cuda.dll"), ""),
                new FakeBridge(CudaAccelerationProviderTest::constantResult) {
                    @Override
                    public CudaProbeResult probe() {
                        return wrongArchitecture;
                    }
                }, false);
        assertThat(rejected.probe().capability().available()).isFalse();

        CudaAccelerationProviderFactory throwing = new CudaAccelerationProviderFactory(
                () -> new CudaNativeLibrary.LoadResult(true, Path.of("C:\\throwing\\cuda.dll"), ""),
                new FakeBridge(CudaAccelerationProviderTest::constantResult) {
                    @Override
                    public CudaProbeResult probe() {
                        throw new IllegalStateException("context creation failed");
                    }
                }, false);
        assertThat(throwing.probe().capability().detail()).contains("self-test failed", "context creation failed");
    }

    private static CudaAccelerationProvider provider(CudaNativeBridge bridge) {
        Capability capability = new Capability("cuda", Backend.CUDA, true, true, "RTX 5090", "");
        return new CudaAccelerationProvider(capability, bridge, qualifiedProbe());
    }

    private static CudaProbeResult qualifiedProbe() {
        return new CudaProbeResult(true, "RTX 5090", 12, 0, 16L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 13_300,
                13_300, "self-test passed");
    }

    private static CudaEvaluationResult constantResult(NativeForecastRequest request) {
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
        return new CudaEvaluationResult(4d, 1d, 1d, 1d, rows);
    }

    private static Request<Forecast> request(MonteCarloPriceForecastIndicator forecast) {
        int toInclusive = forecast.getBarSeries().getEndIndex();
        return new Request<>(forecast, toInclusive - 2, toInclusive);
    }

    private static MonteCarloPriceForecastIndicator forecast(BarSeries series) {
        return forecast(series, 64, 3);
    }

    private static MonteCarloPriceForecastIndicator forecast(BarSeries series, int iterationCount, int horizon) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(horizon)
                .iterationCount(iterationCount)
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

    private static class FakeBridge implements CudaNativeBridge {

        private final Function<NativeForecastRequest, CudaEvaluationResult> evaluation;

        private FakeBridge(Function<NativeForecastRequest, CudaEvaluationResult> evaluation) {
            this.evaluation = evaluation;
        }

        @Override
        public CudaProbeResult probe() {
            return qualifiedProbe();
        }

        @Override
        public CudaEvaluationResult evaluate(NativeForecastRequest request) {
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
