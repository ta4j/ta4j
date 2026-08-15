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

        assertThatThrownBy(() -> composite.evaluate(request())).isInstanceOf(NativeProviderException.class)
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
    void failedMemberStatusFallsThroughToTheNextAvailableMember() {
        // The class javadoc documents that "a member's native failure falls
        // through to the remaining members (for example a broken CUDA device
        // on a host with a healthy OpenCL device)". A returned FAILED status is
        // a member failure just like a thrown NativeProviderException and must
        // not short-circuit the fallback chain.
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0.25d,
                request -> new Result<>(Status.FAILED, Backend.CUDA, List.of(), false, 0L,
                        new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, "cuda", "kernel failed")));
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                CompositeForecastAccelerationProviderTest::executedResult);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.EXECUTED);
        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
    }

    @Test
    void failedMemberStatusPropagatesSoTheServiceCanQuarantineWhenEveryMemberFails() {
        // The class javadoc documents that "when every member fails, the last
        // failure propagates so the service quarantines the composite under its
        // own provider id". A returned FAILED status must propagate like a
        // thrown native failure instead of short-circuiting with a
        // member-attributed result the service never quarantines.
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0.25d,
                request -> new Result<>(Status.FAILED, Backend.CUDA, List.of(), false, 0L,
                        new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, "cuda", "kernel failed")));
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                request -> new Result<>(Status.FAILED, Backend.OPENCL, List.of(), false, 0L,
                        new Diagnostic(DiagnosticCode.PROVIDER_FAILURE, "opencl", "CL_OUT_OF_RESOURCES")));
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        assertThatThrownBy(() -> composite.evaluate(request())).isInstanceOf(NativeProviderException.class)
                .hasMessageContaining("opencl");
    }

    @Test
    void allUnavailableMembersReportTheCompositeOwnIdentity() {
        // The composite is selected under its own provider id ("opencl") and
        // advertises that identity from capability(). When no member can
        // execute, the fallback result must carry the same identity; otherwise
        // every Linux host without accelerators reports a "cuda"-attributed
        // UNAVAILABLE result under an "opencl" selection, and diagnostics
        // attribute the composite's decision to a member the service never
        // selected.
        ForecastAccelerationProvider cuda = unavailable("cuda", Backend.CUDA, "CUDA driver missing");
        ForecastAccelerationProvider opencl = unavailable("opencl", Backend.OPENCL, "no OpenCL device");
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.diagnostic().providerId()).isEqualTo("opencl");
        assertThat(result.backend()).isEqualTo(Backend.OPENCL);
    }

    @Test
    void memberBelowSpeedupThresholdIsSkippedInFavorOfQualifiedMember() {
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0d, request -> {
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

    @Test
    void belowThresholdSkipKeepsTheProbedMembersNativeInitializedFlag() {
        // Members are probed eagerly at construction (capability().available()
        // with nativeInitialized=true), so when every member declines below the
        // automatic speedup threshold the composite's SKIPPED result must not
        // claim native code was never initialized. The runtime aggregates
        // Result.nativeInitialized() into its diagnostics, and the composite's
        // own capability() reports nativeInitialized=true; a false flag here
        // misreports the initialization state for the same decision.
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0d, request -> {
            throw new AssertionError("below-threshold member must not execute");
        });
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0d, request -> {
            throw new AssertionError("below-threshold member must not execute");
        });
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.SKIPPED);
        assertThat(composite.capability().nativeInitialized()).isTrue();
        assertThat(result.nativeInitialized()).isTrue();
    }

    @Test
    void allUnavailableCompositeSurfacesTheSelectionIdentityAndOpenClDetail() {
        // The automatic Linux selection is "opencl" and the composite advertises
        // that identity. When no member is available, evaluate() must keep the
        // selection identity instead of delegating to the first member's ("cuda")
        // result: diagnostics and quarantine key on the advertised identity, and
        // Linux users need the OpenCL probe detail (the universal provider's
        // reason, e.g. "device lacks FP64") to diagnose why acceleration is off.
        ForecastAccelerationProvider cuda = unavailable("cuda", Backend.CUDA, "CUDA driver missing");
        ForecastAccelerationProvider opencl = unavailable("opencl", Backend.OPENCL, "device lacks FP64");
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        Result<Forecast> result = composite.evaluate(request());

        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.diagnostic().providerId()).isEqualTo("opencl");
        assertThat(result.diagnostic().detail()).contains("FP64");
    }

    @Test
    void availableCompositeStillAdvertisesTheFirstAvailableMemberUnderItsOwnIdentity() {
        // Negative control: when a member IS available the composite must keep
        // advertising the first available member's device under its own
        // provider id, and evaluation must still execute the qualified member.
        ForecastAccelerationProvider cuda = member("cuda", Backend.CUDA, 0d, request -> {
            throw new AssertionError("below-threshold member must not execute");
        });
        ForecastAccelerationProvider opencl = member("opencl", Backend.OPENCL, 0.25d,
                CompositeForecastAccelerationProviderTest::executedResult);
        CompositeForecastAccelerationProvider composite = new CompositeForecastAccelerationProvider(
                List.of(() -> cuda, () -> opencl));

        assertThat(composite.capability().providerId()).isEqualTo("opencl");
        assertThat(composite.capability().deviceName()).isEqualTo("fixture-cuda");
        assertThat(composite.capability().available()).isTrue();

        Result<Forecast> result = composite.evaluate(request());
        assertThat(result.status()).isEqualTo(Status.EXECUTED);
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
