/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
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
        System.clearProperty(CliIndicatorAccelerationService.QUALIFICATION_PROVIDER_PROPERTY);
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
        System.setProperty(CliIndicatorAccelerationService.QUALIFICATION_PROVIDER_PROPERTY, "metal");
        CliIndicatorAccelerationService.quarantineForTests("metal", "device lost");
        double[] prices = new double[80];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(close, new EwmaReturnForecastStateIndicator(returns, 8, 0.94d))
                .horizon(2)
                .iterationCount(8)
                .lookbackBarCount(16)
                .build();
        int end = series.getEndIndex();

        Result<Forecast> result = new CliIndicatorAccelerationService().evaluate(new Request<>(forecast, end - 1, end));

        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(result.diagnostic().detail()).contains("quarantined", "device lost");
    }
}
