/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

class CompositeForecastAccelerationProviderTest {

    @Test
    void memberNativeFailureFallsThroughToNextAvailableMember() {
        AtomicInteger cudaAttempts = new AtomicInteger();
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0.25d, request -> {
            cudaAttempts.incrementAndGet();
            throw new NativeProviderException("CUDA", new IllegalStateException("device lost"));
        });
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                CompositeForecastAccelerationProviderTest::executedResult);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.EXECUTED);
        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
        assertThat(result.values()).hasSize(3);
        assertThat(cudaAttempts).hasValue(1);
    }

    @Test
    void allMembersFailingStillPropagatesSoTheServiceCanQuarantine() {
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0.25d, request -> {
            throw new NativeProviderException("CUDA", new IllegalStateException("device lost"));
        });
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d, request -> {
            throw new NativeProviderException("OpenCL", new IllegalStateException("out of resources"));
        });
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        assertThatThrownBy(() -> composite.evaluate(request()))
                .isInstanceOf(NativeProviderException.class)
                .hasMessageContaining("OpenCL");
    }

    @Test
    void unavailableMemberIsSkippedInFavorOfAvailableMember() {
        ForecastAccelerationProvider cuda = unavailable("cuda", Backend.CUDA, "CUDA driver missing");
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                CompositeForecastAccelerationProviderTest::executedResult);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.EXECUTED);
        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
    }

    @Test
    void memberBelowSpeedupThresholdIsSkippedInFavorOfQualifiedMember() {
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0d,
                request -> {
                    throw new AssertionError("below-threshold member must not execute");
                });
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                CompositeForecastAccelerationProviderTest::executedResult);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.EXECUTED);
        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
    }

    private static ForecastAccelerationProvider member(String providerId, Backend backend, double predictedSpeedup,
            Function<Request<Forecast>, Result<Forecast>> evaluation) {
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
                return evaluation.apply(request);
            }
        };
    }

    private static ForecastAccelerationProvider unavailable(String providerId, Backend backend, String detail) {
        return new UnavailableForecastProvider(new Capability(providerId, backend, false, false, "", detail));
    }

    private static Result<Forecast> executedResult(Request<Forecast> request) {
        Forecast unstable = Forecast.unstable(request.fromInclusive(), 3);
        List<Forecast> values = java.util.stream.IntStream.range(0, request.size())
                .mapToObj(index -> unstable)
                .toList();
        return new Result<>(Status.EXECUTED, Backend.OPENCL, values, true, 0L,
                new Diagnostic(DiagnosticCode.ACCELERATED, "opencl", "fixture"));
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
