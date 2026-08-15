/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

class MetalAccelerationProviderTest {

    @AfterEach
    void reset() {
        System.clearProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY);
        System.clearProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY);
        MetalAccelerationProviderFactory.clearProbeCacheForTests();
    }

    @Test
    void fakeBridgeMaterializesOrderedEmpiricalForecasts() {
        MonteCarloPriceForecastIndicator forecast = forecast(series(), 64);
        Request<Forecast> request = request(forecast);
        MetalAccelerationProvider provider = provider(new FakeBridge());

        Result<Forecast> result = provider.evaluate(request);

        assertThat(result.backend()).isEqualTo(Backend.METAL);
        assertThat(result.values()).hasSize(request.size()).allSatisfy(value -> {
            assertThat(value.isStable()).isTrue();
            assertThat(value.sampleCount()).isEqualTo(64);
            assertThat(value.mean().doubleValue()).isEqualTo(100d);
        });
    }

    @Test
    void staleSeriesAndMemoryOverflowRejectTheWholeBatch() {
        BarSeries series = series();
        MonteCarloPriceForecastIndicator forecast = forecast(series, 64);
        MetalNativeBridge mutating = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                series.addPrice(999d);
                return super.evaluate(request);
            }
        };
        assertThrows(IllegalStateException.class, () -> provider(mutating).evaluate(request(forecast)));

        System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, "1");
        MonteCarloPriceForecastIndicator fresh = forecast(series(), 64);
        assertThrows(IllegalArgumentException.class, () -> provider(new FakeBridge()).evaluate(request(fresh)));
    }

    @Test
    void boundedChunksPreserveExactResultCoverage() {
        MonteCarloPriceForecastIndicator forecast = forecast(series(), 64);
        AtomicInteger evaluations = new AtomicInteger();
        FakeBridge bridge = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                evaluations.incrementAndGet();
                return super.evaluate(request);
            }
        };
        long bytesPerDecision = 5L * Double.BYTES + Integer.BYTES + 16L * Double.BYTES + 64L * Float.BYTES;
        System.setProperty(MetalAccelerationProvider.MAX_MEMORY_PROPERTY, Long.toString(bytesPerDecision));

        Result<Forecast> result = provider(bridge).evaluate(request(forecast));

        assertThat(evaluations).hasValue(3);
        assertThat(result.values()).hasSize(3);
        assertThat(result.diagnostic().detail()).contains("chunks=3");
    }

    @Test
    void probeIsLazyCachedAndRequiresSuccessfulSelfTest() {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        MetalNativeBridge bridge = new FakeBridge() {
            @Override
            public MetalProbeResult probe() {
                probes.incrementAndGet();
                return qualifiedProbe();
            }
        };
        System.setProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY, "/tmp/metal.dylib");
        MetalAccelerationProviderFactory factory = new MetalAccelerationProviderFactory(() -> {
            loads.incrementAndGet();
            return new MetalNativeLibrary.LoadResult(true, Path.of("/tmp/metal.dylib"), "");
        }, bridge, true);

        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(factory.probe().capability().available()).isTrue();
        assertThat(loads).hasValue(1);
        assertThat(probes).hasValue(1);

        MetalAccelerationProviderFactory rejected = new MetalAccelerationProviderFactory(
                () -> new MetalNativeLibrary.LoadResult(true, Path.of("/tmp/bad.dylib"), ""), new FakeBridge() {
                    @Override
                    public MetalProbeResult probe() {
                        return new MetalProbeResult(false, "", 0L, "self-test failed");
                    }
                }, false);
        assertThat(rejected.probe().capability().available()).isFalse();

        MetalAccelerationProviderFactory throwing = new MetalAccelerationProviderFactory(
                () -> new MetalNativeLibrary.LoadResult(true, Path.of("/tmp/throwing.dylib"), ""), new FakeBridge() {
                    @Override
                    public MetalProbeResult probe() {
                        throw new IllegalStateException("pipeline creation failed");
                    }
                }, false);
        assertThat(throwing.probe().capability().detail()).contains("self-test failed", "pipeline creation failed");
    }

    @Test
    void automaticSelectionRequiresQualifiedDeviceAndWorkload() {
        MonteCarloPriceForecastIndicator forecast = forecast(series(), 64);
        MetalAccelerationProvider provider = provider(new FakeBridge());

        assertThat(provider.predictedSpeedup(request(forecast))).isZero();
        assertThat(MetalCrossoverModel.predictedSpeedup(qualifiedProbe(), 16_777_216L)).isEqualTo(0.25d);
        assertThat(MetalCrossoverModel.predictedSpeedup(new MetalProbeResult(true, "Other GPU", Long.MAX_VALUE, ""),
                Long.MAX_VALUE)).isZero();
    }

    @Test
    void nativeExecutionFailureIsDistinguishedFromStaleInput() {
        MonteCarloPriceForecastIndicator forecast = forecast(series(), 64);
        MetalNativeBridge failing = new FakeBridge() {
            @Override
            public MetalEvaluationResult evaluate(NativeForecastRequest request) {
                throw new IllegalStateException("device lost");
            }
        };

        NativeProviderException failure = assertThrows(NativeProviderException.class,
                () -> provider(failing).evaluate(request(forecast)));

        assertThat(failure).hasMessageContaining("device lost");
    }

    private static MetalAccelerationProvider provider(MetalNativeBridge bridge) {
        return new MetalAccelerationProvider(new Capability("metal", Backend.METAL, true, true, "Apple M5 Max", ""),
                bridge, qualifiedProbe());
    }

    private static MetalProbeResult qualifiedProbe() {
        return new MetalProbeResult(true, "Apple M5 Max", 64L * 1024 * 1024 * 1024, "ready");
    }

    private static Request<Forecast> request(MonteCarloPriceForecastIndicator forecast) {
        int end = forecast.getBarSeries().getEndIndex();
        return new Request<>(forecast, end - 2, end);
    }

    private static MonteCarloPriceForecastIndicator forecast(BarSeries series, int paths) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 8, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(3)
                .iterationCount(paths)
                .lookbackBarCount(16)
                .seed(17L)
                .build();
    }

    private static BarSeries series() {
        double[] prices = new double[80];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + i * 0.1d;
        }
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
    }

    private static class FakeBridge implements MetalNativeBridge {

        @Override
        public MetalProbeResult probe() {
            return qualifiedProbe();
        }

        @Override
        public MetalEvaluationResult evaluate(NativeForecastRequest request) {
            float[] values = new float[Math.multiplyExact(request.decisionCount(), request.iterationCount())];
            java.util.Arrays.fill(values, 100f);
            return new MetalEvaluationResult(4d, 1d, 2d, values);
        }
    }
}
