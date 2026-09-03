/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.ta4j.core.acceleration.AccelerationRuntime.open;
import static org.ta4j.core.acceleration.AccelerationRuntime.useProvidersForTests;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.acceleration.AccelerationRuntime.Scope;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.AbstractRule;

@Execution(ExecutionMode.SAME_THREAD)
class AccelerationRuntimeTest {

    static {
        AccelerationRuntime.registerPlanner(new TestPlanner());
    }

    @AfterEach
    void resetRuntime() {
        System.clearProperty(AccelerationRuntime.PROPERTY);
        System.clearProperty(AccelerationRuntime.MAX_DEVICE_BYTES_PROPERTY);
        AccelerationRuntime.resetProvidersForTests();
    }

    @Test
    void omittedAndOffModesDoNotContactProviders() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);

        run(series, indicator);
        System.setProperty(AccelerationRuntime.PROPERTY, "off");
        run(series, new ScopeAwareIndicator(series));

        assertEquals(0, provider.assessments.get());
        assertEquals(0, provider.executions.get());
    }

    @Test
    void autoAcceleratesOnceThroughExistingBarSeriesManagerFlow() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();

        TradingRecord record = run(series, new ScopeAwareIndicator(series));

        assertEquals(1, provider.executions.get());
        assertEquals(1, record.getPositionCount());
        assertEquals(1, record.getPositions().getFirst().getEntry().getIndex());
        assertEquals(2, record.getPositions().getFirst().getExit().getIndex());
    }

    @Test
    void malformedOutputQuarantinesAndFallsBackToScalar() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        MalformedProvider provider = new MalformedProvider();
        AccelerationRuntime.useProvidersForTests(List.of(provider));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), indicator.getValue(1));
            assertEquals(series.numFactory().numOf(2), indicator.getValue(2));
            assertEquals(DiagnosticCode.INVALID_RESULT, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, provider.executions.get());
    }

    @Test
    void throwingProviderQuarantinesAndFallsBackToScalar() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        ThrowingProvider provider = new ThrowingProvider();
        AccelerationRuntime.useProvidersForTests(List.of(provider));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(0), indicator.getValue(0));
            assertEquals(series.numFactory().numOf(3), indicator.getValue(3));
            assertEquals(DiagnosticCode.PROVIDER_FAILURE, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, provider.executions.get());
    }

    @Test
    void cpuFasterPredictionKeepsScalarWithoutExecuting() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", Long.MAX_VALUE / 2, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(2), indicator.getValue(2));
            assertEquals(DiagnosticCode.CPU_FASTER, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(0, provider.executions.get());
    }

    @Test
    void peakOverBudgetRejectsWithoutExecuting() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty(AccelerationRuntime.MAX_DEVICE_BYTES_PROPERTY, "8");
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), indicator.getValue(1));
        }

        assertEquals(0, provider.executions.get());
    }

    @Test
    void nonDeterministicAndUnsupportedAssessmentsFallBackToScalar() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider lax = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L) {
            @Override
            public Assessment assess(KernelRequest request) {
                return Assessment.supported(backend, deviceId, 10L, 1_000L, false);
            }
        };
        Provider refusing = new Provider() {
            @Override
            public Assessment assess(KernelRequest request) {
                return Assessment.unsupported(Backend.CPU, "cpu-0", DiagnosticCode.UNSUPPORTED, "refusing", "declined");
            }

            @Override
            public KernelResult execute(KernelRequest request) {
                throw new AssertionError("refusing provider must not execute");
            }
        };
        AccelerationRuntime.useProvidersForTests(List.of(lax, refusing));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), indicator.getValue(1));
            assertEquals(DiagnosticCode.NO_PROVIDER, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(0, lax.executions.get());
    }

    @Test
    void stableTieBreakPrefersCpuBackendThenIds() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider metal = new EchoProvider(Backend.METAL, "a-device", 10L, 1_000L);
        EchoProvider cpu = new EchoProvider(Backend.CPU, "z-device", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(metal, cpu));
        BarSeries series = series();

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            new ScopeAwareIndicator(series).getValue(0);
            assertEquals(cpu.providerId(), AccelerationRuntime.lastDiagnostic().orElseThrow().providerId());
        }

        assertEquals(0, metal.executions.get());
        assertEquals(1, cpu.executions.get());
    }

    @Test
    void replacingTheCurrentBarInvalidatesTheCachedBatch() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(100), indicator.getValue(0));
            assertEquals(1, provider.executions.get());
            series.addBar(series.getBar(series.getEndIndex()), true);
            assertEquals(series.numFactory().numOf(100), indicator.getValue(0));
            assertEquals(2, provider.executions.get());
        }
    }

    @Test
    void removingRetainedBarsInvalidatesTheCachedBatch() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 2, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(102), indicator.getValue(2));
            assertEquals(1, provider.executions.get());
            series.setMaximumBarCount(2);
            assertEquals(series.numFactory().numOf(102), indicator.getValue(2));
            assertEquals(2, provider.executions.get());
        }
    }

    @Test
    void seriesWithoutRevisionTrackingFallBackToScalarValues() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = revisionFreeSeries();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(0), indicator.getValue(0));
        }
        assertEquals(0, provider.executions.get());
    }

    @Test
    void scopeCleanupPreventsValuesLeakingIntoLaterRuns() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        ScopeAwareIndicator first = new ScopeAwareIndicator(series);
        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(100), AccelerationRuntime.value(first, 0).orElseThrow());
            assertEquals(series.numFactory().numOf(100), first.getValue(0));
        }
        System.setProperty(AccelerationRuntime.PROPERTY, "off");

        assertEquals(series.numFactory().zero(), first.getValue(0));
        assertFalse(AccelerationRuntime.value(first, 0).isPresent());
    }

    @Test
    void nestedOffScopeTemporarilySuspendsAnOuterAutomaticScope() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);

        try (AccelerationRuntime.Scope outer = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            System.setProperty(AccelerationRuntime.PROPERTY, "off");
            try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
                assertFalse(AccelerationRuntime.value(indicator, 0).isPresent());
            }
            System.setProperty(AccelerationRuntime.PROPERTY, "auto");
            assertEquals(series.numFactory().numOf(100), AccelerationRuntime.value(indicator, 0).orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "cpu", "metal", "cuda", "hybrid", "required" })
    void removedAndUnknownModesAreRejectedBeforeExecution(String mode) {
        System.setProperty(AccelerationRuntime.PROPERTY, mode);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AccelerationRuntime.open(series(), 0, 1));

        assertTrue(exception.getMessage().contains("'off' or 'auto'"));
    }

    @Test
    void monteCarloShockPathsDecodeThroughOwningFactory() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        try {
            monteCarloDecode();
        } finally {
            System.clearProperty("ta4j.forecast.rngVersion");
        }
    }

    private static void monteCarloDecode() {
        double[] prices = new double[30];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 5, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(5)
                .seed(11L)
                .build();
        AtomicInteger executions = new AtomicInteger();
        Provider kernel = new Provider() {
            @Override
            public String providerId() {
                return "mc-kernel";
            }

            @Override
            public Assessment assess(KernelRequest probe) {
                return Assessment.supported(Backend.CPU, "cpu", 1L, 1_000_000L, true);
            }

            @Override
            public KernelResult execute(KernelRequest request) {
                executions.incrementAndGet();
                int rows = request.toInclusive() - request.fromInclusive() + 1;
                double[] outputs = new double[rows * request.outputsPerIndex()];
                for (int row = 0; row < rows; row++) {
                    double base = row * request.outputsPerIndex();
                    for (int path = 0; path < request.outputsPerIndex(); path++) {
                        outputs[(int) base + path] = path % 2 == 0 ? 90d : 110d;
                    }
                }
                return new KernelResult(outputs, false, 1L);
            }
        };
        useProvidersForTests(List.of(kernel));
        try (Scope scope = open(series, 10, 12)) {
            Forecast value = forecast.getValue(11);
            assertTrue(value.isStable());
            assertEquals(11, value.decisionIndex());
            assertEquals(100d, value.mean().doubleValue(), 1e-9);
            assertEquals(1, executions.get());
        }
    }

    private static TradingRecord run(BarSeries series, ScopeAwareIndicator indicator) {
        Strategy strategy = new BaseStrategy(new IndicatorRule(indicator, 1), new IndexRule(2));
        return new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, TradeType.BUY,
                series.numFactory().one());
    }

    private static BarSeries series() {
        return new MockBarSeriesBuilder().withData(10, 11, 12, 13).build();
    }

    private static BarSeries revisionFreeSeries() {
        BarSeries built = series();
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i <= built.getEndIndex(); i++) {
            bars.add(built.getBar(i));
        }
        return new RevisionFreeSeries("revision-free", bars);
    }

    private static final class TestPlanner implements OperationPlanner {

        @Override
        public PlannedOperation plan(Indicator<?> indicator, int fromInclusive, int toInclusive, NumFactory factory) {
            if (!(indicator instanceof ScopeAwareIndicator)) {
                return null;
            }
            int size = toInclusive - fromInclusive + 1;
            double[] markers = new double[size];
            for (int row = 0; row < size; row++) {
                markers[row] = fromInclusive + row;
            }
            KernelRequest request = new KernelRequest(AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1,
                    fromInclusive, toInclusive, 1, AccelerationRuntime.NumericEncoding.FLOAT64,
                    AccelerationRuntime.Determinism.BITWISE_IDENTICAL, 7L, Double.NaN,
                    new double[] { 1d, 0d, 1d, 8d, 4d, 0.94d }, List.of(markers), 1_000_000L, 1_000_000L);
            return new PlannedOperation(request, (slice, index, decodingFactory) -> decodingFactory.numOf(slice[0]));
        }
    }

    private static final class ScopeAwareIndicator extends CachedIndicator<Num> {

        private ScopeAwareIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public Num getValue(int index) {
            return AccelerationRuntime.value(this, index).orElseGet(() -> super.getValue(index));
        }

        @Override
        protected Num calculate(int index) {
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class IndicatorRule extends AbstractRule {

        private final Indicator<Num> indicator;
        private final int indexToEnter;

        private IndicatorRule(Indicator<Num> indicator, int indexToEnter) {
            this.indicator = indicator;
            this.indexToEnter = indexToEnter;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return index == indexToEnter
                    && indicator.getValue(index).isGreaterThan(indicator.getBarSeries().numFactory().numOf(50));
        }
    }

    private static final class IndexRule extends AbstractRule {

        private final int satisfiedIndex;

        private IndexRule(int satisfiedIndex) {
            this.satisfiedIndex = satisfiedIndex;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return index == satisfiedIndex;
        }
    }

    private static class EchoProvider implements Provider {

        final Backend backend;
        final String deviceId;
        private final long predictedNanos;
        private final long peakBytes;
        final AtomicInteger assessments = new AtomicInteger();
        final AtomicInteger executions = new AtomicInteger();

        private EchoProvider(Backend backend, String deviceId, long predictedNanos, long peakBytes) {
            this.backend = backend;
            this.deviceId = deviceId;
            this.predictedNanos = predictedNanos;
            this.peakBytes = peakBytes;
        }

        @Override
        public String providerId() {
            return backend.name().toLowerCase(Locale.ROOT) + "-echo";
        }

        @Override
        public Assessment assess(KernelRequest request) {
            assessments.incrementAndGet();
            return Assessment.supported(backend, deviceId, predictedNanos, peakBytes, true);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            executions.incrementAndGet();
            double[] markers = request.inputs().getFirst();
            double[] outputs = new double[request.expectedOutputLength()];
            for (int row = 0; row < request.size(); row++) {
                outputs[row] = 100 + markers[row];
            }
            return new KernelResult(outputs, false, 1L);
        }
    }

    private static final class MalformedProvider extends EchoProvider {

        private MalformedProvider() {
            super(Backend.METAL, "gpu-0", 10L, 1_000L);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            super.executions.incrementAndGet();
            return new KernelResult(new double[] { 1d }, false, 1L);
        }
    }

    private static final class ThrowingProvider extends EchoProvider {

        private ThrowingProvider() {
            super(Backend.METAL, "gpu-0", 10L, 1_000L);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            super.executions.incrementAndGet();
            throw new IllegalStateException("native launch failed");
        }
    }

    private static final class RevisionFreeSeries extends BaseBarSeries {

        private RevisionFreeSeries(String name, List<Bar> bars) {
            super(name, bars);
        }

        @Override
        public long getBarHistoryRevision() {
            return -1L;
        }
    }
}
