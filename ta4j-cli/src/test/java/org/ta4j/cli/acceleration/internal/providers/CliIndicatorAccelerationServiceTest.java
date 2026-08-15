/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
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
    void linuxCompositeFailureIsQuarantinedUnderTheSelectionProviderId() {
        // Mirrors the automatic Linux selection: the composite advertises the
        // first available member's capability (CUDA here) while the selection's
        // providerId is "opencl". A failing member must quarantine the selection
        // so later evaluations fail closed instead of re-running native code.
        CliIndicatorAccelerationService
                .useProviderSelectionForTests(() -> new CliIndicatorAccelerationService.ProviderSelection("opencl",
                        Backend.OPENCL, () -> new CompositeForecastAccelerationProvider(
                                List.of(() -> new ForecastAccelerationProvider() {
                                    @Override
                                    public Capability capability() {
                                        return new Capability("cuda", Backend.CUDA, true, true, "fixture-gpu", "");
                                    }

                                    @Override
                                    public double predictedSpeedup(Request<Forecast> request) {
                                        return 1d;
                                    }

                                    @Override
                                    public Result<Forecast> evaluate(Request<Forecast> request) {
                                        throw new NativeProviderException("CUDA",
                                                new IllegalStateException("device lost"));
                                    }
                                }, () -> new ForecastAccelerationProvider() {
                                    @Override
                                    public Capability capability() {
                                        return new Capability("opencl", Backend.OPENCL, true, true, "fixture-opencl",
                                                "");
                                    }

                                    @Override
                                    public double predictedSpeedup(Request<Forecast> request) {
                                        return 0d;
                                    }

                                    @Override
                                    public Result<Forecast> evaluate(Request<Forecast> request) {
                                        return Result.notExecuted(Status.SKIPPED, Backend.OPENCL,
                                                new Diagnostic(DiagnosticCode.CPU_FASTER, "opencl", "below threshold"));
                                    }
                                }))));
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end - 1, end);

        Result<Forecast> first = new CliIndicatorAccelerationService().evaluate(request);
        assertThat(first.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);

        Result<Forecast> second = new CliIndicatorAccelerationService().evaluate(request);
        assertThat(second.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(second.diagnostic().detail()).contains("quarantined");
    }

    @Test
    void quarantinedProviderFailsClosedWithoutNativeExecution() {
        // An automatic (non-qualification) evaluation must fail closed after a
        // quarantine so a known-broken native path is not re-run on every
        // request. The explicit qualification route is the operator's escape
        // hatch and is covered by QualificationAfterQuarantineTest.
        CliIndicatorAccelerationService.useProviderForTests(new ForecastAccelerationProvider() {
            private final Capability capability = new Capability("metal", Backend.METAL, true, true, "fixture", "");

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
                throw new AssertionError("quarantined provider must not execute");
            }
        });
        CliIndicatorAccelerationService.quarantineForTests("metal", "device lost");
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();

        Result<Forecast> result = new CliIndicatorAccelerationService().evaluate(new Request<>(forecast, end - 1, end));

        assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(result.diagnostic().detail()).contains("quarantined", "device lost");
    }

    @Test
    void malformedNativeOutputQuarantinesProviderAndFailsClosed() {
        // A provider that materializes output contradicting the captured
        // request (shape or value invariants) is quarantined under its
        // provider id and the request fails closed to scalar execution.
        CliIndicatorAccelerationService.useProviderForTests(new ForecastAccelerationProvider() {
            @Override
            public Capability capability() {
                return new Capability("metal", Backend.METAL, true, true, "fixture", "");
            }

            @Override
            public double predictedSpeedup(Request<Forecast> request) {
                return 1d;
            }

            @Override
            public Result<Forecast> evaluate(Request<Forecast> request) {
                throw new MalformedProviderResultException("materialized row count 7 does not match decision count 4");
            }
        });
        MonteCarloPriceForecastIndicator forecast = forecast();
        int end = forecast.getBarSeries().getEndIndex();
        Request<Forecast> request = new Request<>(forecast, end - 1, end);

        Result<Forecast> first = new CliIndicatorAccelerationService().evaluate(request);
        assertThat(first.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(first.diagnostic().detail()).contains("malformed native output: materialized row count 7");

        Result<Forecast> second = new CliIndicatorAccelerationService().evaluate(request);
        assertThat(second.diagnostic().code()).isEqualTo(DiagnosticCode.PROVIDER_FAILURE);
        assertThat(second.diagnostic().detail()).contains("quarantined", "materialized row count 7");
    }

    @Test
    void legacyRngVersionDisablesAccelerationBecauseNativeLanesCannotReproduceIt() {
        // -Dta4j.forecast.rngVersion=0 switches the scalar lane to the
        // pre-0.23.1 shared SplittableRandom stream (see
        // MonteCarloSimulation.legacyStreamRequested()). The native kernels only
        // implement the versioned per-path stream, so an accelerated result would
        // silently publish different values than the scalar lane the property
        // promises to restore. Transparent acceleration must therefore refuse to
        // execute and let scalar evaluation serve the legacy values.
        System.setProperty("ta4j.forecast.rngVersion", "0");
        try {
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
                    return Result.executed(Backend.METAL, List.of(Forecast.unstable(request.toInclusive(), 2)), true,
                            1L, new Diagnostic(DiagnosticCode.ACCELERATED, "test", "fixture"));
                }
            });
            MonteCarloPriceForecastIndicator forecast = forecast();
            int end = forecast.getBarSeries().getEndIndex();

            Result<Forecast> result = new CliIndicatorAccelerationService()
                    .evaluate(new Request<>(forecast, end - 1, end));

            assertThat(result.status()).isEqualTo(Status.SKIPPED);
            assertThat(result.diagnostic().code()).isEqualTo(DiagnosticCode.CPU_FASTER);
            assertThat(result.diagnostic().detail()).contains("rngVersion");
        } finally {
            System.clearProperty("ta4j.forecast.rngVersion");
        }
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

        assertThat(result.status()).isEqualTo(org.ta4j.core.acceleration.AccelerationRuntime.Status.SKIPPED);
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
