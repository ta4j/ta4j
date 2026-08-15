/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * The fallback-chain remediation documents the composite contract as
 * "evaluation executes the first member whose crossover prediction clears the
 * service threshold", yet the tail of {@code evaluate()} still calls
 * {@code members.get(0).evaluate(request)} when no member ran. That executes
 * member 0 with neither an availability nor a speedup-threshold check: on Linux
 * the composite is built with CUDA first, and CUDA's crossover model
 * intentionally never qualifies (compute 12.0 and an unreachable work floor),
 * so the tail would run the unqualified CUDA native kernel. The crossover gates
 * added by the device-capability remediation (GPU-only, workload floor, 2 GiB
 * device memory) exist precisely to keep below-threshold devices from
 * executing; the tail defeats them.
 */
class CompositeBelowThresholdTailTest {

    @Test
    void noMemberExecutesWhenEveryMemberIsBelowTheAutomaticThreshold() {
        AtomicInteger executions = new AtomicInteger();
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0d, executions);
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0d, executions);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(executions).hasValue(0);
        assertThat(result.status()).isEqualTo(Status.SKIPPED);
        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.CPU_FASTER);
    }

    @Test
    void unavailableCompositeReportsItsOwnIdentityWithTheLastMembersReason() {
        // Negative control: an all-unavailable composite keeps the selection
        // identity ("opencl") and surfaces the last member's (OpenCL's)
        // actionable reason instead of executing anything or misattributing the
        // failure to the first (CUDA) member.
        ForecastAccelerationProvider cuda = unavailable("cuda", Backend.CUDA, "CUDA driver missing");
        ForecastAccelerationProvider opencl = unavailable("opencl", Backend.OPENCL, "OpenCL ICD missing");
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.diagnostic().providerId()).isEqualTo("opencl");
        assertThat(result.diagnostic().detail()).contains("OpenCL ICD missing");
    }

    private static ForecastAccelerationProvider member(String providerId, Backend backend, double predictedSpeedup,
            AtomicInteger executions) {
        Capability capability = new Capability(providerId, backend, true, true, "fixture-" + providerId, "");
        return new ForecastAccelerationProvider() {
            @Override
            public Capability capability() {
                return capability;
            }

            @Override
            public double predictedSpeedup(Request<Forecast> request) {
                return predictedSpeedup;
            }

            @Override
            public Result<Forecast> evaluate(Request<Forecast> request) {
                executions.incrementAndGet();
                List<Forecast> values = java.util.stream.IntStream.range(0, request.size())
                        .mapToObj(index -> Forecast.unstable(request.fromInclusive() + index, 3))
                        .toList();
                return new Result<>(Status.EXECUTED, backend, values, true, 0L,
                        new Diagnostic(DiagnosticCode.ACCELERATED, providerId, "fixture"));
            }
        };
    }

    private static ForecastAccelerationProvider unavailable(String providerId, Backend backend, String detail) {
        return new UnavailableForecastProvider(new Capability(providerId, backend, false, false, "", detail));
    }

    private static Request<Forecast> request() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();
        return new Request<>(forecast, end - 2, end);
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
                .horizon(3)
                .iterationCount(8)
                .lookbackBarCount(16)
                .build();
    }
}
