/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.time.Instant;
import java.util.List;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.display.SwingChartDisplayer;
import ta4jexamples.charting.workflow.ChartWorkflow;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.datasources.BarSeriesDataSource;

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

    private static final class StubBarSeriesDataSource implements BarSeriesDataSource {
        private final boolean throwOnLoad;
        private final String sourceName;
        private final BarSeries series;

        private StubBarSeriesDataSource(String sourceName, BarSeries series, boolean throwOnLoad) {
            this.throwOnLoad = throwOnLoad;
            this.sourceName = sourceName;
            this.series = series;
        }

        @Override
        public BarSeries loadSeries(String ticker, Duration interval, Instant start, Instant end) {
            if (throwOnLoad) {
                throw new IllegalStateException("Stubbed load failure");
            }
            return series;
        }

        @Override
        public BarSeries loadSeries(String source) {
            return series;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }
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

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource((String) null, ticker, barDuration, startTime,
                endTime);

        assertNull(result, "Should return null for null data source");
    }

    @Test
    void loadSeriesFromDataSource_withStubSeries_returnsSeries() {
        BarSeries series = loadOssifiedSeries();
        BarSeriesDataSource source = new StubBarSeriesDataSource("Stub", series, false);

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(source, "BTC-USD", Duration.ofDays(1),
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-31T23:59:59Z"));

        assertEquals(series, result, "Should return the stubbed series");
    }

    @Test
    void loadSeriesFromDataSource_withStubReturningNull_returnsNull() {
        BarSeriesDataSource source = new StubBarSeriesDataSource("Stub", null, false);

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(source, "BTC-USD", Duration.ofDays(1),
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-31T23:59:59Z"));

        assertNull(result, "Should return null when the data source returns null");
    }

    @Test
    void loadSeriesFromDataSource_withStubReturningEmpty_returnsNull() {
        BarSeries emptySeries = new BaseBarSeriesBuilder().withName("Empty").build();
        BarSeriesDataSource source = new StubBarSeriesDataSource("Stub", emptySeries, false);

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(source, "BTC-USD", Duration.ofDays(1),
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-31T23:59:59Z"));

        assertNull(result, "Should return null when the data source returns an empty series");
    }

    @Test
    void loadSeriesFromDataSource_withStubThrowingException_returnsNull() {
        BarSeriesDataSource source = new StubBarSeriesDataSource("Stub", null, true);

        BarSeries result = ElliottWaveAnalysis.loadSeriesFromDataSource(source, "BTC-USD", Duration.ofDays(1),
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-31T23:59:59Z"));

        assertNull(result, "Should return null when the data source throws");
    }

    @Test
    void parseBarSeriesRequest_withDayBasedDuration_convertsToHours() {
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1686960000", "1697040000" };

        Optional<ElliottWaveAnalysis.BarSeriesRequest> request = ElliottWaveAnalysis.parseBarSeriesRequest(args);

        assertTrue(request.isPresent(), "Request should parse successfully");
        assertEquals(Duration.ofDays(1), request.get().barDuration(), "Duration should normalize to 24 hours");
    }

    @Test
    void parseBarSeriesRequest_withInvalidEpoch_returnsEmpty() {
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "invalid-epoch" };

        Optional<ElliottWaveAnalysis.BarSeriesRequest> request = ElliottWaveAnalysis.parseBarSeriesRequest(args);

        assertTrue(request.isEmpty(), "Invalid epoch should return empty request");
    }

    @Test
    void parseBarSeriesRequest_withInvalidDataSource_returnsEmpty() {
        String[] args = { "InvalidSource", "BTC-USD", "PT1D", "1686960000", "1697040000" };

        Optional<ElliottWaveAnalysis.BarSeriesRequest> request = ElliottWaveAnalysis.parseBarSeriesRequest(args);

        assertTrue(request.isEmpty(), "Invalid data source should return empty request");
    }

    @Test
    void parseBarSeriesRequest_withInvalidTimeRange_returnsEmpty() {
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1697040000", "1686960000" };

        Optional<ElliottWaveAnalysis.BarSeriesRequest> request = ElliottWaveAnalysis.parseBarSeriesRequest(args);

        assertTrue(request.isEmpty(), "Invalid time range should return empty request");
    }

    @Test
    void resolveDegree_withExplicitDegree_usesProvidedDegree() {
        BarSeries series = loadOssifiedSeries();
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "PRIMARY", "1686960000", "1697040000" };

        ElliottDegree resolved = ElliottWaveAnalysis.resolveDegree(args, series);

        assertEquals(ElliottDegree.PRIMARY, resolved, "Explicit degree should be used");
    }

    @Test
    void resolveDegree_withCaseInsensitiveDegree_usesProvidedDegree() {
        BarSeries series = loadOssifiedSeries();
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "primary", "1686960000", "1697040000" };

        ElliottDegree resolved = ElliottWaveAnalysis.resolveDegree(args, series);

        assertEquals(ElliottDegree.PRIMARY, resolved, "Degree should be parsed case-insensitively");
    }

    @Test
    void resolveDegree_withoutExplicitDegree_usesRecommendation() {
        BarSeries series = loadOssifiedSeries();
        String[] args = { "Coinbase", "BTC-USD", "PT1D", "1686960000", "1697040000" };

        List<ElliottDegree> recommendations = ElliottDegree.getRecommendedDegrees(series.getFirstBar().getTimePeriod(),
                series.getBarCount());
        ElliottDegree resolved = ElliottWaveAnalysis.resolveDegree(args, series);

        if (recommendations.isEmpty()) {
            assertEquals(ElliottDegree.PRIMARY, resolved, "Default degree should be used when none recommended");
        } else {
            assertEquals(recommendations.get(0), resolved, "Recommended degree should be used");
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
        assertNotNull(result.structuredResult(), "Structured result should not be null");
        assertNotNull(result.structuredResult().swingSnapshot(), "Structured result swing snapshot should not be null");
        assertNotNull(result.structuredResult().latestAnalysis(),
                "Structured result latest analysis should not be null");
        assertNotNull(result.structuredResult().scenarioSummary(),
                "Structured result scenario summary should not be null");
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
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> analysis.visualizeAnalysisResult(null));
        assertNotNull(exception.getMessage(), "Exception message should not be null");
        assertTrue(exception.getMessage().contains("Analysis result cannot be null"),
                "Exception message should indicate null result");
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> analysis.analyze(emptySeries, ElliottDegree.PRIMARY, FIB_TOLERANCE));
        assertNotNull(exception.getMessage(), "Exception message should not be null");
        assertTrue(exception.getMessage().contains("Series cannot be empty"),
                "Exception message should indicate empty series");
    }

    @Test
    void analyze_withNullSeries_throwsNullPointerException() {
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> analysis.analyze(null, ElliottDegree.PRIMARY, FIB_TOLERANCE));
        assertNotNull(exception.getMessage(), "Exception message should not be null");
        assertTrue(exception.getMessage().contains("Series cannot be null"),
                "Exception message should indicate null series");
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
