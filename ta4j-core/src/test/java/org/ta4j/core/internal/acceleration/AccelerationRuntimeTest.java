/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.internal.acceleration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

@Execution(ExecutionMode.SAME_THREAD)
class AccelerationRuntimeTest {

    @AfterEach
    void resetRuntime() {
        System.clearProperty(AccelerationRuntime.PROPERTY);
        AccelerationRuntime.resetProvidersForTests();
    }

    @Test
    void omittedAndOffModesDoNotDiscoverOrInvokeProviders() {
        CountingProvider provider = new CountingProvider();
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);

        run(series, indicator);
        System.setProperty(AccelerationRuntime.PROPERTY, "off");
        run(series, new ScopeAwareIndicator(series));

        assertEquals(0, provider.calls.get());
    }

    @Test
    void autoBatchesOnceThroughExistingBarSeriesManagerFlow() {
        CountingProvider provider = new CountingProvider();
        AccelerationRuntime.useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();

        TradingRecord record = run(series, new ScopeAwareIndicator(series));

        assertEquals(1, provider.calls.get());
        assertEquals(1, record.getPositionCount());
        assertEquals(1, record.getPositions().getFirst().getEntry().getIndex());
        assertEquals(2, record.getPositions().getFirst().getExit().getIndex());
    }

    @Test
    void skippedFailedAndMalformedProviderResultsFallBackToScalarValues() {
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        Provider malformed = new Provider() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Result<T> evaluate(Request<T> request) {
                List<Num> values = List.of(series.numFactory().numOf(100));
                return (Result<T>) Result.executed(Backend.METAL, values, true, 1L,
                        diagnostic(DiagnosticCode.ACCELERATED));
            }
        };
        AccelerationRuntime.useProvidersForTests(List.of(malformed));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), indicator.getValue(1));
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
    void scopeCleanupPreventsValuesLeakingIntoLaterRuns() {
        CountingProvider provider = new CountingProvider();
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
        CountingProvider provider = new CountingProvider();
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

    private static TradingRecord run(BarSeries series, ScopeAwareIndicator indicator) {
        Strategy strategy = new BaseStrategy(new IndicatorRule(indicator, 1), new IndexRule(2));
        return new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, TradeType.BUY,
                series.numFactory().one());
    }

    private static BarSeries series() {
        return new MockBarSeriesBuilder().withData(10, 11, 12, 13).build();
    }

    private static Diagnostic diagnostic(DiagnosticCode code) {
        return new Diagnostic(code, "fake-metal", code.name());
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

    private static final class CountingProvider implements Provider {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Result<T> evaluate(Request<T> request) {
            calls.incrementAndGet();
            List<Num> values = new ArrayList<>(request.size());
            for (int i = 0; i < request.size(); i++) {
                values.add(request.series().numFactory().numOf(100 + i));
            }
            return (Result<T>) Result.executed(Backend.METAL, values, true, 1L, diagnostic(DiagnosticCode.ACCELERATED));
        }
    }
}
