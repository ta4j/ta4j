/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
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
@Tag("requires-cuda")
class CudaNativeIntegrationTest {

    private static final double RELATIVE_TOLERANCE = 1e-4;

    @Test
    void nativeProbePassesOnlyAfterDeviceSelfTest() {
        Path library = configuredLibrary();
        assertThat(Files.isRegularFile(library)).isTrue();
        CudaAccelerationProviderFactory.clearProbeCacheForTests();

        ForecastAccelerationProvider provider = new CudaAccelerationProviderFactory().probe();

        assertThat(provider.capability().available()).isTrue();
        assertThat(provider.capability().nativeInitialized()).isTrue();
        assertThat(provider.capability().deviceName()).isNotBlank();
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
    void concurrentRequestsRemainDeterministicAndComplete() throws Exception {
        MonteCarloPriceForecastIndicator forecast = forecast(ShockModel.STANDARDIZED_EMPIRICAL,
                VolatilityUpdateMode.CONSTANT, 512);
        int index = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, index - 1, index);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Result<Forecast>> first = executor.submit(() -> provider().evaluate(request));
            Future<Result<Forecast>> second = executor.submit(() -> provider().evaluate(request));

            Result<Forecast> firstResult = first.get(10, TimeUnit.SECONDS);
            Result<Forecast> secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.values()).usingRecursiveComparison().isEqualTo(secondResult.values());
        }
    }

    private static void assertParity(ShockModel shockModel, VolatilityUpdateMode volatilityMode, int paths) {
        MonteCarloPriceForecastIndicator forecast = forecast(shockModel, volatilityMode, paths);
        int toInclusive = forecast.getBarSeries().getEndIndex();
        assertParity(forecast, toInclusive - 2, toInclusive, shockModel + "/" + volatilityMode + "/paths=" + paths);
    }

    private static void assertParity(MonteCarloPriceForecastIndicator forecast, int fromInclusive, int toInclusive,
            String context) {
        List<Forecast> expected = new ArrayList<>();
        for (int index = fromInclusive; index <= toInclusive; index++) {
            expected.add(forecast.getValue(index));
        }
        Request<Forecast> request = new Request<>(forecast, fromInclusive, toInclusive);
        Result<Forecast> actual = provider().evaluate(request);

        assertThat(actual.backend()).isEqualTo(Backend.CUDA);
        assertThat(actual.nativeInitialized()).isTrue();
        assertThat(actual.values()).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            Forecast expectedForecast = expected.get(i);
            Forecast actualForecast = actual.values().get(i);
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

    private static ForecastAccelerationProvider provider() {
        ForecastAccelerationProvider provider = new CudaAccelerationProviderFactory().probe();
        assertThat(provider.capability().available()).as(provider.capability().detail()).isTrue();
        return provider;
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
