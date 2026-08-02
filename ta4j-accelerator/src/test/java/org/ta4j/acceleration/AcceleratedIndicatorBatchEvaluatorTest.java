/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.acceleration.internal.adapters.CloseSmaControlAdapter;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.IndicatorAccelerationProviderFactory;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationDiagnostic;
import org.ta4j.core.acceleration.AccelerationDiagnosticCode;
import org.ta4j.core.acceleration.AccelerationDiagnostics;
import org.ta4j.core.acceleration.AccelerationException;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndicatorBatchEvaluator;
import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;
import org.ta4j.core.acceleration.IndexedIndicatorValue;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;

class AcceleratedIndicatorBatchEvaluatorTest {

    @Test
    void unsupportedGraphFallsBackToCpuWithoutProviderProbe() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        MockIndicator indicator = new MockIndicator(series,
                List.of(series.numFactory().one(), series.numFactory().two(), series.numFactory().numOf(3)));
        CountingProviderFactory providerFactory = new CountingProviderFactory(false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new CloseSmaControlAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Num> result = evaluator.evaluate(indicator, 0, 2, AccelerationConfig.auto());

        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.UNSUPPORTED_GRAPH)).isTrue();
        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(providerFactory.probeCount()).isZero();
    }

    @Test
    void closeSmaControlStaysOnCpuWhenAutoEnabled() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(close, 2);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new CloseSmaControlAdapter()), List.of(new CountingProviderFactory(true)));

        IndicatorBatchResult<Num> result = evaluator.evaluate(sma, 1, 3, AccelerationConfig.auto());

        assertThat(result.diagnostics().operationId()).isEqualTo(CloseSmaControlAdapter.OPERATION_ID);
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.CPU_FASTER)).isTrue();
        assertThat(result.diagnostics().nativeInitialized()).isFalse();
    }

    @Test
    void requiredDeviceControlGraphFailsInsteadOfForcingKnownSlowerPath() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        AccelerationConfig requiredMetal = new AccelerationConfig(AccelerationMode.METAL, true, 0.10d);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new CloseSmaControlAdapter()), List.of(new CountingProviderFactory(true)));

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> evaluator.evaluate(close, 0, 2, requiredMetal));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.NO_BENEFICIAL_DEVICE_STAGE);
    }

    @Test
    void forecastAdapterCanUseAvailableProvider() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.METAL, false, 0.10d));

        assertThat(providerFactory.probeCount()).isEqualTo(1);
        assertThat(result.diagnostics().backendId()).isEqualTo("fake-metal");
        assertThat(result.diagnostics().effectiveMode()).isEqualTo(AccelerationMode.METAL);
        assertThat(result.values()).hasSize(3);
    }

    @Test
    void autoKeepsProviderBelowMinimumSpeedupOnCpu() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 0, 0.05d, false, false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.AUTO, false, 0.10d));

        assertThat(providerFactory.probeCount()).isEqualTo(1);
        assertThat(providerFactory.evaluationCount()).isZero();
        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.CPU_FASTER)).isTrue();
    }

    @Test
    void autoUsesProviderAtMinimumSpeedup() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 0, 0.10d, false, false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.AUTO, false, 0.10d));

        assertThat(providerFactory.evaluationCount()).isEqualTo(1);
        assertThat(result.diagnostics().backendId()).isEqualTo("fake-metal");
    }

    @Test
    void requiredAutoRejectsProviderBelowMinimumSpeedup() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 0, 0.05d, false, false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> evaluator.evaluate(forecast, 20, 22, new AccelerationConfig(AccelerationMode.AUTO, true, 0.10d)));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.NO_BENEFICIAL_DEVICE_STAGE);
        assertThat(providerFactory.evaluationCount()).isZero();
    }

    @Test
    void autoSkipsUnprofitableProviderAndUsesNextQualifiedProvider() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory slowCuda = new CountingProviderFactory("a-slow-cuda", AccelerationMode.CUDA, true,
                false, 0, 0.05d, false, false);
        CountingProviderFactory fastMetal = new CountingProviderFactory("b-fast-metal", AccelerationMode.METAL, true,
                false, 0, 0.25d, false, false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(fastMetal, slowCuda));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.AUTO, false, 0.10d));

        assertThat(slowCuda.probeCount()).isEqualTo(1);
        assertThat(slowCuda.evaluationCount()).isZero();
        assertThat(fastMetal.probeCount()).isEqualTo(1);
        assertThat(fastMetal.evaluationCount()).isEqualTo(1);
        assertThat(result.diagnostics().backendId()).isEqualTo("b-fast-metal");
    }

    @Test
    void requiredAutoReportsProviderFailureAfterSkippingSlowerCandidate() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory slowCuda = new CountingProviderFactory("a-slow-cuda", AccelerationMode.CUDA, true,
                false, 0, 0.05d, false, false);
        CountingProviderFactory failingMetal = new CountingProviderFactory("b-failing-metal", AccelerationMode.METAL,
                true, false, 0, 0.25d, false, true);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(failingMetal, slowCuda));

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> evaluator.evaluate(forecast, 20, 22, new AccelerationConfig(AccelerationMode.AUTO, true, 0.10d)));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE);
    }

    @Test
    void defaultProviderPredictionIsConservativeAndValidatesInputs() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        IndicatorBatchRequest<Forecast> request = new IndicatorBatchRequest<>(forecast, 20, 22,
                AccelerationConfig.auto());
        AdapterMatch<Forecast> match = new ForecastBatchAdapter().match(forecast);
        IndicatorAccelerationProvider provider = () -> new ProviderCapability("default", AccelerationMode.METAL, true,
                false, "device", List.of(ForecastBatchAdapter.OPERATION_ID), "");

        assertThat(provider.predictedSpeedup(request, match)).isZero();
        assertThrows(NullPointerException.class, () -> provider.predictedSpeedup(null, match));
        assertThrows(NullPointerException.class, () -> provider.predictedSpeedup(request, null));
    }

    @Test
    void invalidProviderResultFallsBackToCpu() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 1);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.METAL, false, 0.10d));

        assertThat(providerFactory.probeCount()).isEqualTo(1);
        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE)).isTrue();
        assertThat(result.values()).hasSize(3);
    }

    @Test
    void hybridFallsBackToCompleteCpuResultWhenNoGpuPartitionIsAvailable() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(new CountingProviderFactory(false)));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.HYBRID, false, 0.10d));

        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.HYBRID_FALLBACK)).isTrue();
        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(result.values()).hasSize(3);
    }

    @Test
    void nativeProbeFallbackPreservesNativeInitializedDiagnostic() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(new CountingProviderFactory(false, true)));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.METAL, false, 0.10d));

        assertThat(result.diagnostics().nativeInitialized()).isTrue();
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.NATIVE_PROVIDER_INITIALIZED)).isTrue();
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE)).isTrue();
    }

    @Test
    void requiredCudaForecastFailsWhileSkeletonIsUnavailable() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(new CountingProviderFactory(false)));

        AccelerationException exception = assertThrows(AccelerationException.class,
                () -> evaluator.evaluate(forecast, 20, 22, new AccelerationConfig(AccelerationMode.CUDA, true, 0.10d)));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE);
    }

    @Test
    void preferredModeFallsBackWhenProviderProbeFails() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(false, false, 0, 0d, true, false);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.METAL, false, 0.10d));

        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE)).isTrue();
        assertThat(result.values()).hasSize(3);
    }

    @Test
    void preferredModeFallsBackWhenProviderExecutionFails() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 0, 0.25d, false, true);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        IndicatorBatchResult<Forecast> result = evaluator.evaluate(forecast, 20, 22,
                new AccelerationConfig(AccelerationMode.METAL, false, 0.10d));

        assertThat(result.diagnostics().backendId()).isEqualTo("cpu");
        assertThat(result.diagnostics().hasCode(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE)).isTrue();
        assertThat(result.values()).hasSize(3);
    }

    @Test
    void requiredModeConvertsProviderExecutionFailureToStableException() {
        MonteCarloPriceForecastIndicator forecast = forecast();
        CountingProviderFactory providerFactory = new CountingProviderFactory(true, false, 0, 0.25d, false, true);
        AcceleratedIndicatorBatchEvaluator evaluator = new AcceleratedIndicatorBatchEvaluator(
                List.of(new ForecastBatchAdapter()), List.of(providerFactory));

        AccelerationException exception = assertThrows(AccelerationException.class, () -> evaluator.evaluate(forecast,
                20, 22, new AccelerationConfig(AccelerationMode.METAL, true, 0.10d)));

        assertThat(exception.code()).isEqualTo(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE);
    }

    private static MonteCarloPriceForecastIndicator forecast() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118,
                        119, 120, 121, 122)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 5, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(2)
                .iterationCount(64)
                .lookbackBarCount(8)
                .seed(7L)
                .build();
    }

    private static final class CountingProviderFactory implements IndicatorAccelerationProviderFactory {

        private final String providerId;
        private final AccelerationMode mode;
        private final boolean available;
        private final boolean nativeInitialized;
        private final int trailingValuesToDrop;
        private final double predictedSpeedup;
        private final boolean failProbe;
        private final boolean failEvaluation;
        private final AtomicInteger probeCount = new AtomicInteger();
        private final AtomicInteger evaluationCount = new AtomicInteger();

        private CountingProviderFactory(boolean available) {
            this(available, false, 0);
        }

        private CountingProviderFactory(boolean available, boolean nativeInitialized) {
            this(available, nativeInitialized, 0);
        }

        private CountingProviderFactory(boolean available, boolean nativeInitialized, int trailingValuesToDrop) {
            this(available, nativeInitialized, trailingValuesToDrop, 0.25d, false, false);
        }

        private CountingProviderFactory(boolean available, boolean nativeInitialized, int trailingValuesToDrop,
                double predictedSpeedup, boolean failProbe, boolean failEvaluation) {
            this("fake-metal", AccelerationMode.METAL, available, nativeInitialized, trailingValuesToDrop,
                    predictedSpeedup, failProbe, failEvaluation);
        }

        private CountingProviderFactory(String providerId, AccelerationMode mode, boolean available,
                boolean nativeInitialized, int trailingValuesToDrop, double predictedSpeedup, boolean failProbe,
                boolean failEvaluation) {
            this.providerId = providerId;
            this.mode = mode;
            this.available = available;
            this.nativeInitialized = nativeInitialized;
            this.trailingValuesToDrop = trailingValuesToDrop;
            this.predictedSpeedup = predictedSpeedup;
            this.failProbe = failProbe;
            this.failEvaluation = failEvaluation;
        }

        private int probeCount() {
            return probeCount.get();
        }

        private int evaluationCount() {
            return evaluationCount.get();
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public AccelerationMode mode() {
            return mode;
        }

        @Override
        public IndicatorAccelerationProvider probe(java.util.Collection<String> operationIds) {
            probeCount.incrementAndGet();
            if (failProbe) {
                throw new UnsatisfiedLinkError("fake probe failure");
            }
            ProviderCapability capability = new ProviderCapability(providerId(), mode(), available, nativeInitialized,
                    available ? "fake device" : "", List.of(ForecastBatchAdapter.OPERATION_ID),
                    available ? "" : "fake unavailable");
            return new IndicatorAccelerationProvider() {
                @Override
                public ProviderCapability capability() {
                    return capability;
                }

                @Override
                public <T> double predictedSpeedup(IndicatorBatchRequest<T> request, AdapterMatch<T> match) {
                    return predictedSpeedup;
                }

                @Override
                public <T> Optional<IndicatorBatchResult<T>> evaluate(IndicatorBatchRequest<T> request,
                        AdapterMatch<T> match) {
                    evaluationCount.incrementAndGet();
                    if (failEvaluation) {
                        throw new IllegalStateException("fake execution failure");
                    }
                    if (!capability.available()) {
                        return Optional.empty();
                    }
                    AccelerationDiagnostics diagnostics = new AccelerationDiagnostics(request.config().mode(),
                            capability.mode(), providerId(), match.operationId(), false,
                            List.of(new AccelerationDiagnostic(AccelerationDiagnosticCode.LAZY_PROVIDER_DISCOVERED,
                                    "fake provider executed", providerId(), match.operationId())));
                    IndicatorBatchResult<T> scalar = IndicatorBatchEvaluator.evaluate(request.indicator(),
                            request.fromInclusive(), request.toInclusive(), AccelerationConfig.cpu());
                    List<IndexedIndicatorValue<T>> values = scalar.values();
                    if (trailingValuesToDrop > 0) {
                        values = values.subList(0, Math.max(0, values.size() - trailingValuesToDrop));
                    }
                    return Optional.of(new IndicatorBatchResult<>(values, diagnostics));
                }
            };
        }
    }
}
