/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
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
@Tag("requires-metal")
class MetalNativeIntegrationTest {

    private static final double RELATIVE_TOLERANCE = 2e-4;

    @Test
    void nativeProbeInitializesTheMetalPipeline() {
        Path library = configuredLibrary();
        assertThat(Files.isRegularFile(library)).isTrue();
        MetalAccelerationProviderFactory.clearProbeCacheForTests();

        ForecastAccelerationProvider provider = new MetalAccelerationProviderFactory().probe();

        assertThat(provider.capability().available()).as(provider.capability().detail()).isTrue();
        assertThat(provider.capability().nativeInitialized()).isTrue();
        assertThat(provider.capability().deviceName()).isNotBlank();
    }

    @Test
    void metalPreservesForecastDecisionsAcrossAllModelsAndChunkBoundary() {
        for (ShockModel shockModel : ShockModel.values()) {
            for (VolatilityUpdateMode volatilityMode : VolatilityUpdateMode.values()) {
                assertParity(shockModel, volatilityMode, 255);
                assertParity(shockModel, volatilityMode, 256);
            }
        }
    }

    @Test
    void metalPreservesUnstableAndZeroVarianceForecasts() {
        double[] prices = new double[320];
        java.util.Arrays.fill(prices, 100d);
        MonteCarloPriceForecastIndicator forecast = forecast(prices, ShockModel.NORMAL, VolatilityUpdateMode.EWMA, 256);

        assertParity(forecast, 0, 2, "unstable");
        int endIndex = forecast.getBarSeries().getEndIndex();
        assertParity(forecast, endIndex - 2, endIndex, "zero-variance");
    }

    private static void assertParity(ShockModel shockModel, VolatilityUpdateMode volatilityMode, int paths) {
        MonteCarloPriceForecastIndicator forecast = forecast(prices(), shockModel, volatilityMode, paths);
        int endIndex = forecast.getBarSeries().getEndIndex();
        assertParity(forecast, endIndex - 2, endIndex, shockModel + "/" + volatilityMode + "/paths=" + paths);
    }

    private static void assertParity(MonteCarloPriceForecastIndicator forecast, int fromInclusive, int toInclusive,
            String context) {
        List<Forecast> expected = new ArrayList<>();
        for (int index = fromInclusive; index <= toInclusive; index++) {
            expected.add(forecast.getValue(index));
        }
        Result<Forecast> actual = provider().evaluate(new Request<>(forecast, fromInclusive, toInclusive));

        assertThat(actual.backend()).isEqualTo(Backend.METAL);
        assertThat(actual.values()).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            Forecast scalar = expected.get(i);
            Forecast accelerated = actual.values().get(i);
            assertThat(accelerated.isStable()).as(context + "/stable/index=" + i).isEqualTo(scalar.isStable());
            assertThat(accelerated.sampleCount()).isEqualTo(scalar.sampleCount());
            if (!scalar.isStable()) {
                continue;
            }
            assertClose(accelerated.mean(), scalar.mean(), context + "/mean/index=" + i);
            assertClose(accelerated.median(), scalar.median(), context + "/median/index=" + i);
            assertClose(accelerated.standardDeviation(), scalar.standardDeviation(),
                    context + "/standardDeviation/index=" + i);
            for (Double probability : scalar.quantiles().keySet()) {
                assertClose(accelerated.quantile(probability), scalar.quantile(probability),
                        context + "/quantile=" + probability + "/index=" + i);
            }
        }
    }

    private static ForecastAccelerationProvider provider() {
        ForecastAccelerationProvider provider = new MetalAccelerationProviderFactory().probe();
        assertThat(provider.capability().available()).as(provider.capability().detail()).isTrue();
        return provider;
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

    private static double[] prices() {
        double[] prices = new double[320];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.07d + Math.sin(i * 0.13d) * 1.5d;
        }
        return prices;
    }

    private static void assertClose(Num actual, Num expected, String context) {
        double expectedValue = expected.doubleValue();
        double tolerance = Math.max(1e-8, Math.abs(expectedValue) * RELATIVE_TOLERANCE);
        assertThat(actual.doubleValue()).as(context).isCloseTo(expectedValue, Offset.offset(tolerance));
    }

    private static Path configuredLibrary() {
        String configured = System.getProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY);
        assertThat(configured).as(MetalAccelerationProviderFactory.LIBRARY_PROPERTY).isNotBlank();
        return Path.of(configured);
    }
}
