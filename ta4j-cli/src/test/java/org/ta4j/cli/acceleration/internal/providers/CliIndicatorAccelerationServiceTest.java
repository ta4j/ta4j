/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class CliIndicatorAccelerationServiceTest {

    @AfterEach
    void restoreProperties() {
        CliIndicatorAccelerationService.clearQuarantineForTests();
    }

    @Test
    void unsupportedIndicatorIsRejectedBeforePlatformProbe() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        Result<Num> result = new CliIndicatorAccelerationService()
                .evaluate(new Request<>(close, 0, series.getEndIndex()));

        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.UNSUPPORTED);
        assertThat(result.values()).isEmpty();
    }

    @Test
    void quarantinedProviderFailsClosedWithoutNativeExecution() {
        CliIndicatorAccelerationService.useQualificationProviderForTests("metal");
        CliIndicatorAccelerationService.quarantineForTests("metal", "device lost");
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();

        Result<Forecast> result = new CliIndicatorAccelerationService().evaluate(new Request<>(forecast, end - 1, end));

        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(result.diagnostic().detail()).contains("quarantined", "device lost");
    }

    @Test
    void providerRequestRejectionFallsBackWithoutEscapingTheTransparentBoundary() {
        CliIndicatorAccelerationService.useProviderForTests(new ForecastAccelerationProvider() {
            private final Capability capability = new Capability("test", Backend.METAL, true, false, "fixture", "");

            @Override
            public Capability capability() {
                return capability;
            }

            @Override
            public double predictedSpeedup(Request<Forecast> request) {
                return 1d;
            }

            @Override
            public Result<Forecast> evaluate(Request<Forecast> request) {
                throw new IllegalArgumentException("fixture memory ceiling");
            }
        });
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();

        Result<Forecast> result = new CliIndicatorAccelerationService().evaluate(new Request<>(forecast, end - 1, end));

        assertThat(result.status()).isEqualTo(org.ta4j.core.internal.acceleration.AccelerationRuntime.Status.SKIPPED);
        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_UNAVAILABLE);
        assertThat(result.diagnostic().detail()).contains("fixture memory ceiling");
    }

    private static MonteCarloPriceForecastIndicator forecast() {
        double[] prices = new double[80];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        return MonteCarloPriceForecastIndicator.builder(close, new EwmaReturnForecastStateIndicator(returns, 8, 0.94d))
                .horizon(2)
                .iterationCount(8)
                .lookbackBarCount(16)
                .build();
    }
}
