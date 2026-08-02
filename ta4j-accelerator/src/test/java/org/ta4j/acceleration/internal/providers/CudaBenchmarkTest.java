/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.AcceleratedIndicatorBatchEvaluator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndicatorBatchEvaluator;
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

@Tag("benchmark")
class CudaBenchmarkTest {

    @Test
    void measuresFreshForecastsAndEmitsMachineReadableResult() {
        int decisions = Integer.getInteger("ta4j.cuda.benchmark.decisions", 8);
        int paths = Integer.getInteger("ta4j.cuda.benchmark.paths", 4_096);
        int horizon = Integer.getInteger("ta4j.cuda.benchmark.horizon", 8);
        int repetitions = Integer.getInteger("ta4j.cuda.benchmark.repetitions", 3);

        evaluate(1, 64, 2, AccelerationMode.CPU);
        evaluate(1, 64, 2, AccelerationMode.CUDA);
        List<Long> cpuNanos = new ArrayList<>(repetitions);
        List<Long> cudaNanos = new ArrayList<>(repetitions);
        for (int repetition = 0; repetition < repetitions; repetition++) {
            cpuNanos.add(evaluate(decisions, paths, horizon, AccelerationMode.CPU).elapsedNanos());
            cudaNanos.add(evaluate(decisions, paths, horizon, AccelerationMode.CUDA).elapsedNanos());
        }
        Collections.sort(cpuNanos);
        Collections.sort(cudaNanos);
        long cpuMedian = cpuNanos.get(cpuNanos.size() / 2);
        long cudaMedian = cudaNanos.get(cudaNanos.size() / 2);
        Measurement auto = evaluate(decisions, paths, horizon, AccelerationMode.AUTO);
        Measurement hybrid = evaluate(decisions, paths, horizon, AccelerationMode.HYBRID);
        double speedup = (double) cpuMedian / cudaMedian;
        long work = Math.multiplyExact(Math.multiplyExact((long) decisions, paths), horizon);
        System.out.printf(
                "CUDA_BENCHMARK {\"decisions\":%d,\"paths\":%d,\"horizon\":%d,\"work\":%d,\"cpuNanos\":%d,\"cudaNanos\":%d,\"autoNanos\":%d,\"hybridNanos\":%d,\"autoMode\":\"%s\",\"hybridMode\":\"%s\",\"speedup\":%.6f}%n",
                decisions, paths, horizon, work, cpuMedian, cudaMedian, auto.elapsedNanos(), hybrid.elapsedNanos(),
                auto.effectiveMode(), hybrid.effectiveMode(), speedup);
        assertThat(cpuMedian).isPositive();
        assertThat(cudaMedian).isPositive();
        assertThat(auto.effectiveMode()).isEqualTo(AccelerationMode.CPU);
        assertThat(hybrid.effectiveMode()).isEqualTo(AccelerationMode.CPU);
    }

    private static Measurement evaluate(int decisions, int paths, int horizon, AccelerationMode mode) {
        MonteCarloPriceForecastIndicator forecast = forecast(decisions, paths, horizon);
        int toInclusive = forecast.getBarSeries().getEndIndex();
        int fromInclusive = toInclusive - decisions + 1;
        long start = System.nanoTime();
        IndicatorBatchResult<Forecast> result = mode == AccelerationMode.CPU
                ? IndicatorBatchEvaluator.evaluate(forecast, fromInclusive, toInclusive, AccelerationConfig.cpu())
                : new AcceleratedIndicatorBatchEvaluator().evaluate(forecast, fromInclusive, toInclusive,
                        new AccelerationConfig(mode, mode == AccelerationMode.CUDA, 0.10d));
        long elapsed = System.nanoTime() - start;
        assertThat(result.values()).hasSize(decisions)
                .allSatisfy(value -> assertThat(value.value().isStable()).isTrue());
        return new Measurement(elapsed, result.diagnostics().effectiveMode());
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

    private record Measurement(long elapsedNanos, AccelerationMode effectiveMode) {
    }
}
