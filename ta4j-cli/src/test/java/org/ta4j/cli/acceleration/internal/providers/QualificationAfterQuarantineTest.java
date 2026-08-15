/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
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

/**
 * The Linux composite failure quarantine became effective under the selection
 * provider id in the fallback-chain remediation. Once the quarantine store
 * holds an entry it has no escape hatch: the explicit internal qualification
 * path ({@code -Dta4j.acceleration=opencl} / the test qualification hook) is
 * consulted <em>after</em> the quarantine lookup and can therefore never
 * re-attempt a provider that failed once - even when the failure was transient
 * (a driver hiccup, a one-time context creation race) and the operator
 * explicitly asked to qualify the device. A failure in one selection therefore
 * permanently disables the provider for every later selection in the same JVM,
 * including the documented qualification route.
 */
class QualificationAfterQuarantineTest {

    @AfterEach
    void restore() {
        CliIndicatorAccelerationService.clearQuarantineForTests();
    }

    @Test
    void explicitQualificationCanStillAttemptAQuarantinedProvider() {
        // Phase 1: a Linux-style composite whose members both fail natively
        // quarantines the "opencl" selection id through the real service path.
        CliIndicatorAccelerationService.useProviderSelectionForTests(
                () -> new CliIndicatorAccelerationService.ProviderSelection("opencl", Backend.OPENCL,
                        () -> new CompositeForecastAccelerationProvider(List.of(
                                (Supplier<ForecastAccelerationProvider>) () -> throwingMember("cuda", "device lost"),
                                () -> throwingMember("opencl", "out of resources")))));
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end - 1, end);

        Result<Forecast> failed = new CliIndicatorAccelerationService().evaluate(request);
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);

        // Phase 2: the explicit qualification path for the same provider id
        // must still attempt the provider (that is the documented way to
        // qualify a device), instead of being short-circuited by the earlier
        // failure. The quarantine stays in place; only the qualification route
        // is allowed through.
        CliIndicatorAccelerationService.useProviderSelectionForTests(null);
        CliIndicatorAccelerationService.useQualificationProviderForTests("opencl");
        AtomicInteger attempts = new AtomicInteger();
        CliIndicatorAccelerationService.useProviderForTests(qualificationProvider(attempts));

        Result<Forecast> qualified = new CliIndicatorAccelerationService().evaluate(request);

        assertThat(attempts).hasValue(1);
        assertThat(qualified.status()).isEqualTo(Status.EXECUTED);
        assertThat(qualified.diagnostic().code()).isEqualTo(DiagnosticCode.ACCELERATED);
    }

    @Test
    void automaticSelectionStillFailsClosedAfterQuarantine() {
        // Negative control: the automatic (non-qualification) path must keep
        // failing closed after a quarantine so a known-broken native path is
        // not re-run on every request.
        CliIndicatorAccelerationService.quarantineForTests("opencl", "device lost");
        AtomicInteger attempts = new AtomicInteger();
        CliIndicatorAccelerationService.useProviderForTests(qualificationProvider(attempts));

        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();
        Result<Forecast> result = new CliIndicatorAccelerationService().evaluate(new Request<>(forecast, end - 1, end));

        assertThat(attempts).hasValue(0);
        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.diagnostic().detail()).contains("quarantined");
    }

    private static ForecastAccelerationProvider qualificationProvider(AtomicInteger attempts) {
        Capability capability = new Capability("opencl", Backend.OPENCL, true, true, "fixture-opencl", "");
        return new ForecastAccelerationProvider() {
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
                attempts.incrementAndGet();
                return new Result<>(Status.EXECUTED, Backend.OPENCL, values(request), true, 0L,
                        new Diagnostic(DiagnosticCode.ACCELERATED, "opencl", "fixture"));
            }
        };
    }

    private static ForecastAccelerationProvider throwingMember(String providerId, String detail) {
        Capability capability = new Capability(providerId, providerId.equals("cuda") ? Backend.CUDA : Backend.OPENCL,
                true, true, "fixture-" + providerId, "");
        return new ForecastAccelerationProvider() {
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
                throw new NativeProviderException(providerId, new IllegalStateException(detail));
            }
        };
    }

    private static List<Forecast> values(Request<Forecast> request) {
        return java.util.stream.IntStream.range(0, request.size())
                .mapToObj(index -> Forecast.unstable(request.fromInclusive() + index, 2))
                .toList();
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
