/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
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

@Tag("benchmark")
@Tag("requires-cuda")
class CudaBenchmarkTest {

    @Test
    void emitsFreshProcessCrossoverMeasurement() {
        int decisions = Integer.getInteger("ta4j.cuda.benchmark.decisions", 8);
        int paths = Integer.getInteger("ta4j.cuda.benchmark.paths", 4_096);
        int horizon = Integer.getInteger("ta4j.cuda.benchmark.horizon", 8);
        int repetitions = Integer.getInteger("ta4j.cuda.benchmark.repetitions", 3);

        evaluateScalar(1, 64, 2);
        evaluateCuda(1, 64, 2);
        List<Long> scalarNanos = new ArrayList<>(repetitions);
        List<Long> cudaNanos = new ArrayList<>(repetitions);
        for (int repetition = 0; repetition < repetitions; repetition++) {
            scalarNanos.add(evaluateScalar(decisions, paths, horizon));
            cudaNanos.add(evaluateCuda(decisions, paths, horizon));
        }
        Collections.sort(scalarNanos);
        Collections.sort(cudaNanos);
        long scalarMedian = scalarNanos.get(scalarNanos.size() / 2);
        long cudaMedian = cudaNanos.get(cudaNanos.size() / 2);
        double speedup = (double) scalarMedian / cudaMedian;
        long work = Math.multiplyExact(Math.multiplyExact((long) decisions, paths), horizon);
        System.out.printf(
                "CUDA_BENCHMARK {\"decisions\":%d,\"paths\":%d,\"horizon\":%d,\"work\":%d,\"scalarNanos\":%d,\"cudaNanos\":%d,\"speedup\":%.6f}%n",
                decisions, paths, horizon, work, scalarMedian, cudaMedian, speedup);
    }

    private static long evaluateScalar(int decisions, int paths, int horizon) {
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int toInclusive = forecast.getBarSeries().getEndIndex();
        int fromInclusive = toInclusive - decisions + 1;
        long started = System.nanoTime();
        List<Forecast> values = new ArrayList<>(decisions);
        for (int index = fromInclusive; index <= toInclusive; index++) {
            values.add(forecast.getValue(index));
        }
        long elapsed = System.nanoTime() - started;
        assertThat(values).hasSize(decisions).allSatisfy(value -> assertThat(value.isStable()).isTrue());
        return elapsed;
    }

    private static long evaluateCuda(int decisions, int paths, int horizon) {
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int toInclusive = forecast.getBarSeries().getEndIndex();
        int fromInclusive = toInclusive - decisions + 1;
        Request<Forecast> request = new Request<>(forecast, fromInclusive, toInclusive);
        ForecastAccelerationProvider provider = new CudaAccelerationProviderFactory().probe();
        assertThat(provider.capability().available()).as(provider.capability().detail()).isTrue();
        long started = System.nanoTime();
        Result<Forecast> result = provider.evaluate(request);
        long elapsed = System.nanoTime() - started;
        assertThat(result.values()).hasSize(decisions).allSatisfy(value -> assertThat(value.isStable()).isTrue());
        return elapsed;
    }

    private static MonteCarloPriceForecastIndicator forecast(int decisions, int paths, int horizon) {
        int barCount = Math.max(320, decisions + 160);
        double[] prices = new double[barCount];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.04d + Math.sin(i * 0.09d) * 1.25d;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 32, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(horizon)
                .iterationCount(paths)
                .lookbackBarCount(128)
                .seed(0x5090C0DEL)
                .shockModel(ShockModel.STANDARDIZED_EMPIRICAL)
                .volatilityUpdateMode(VolatilityUpdateMode.CONSTANT)
                .build();
    }
}
