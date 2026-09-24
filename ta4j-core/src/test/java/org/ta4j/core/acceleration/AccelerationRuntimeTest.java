/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.ta4j.core.acceleration.AccelerationRuntime.open;
import static org.ta4j.core.acceleration.AccelerationRuntime.useProvidersForTests;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.acceleration.AccelerationRuntime.Scope;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloKernel;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
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
        System.clearProperty("ta4j.forecast.rngVersion");
        AccelerationRuntime.resetProvidersForTests();
    }

    @Test
    void builtBundleExportsThePublicProviderSpi() throws Exception {
        Path classes = Path.of(AccelerationRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (InputStream input = Files.newInputStream(classes.resolve("META-INF/MANIFEST.MF"))) {
            String exports = new Manifest(input).getMainAttributes().getValue("Export-Package");
            assertNotNull(exports, "The built bundle must declare its public packages");
            // Commas inside quoted uses directives do not separate exported packages.
            assertTrue(
                    Arrays.stream(exports.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
                            .map(clause -> clause.split(";", 2)[0].trim())
                            .anyMatch(AccelerationRuntime.class.getPackageName()::equals),
                    "OSGi providers must be able to import the acceleration SPI from the built bundle");
        }
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

    @ParameterizedTest
    @EnumSource(ProviderFault.class)
    void faultyBestRankedProviderQuarantinesAndFallsBackToHealthySibling(ProviderFault fault) {
        BarSeries series = series();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider faulty = new EchoProvider(Backend.METAL, "a-device", 1L, 1_000L) {
            @Override
            public KernelResult execute(KernelRequest request) {
                executions.incrementAndGet();
                return switch (fault) {
                case THROWS -> throw new IllegalStateException("native launch failed");
                case MALFORMED -> new KernelResult(new double[] { 1d }, false, 1L);
                case NULL_RESULT -> null;
                case NON_FINITE -> {
                    double[] markers = request.inputs().getFirst();
                    double[] outputs = new double[request.expectedOutputLength()];
                    for (int row = 0; row < request.size(); row++) {
                        outputs[row] = 100 + markers[row];
                    }
                    outputs[0] = Double.NaN;
                    yield new KernelResult(outputs, false, 0L);
                }
                };
            }
        };
        EchoProvider healthy = new EchoProvider(Backend.CPU, "z-device", 10L, 1_000L);
        useProvidersForTests(List.of(faulty, healthy));
        ScopeAwareIndicator first = new ScopeAwareIndicator(series);
        ScopeAwareIndicator second = new ScopeAwareIndicator(series);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(101), first.getValue(1));
            assertEquals(DiagnosticCode.ACCELERATED, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
            assertEquals(series.numFactory().numOf(102), second.getValue(2));
            assertEquals(DiagnosticCode.ACCELERATED, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        // Quarantine must keep the second evaluation from relaunching the faulty
        // provider, while the healthy sibling executes once per attempt.
        assertEquals(1, faulty.executions.get());
        assertEquals(2, healthy.executions.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void invalidProviderIdentityFallsBackToScalar(boolean throwsOnIdentity) {
        EchoProvider provider = new EchoProvider(Backend.CPU, "cpu", 1L, 1_000L) {
            @Override
            public String providerId() {
                if (throwsOnIdentity) {
                    throw new IllegalStateException("identity unavailable");
                }
                return null;
            }
        };
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(2), indicator.getValue(2));
            assertEquals(DiagnosticCode.PROVIDER_FAILURE, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }
        assertEquals(0, provider.executions.get());
    }

    @Test
    void providerIdentityIsCapturedBeforeExecution() {
        EchoProvider provider = new EchoProvider(Backend.CPU, "cpu", 1L, 1_000L) {
            private boolean identified;

            @Override
            public String providerId() {
                if (identified) {
                    throw new IllegalStateException("identity was already consumed");
                }
                identified = true;
                return "stable-id";
            }
        };
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(102), new ScopeAwareIndicator(series).getValue(2));
            assertEquals("stable-id", AccelerationRuntime.lastDiagnostic().orElseThrow().providerId());
        }
    }

    @Test
    void decimalForecastInsideDoubleScopeRemainsScalar() {
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(100, 102, 101, 104, 103, 105, 106, 104, 108, 109)
                .build();
        BarSeries scopedSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 102, 101, 104, 103, 105, 106, 104, 108, 109)
                .build();
        System.setProperty("ta4j.forecast.rngVersion", "1");
        MonteCarloPriceForecastIndicator reference = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(indicatorSeries),
                        new EwmaReturnForecastStateIndicator(new LogReturnIndicator(indicatorSeries), 3, 0.94d))
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(3)
                .seed(11L)
                .build();
        Forecast scalar = reference.getValue(8);
        assertTrue(scalar.isStable());
        // Fresh instance: a pre-scope read caches the scalar value, so the scoped
        // read must run on its own indicator to consult the runtime at all.
        MonteCarloPriceForecastIndicator scoped = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(indicatorSeries),
                        new EwmaReturnForecastStateIndicator(new LogReturnIndicator(indicatorSeries), 3, 0.94d))
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(3)
                .seed(11L)
                .build();
        EchoProvider provider = new EchoProvider(Backend.CPU, "cpu", 1L, 1_000L);
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        try (Scope ignored = open(scopedSeries, 8, 8)) {
            Forecast actual = scoped.getValue(8);
            assertEquals(scalar.mean(), actual.mean());
            assertEquals(0, provider.executions.get());
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = { Double.NaN, Double.POSITIVE_INFINITY })
    void nonFiniteForecastOutputsFallBackAndQuarantineProvider(double invalidValue) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 102, 101, 104, 103, 105, 106, 104, 108, 109)
                .build();
        System.setProperty("ta4j.forecast.rngVersion", "1");
        MonteCarloPriceForecastIndicator.Builder builder = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series),
                        new EwmaReturnForecastStateIndicator(new LogReturnIndicator(series), 3, 0.94d))
                .horizon(2)
                .iterationCount(4)
                .lookbackBarCount(3)
                .seed(11L);
        MonteCarloPriceForecastIndicator firstReference = builder.build();
        MonteCarloPriceForecastIndicator secondReference = builder.build();
        Forecast expectedFirst = firstReference.getValue(8);
        Forecast expectedSecond = secondReference.getValue(9);
        assertTrue(expectedFirst.isStable());
        assertTrue(expectedSecond.isStable());
        EchoProvider provider = new EchoProvider(Backend.CPU, "cpu", 1L, 1_000L) {
            @Override
            public KernelResult execute(KernelRequest request) {
                double[] outputs = super.execute(request).outputs();
                outputs[0] = invalidValue;
                return new KernelResult(outputs, false, 0L);
            }
        };
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        // Fresh instances: pre-scope reads would be cached and never reach the
        // runtime, so the scoped reads run on their own indicators.
        MonteCarloPriceForecastIndicator first = builder.build();
        MonteCarloPriceForecastIndicator second = builder.build();
        try (Scope ignored = open(series, 8, 9)) {
            Forecast actualFirst = first.getValue(8);
            assertTrue(actualFirst.isStable());
            assertEquals(expectedFirst.mean(), actualFirst.mean());
            assertEquals(DiagnosticCode.INVALID_RESULT, AccelerationRuntime.lastDiagnostic().orElseThrow().code());

            // The quarantined provider is not relaunched for the second indicator;
            // every eligible provider is quarantined in this scope now.
            Forecast actualSecond = second.getValue(9);
            assertTrue(actualSecond.isStable());
            assertEquals(expectedSecond.mean(), actualSecond.mean());
            Diagnostic quarantined = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.PROVIDER_FAILURE, quarantined.code());
            assertEquals("none", quarantined.providerId());
            assertTrue(quarantined.detail().contains("quarantined"));
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

    @ParameterizedTest
    @CsvSource({ "833334, false", "666667, false", "666666, true", "625000, true" })
    void automaticSelectionRequiresAPredictedSpeedupOfOnePointFive(long predictedNanos, boolean admitted) {
        // The planner fixture estimates the scalar baseline at 1,000,000ns: 1.2x and
        // just under 1.5x stay scalar, while 1.5x and 1.6x engage.
        BarSeries series = series();
        ScopeAwareIndicator indicator = new ScopeAwareIndicator(series);
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", predictedNanos, 1_000L);
        AccelerationRuntime.useProvidersForTests(List.of(provider));

        try (AccelerationRuntime.Scope ignored = AccelerationRuntime.open(series, 0, series.getEndIndex())) {
            indicator.getValue(2);
            assertEquals(admitted ? DiagnosticCode.ACCELERATED : DiagnosticCode.CPU_FASTER,
                    AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(admitted ? 1 : 0, provider.executions.get());
    }

    @Test
    void kernelRequestsRejectMismatchedDeterminismAndTolerance() {
        for (double tolerance : new double[] { Double.NaN, Double.POSITIVE_INFINITY, 0d, -0.01d }) {
            assertThrows(IllegalArgumentException.class,
                    () -> request(AccelerationRuntime.Determinism.APPROXIMATE, tolerance));
        }
        assertThrows(IllegalArgumentException.class,
                () -> request(AccelerationRuntime.Determinism.BITWISE_IDENTICAL, 0.01d));
        assertEquals(0.01d, request(AccelerationRuntime.Determinism.APPROXIMATE, 0.01d).tolerance());
        assertTrue(Double.isNaN(request(AccelerationRuntime.Determinism.BITWISE_IDENTICAL, Double.NaN).tolerance()));
    }

    private static KernelRequest request(AccelerationRuntime.Determinism determinism, double tolerance) {
        return new KernelRequest(AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1, 0, 0, 1,
                AccelerationRuntime.NumericEncoding.FLOAT64, determinism, 7L, tolerance, new double[0],
                List.of(new double[1]), 1L, 0L);
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
            Diagnostic diagnostic = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.UNSUPPORTED, diagnostic.code());
            assertEquals("metal-echo", diagnostic.providerId());
            assertTrue(diagnostic.detail().contains("BITWISE_IDENTICAL"));
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
    void seriesChangesDuringPlanningFallsBackWithoutExecutingOrCaching() {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        SeriesValueIndicator indicator = new SeriesValueIndicator(series, () -> replaceLastBarWithClose(series, 42d));

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(42), indicator.getValue(series.getEndIndex()));
            assertEquals(series.numFactory().numOf(42), indicator.getValue(series.getEndIndex()));
            assertEquals(DiagnosticCode.STALE_SERIES, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, provider.assessments.get());
        assertEquals(0, provider.executions.get());
    }

    @Test
    void seriesChangesDuringAssessmentFallsBackWithoutExecutingOrCaching() {
        BarSeries series = series();
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L) {
            @Override
            public Assessment assess(KernelRequest request) {
                Assessment assessment = super.assess(request);
                replaceLastBarWithClose(series, 42d);
                return assessment;
            }
        };
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        SeriesValueIndicator indicator = new SeriesValueIndicator(series, null);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(42), indicator.getValue(series.getEndIndex()));
            assertEquals(series.numFactory().numOf(42), indicator.getValue(series.getEndIndex()));
            assertEquals(DiagnosticCode.STALE_SERIES, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, provider.assessments.get());
        assertEquals(0, provider.executions.get());
    }

    @Test
    void staleProviderResultDoesNotReachFallbackProvider() {
        BarSeries series = series();
        EchoProvider staleProvider = new EchoProvider(Backend.CPU, "a-device", 10L, 1_000L) {
            @Override
            public KernelResult execute(KernelRequest request) {
                KernelResult result = super.execute(request);
                replaceLastBarWithClose(series, 42d);
                return result;
            }
        };
        EchoProvider fallbackProvider = new EchoProvider(Backend.METAL, "z-device", 10L, 1_000L);
        useProvidersForTests(List.of(staleProvider, fallbackProvider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        SeriesValueIndicator indicator = new SeriesValueIndicator(series, null);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(42), indicator.getValue(series.getEndIndex()));
            assertEquals(DiagnosticCode.STALE_SERIES, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, staleProvider.executions.get());
        assertEquals(0, fallbackProvider.executions.get());
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
    @ValueSource(strings = { "cpu", "metal", "cuda", "hybrid", "required", "gpu" })
    void removedAndUnknownModesAreRejectedBeforeExecution(String mode) {
        System.setProperty(AccelerationRuntime.PROPERTY, mode);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AccelerationRuntime.open(series(), 0, 1));

        assertTrue(exception.getMessage().contains("'auto', 'true', 'off' or 'false'"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "auto", "true", "Auto", "TRUE" })
    void booleanAndCaseVariantsEnableAcceleration(String mode) {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, mode);
        BarSeries series = series();

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(100), new ScopeAwareIndicator(series).getValue(0));
        }

        assertEquals(1, provider.executions.get());
    }

    @ParameterizedTest
    @ValueSource(strings = { "off", "false", "OFF", "False" })
    void booleanAndCaseVariantsDisableAcceleration(String mode) {
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, mode);
        BarSeries series = series();

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(2), new ScopeAwareIndicator(series).getValue(2));
        }

        assertEquals(0, provider.executions.get());
    }

    @Test
    void monteCarloShockPathsDecodeThroughOwningFactory() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        monteCarloDecode();
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
        KernelProvider kernel = new KernelProvider();
        useProvidersForTests(List.of(kernel));
        try (Scope scope = open(series, 10, 12)) {
            Forecast value = forecast.getValue(11);
            assertTrue(value.isStable());
            assertEquals(11, value.decisionIndex());
            // A flat-path kernel returns zero log-returns, so every terminal price is
            // the decoded spot price at index 11.
            assertEquals(111d, value.mean().doubleValue(), 0d);
            assertEquals(1, kernel.executions.get());
        }
    }

    private static final class KernelProvider implements Provider {

        final AtomicInteger executions = new AtomicInteger();
        final List<KernelRequest> requests = new ArrayList<>();

        @Override
        public String providerId() {
            return "mc-kernel";
        }

        @Override
        public Assessment assess(KernelRequest request) {
            return Assessment.supported(Backend.CPU, "cpu", 1L, 1_000_000L, true);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            executions.incrementAndGet();
            requests.add(request);
            int rows = request.toInclusive() - request.fromInclusive() + 1;
            double[] outputs = new double[rows * request.outputsPerIndex()];
            for (int row = 0; row < rows; row++) {
                double base = row * request.outputsPerIndex();
                for (int path = 0; path < request.outputsPerIndex(); path++) {
                    outputs[(int) base + path] = 0d;
                }
            }
            return new KernelResult(outputs, false, 1L);
        }
    }

    @ParameterizedTest
    @CsvSource({ "HISTORICAL_BOOTSTRAP, CONSTANT", "HISTORICAL_BOOTSTRAP, EWMA", "STANDARDIZED_EMPIRICAL, CONSTANT",
            "STANDARDIZED_EMPIRICAL, EWMA", "SMOOTHED_EMPIRICAL, CONSTANT", "SMOOTHED_EMPIRICAL, EWMA",
            "NORMAL, CONSTANT", "NORMAL, EWMA" })
    void referenceKernelReproducesTheScalarLaneBitForBit(MonteCarloReturnProjectionIndicator.ShockModel model,
            MonteCarloReturnProjectionIndicator.VolatilityUpdateMode mode) {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        double[] prices = new double[60];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100d + 5d * Math.sin(i * 0.7d) + 0.3d * i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(new LogReturnIndicator(series), 5,
                0.94d);
        MonteCarloPriceForecastIndicator.Builder builder = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(3)
                .iterationCount(16)
                .lookbackBarCount(8)
                .seed(5L)
                .shockModel(model)
                .volatilityUpdateMode(mode)
                .volatilityDecayFactor(0.9d);
        MonteCarloPriceForecastIndicator scalar = builder.build();
        MonteCarloPriceForecastIndicator accelerated = builder.build();
        List<Forecast> expected = new ArrayList<>();
        for (int index = 0; index <= series.getEndIndex(); index++) {
            expected.add(scalar.getValue(index));
        }
        ReferenceShockPathKernel kernel = new ReferenceShockPathKernel();
        useProvidersForTests(List.of(kernel));

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            for (int index = 0; index <= series.getEndIndex(); index++) {
                assertSameForecast(expected.get(index), accelerated.getValue(index));
            }
        }

        assertEquals(1, kernel.executions.get());
        assertTrue(expected.get(series.getEndIndex()).isStable());
    }

    private static void assertSameForecast(Forecast expected, Forecast actual) {
        assertEquals(expected.isStable(), actual.isStable(), "stability at " + expected.decisionIndex());
        if (!expected.isStable()) {
            return;
        }
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertEquals(expected.mean().doubleValue(), actual.mean().doubleValue(), 0d);
        assertEquals(expected.median().doubleValue(), actual.median().doubleValue(), 0d);
        assertEquals(expected.standardDeviation().doubleValue(), actual.standardDeviation().doubleValue(), 0d);
        for (Double probability : expected.quantiles().keySet()) {
            assertEquals(expected.quantile(probability).doubleValue(), actual.quantile(probability).doubleValue(), 0d);
        }
    }

    /**
     * Executable reference of the {@code MONTE_CARLO_SHOCK_PATHS_V1} contract: it
     * computes cumulative log-returns from request primitives only, exactly as a
     * native provider must.
     */
    private static final class ReferenceShockPathKernel implements Provider {

        final AtomicInteger executions = new AtomicInteger();

        @Override
        public String providerId() {
            return "reference-kernel";
        }

        @Override
        public Assessment assess(KernelRequest request) {
            return Assessment.supported(Backend.CPU, "reference", 1L, 1L, true);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            executions.incrementAndGet();
            double[] params = request.params();
            int shockModel = (int) params[MonteCarloKernel.PARAM_SHOCK_MODEL];
            boolean ewma = (int) params[MonteCarloKernel.PARAM_VOLATILITY_MODE] == MonteCarloKernel.VOLATILITY_EWMA;
            int horizon = (int) params[MonteCarloKernel.PARAM_HORIZON];
            int iterations = (int) params[MonteCarloKernel.PARAM_ITERATIONS];
            int lookback = (int) params[MonteCarloKernel.PARAM_LOOKBACK];
            double decay = params[MonteCarloKernel.PARAM_DECAY];
            double oneMinusDecay = 1d - decay;
            List<double[]> inputs = request.inputs();
            double[] means = inputs.get(MonteCarloKernel.INPUT_MEANS);
            double[] drifts = inputs.get(MonteCarloKernel.INPUT_DRIFTS);
            double[] variances = inputs.get(MonteCarloKernel.INPUT_VARIANCES);
            double[] returns = inputs.get(MonteCarloKernel.INPUT_RETURNS);
            boolean bootstrap = shockModel == MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP;
            boolean normal = shockModel == MonteCarloKernel.SHOCK_NORMAL;
            double[] outputs = new double[request.expectedOutputLength()];
            for (int row = 0; row < request.size(); row++) {
                int decisionIndex = request.fromInclusive() + row;
                double startVolatility = variances[row] == 0d ? 0d : Math.sqrt(variances[row]);
                boolean zeroShocks = !bootstrap && !normal && startVolatility == 0d;
                double[] table = new double[lookback];
                for (int k = 0; k < lookback; k++) {
                    double value = returns[row + k];
                    table[k] = bootstrap || zeroShocks ? value : (value - means[row]) / startVolatility;
                }
                double bandwidth = shockModel == MonteCarloKernel.SHOCK_SMOOTHED_EMPIRICAL && !zeroShocks
                        ? bandwidth(table, params[MonteCarloKernel.PARAM_SMOOTHING_FACTOR])
                        : 0d;
                for (int path = 0; path < iterations; path++) {
                    long[] stream = { MonteCarloKernel.initialPathState(request.seed(), decisionIndex, horizon, path) };
                    double cumulative = 0d;
                    double mean = means[row];
                    double variance = variances[row];
                    double volatility = startVolatility;
                    for (int step = 0; step < horizon; step++) {
                        double shock;
                        if (normal) {
                            shock = gaussian(stream);
                        } else if (zeroShocks) {
                            shock = 0d;
                        } else {
                            shock = table[nextInt(stream, lookback)];
                            if (bandwidth != 0d) {
                                shock = shock + gaussian(stream) * bandwidth;
                            }
                        }
                        double stepReturn = bootstrap ? shock : drifts[row] + volatility * shock;
                        cumulative = cumulative + stepReturn;
                        if (ewma) {
                            double deviation = stepReturn - mean;
                            mean = mean * decay + stepReturn * oneMinusDecay;
                            variance = variance * decay + deviation * deviation * oneMinusDecay;
                            volatility = variance == 0d ? 0d : Math.sqrt(variance);
                        }
                    }
                    outputs[row * iterations + path] = cumulative;
                }
            }
            return new KernelResult(outputs, false, 1L);
        }

        private static double bandwidth(double[] shocks, double factor) {
            if (shocks.length < 2) {
                return 0d;
            }
            double sum = 0d;
            for (double shock : shocks) {
                sum = sum + shock;
            }
            double mean = sum / shocks.length;
            double squaredDeviationSum = 0d;
            for (double shock : shocks) {
                double deviation = shock - mean;
                squaredDeviationSum = squaredDeviationSum + deviation * deviation;
            }
            double variance = squaredDeviationSum / (shocks.length - 1L);
            if (!Double.isFinite(variance) || variance <= 0d) {
                return 0d;
            }
            return Math.sqrt(variance) * factor;
        }

        private static long nextLong(long[] stream) {
            stream[0] = MonteCarloKernel.advanceState(stream[0]);
            return MonteCarloKernel.mix64(stream[0]);
        }

        private static int nextInt(long[] stream, int bound) {
            long candidate = nextLong(stream) >>> 1;
            long remainder = candidate % bound;
            while (candidate - remainder + bound - 1 < 0L) {
                candidate = nextLong(stream) >>> 1;
                remainder = candidate % bound;
            }
            return (int) remainder;
        }

        private static double gaussian(long[] stream) {
            double first = MonteCarloKernel.toUnitDouble(nextLong(stream));
            double second = MonteCarloKernel.toUnitDouble(nextLong(stream));
            return MonteCarloKernel.gaussian(first, second);
        }
    }

    @Test
    void accelerationKeepsTheScalarStabilityBoundary() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        BarSeries series = longSeries();
        MonteCarloPriceForecastIndicator scalar = longForecast(series);
        assertEquals(252, scalar.getCountOfUnstableBars());
        assertFalse(scalar.getValue(251).isStable());
        assertTrue(scalar.getValue(252).isStable());

        KernelProvider kernel = new KernelProvider();
        useProvidersForTests(List.of(kernel));
        // Fresh instance: the scalar instance's reads are already cached.
        MonteCarloPriceForecastIndicator accelerated = longForecast(series);
        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertFalse(accelerated.getValue(251).isStable());
            assertEquals(252, kernel.requests.getFirst().fromInclusive());
            assertTrue(accelerated.getValue(252).isStable());
        }
    }

    @Test
    void warmUpReadsStayScalarWithoutDisablingLaterAcceleration() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        BarSeries series = longSeries();
        KernelProvider kernel = new KernelProvider();
        useProvidersForTests(List.of(kernel));
        MonteCarloPriceForecastIndicator forecast = longForecast(series);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertFalse(forecast.getValue(0).isStable());
            assertFalse(forecast.getValue(100).isStable());
            assertFalse(forecast.getValue(251).isStable());
            assertEquals(1, kernel.executions.get());
            assertTrue(forecast.getValue(252).isStable());
            assertTrue(forecast.getValue(299).isStable());
            assertEquals(1, kernel.executions.get());
        }
    }

    @Test
    void defaultStrategyJourneyAcceleratesAfterTheForecastWarmUp() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        BarSeries series = longSeries();
        KernelProvider kernel = new KernelProvider();
        useProvidersForTests(List.of(kernel));
        MonteCarloPriceForecastIndicator forecast = longForecast(series);
        Strategy strategy = new BaseStrategy(new ForecastRule(forecast, 260), new IndexRule(series.getEndIndex()));

        TradingRecord record = new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, TradeType.BUY,
                series.numFactory().one());

        assertEquals(1, record.getPositions().size());
        assertEquals(260, record.getPositions().getFirst().getEntry().getIndex());
        assertEquals(1, kernel.executions.get());
    }

    @Test
    void acceleratedForecastCachesAcrossRunsAndMatchesTheScalarRecord() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        BarSeries series = longSeries();
        KernelProvider kernel = new KernelProvider();
        useProvidersForTests(List.of(kernel));
        MonteCarloPriceForecastIndicator forecast = longForecast(series);
        Strategy strategy = new BaseStrategy(new ForecastRule(forecast, 260), new IndexRule(series.getEndIndex()));
        BarSeriesManager manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());

        TradingRecord firstRun = manager.run(strategy, TradeType.BUY, series.numFactory().one());
        TradingRecord secondRun = manager.run(strategy, TradeType.BUY, series.numFactory().one());

        // Accelerated values populate the indicator cache, so the second run must
        // not contact the provider again.
        assertEquals(1, kernel.executions.get());

        System.setProperty(AccelerationRuntime.PROPERTY, "off");
        MonteCarloPriceForecastIndicator scalarForecast = longForecast(series);
        Strategy scalarStrategy = new BaseStrategy(new ForecastRule(scalarForecast, 260),
                new IndexRule(series.getEndIndex()));
        TradingRecord scalarRun = new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(scalarStrategy,
                TradeType.BUY, series.numFactory().one());

        assertSameTradingRecord(scalarRun, firstRun);
        assertSameTradingRecord(scalarRun, secondRun);
    }

    @Test
    void lastDiagnosticSurvivesBarSeriesManagerAndNamesTheUnsupportedFactory() {
        double[] prices = new double[300];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + i;
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(prices)
                .build();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        System.setProperty("ta4j.forecast.rngVersion", "1");
        MonteCarloPriceForecastIndicator forecast = longForecast(series);
        Strategy strategy = new BaseStrategy(new ForecastRule(forecast, 260), new IndexRule(series.getEndIndex()));

        new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, TradeType.BUY,
                series.numFactory().one());

        assertTrue(AccelerationRuntime.lastDiagnostic().isPresent());
        Diagnostic diagnostic = AccelerationRuntime.lastDiagnostic().orElseThrow();
        assertEquals(DiagnosticCode.UNSUPPORTED, diagnostic.code());
        assertTrue(diagnostic.detail().contains("DoubleNum"), diagnostic.detail());
    }

    private static void assertSameTradingRecord(TradingRecord expected, TradingRecord actual) {
        assertEquals(expected.getPositionCount(), actual.getPositionCount());
        for (int position = 0; position < expected.getPositionCount(); position++) {
            assertEquals(expected.getPositions().get(position).getEntry().getIndex(),
                    actual.getPositions().get(position).getEntry().getIndex(), "entry of position " + position);
            assertEquals(expected.getPositions().get(position).getExit().getIndex(),
                    actual.getPositions().get(position).getExit().getIndex(), "exit of position " + position);
        }
    }

    @Test
    void mutationDuringDecodingIsNotPublished() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19).build();
        EchoProvider provider = new EchoProvider(Backend.METAL, "gpu-0", 10L, 1_000L);
        useProvidersForTests(List.of(provider));
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        AtomicInteger decodes = new AtomicInteger();
        SeriesValueIndicator indicator = new SeriesValueIndicator(series, null, () -> {
            if (decodes.incrementAndGet() == 1) {
                replaceLastBarWithClose(series, 42d);
            }
        });

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(15), indicator.getValue(5));
            assertEquals(DiagnosticCode.STALE_SERIES, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
            assertEquals(1, provider.executions.get());
            assertEquals(series.numFactory().numOf(117), indicator.getValue(7));
            assertEquals(2, provider.executions.get());
        }
    }

    @Test
    void recognizedPlannerDeclineReachesLastDiagnosticUnchanged() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), new DecliningIndicator(series).getValue(1));
            Diagnostic recognized = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.UNSUPPORTED, recognized.code());
            assertEquals("none", recognized.providerId());
            assertEquals("test planner cannot lower the declining indicator", recognized.detail());

            assertEquals(series.numFactory().numOf(2), new UnclaimedIndicator(series).getValue(2));
            Diagnostic unclaimed = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.UNSUPPORTED, unclaimed.code());
            assertEquals("no operation planner claims UnclaimedIndicator", unclaimed.detail());
        }
    }

    @Test
    void chunkedBatchesContinueAcrossTheBatchEnd() {
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        BarSeries series = series();
        RecordingEchoProvider provider = new RecordingEchoProvider();
        useProvidersForTests(List.of(provider));
        ChunkedIndicator indicator = new ChunkedIndicator(series, 2);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            for (int index = 0; index <= series.getEndIndex(); index++) {
                assertEquals(series.numFactory().numOf(100 + index), indicator.getValue(index), "index " + index);
            }
            // A read before the cached batch start stays scalar without re-planning.
            assertEquals(series.numFactory().numOf(0), indicator.getValue(0));
        }

        assertEquals(2, provider.executions.get());
        assertEquals(0, provider.requests.get(0).fromInclusive());
        assertEquals(1, provider.requests.get(0).toInclusive());
        assertEquals(2, provider.requests.get(1).fromInclusive());
        assertEquals(3, provider.requests.get(1).toInclusive());
    }

    @Test
    void allQuarantinedCandidatesReportProviderFailure() {
        BarSeries series = series();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider failing = new EchoProvider(Backend.METAL, "a-device", 1L, 1_000L) {
            @Override
            public KernelResult execute(KernelRequest request) {
                executions.incrementAndGet();
                throw new IllegalStateException("native launch failed");
            }
        };
        EchoProvider sibling = new EchoProvider(Backend.CPU, "z-device", 10L, 1_000L) {
            private boolean declinedOnce;

            @Override
            public Assessment assess(KernelRequest request) {
                if (declinedOnce) {
                    return Assessment.unsupported(Backend.CPU, "z-device", DiagnosticCode.UNSUPPORTED, "z-device",
                            "retired after the first batch");
                }
                declinedOnce = true;
                return super.assess(request);
            }
        };
        useProvidersForTests(List.of(failing, sibling));
        ScopeAwareIndicator first = new ScopeAwareIndicator(series);
        ScopeAwareIndicator second = new ScopeAwareIndicator(series);

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(100), first.getValue(0));
            assertEquals(DiagnosticCode.ACCELERATED, AccelerationRuntime.lastDiagnostic().orElseThrow().code());

            assertEquals(series.numFactory().numOf(1), second.getValue(1));
            Diagnostic diagnostic = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.PROVIDER_FAILURE, diagnostic.code());
            assertEquals("none", diagnostic.providerId());
            assertTrue(diagnostic.detail().contains("quarantined"));
        }

        assertEquals(1, failing.executions.get());
    }

    @Test
    void discoverySkipsBrokenEntriesAndAbortsRepeatedFailures() {
        Provider valid = new EchoProvider(Backend.CPU, "cpu", 1L, 1_000L);
        Iterator<Provider> brokenThenValid = new Iterator<>() {

            private int stage;

            @Override
            public boolean hasNext() {
                if (stage == 0) {
                    stage = 1;
                    throw new ServiceConfigurationError("provider class not found: broken.Provider");
                }
                return stage == 1;
            }

            @Override
            public Provider next() {
                if (stage != 1) {
                    throw new NoSuchElementException("no further provider");
                }
                stage = 2;
                return valid;
            }
        };

        assertEquals(List.of(valid), AccelerationRuntime.loadProviders(brokenThenValid));

        // An iterator that keeps failing must terminate with an empty list.
        assertEquals(List.of(), AccelerationRuntime.loadProviders(new Iterator<>() {

            @Override
            public boolean hasNext() {
                throw new ServiceConfigurationError("provider class not found: broken.Provider");
            }

            @Override
            public Provider next() {
                throw new ServiceConfigurationError("provider class not found: broken.Provider");
            }
        }));
    }

    @Test
    void discoveryResultIsCachedAcrossEvaluations(@TempDir Path tempDir) throws Exception {
        Path firstServices = tempDir.resolve("first/META-INF/services");
        Path secondServices = tempDir.resolve("second/META-INF/services");
        Files.createDirectories(firstServices);
        Files.createDirectories(secondServices);
        String serviceName = Provider.class.getName();
        Files.writeString(firstServices.resolve(serviceName),
                "does.not.exist.Provider\n" + FirstDiscoveryProvider.class.getName() + "\n");
        Files.writeString(secondServices.resolve(serviceName), SecondDiscoveryProvider.class.getName() + "\n");
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        URLClassLoader firstLoader = new URLClassLoader(new URL[] { tempDir.resolve("first").toUri().toURL() },
                AccelerationRuntimeTest.class.getClassLoader());
        URLClassLoader secondLoader = new URLClassLoader(new URL[] { tempDir.resolve("second").toUri().toURL() },
                AccelerationRuntimeTest.class.getClassLoader());
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        AccelerationRuntime.resetProvidersForTests();
        FirstDiscoveryProvider.EXECUTIONS.set(0);
        SecondDiscoveryProvider.EXECUTIONS.set(0);
        BarSeries series = series();
        try {
            Thread.currentThread().setContextClassLoader(firstLoader);
            try (Scope ignored = open(series, 0, series.getEndIndex())) {
                assertEquals(series.numFactory().numOf(100), new ScopeAwareIndicator(series).getValue(0));
            }
            assertEquals(1, FirstDiscoveryProvider.EXECUTIONS.get());
            assertEquals(0, SecondDiscoveryProvider.EXECUTIONS.get());

            // A second evaluation must reuse the cached discovery result instead of
            // rescanning under the new discovery configuration.
            Thread.currentThread().setContextClassLoader(secondLoader);
            try (Scope ignored = open(series, 0, series.getEndIndex())) {
                assertEquals(series.numFactory().numOf(100), new ScopeAwareIndicator(series).getValue(0));
            }
            assertEquals(2, FirstDiscoveryProvider.EXECUTIONS.get());
            assertEquals(0, SecondDiscoveryProvider.EXECUTIONS.get());
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
            firstLoader.close();
            secondLoader.close();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PROVIDER_UNAVAILABLE, metal-kernel, set -Dta4j.acceleration.approximateTolerance=1e-3 to allow approximate execution",
            "PROVIDER_UNAVAILABLE, metal-kernel, the native classifier library is missing",
            "UNSUPPORTED, metal-kernel, the request needs more memory than the device budget allows" })
    void providerDeclineKeepsItsActionableDiagnostic(DiagnosticCode code, String providerId, String detail) {
        BarSeries series = series();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        Provider declining = new Provider() {
            @Override
            public String providerId() {
                return "declining";
            }

            @Override
            public Assessment assess(KernelRequest request) {
                return Assessment.unsupported(Backend.METAL, "gpu-0", code, providerId, detail);
            }

            @Override
            public KernelResult execute(KernelRequest request) {
                throw new AssertionError("declining provider must not execute");
            }
        };
        useProvidersForTests(List.of(declining));

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), new ScopeAwareIndicator(series).getValue(1));
            Diagnostic diagnostic = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(code, diagnostic.code());
            assertEquals(providerId, diagnostic.providerId());
            assertEquals(detail, diagnostic.detail());
        }
    }

    @Test
    void zeroProvidersReportNoProviderDiscovered() {
        BarSeries series = series();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        useProvidersForTests(List.of());

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(1), new ScopeAwareIndicator(series).getValue(1));
            Diagnostic diagnostic = AccelerationRuntime.lastDiagnostic().orElseThrow();
            assertEquals(DiagnosticCode.NO_PROVIDER, diagnostic.code());
            assertEquals("none", diagnostic.providerId());
        }
    }

    @Test
    void failingProviderBeforeSuccessfulSiblingStillAccelerates() {
        BarSeries series = series();
        System.setProperty(AccelerationRuntime.PROPERTY, "auto");
        EchoProvider failing = new EchoProvider(Backend.METAL, "a-device", 10L, 1_000L) {
            @Override
            public Assessment assess(KernelRequest request) {
                super.assessments.incrementAndGet();
                throw new IllegalStateException("native probe failed");
            }
        };
        EchoProvider healthy = new EchoProvider(Backend.CPU, "z-device", 10L, 1_000L);
        useProvidersForTests(List.of(failing, healthy));

        try (Scope ignored = open(series, 0, series.getEndIndex())) {
            assertEquals(series.numFactory().numOf(101), new ScopeAwareIndicator(series).getValue(1));
            assertEquals(DiagnosticCode.ACCELERATED, AccelerationRuntime.lastDiagnostic().orElseThrow().code());
        }

        assertEquals(1, healthy.executions.get());
    }

    private static TradingRecord run(BarSeries series, ScopeAwareIndicator indicator) {
        Strategy strategy = new BaseStrategy(new IndicatorRule(indicator, 1), new IndexRule(2));
        return new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy, TradeType.BUY,
                series.numFactory().one());
    }

    private static BarSeries series() {
        return new MockBarSeriesBuilder().withData(10, 11, 12, 13).build();
    }

    private static void replaceLastBarWithClose(BarSeries series, double closePrice) {
        Bar last = series.getLastBar();
        Bar replacement = series.barBuilder()
                .timePeriod(last.getTimePeriod())
                .endTime(last.getEndTime())
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(last.getVolume())
                .build();
        series.addBar(replacement, true);
    }

    private static BarSeries revisionFreeSeries() {
        BarSeries built = series();
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i <= built.getEndIndex(); i++) {
            bars.add(built.getBar(i));
        }
        return new RevisionFreeSeries("revision-free", bars);
    }

    private static BarSeries longSeries() {
        double[] prices = new double[300];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + i;
        }
        return new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance()).withData(prices).build();
    }

    private static MonteCarloPriceForecastIndicator longForecast(BarSeries series) {
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 5, 0.94d);
        return MonteCarloPriceForecastIndicator.builder(new ClosePriceIndicator(series), state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(252)
                .seed(11L)
                .build();
    }

    private static final class TestPlanner implements OperationPlanner {

        @Override
        public PlanAttempt plan(Indicator<?> indicator, int fromInclusive, int toInclusive, NumFactory factory,
                long memoryLimitBytes) {
            if (indicator instanceof DecliningIndicator) {
                return PlanAttempt
                        .declined(PlanDecline.unsupported("test planner cannot lower the declining indicator"));
            }
            if (indicator instanceof UnclaimedIndicator) {
                return PlanAttempt.declined(PlanDecline.unclaimed());
            }
            SeriesValueIndicator seriesValue = indicator instanceof SeriesValueIndicator value ? value : null;
            ChunkedIndicator chunked = indicator instanceof ChunkedIndicator value ? value : null;
            if (!(indicator instanceof ScopeAwareIndicator) && seriesValue == null && chunked == null) {
                return PlanAttempt.declined(PlanDecline.unclaimed());
            }
            int end = chunked != null ? Math.min(toInclusive, fromInclusive + chunked.chunkSize() - 1) : toInclusive;
            int size = end - fromInclusive + 1;
            double[] markers = new double[size];
            for (int row = 0; row < size; row++) {
                int index = fromInclusive + row;
                markers[row] = seriesValue == null ? index : seriesValue.markerAt(index);
            }
            KernelRequest request = new KernelRequest(AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1,
                    fromInclusive, end, 1, AccelerationRuntime.NumericEncoding.FLOAT64,
                    AccelerationRuntime.Determinism.BITWISE_IDENTICAL, 7L, Double.NaN,
                    new double[] { 1d, 0d, 1d, 8d, 4d, 0.94d }, List.of(markers), 1_000_000L, 1_000_000L);
            if (seriesValue != null) {
                seriesValue.runAfterPlanning();
            }
            return PlanAttempt.planned(new PlannedOperation(request, (slice, index, decodingFactory) -> {
                Num decoded = decodingFactory.numOf(slice[0]);
                if (seriesValue != null) {
                    seriesValue.runAfterDecoding();
                }
                return decoded;
            }));
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

    private static final class ChunkedIndicator extends CachedIndicator<Num> {

        private final int chunkSize;

        private ChunkedIndicator(BarSeries series, int chunkSize) {
            super(series);
            this.chunkSize = chunkSize;
        }

        private int chunkSize() {
            return chunkSize;
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

    private static final class DecliningIndicator extends CachedIndicator<Num> {

        private DecliningIndicator(BarSeries series) {
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

    private static final class UnclaimedIndicator extends CachedIndicator<Num> {

        private UnclaimedIndicator(BarSeries series) {
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

    private static final class SeriesValueIndicator extends CachedIndicator<Num> {

        private final Runnable afterPlanning;
        private final Runnable afterDecoding;

        private SeriesValueIndicator(BarSeries series, Runnable afterPlanning) {
            this(series, afterPlanning, null);
        }

        private SeriesValueIndicator(BarSeries series, Runnable afterPlanning, Runnable afterDecoding) {
            super(series);
            this.afterPlanning = afterPlanning;
            this.afterDecoding = afterDecoding;
        }

        @Override
        public Num getValue(int index) {
            return AccelerationRuntime.value(this, index).orElseGet(() -> super.getValue(index));
        }

        @Override
        protected Num calculate(int index) {
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private double markerAt(int index) {
            return getBarSeries().getBar(index).getClosePrice().doubleValue();
        }

        private void runAfterPlanning() {
            if (afterPlanning != null) {
                afterPlanning.run();
            }
        }

        private void runAfterDecoding() {
            if (afterDecoding != null) {
                afterDecoding.run();
            }
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

    private static final class ForecastRule extends AbstractRule {

        private final MonteCarloPriceForecastIndicator forecast;
        private final int indexToEnter;

        private ForecastRule(MonteCarloPriceForecastIndicator forecast, int indexToEnter) {
            this.forecast = forecast;
            this.indexToEnter = indexToEnter;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            Forecast value = forecast.getValue(index);
            return index == indexToEnter && value.isStable()
                    && value.mean().isGreaterThan(value.mean().getNumFactory().zero());
        }
    }

    /**
     * Faults injected into the best-ranked provider of the execute-failure matrix.
     */
    private enum ProviderFault {
        THROWS, MALFORMED, NULL_RESULT, NON_FINITE
    }

    private static final class RecordingEchoProvider extends EchoProvider {

        final List<KernelRequest> requests = new ArrayList<>();

        private RecordingEchoProvider() {
            super(Backend.CPU, "cpu", 1L, 1_000L);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            requests.add(request);
            return super.execute(request);
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

    /**
     * ServiceLoader-instantiated test provider; must be public with a public no-arg
     * constructor for discovery.
     */
    public static final class FirstDiscoveryProvider implements Provider {

        static final AtomicInteger EXECUTIONS = new AtomicInteger();

        @Override
        public String providerId() {
            return "discovery-first";
        }

        @Override
        public Assessment assess(KernelRequest request) {
            return Assessment.supported(Backend.CPU, "cpu", 1L, 1_000L, true);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            EXECUTIONS.incrementAndGet();
            double[] outputs = new double[request.expectedOutputLength()];
            for (int row = 0; row < request.size(); row++) {
                outputs[row] = 100 + request.fromInclusive() + row;
            }
            return new KernelResult(outputs, false, 1L);
        }
    }

    /**
     * ServiceLoader-instantiated test provider; must be public with a public no-arg
     * constructor for discovery.
     */
    public static final class SecondDiscoveryProvider implements Provider {

        static final AtomicInteger EXECUTIONS = new AtomicInteger();

        @Override
        public String providerId() {
            return "discovery-second";
        }

        @Override
        public Assessment assess(KernelRequest request) {
            return Assessment.supported(Backend.CPU, "cpu", 1L, 1_000L, true);
        }

        @Override
        public KernelResult execute(KernelRequest request) {
            EXECUTIONS.incrementAndGet();
            double[] outputs = new double[request.expectedOutputLength()];
            for (int row = 0; row < request.size(); row++) {
                outputs[row] = 100 + request.fromInclusive() + row;
            }
            return new KernelResult(outputs, false, 1L);
        }
    }

    private static final class RevisionFreeSeries extends BaseBarSeries {

        private RevisionFreeSeries(String name, List<Bar> bars) {
            super(name, bars);
        }

        @Override
        public synchronized long getBarHistoryRevision() {
            return -1L;
        }
    }
}
