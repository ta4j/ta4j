/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.providers.CudaAccelerationProvider;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
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
        System.out.printf(Locale.ROOT,
                "CUDA_BENCHMARK {\"decisions\":%d,\"paths\":%d,\"horizon\":%d,\"work\":%d,\"scalarNanos\":%d,\"cudaNanos\":%d,\"speedup\":%.6f}%n",
                decisions, paths, horizon, work, scalarMedian, cudaMedian, speedup);
    }

    private static long evaluateScalar(int decisions, int paths, int horizon) {
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int to = forecast.getBarSeries().getEndIndex();
        int from = to - decisions + 1;
        long started = System.nanoTime();
        for (int index = from; index <= to; index++) {
            assertThat(forecast.getValue(index).isStable()).isTrue();
        }
        return System.nanoTime() - started;
    }

    private static long evaluateCuda(int decisions, int paths, int horizon) {
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int to = forecast.getBarSeries().getEndIndex();
        int from = to - decisions + 1;
        return NativeBenchmarkSupport.evaluate(new CudaAccelerationProvider(), forecast, from, to);
    }

    private static MonteCarloPriceForecastIndicator forecast(int decisions, int paths, int horizon) {
        int barCount = Math.max(320, decisions + 160);
        double[] prices = new double[barCount];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.04d + Math.sin(i * 0.09d) * 1.25d;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(new LogReturnIndicator(close), 32, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state).horizon(horizon).iterationCount(paths)
                .lookbackBarCount(128).seed(0x5090C0DEL).shockModel(ShockModel.STANDARDIZED_EMPIRICAL)
                .volatilityUpdateMode(VolatilityUpdateMode.CONSTANT).build();
    }
}
