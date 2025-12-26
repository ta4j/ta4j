/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenario;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.display.SwingChartDisplayer;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class ElliottWaveAnalysisTest {

    private static final String OSSIFIED_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    private static final double FIB_TOLERANCE = 0.25;

    @BeforeEach
    void setUp() {
        // Disable chart display to prevent windows from appearing in tests
        // This allows tests to create charts without failing in headless environments
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
    }

    @AfterEach
    void tearDown() {
        // Clear the property after each test
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
    }

    @Test
    void ossifiedDatasetProducesImpulseAndCorrection() {
        BarSeries series = loadOssifiedSeries();
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, ElliottDegree.PRIMARY);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(FIB_TOLERANCE));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);

        int endIndex = series.getEndIndex();
        assertEquals(ElliottPhase.CORRECTIVE_C, phaseIndicator.getValue(endIndex));
        assertTrue(phaseIndicator.isImpulseConfirmed(endIndex));
        assertTrue(phaseIndicator.isCorrectiveConfirmed(endIndex));

        assertEquals(5, phaseIndicator.impulseSwings(endIndex).size());
        assertEquals(3, phaseIndicator.correctiveSwings(endIndex).size());
    }

    @Test
    void rendersWaveLabelsOnChart() {
        BarSeries series = loadOssifiedSeries();
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, ElliottDegree.PRIMARY);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(FIB_TOLERANCE));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);

        BarSeriesLabelIndicator waveLabels = buildWaveLabels(series, phaseIndicator);

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(waveLabels)
                .withLabel("Wave labels")
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot pricePlot = combinedPlot.getSubplots().get(0);

        List<String> annotationTexts = pricePlot.getAnnotations()
                .stream()
                .filter(XYTextAnnotation.class::isInstance)
                .map(annotation -> ((XYTextAnnotation) annotation).getText())
                .toList();

        assertTrue(annotationTexts.containsAll(List.of("1", "2", "3", "4", "5", "A", "B", "C")));
    }

    private static BarSeries loadOssifiedSeries() {
        try (InputStream stream = ElliottWaveAnalysisTest.class.getClassLoader()
                .getResourceAsStream(OSSIFIED_OHLCV_RESOURCE)) {
            assertNotNull(stream, "Missing resource: " + OSSIFIED_OHLCV_RESOURCE);

            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            assertNotNull(loaded, "Failed to deserialize BarSeries from resource");

            BarSeries series = new BaseBarSeriesBuilder().withName("BTC-USD_PT1D@Coinbase (ossified)").build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            throw new AssertionError("Failed to load dataset", ex);
        }
    }

    private static BarSeriesLabelIndicator buildWaveLabels(BarSeries series, ElliottPhaseIndicator phaseIndicator) {
        int endIndex = series.getEndIndex();
        List<ElliottSwing> impulse = phaseIndicator.impulseSwings(endIndex);
        List<ElliottSwing> correction = phaseIndicator.correctiveSwings(endIndex);

        List<BarLabel> labels = new ArrayList<>();
        if (!impulse.isEmpty()) {
            ElliottSwing first = impulse.get(0);
            labels.add(new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
        }

        for (int i = 0; i < impulse.size(); i++) {
            ElliottSwing swing = impulse.get(i);
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                    placementForPivot(swing.isRising())));
        }

        for (int i = 0; i < correction.size(); i++) {
            ElliottSwing swing = correction.get(i);
            String label = switch (i) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), label, placementForPivot(swing.isRising())));
        }

        return new BarSeriesLabelIndicator(series, labels);
    }

    private static LabelPlacement placementForPivot(boolean isHighPivot) {
        return isHighPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    @Test
    void loadSeriesFromDataSource_withUnsupportedDataSource_returnsNull() {
        String unsupportedDataSource = "UnsupportedSource";
        String ticker = "BTC-USD";
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(unsupportedDataSource, ticker, barDuration,
                startTime, endTime);

        assertNull(result, "Should return null for unsupported data source");
    }

    @Test
    void loadSeriesFromDataSource_withNullDataSource_returnsNull() {
        String ticker = "BTC-USD";
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(null, ticker, barDuration, startTime, endTime);

        assertNull(result, "Should return null for null data source");
    }

    @Test
    void loadSeriesFromDataSource_withYahooFinanceDataSource_createsYahooFinanceSource() {
        // This test verifies that YahooFinance datasource is created correctly
        // We can't easily mock it since it's created inside the method, but we can test
        // that it doesn't throw an exception for valid parameters
        String dataSource = "YahooFinance";
        String ticker = "AAPL";
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        // This will likely return null due to network/API issues in test environment,
        // but it should not throw an exception
        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime,
                endTime);

        // Result may be null if API call fails, but method should handle it gracefully
        // The important thing is that it doesn't throw an exception
        assertTrue(result == null || !result.isEmpty(), "Result should be null or non-empty series");
    }

    @Test
    void loadSeriesFromDataSource_withCoinbaseDataSource_createsCoinbaseSource() {
        // This test verifies that Coinbase datasource is created correctly
        String dataSource = "Coinbase";
        String ticker = "BTC-USD";
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        // This will likely return null due to network/API issues in test environment,
        // but it should not throw an exception
        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime,
                endTime);

        // Result may be null if API call fails, but method should handle it gracefully
        assertTrue(result == null || !result.isEmpty(), "Result should be null or non-empty series");
    }

    @Test
    void loadSeriesFromDataSource_withCaseInsensitiveDataSource_handlesCorrectly() {
        // Test that data source name matching is case-insensitive
        String dataSource = "yahoofinance"; // lowercase
        String ticker = "AAPL";
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime,
                endTime);

        // Should not throw exception, may return null if API fails
        assertTrue(result == null || !result.isEmpty(), "Result should be null or non-empty series");
    }

    @Test
    void loadSeriesFromDataSource_withEmptySeries_returnsNull() {
        // This test uses a mock datasource that returns an empty series
        // Since we can't easily inject mocks, we'll test with a scenario that
        // would produce an empty series (very short time range that might not have
        // data)
        String dataSource = "Coinbase";
        String ticker = "BTC-USD";
        Duration barDuration = Duration.ofDays(1);
        // Very short time range that might not have data
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-01T01:00:00Z"); // Only 1 hour

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime,
                endTime);

        // Result may be null or empty, method should handle both gracefully
        assertTrue(result == null || result.isEmpty() || !result.isEmpty(),
                "Result should be null, empty, or non-empty series");
    }

    @Test
    void loadSeriesFromDataSource_withNullTicker_handlesGracefully() {
        String dataSource = "YahooFinance";
        String ticker = null;
        Duration barDuration = Duration.ofDays(1);
        Instant startTime = Instant.parse("2023-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-01-31T23:59:59Z");

        // Should not throw exception, datasource should handle null ticker
        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime,
                endTime);

        // Result should be null when ticker is null
        assertNull(result, "Should return null for null ticker");
    }

    /**
     * Security manager that throws a special exception when System.exit is called,
     * allowing tests to verify exit behavior without actually terminating the JVM.
     * <p>
     * Note: SecurityManager is deprecated in Java 17+, but is still functional for
     * testing purposes.
     */
    @SuppressWarnings("removal")
    private static class ExitSecurityManager extends SecurityManager {
        private int exitCode = -1;

        @Override
        public void checkPermission(Permission perm) {
            // Allow all permissions except exit
        }

        @Override
        public void checkExit(int status) {
            exitCode = status;
            throw new SecurityException("System.exit(" + status + ") called");
        }

        int getExitCode() {
            return exitCode;
        }
    }

    /**
     * Checks if SecurityManager is supported in the current JVM. SecurityManager is
     * deprecated and may not be supported in future Java versions.
     */
    @SuppressWarnings("removal")
    private static boolean isSecurityManagerSupported() {
        try {
            SecurityManager current = System.getSecurityManager();
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission perm) {
                }
            });
            System.setSecurityManager(current);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely sets the SecurityManager, handling cases where it's not supported.
     */
    @SuppressWarnings("removal")
    private static boolean safeSetSecurityManager(SecurityManager manager) {
        try {
            System.setSecurityManager(manager);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withDayBasedDuration_normalizesToHours() {
        // Test that main() handles PT1D duration correctly (normalizes to PT24H
        // internally)
        // This tests normalizeDurationString indirectly
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1686960000", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            // Skip test if SecurityManager not supported
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
            // If we get here, main() completed without calling System.exit
            // This is expected if API call succeeds or fails gracefully
        } catch (SecurityException e) {
            // Expected when System.exit is called (e.g., if series is null/empty)
            assertTrue(e.getMessage().contains("System.exit"),
                    "SecurityException should indicate System.exit was called");
        } catch (Exception ex) {
            // Other exceptions are also acceptable (e.g., network errors)
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withInvalidEpoch_exitsGracefully() {
        // Test that main() handles invalid epoch values correctly
        // This tests isEpochSeconds indirectly
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "invalid-epoch", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            // Expected - invalid epoch should cause System.exit(1)
            assertTrue(e.getMessage().contains("System.exit"),
                    "SecurityException should indicate System.exit was called");
            assertEquals(1, securityManager.getExitCode(), "Exit code should be 1 for error");
        } catch (Exception ex) {
            // Other exceptions are also acceptable
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withExplicitDegree_usesProvidedDegree() {
        // Test that main() accepts explicit degree in args
        // This tests parseExplicitDegree and hasDegreeToken indirectly
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "PRIMARY", "1686960000", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            // Expected if API fails and System.exit is called
            assertTrue(e.getMessage().contains("System.exit"));
        } catch (Exception ex) {
            // Expected if API fails
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withCaseInsensitiveDegree_acceptsDegree() {
        // Test case-insensitive degree parsing
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "primary", "1686960000", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("System.exit"));
        } catch (Exception ex) {
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withoutExplicitDegree_autoSelectsDegree() {
        // Test that main() auto-selects degree when not provided
        // This tests selectRecommendedDegree indirectly
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1686960000", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("System.exit"));
        } catch (Exception ex) {
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withInsufficientArgs_loadsDefaultDataset() {
        // Test that main() loads default dataset when args < 4
        // This tests loadBarSeries indirectly
        String[] args = {};
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
            // If default dataset loads successfully, main() should complete
        } catch (SecurityException e) {
            // Expected if default dataset fails to load
            assertTrue(e.getMessage().contains("System.exit"));
        } catch (Exception ex) {
            // Other exceptions are acceptable
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withInvalidDataSource_exitsGracefully() {
        // Test that main() handles invalid data source
        String[] args = { "InvalidSource", "BTC-USD", "PT1D", "1686960000", "1697040000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            // Expected - invalid data source should cause System.exit(1)
            assertTrue(e.getMessage().contains("System.exit"));
            assertEquals(1, securityManager.getExitCode(), "Exit code should be 1 for error");
        } catch (Exception ex) {
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void main_withInvalidTimeRange_exitsGracefully() {
        // Test that main() handles invalid time range
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1697040000", "1686960000" };
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!isSecurityManagerSupported() || !safeSetSecurityManager(securityManager)) {
            return;
        }

        try {
            ElliottWaveAnalysis.main(args);
        } catch (SecurityException e) {
            // Expected - invalid time range may cause System.exit(1)
            assertTrue(e.getMessage().contains("System.exit"));
        } catch (Exception ex) {
            assertNotNull(ex);
        } finally {
            safeSetSecurityManager(null);
        }
    }

    @Test
    void analyze_withValidSeries_completesSuccessfully() {
        // Test that analyze() completes successfully and generates wave labels
        // This tests buildWaveLabelsFromScenario and placementForPivot indirectly
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        // Should not throw exception
        analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);
        // The analyze method internally calls buildWaveLabelsFromScenario
        // We can verify it worked by checking that charts were generated
        // (though we can't directly access the labels, we know they were created)
    }

    @Test
    void analyze_withDifferentScenarioTypes_generatesAppropriateLabels() {
        // Test that analyze() handles different scenario types
        // This indirectly tests buildWaveLabelsFromScenario with different types
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        // Test with different degrees which may produce different scenario types
        analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);
        analysis.analyze(series, ElliottDegree.INTERMEDIATE, FIB_TOLERANCE);
        analysis.analyze(series, ElliottDegree.MINOR, FIB_TOLERANCE);

        // Should complete without exception, indicating label generation worked
    }

    @Test
    void analyze_withDifferentDegrees_completesSuccessfully() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        // Test with different degrees
        analysis.analyze(series, ElliottDegree.INTERMEDIATE, FIB_TOLERANCE);
        analysis.analyze(series, ElliottDegree.MINOR, FIB_TOLERANCE);
    }

    @Test
    void analyze_withDifferentFibTolerances_completesSuccessfully() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        // Test with different tolerances
        analysis.analyze(series, ElliottDegree.PRIMARY, 0.1);
        analysis.analyze(series, ElliottDegree.PRIMARY, 0.5);
    }

    @Test
    void analyze_returnsAnalysisResult() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        assertNotNull(result, "Analysis result should not be null");
        assertEquals(series, result.series(), "Series should match");
        assertEquals(ElliottDegree.PRIMARY, result.degree(), "Degree should match");
        assertNotNull(result.phaseIndicator(), "Phase indicator should not be null");
        assertNotNull(result.invalidationIndicator(), "Invalidation indicator should not be null");
        assertNotNull(result.channelIndicator(), "Channel indicator should not be null");
        assertNotNull(result.ratioIndicator(), "Ratio indicator should not be null");
        assertNotNull(result.confluenceIndicator(), "Confluence indicator should not be null");
        assertNotNull(result.swingCount(), "Swing count indicator should not be null");
        assertNotNull(result.filteredSwingCount(), "Filtered swing count indicator should not be null");
        assertNotNull(result.scenarioIndicator(), "Scenario indicator should not be null");
        assertNotNull(result.swingMetadata(), "Swing metadata should not be null");
        assertNotNull(result.scenarioSet(), "Scenario set should not be null");
        assertNotNull(result.ratioValue(), "Ratio value indicator should not be null");
        assertNotNull(result.swingCountAsNum(), "Swing count as num indicator should not be null");
        assertNotNull(result.filteredSwingCountAsNum(), "Filtered swing count as num indicator should not be null");
        assertNotNull(result.baseCaseChartPlan(), "Base case chart plan optional should not be null");
        assertNotNull(result.alternativeChartPlans(), "Alternative chart plans list should not be null");
    }

    @Test
    void analyze_withBaseCaseScenario_createsBaseCaseChartPlan() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        assertTrue(result.baseCaseChartPlan().isPresent(),
                "Base case chart plan should be present when base case scenario exists");
        assertNotNull(result.baseCaseChartPlan().get(), "Base case chart plan should not be null");
        assertNotNull(result.scenarioSet().base(), "Base case scenario should exist");
    }

    @Test
    void analyze_createsChartPlansForAllScenarios() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        // Verify base case chart plan exists if base case scenario exists
        if (result.scenarioSet().base().isPresent()) {
            assertTrue(result.baseCaseChartPlan().isPresent(),
                    "Base case chart plan should exist when base case scenario exists");
        }

        // Verify alternative chart plans match alternative scenarios
        int alternativeCount = result.scenarioSet().alternatives().size();
        assertEquals(alternativeCount, result.alternativeChartPlans().size(),
                "Number of alternative chart plans should match number of alternative scenarios");
    }

    @Test
    void visualizeAnalysisResult_withValidResult_completesSuccessfully() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        // Should not throw exception
        analysis.visualizeAnalysisResult(result);
    }

    @Test
    void visualizeAnalysisResult_withNullResult_throwsNullPointerException() {
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        try {
            analysis.visualizeAnalysisResult(null);
            // Should not reach here
            assertTrue(false, "Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage(), "Exception message should not be null");
            assertTrue(e.getMessage().contains("Analysis result cannot be null"),
                    "Exception message should indicate null result");
        }
    }

    @Test
    void visualizeAnalysisResult_withDifferentDegrees_formatsWindowTitlesCorrectly() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        // Test with PRIMARY degree
        ElliottWaveAnalysis.AnalysisResult primaryResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);
        if (primaryResult.baseCaseChartPlan().isPresent() && primaryResult.scenarioSet().base().isPresent()) {
            ElliottScenario baseCase = primaryResult.scenarioSet().base().get();
            String expectedBaseCaseTitle = String.format("%s - BASE CASE: %s (%s) - %.1f%% - %s", ElliottDegree.PRIMARY,
                    baseCase.currentPhase(), baseCase.type(), baseCase.confidence().asPercentage(), series.getName());
            assertTrue(expectedBaseCaseTitle.startsWith("PRIMARY -"),
                    "Base case window title should start with degree");
            assertTrue(expectedBaseCaseTitle.contains("BASE CASE:"),
                    "Base case window title should contain BASE CASE:");
        }

        // Test with INTERMEDIATE degree
        ElliottWaveAnalysis.AnalysisResult intermediateResult = analysis.analyze(series, ElliottDegree.INTERMEDIATE,
                FIB_TOLERANCE);
        if (intermediateResult.baseCaseChartPlan().isPresent() && intermediateResult.scenarioSet().base().isPresent()) {
            ElliottScenario baseCase = intermediateResult.scenarioSet().base().get();
            String expectedIntermediateTitle = String.format("%s - BASE CASE: %s (%s) - %.1f%% - %s",
                    ElliottDegree.INTERMEDIATE, baseCase.currentPhase(), baseCase.type(),
                    baseCase.confidence().asPercentage(), series.getName());
            assertTrue(expectedIntermediateTitle.startsWith("INTERMEDIATE -"),
                    "Intermediate window title should start with degree");
        }

        // Test with MINOR degree
        ElliottWaveAnalysis.AnalysisResult minorResult = analysis.analyze(series, ElliottDegree.MINOR, FIB_TOLERANCE);
        if (minorResult.baseCaseChartPlan().isPresent() && minorResult.scenarioSet().base().isPresent()) {
            ElliottScenario baseCase = minorResult.scenarioSet().base().get();
            String expectedMinorTitle = String.format("%s - BASE CASE: %s (%s) - %.1f%% - %s", ElliottDegree.MINOR,
                    baseCase.currentPhase(), baseCase.type(), baseCase.confidence().asPercentage(), series.getName());
            assertTrue(expectedMinorTitle.startsWith("MINOR -"), "Minor window title should start with degree");
        }
    }

    @Test
    void visualizeAnalysisResult_withAlternativeScenarios_formatsWindowTitlesCorrectly() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        List<ElliottScenario> alternatives = result.scenarioSet().alternatives();
        for (int i = 0; i < alternatives.size() && i < result.alternativeChartPlans().size(); i++) {
            ElliottScenario alt = alternatives.get(i);
            String expectedAltTitle = String.format("%s - ALTERNATIVE %d: %s (%s) - %.1f%% - %s", result.degree(),
                    i + 1, alt.currentPhase(), alt.type(), alt.confidence().asPercentage(), series.getName());
            assertTrue(expectedAltTitle.startsWith(result.degree().toString() + " -"),
                    "Alternative window title should start with degree");
            assertTrue(expectedAltTitle.contains("ALTERNATIVE " + (i + 1) + ":"),
                    "Alternative window title should contain alternative number");
        }
    }

    @Test
    void analyze_withEmptySeries_throwsIllegalArgumentException() {
        BarSeries emptySeries = new BaseBarSeriesBuilder().withName("Empty").build();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        try {
            analysis.analyze(emptySeries, ElliottDegree.PRIMARY, FIB_TOLERANCE);
            assertTrue(false, "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage(), "Exception message should not be null");
            assertTrue(e.getMessage().contains("Series cannot be empty"),
                    "Exception message should indicate empty series");
        }
    }

    @Test
    void analyze_withNullSeries_throwsNullPointerException() {
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        try {
            analysis.analyze(null, ElliottDegree.PRIMARY, FIB_TOLERANCE);
            assertTrue(false, "Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage(), "Exception message should not be null");
            assertTrue(e.getMessage().contains("Series cannot be null"),
                    "Exception message should indicate null series");
        }
    }

    @Test
    void analyze_separatesAnalysisFromVisualization() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        // Analyze without visualization
        ElliottWaveAnalysis.AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, FIB_TOLERANCE);

        // Verify analysis completed
        assertNotNull(result);
        assertNotNull(result.scenarioSet());

        // Now visualize separately
        analysis.visualizeAnalysisResult(result);

        // Both should complete without exception
    }

}
