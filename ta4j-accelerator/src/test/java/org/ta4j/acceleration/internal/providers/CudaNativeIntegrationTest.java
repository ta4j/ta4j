/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.AcceleratedIndicatorBatchEvaluator;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndicatorBatchEvaluator;
import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

@Tag("integration")
class CudaNativeIntegrationTest {

    private static final double RELATIVE_TOLERANCE = 1e-4;

    @Test
    void nativeProbePassesOnlyAfterDeviceSelfTest() {
        Path library = configuredLibrary();
        assertThat(Files.isRegularFile(library)).isTrue();
        CudaAccelerationProviderFactory.clearProbeCacheForTests();

        IndicatorAccelerationProvider provider = new CudaAccelerationProviderFactory()
                .probe(List.of(ForecastBatchAdapter.OPERATION_ID));

        assertThat(provider.capability().available()).isTrue();
        assertThat(provider.capability().nativeInitialized()).isTrue();
        assertThat(provider.capability().deviceName()).contains("RTX 5090");
    }

    @Test
    void cudaMatchesScalarForecastFixturesAcrossShockAndVolatilityModes() {
        for (ShockModel shockModel : ShockModel.values()) {
            for (VolatilityUpdateMode volatilityMode : VolatilityUpdateMode.values()) {
                assertParity(shockModel, volatilityMode, 255);
                assertParity(shockModel, volatilityMode, 256);
            }
        }
    }

    @Test
    void cudaMatchesZeroVarianceAndUnstableForecasts() {
        double[] constantPrices = new double[320];
        java.util.Arrays.fill(constantPrices, 100d);
        MonteCarloPriceForecastIndicator forecast = forecast(constantPrices, ShockModel.NORMAL,
                VolatilityUpdateMode.EWMA, 256);

        assertParity(forecast, 0, 2, "unstable");
        int endIndex = forecast.getBarSeries().getEndIndex();
        assertParity(forecast, endIndex - 2, endIndex, "zero-variance");
    }

    @Test
    void requiredCudaExecutesThroughThePublicBatchEvaluator() {
        MonteCarloPriceForecastIndicator forecast = forecast(ShockModel.NORMAL, VolatilityUpdateMode.EWMA, 512);
        int index = forecast.getBarSeries().getEndIndex();

        IndicatorBatchResult<Forecast> result = new AcceleratedIndicatorBatchEvaluator().evaluate(forecast, index - 1,
                index, new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d));

        assertThat(result.diagnostics().effectiveMode()).isEqualTo(AccelerationMode.CUDA);
        assertThat(result.values()).hasSize(2).allSatisfy(value -> assertThat(value.value().isStable()).isTrue());
    }

    @Test
    void concurrentRequestsRemainDeterministicAndComplete() throws Exception {
        MonteCarloPriceForecastIndicator forecast = forecast(ShockModel.STANDARDIZED_EMPIRICAL,
                VolatilityUpdateMode.CONSTANT, 512);
        int index = forecast.getBarSeries().getEndIndex();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IndicatorBatchResult<Forecast>> first = executor
                    .submit(() -> new AcceleratedIndicatorBatchEvaluator().evaluate(forecast, index - 1, index,
                            new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d)));
            Future<IndicatorBatchResult<Forecast>> second = executor
                    .submit(() -> new AcceleratedIndicatorBatchEvaluator().evaluate(forecast, index - 1, index,
                            new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d)));

            IndicatorBatchResult<Forecast> firstResult = first.get(10, TimeUnit.SECONDS);
            IndicatorBatchResult<Forecast> secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.orderedValues()).usingRecursiveComparison().isEqualTo(secondResult.orderedValues());
        }
    }

    private static void assertParity(ShockModel shockModel, VolatilityUpdateMode volatilityMode, int paths) {
        MonteCarloPriceForecastIndicator forecast = forecast(shockModel, volatilityMode, paths);
        int toInclusive = forecast.getBarSeries().getEndIndex();
        int fromInclusive = toInclusive - 2;
        assertParity(forecast, fromInclusive, toInclusive, shockModel + "/" + volatilityMode + "/paths=" + paths);
    }

    private static void assertParity(MonteCarloPriceForecastIndicator forecast, int fromInclusive, int toInclusive,
            String context) {
        IndicatorBatchResult<Forecast> expected = IndicatorBatchEvaluator.evaluate(forecast, fromInclusive, toInclusive,
                AccelerationConfig.cpu());
        AccelerationConfig config = new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d);
        IndicatorBatchRequest<Forecast> request = new IndicatorBatchRequest<>(forecast, fromInclusive, toInclusive,
                config);
        AdapterMatch<Forecast> match = new ForecastBatchAdapter().match(forecast);
        IndicatorAccelerationProvider provider = new CudaAccelerationProviderFactory()
                .probe(List.of(ForecastBatchAdapter.OPERATION_ID));
        IndicatorBatchResult<Forecast> actual = provider.evaluate(request, match).orElseThrow();

        assertThat(actual.diagnostics().effectiveMode()).as(actual.diagnostics().toString())
                .isEqualTo(AccelerationMode.CUDA);
        assertThat(actual.diagnostics().nativeInitialized()).isTrue();
        assertThat(actual.values()).hasSameSizeAs(expected.values());
        for (int i = 0; i < expected.values().size(); i++) {
            Forecast expectedForecast = expected.values().get(i).value();
            Forecast actualForecast = actual.values().get(i).value();
            assertThat(actual.values().get(i).index()).isEqualTo(expected.values().get(i).index());
            assertThat(actualForecast.isStable()).isEqualTo(expectedForecast.isStable());
            assertThat(actualForecast.sampleCount()).isEqualTo(expectedForecast.sampleCount());
            if (!expectedForecast.isStable()) {
                continue;
            }
            assertClose(actualForecast.mean(), expectedForecast.mean(), context + "/mean/index=" + i);
            assertClose(actualForecast.median(), expectedForecast.median(), context + "/median/index=" + i);
            assertClose(actualForecast.standardDeviation(), expectedForecast.standardDeviation(),
                    context + "/standardDeviation/index=" + i);
            for (Double probability : expectedForecast.quantiles().keySet()) {
                assertClose(actualForecast.quantile(probability), expectedForecast.quantile(probability),
                        context + "/quantile=" + probability + "/index=" + i);
            }
        }
    }

    private static MonteCarloPriceForecastIndicator forecast(ShockModel shockModel, VolatilityUpdateMode volatilityMode,
            int paths) {
        double[] prices = new double[320];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.07d + Math.sin(i * 0.13d) * 1.5d;
        }
        return forecast(prices, shockModel, volatilityMode, paths);
    }

    private static MonteCarloPriceForecastIndicator forecast(double[] prices, ShockModel shockModel,
            VolatilityUpdateMode volatilityMode, int paths) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 32, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(7)
                .iterationCount(paths)
                .lookbackBarCount(64)
                .seed(0x5A17C0DEL)
                .shockModel(shockModel)
                .volatilityUpdateMode(volatilityMode)
                .quantiles(0.0, 0.05, 0.25, 0.5, 0.75, 0.95, 1.0)
                .build();
    }

    private static void assertClose(Num actual, Num expected, String context) {
        double expectedValue = expected.doubleValue();
        double tolerance = Math.max(1e-10, Math.abs(expectedValue) * RELATIVE_TOLERANCE);
        assertThat(actual.doubleValue()).as(context)
                .isCloseTo(expectedValue, org.assertj.core.data.Offset.offset(tolerance));
    }

    private static Path configuredLibrary() {
        String configured = System.getProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
        assertThat(configured).as(CudaAccelerationProviderFactory.LIBRARY_PROPERTY).isNotBlank();
        return Path.of(configured);
    }
}
