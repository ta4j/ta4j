/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;
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
        IndicatorBatchRequest<Forecast> request = request(forecast);
        CudaAccelerationProvider provider = provider(new FakeBridge(CudaAccelerationProviderTest::constantResult));

        IndicatorBatchResult<Forecast> result = provider.evaluate(request, match(forecast)).orElseThrow();

        assertThat(result.diagnostics().effectiveMode()).isEqualTo(AccelerationMode.CUDA);
        assertThat(result.values()).extracting(value -> value.index())
                .containsExactly(request.fromInclusive(), request.fromInclusive() + 1, request.toInclusive());
        assertThat(result.orderedValues()).allSatisfy(value -> {
            assertThat(value.isStable()).isTrue();
            assertThat(value.mean().doubleValue()).isEqualTo(100d);
            assertThat(value.standardDeviation().doubleValue()).isZero();
        });
    }

    @Test
    void hybridPartitionsWholeIndexRangesAndMergesInOrder() {
        MonteCarloPriceForecastIndicator forecast = forecast(doubleSeries());
        IndicatorBatchRequest<Forecast> base = request(forecast);
        IndicatorBatchRequest<Forecast> hybrid = new IndicatorBatchRequest<>(forecast, base.fromInclusive(),
                base.toInclusive(), new AccelerationConfig(AccelerationMode.HYBRID, true, 0.10d));

        IndicatorBatchResult<Forecast> result = provider(new FakeBridge(CudaAccelerationProviderTest::constantResult))
                .evaluate(hybrid, match(forecast))
                .orElseThrow();

        assertThat(result.diagnostics().effectiveMode()).isEqualTo(AccelerationMode.HYBRID);
        assertThat(result.diagnostics().backendId()).isEqualTo("cpu+cuda");
        assertThat(result.values()).extracting(value -> value.index())
                .containsExactly(hybrid.fromInclusive(), hybrid.fromInclusive() + 1, hybrid.toInclusive());
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
                () -> provider(bridge).evaluate(request(forecast), match(forecast)));

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
                () -> provider(bridge).evaluate(request(forecast), match(forecast)));

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
        assertThrows(IllegalArgumentException.class,
                () -> provider(bridge).evaluate(request(decimalForecast), match(decimalForecast)));

        System.setProperty(CudaAccelerationProvider.MAX_MEMORY_PROPERTY, "1");
        MonteCarloPriceForecastIndicator doubleForecast = forecast(doubleSeries());
        assertThrows(IllegalArgumentException.class,
                () -> provider(bridge).evaluate(request(doubleForecast), match(doubleForecast)));
        assertThat(evaluations).hasValue(0);
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

        assertThat(factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID)).capability().available()).isTrue();
        assertThat(factory.probe(List.of(ForecastBatchAdapter.OPERATION_ID)).capability().available()).isTrue();
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
        assertThat(rejected.probe(List.of(ForecastBatchAdapter.OPERATION_ID)).capability().available()).isFalse();
    }

    private static CudaAccelerationProvider provider(CudaNativeBridge bridge) {
        ProviderCapability capability = new ProviderCapability("cuda", AccelerationMode.CUDA, true, true, "RTX 5090",
                List.of(ForecastBatchAdapter.OPERATION_ID), "");
        return new CudaAccelerationProvider(capability, bridge, qualifiedProbe());
    }

    private static CudaProbeResult qualifiedProbe() {
        return new CudaProbeResult(true, "RTX 5090", 12, 0, 16L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 13_300,
                13_300, "self-test passed");
    }

    private static CudaEvaluationResult constantResult(CudaNativeRequest request) {
        int rowLength = 4 + request.quantiles().length;
        double[] rows = new double[request.decisionCount() * rowLength];
        for (int decision = 0; decision < request.decisionCount(); decision++) {
            int offset = decision * rowLength;
            if (request.stable()[decision] == 0) {
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

    private static IndicatorBatchRequest<Forecast> request(MonteCarloPriceForecastIndicator forecast) {
        int toInclusive = forecast.getBarSeries().getEndIndex();
        return new IndicatorBatchRequest<>(forecast, toInclusive - 2, toInclusive,
                new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d));
    }

    private static AdapterMatch<Forecast> match(MonteCarloPriceForecastIndicator forecast) {
        return new ForecastBatchAdapter().match(forecast);
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

    private static class FakeBridge implements CudaNativeBridge {

        private final Function<CudaNativeRequest, CudaEvaluationResult> evaluation;

        private FakeBridge(Function<CudaNativeRequest, CudaEvaluationResult> evaluation) {
            this.evaluation = evaluation;
        }

        @Override
        public CudaProbeResult probe() {
            return qualifiedProbe();
        }

        @Override
        public CudaEvaluationResult evaluate(CudaNativeRequest request) {
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
