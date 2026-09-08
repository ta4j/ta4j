/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.providers.OpenClAccelerationProvider;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

@Tag("benchmark")
@Tag("requires-opencl")
class OpenClBacktestBenchmarkTest {

    @Test
    void compareScalarAndExplicitOpenClEvaluation() throws Exception {
        int decisions = Integer.getInteger("ta4j.opencl.benchmark.decisions", 256);
        int paths = Integer.getInteger("ta4j.opencl.benchmark.paths", 2_048);
        int horizon = Integer.getInteger("ta4j.opencl.benchmark.horizon", 32);
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int from = forecast.getBarSeries().getEndIndex() - decisions + 1;
        MonteCarloPriceForecastIndicator scalar = forecast(decisions, paths, horizon);
        long scalarStarted = System.nanoTime();
        for (int index = from; index <= scalar.getBarSeries().getEndIndex(); index++) {
            assertThat(scalar.getValue(index).isStable()).isTrue();
        }
        long scalarNanos = System.nanoTime() - scalarStarted;
        long elapsed = NativeBenchmarkSupport.evaluate(new OpenClAccelerationProvider(), forecast, from,
                forecast.getBarSeries().getEndIndex());
        Path output = Path.of(System.getProperty("ta4j.acceleration.benchmark.output",
                ".agents/benchmarks/cf-336-validation/cf-336-transparent-opencl-backtest.json"));
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        String report = "{\n  \"schemaVersion\": 1,\n  \"backend\": \"opencl\",\n"
                + "  \"dispatchMode\": \"explicit-native\",\n  \"scalarNanos\": " + scalarNanos + ",\n"
                + "  \"decisions\": " + decisions + ",\n  \"paths\": " + paths + ",\n"
                + "  \"horizon\": " + horizon + ",\n  \"elapsedNanos\": " + elapsed + "\n}\n";
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    private static MonteCarloPriceForecastIndicator forecast(int decisions, int paths, int horizon) {
        int barCount = Math.max(4_096, decisions + 512);
        double[] prices = new double[barCount];
        prices[0] = 100d;
        for (int i = 1; i < prices.length; i++) {
            prices[i] = prices[i - 1] * Math.exp(0.0002d + 0.006d * Math.sin(i * 0.031d)
                    + 0.003d * Math.cos(i * 0.071d));
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(new LogReturnIndicator(close), 256,
                0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state).horizon(horizon).iterationCount(paths)
                .lookbackBarCount(256).seed(42L).shockModel(ShockModel.STANDARDIZED_EMPIRICAL)
                .volatilityUpdateMode(VolatilityUpdateMode.CONSTANT).build();
    }
}
