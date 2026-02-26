/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Optional;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.BarSeries;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.charting.display.SwingChartDisplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ElliottWaveAnalysisReportTest {

    private static final String OSSIFIED_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    private static final double FIB_TOLERANCE = 0.25;

    @BeforeEach
    void setUp() {
        // Disable chart display to prevent windows from appearing in tests
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
    }

    @Test
    void from_withValidInputs_createsResult() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        // The structured result is now automatically created in analyze()
        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        assertNotNull(result, "Result should not be null");
        assertEquals(ElliottDegree.PRIMARY, result.degree(), "Degree should match");
        assertEquals(series.getEndIndex(), result.endIndex(), "End index should match");
        assertNotNull(result.swingSnapshot(), "Swing snapshot should not be null");
        assertNotNull(result.latestAnalysis(), "Latest analysis should not be null");
        assertNotNull(result.scenarioSummary(), "Scenario summary should not be null");
        assertNotNull(result.alternatives(), "Alternatives list should not be null");
    }

    @Test
    void from_withBaseCaseScenario_includesBaseCase() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        if (analysisResult.scenarioSet().base().isPresent()) {
            assertNotNull(result.baseCase(), "Base case should not be null when scenario exists");
            assertNotNull(result.baseCase().currentPhase(), "Base case phase should not be null");
            assertNotNull(result.baseCase().type(), "Base case type should not be null");
            assertTrue(result.baseCase().overallConfidence() >= 0 && result.baseCase().overallConfidence() <= 100,
                    "Confidence should be between 0 and 100");
            assertTrue(result.baseCase().scenarioProbability() >= 0 && result.baseCase().scenarioProbability() <= 1.0,
                    "Scenario probability should be between 0 and 1");
            assertNotNull(result.baseCase().confidenceLevel(), "Confidence level should not be null");
            assertNotNull(result.baseCase().swings(), "Swings list should not be null");
        }
    }

    @Test
    void from_withBaseCaseChartPlan_encodesChartImage() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        if (analysisResult.baseCaseChartPlan().isPresent()) {
            assertNotNull(result.baseCaseChartImage(), "Base case chart image should not be null");
            assertTrue(result.baseCaseChartImage().length() > 0, "Base case chart image should not be empty");
            // Verify it's valid base64
            assertTrue(isValidBase64(result.baseCaseChartImage()), "Base case chart image should be valid base64");
        } else {
            assertNull(result.baseCaseChartImage(), "Base case chart image should be null when no chart plan");
        }
    }

    @Test
    void from_withAlternativeChartPlans_encodesChartImages() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        assertEquals(analysisResult.alternativeChartPlans().size(), result.alternativeChartImages().size(),
                "Number of alternative chart images should match number of chart plans");

        for (String chartImage : result.alternativeChartImages()) {
            assertNotNull(chartImage, "Alternative chart image should not be null");
            assertTrue(chartImage.length() > 0, "Alternative chart image should not be empty");
            assertTrue(isValidBase64(chartImage), "Alternative chart image should be valid base64");
        }
    }

    @Test
    void from_withNullDegree_throwsNullPointerException() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(null, analysisResult.swingMetadata(),
                        analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                        analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                        analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex,
                        analysisResult.baseCaseChartPlan(), analysisResult.alternativeChartPlans()));
    }

    @Test
    void from_withNullSwingMetadata_throwsNullPointerException() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(analysisResult.degree(), null, analysisResult.phaseIndicator(),
                        analysisResult.ratioIndicator(), analysisResult.channelIndicator(),
                        analysisResult.confluenceIndicator(), analysisResult.invalidationIndicator(),
                        analysisResult.scenarioSet(), endIndex, analysisResult.baseCaseChartPlan(),
                        analysisResult.alternativeChartPlans()));
    }

    @Test
    void from_withNullPhaseIndicator_throwsNullPointerException() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(analysisResult.degree(), analysisResult.swingMetadata(), null,
                        analysisResult.ratioIndicator(), analysisResult.channelIndicator(),
                        analysisResult.confluenceIndicator(), analysisResult.invalidationIndicator(),
                        analysisResult.scenarioSet(), endIndex, analysisResult.baseCaseChartPlan(),
                        analysisResult.alternativeChartPlans()));
    }

    @Test
    void from_withNullScenarioSet_throwsNullPointerException() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(analysisResult.degree(), analysisResult.swingMetadata(),
                        analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                        analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                        analysisResult.invalidationIndicator(), null, endIndex, analysisResult.baseCaseChartPlan(),
                        analysisResult.alternativeChartPlans()));
    }

    @Test
    void from_withNullChartPlans_throwsNullPointerException() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(analysisResult.degree(), analysisResult.swingMetadata(),
                        analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                        analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                        analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex, null,
                        analysisResult.alternativeChartPlans()));

        assertThrows(NullPointerException.class,
                () -> ElliottWaveAnalysisReport.from(analysisResult.degree(), analysisResult.swingMetadata(),
                        analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                        analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                        analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex,
                        analysisResult.baseCaseChartPlan(), null));
    }

    @Test
    void toJson_serializesResultToValidJson() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        String json = result.toJson();
        assertNotNull(json, "JSON should not be null");
        assertTrue(json.length() > 0, "JSON should not be empty");
        assertTrue(json.contains("\"degree\""), "JSON should contain degree field");
        assertTrue(json.contains("\"endIndex\""), "JSON should contain endIndex field");
        assertTrue(json.contains("\"swingSnapshot\""), "JSON should contain swingSnapshot field");
        assertTrue(json.contains("\"latestAnalysis\""), "JSON should contain latestAnalysis field");
        assertTrue(json.contains("\"scenarioSummary\""), "JSON should contain scenarioSummary field");
        assertTrue(json.contains("\"trendBias\""), "JSON should contain trendBias field");
    }

    @Test
    void toJson_withNaNValues_serializesNulls() {
        ElliottWaveAnalysisReport.SwingSnapshot snapshot = new ElliottWaveAnalysisReport.SwingSnapshot(false, 0,
                Double.NaN, Double.NaN);
        ElliottWaveAnalysisReport.LatestAnalysis latest = new ElliottWaveAnalysisReport.LatestAnalysis(
                ElliottPhase.NONE, false, false, RatioType.NONE, Double.NaN, null, Double.NaN, false, false);
        ElliottWaveAnalysisReport.ScenarioSummary summary = new ElliottWaveAnalysisReport.ScenarioSummary("summary",
                false, ElliottPhase.NONE);
        ElliottTrendBias trendBias = ElliottTrendBias.unknown();
        ElliottWaveAnalysisReport result = new ElliottWaveAnalysisReport(ElliottDegree.PRIMARY, 0, snapshot, latest,
                summary, trendBias, null, List.of(), null, List.of());

        String json = result.toJson();

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject swingSnapshot = root.getAsJsonObject("swingSnapshot");
        JsonObject latestAnalysis = root.getAsJsonObject("latestAnalysis");

        assertTrue(swingSnapshot.get("high").isJsonNull(), "NaN values should serialize as null");
        assertTrue(latestAnalysis.get("ratioValue").isJsonNull(), "NaN values should serialize as null");
        assertTrue(latestAnalysis.get("confluenceScore").isJsonNull(), "NaN values should serialize as null");
    }

    @Test
    void swingSnapshot_fromMetadata_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        ElliottWaveAnalysisReport.SwingSnapshot snapshot = result.swingSnapshot();
        assertNotNull(snapshot, "Swing snapshot should not be null");
        assertEquals(analysisResult.swingMetadata().isValid(), snapshot.valid(), "Valid flag should match");
        assertEquals(analysisResult.swingMetadata().size(), snapshot.swings(), "Swing count should match");
        assertTrue(snapshot.high() >= snapshot.low(), "High should be >= low");
    }

    @Test
    void latestAnalysis_fromIndicators_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        ElliottWaveAnalysisReport.LatestAnalysis latest = result.latestAnalysis();
        assertNotNull(latest, "Latest analysis should not be null");
        assertNotNull(latest.phase(), "Phase should not be null");
        assertEquals(analysisResult.phaseIndicator().getValue(endIndex), latest.phase(), "Phase should match");
        assertEquals(analysisResult.phaseIndicator().isImpulseConfirmed(endIndex), latest.impulseConfirmed(),
                "Impulse confirmed should match");
        assertEquals(analysisResult.phaseIndicator().isCorrectiveConfirmed(endIndex), latest.correctiveConfirmed(),
                "Corrective confirmed should match");
        assertNotNull(latest.ratioType(), "Ratio type should not be null");
    }

    @Test
    void scenarioSummary_fromScenarioSet_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        ElliottWaveAnalysisReport result = ElliottWaveAnalysisReport.from(analysisResult.degree(),
                analysisResult.swingMetadata(), analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex,
                analysisResult.baseCaseChartPlan(), analysisResult.alternativeChartPlans());

        ElliottWaveAnalysisReport.ScenarioSummary summary = result.scenarioSummary();
        assertNotNull(summary, "Scenario summary should not be null");
        assertNotNull(summary.summary(), "Summary string should not be null");
        assertEquals(analysisResult.scenarioSet().hasStrongConsensus(), summary.strongConsensus(),
                "Strong consensus should match");
        assertEquals(analysisResult.scenarioSet().consensus(), summary.consensusPhase(),
                "Consensus phase should match");
    }

    @Test
    void baseCaseScenario_fromScenario_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        if (result.baseCase() != null) {
            ElliottWaveAnalysisReport.BaseCaseScenario baseCase = result.baseCase();
            assertNotNull(baseCase.currentPhase(), "Current phase should not be null");
            assertNotNull(baseCase.type(), "Type should not be null");
            assertTrue(baseCase.overallConfidence() >= 0 && baseCase.overallConfidence() <= 100,
                    "Overall confidence should be between 0 and 100");
            assertTrue(
                    baseCase.confidenceLevel().equals("HIGH") || baseCase.confidenceLevel().equals("MEDIUM")
                            || baseCase.confidenceLevel().equals("LOW"),
                    "Confidence level should be HIGH, MEDIUM, or LOW");
            assertTrue(baseCase.fibonacciScore() >= 0 && baseCase.fibonacciScore() <= 100,
                    "Fibonacci score should be between 0 and 100");
            assertTrue(baseCase.timeScore() >= 0 && baseCase.timeScore() <= 100,
                    "Time score should be between 0 and 100");
            assertTrue(baseCase.alternationScore() >= 0 && baseCase.alternationScore() <= 100,
                    "Alternation score should be between 0 and 100");
            assertTrue(baseCase.channelScore() >= 0 && baseCase.channelScore() <= 100,
                    "Channel score should be between 0 and 100");
            assertTrue(baseCase.completenessScore() >= 0 && baseCase.completenessScore() <= 100,
                    "Completeness score should be between 0 and 100");
            assertNotNull(baseCase.primaryReason(), "Primary reason should not be null");
            assertNotNull(baseCase.weakestFactor(), "Weakest factor should not be null");
            assertTrue(baseCase.direction().equals("BULLISH") || baseCase.direction().equals("BEARISH"),
                    "Direction should be BULLISH or BEARISH");
            assertNotNull(baseCase.swings(), "Swings list should not be null");
        }
    }

    @Test
    void alternativeScenario_fromScenario_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        for (ElliottWaveAnalysisReport.AlternativeScenario alt : result.alternatives()) {
            assertNotNull(alt.currentPhase(), "Current phase should not be null");
            assertNotNull(alt.type(), "Type should not be null");
            assertTrue(alt.confidencePercent() >= 0 && alt.confidencePercent() <= 100,
                    "Confidence percent should be between 0 and 100");
            assertTrue(alt.scenarioProbability() >= 0 && alt.scenarioProbability() <= 1.0,
                    "Scenario probability should be between 0 and 1");
            assertNotNull(alt.swings(), "Swings list should not be null");
        }
    }

    @Test
    void scenarioProbabilities_sumToOne() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();
        if (result.baseCase() == null) {
            assertTrue(result.alternatives().isEmpty(), "No base case implies no alternatives");
            return;
        }

        double totalProbability = result.baseCase().scenarioProbability();
        for (ElliottWaveAnalysisReport.AlternativeScenario alt : result.alternatives()) {
            totalProbability += alt.scenarioProbability();
        }
        assertEquals(1.0, totalProbability, 0.0001, "Scenario probabilities should sum to 1");
    }

    @Test
    void scenarioProbabilities_preferConsensusOverlap() {
        ElliottScenario scenarioA = buildScenario("A", 0.8, ElliottPhase.WAVE5, ScenarioType.IMPULSE, true);
        ElliottScenario scenarioB = buildScenario("B", 0.6, ElliottPhase.WAVE5, ScenarioType.IMPULSE, true);
        ElliottScenario scenarioC = buildScenario("C", 0.4, ElliottPhase.CORRECTIVE_C, ScenarioType.CORRECTIVE_ZIGZAG,
                false);

        ElliottScenarioSet scenarioSet = ElliottScenarioSet.of(List.of(scenarioA, scenarioB, scenarioC), 0);
        Map<String, Double> probabilities = ElliottWaveAnalysisReport.computeScenarioProbabilities(scenarioSet);

        double normalizedConsensus = (0.8 + 0.6) / 1.8;
        double normalizedOutlier = 0.4 / 1.8;

        double probA = probabilities.get("A");
        double probB = probabilities.get("B");
        double probC = probabilities.get("C");

        assertEquals(1.0, probA + probB + probC, 0.0001, "Scenario probabilities should sum to 1");
        assertTrue(probA > probB, "Higher confidence should remain higher within a consensus group");
        assertTrue(probA + probB > normalizedConsensus, "Consensus overlap should boost aligned scenarios");
        assertTrue(probC < normalizedOutlier, "Outlier scenarios should receive less than raw confidence");
    }

    @Test
    void scenarioProbabilities_applyConfidenceContrastForCloseScores() {
        ElliottScenario scenarioA = buildScenario("A", 0.55, ElliottPhase.WAVE3, ScenarioType.IMPULSE, true);
        ElliottScenario scenarioB = buildScenario("B", 0.54, ElliottPhase.WAVE3, ScenarioType.IMPULSE, true);
        ElliottScenario scenarioC = buildScenario("C", 0.53, ElliottPhase.WAVE3, ScenarioType.IMPULSE, true);

        ElliottScenarioSet scenarioSet = ElliottScenarioSet.of(List.of(scenarioA, scenarioB, scenarioC), 0);
        Map<String, Double> probabilities = ElliottWaveAnalysisReport.computeScenarioProbabilities(scenarioSet);

        double rawRatio = 0.55 / 0.53;
        double adjustedRatio = probabilities.get("A") / probabilities.get("C");

        assertTrue(adjustedRatio > rawRatio, "Contrast should widen the gap between close confidence scores");
    }

    @Test
    void toJson_roundsScenarioProbabilityToThreeDecimals() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();
        String json = result.toJson();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (result.baseCase() != null) {
            double expected = roundScenarioProbability(result.baseCase().scenarioProbability());
            double actual = root.getAsJsonObject("baseCase").get("scenarioProbability").getAsDouble();
            assertEquals(expected, actual, 0.0001, "Base case scenario probability should be rounded to 3 decimals");
        }
        if (!result.alternatives().isEmpty()) {
            ElliottWaveAnalysisReport.AlternativeScenario alt = result.alternatives().get(0);
            double expected = roundScenarioProbability(alt.scenarioProbability());
            double actual = root.getAsJsonArray("alternatives")
                    .get(0)
                    .getAsJsonObject()
                    .get("scenarioProbability")
                    .getAsDouble();
            assertEquals(expected, actual, 0.0001, "Alternative scenario probability should be rounded to 3 decimals");
        }
    }

    @Test
    void swingData_fromSwing_capturesCorrectData() {
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        ElliottWaveAnalysisReport result = analysisResult.structuredResult();

        if (result.baseCase() != null && !result.baseCase().swings().isEmpty()) {
            ElliottWaveAnalysisReport.SwingData swingData = result.baseCase().swings().get(0);
            assertTrue(swingData.fromIndex() >= 0, "From index should be non-negative");
            assertTrue(swingData.toIndex() >= 0, "To index should be non-negative");
            assertTrue(swingData.toIndex() != swingData.fromIndex(), "To index should differ from from index");
            assertTrue(swingData.fromPrice() >= 0 || Double.isNaN(swingData.fromPrice()),
                    "From price should be non-negative or NaN");
            assertTrue(swingData.toPrice() >= 0 || Double.isNaN(swingData.toPrice()),
                    "To price should be non-negative or NaN");
        }
    }

    @Test
    void from_withEmptyAlternativeChartPlans_createsEmptyList() {
        // This test verifies that when there are no alternative chart plans, the list
        // is empty
        // The actual integration creates the result automatically, so we test the
        // factory method directly
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        // Test factory method with empty list
        ElliottWaveAnalysisReport result = ElliottWaveAnalysisReport.from(analysisResult.degree(),
                analysisResult.swingMetadata(), analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex,
                analysisResult.baseCaseChartPlan(), List.of());

        assertNotNull(result.alternativeChartImages(), "Alternative chart images list should not be null");
        assertEquals(0, result.alternativeChartImages().size(), "Alternative chart images list should be empty");
    }

    @Test
    void from_withEmptyBaseCaseChartPlan_createsNullImage() {
        // This test verifies that when there's no base case chart plan, the image is
        // null
        // The actual integration creates the result automatically, so we test the
        // factory method directly
        BarSeries series = loadOssifiedSeries();
        ElliottWaveIndicatorSuiteDemo analysis = new ElliottWaveIndicatorSuiteDemo();
        ElliottWaveIndicatorSuiteDemo.AnalysisResult analysisResult = analysis.analyze(series, ElliottDegree.PRIMARY,
                FIB_TOLERANCE);

        int endIndex = series.getEndIndex();
        // Test factory method with empty optional
        ElliottWaveAnalysisReport result = ElliottWaveAnalysisReport.from(analysisResult.degree(),
                analysisResult.swingMetadata(), analysisResult.phaseIndicator(), analysisResult.ratioIndicator(),
                analysisResult.channelIndicator(), analysisResult.confluenceIndicator(),
                analysisResult.invalidationIndicator(), analysisResult.scenarioSet(), endIndex, Optional.empty(),
                analysisResult.alternativeChartPlans());

        assertNull(result.baseCaseChartImage(), "Base case chart image should be null when no chart plan");
    }

    /**
     * Helper method to build synthetic scenarios for probability tests.
     */
    private static ElliottScenario buildScenario(String id, double confidence, ElliottPhase phase, ScenarioType type,
            boolean bullish) {
        DoubleNum score = DoubleNum.valueOf(confidence);
        ElliottConfidence confidenceScore = new ElliottConfidence(score, score, score, score, score, score,
                "Test confidence");
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .confidence(confidenceScore)
                .degree(ElliottDegree.PRIMARY)
                .type(type)
                .bullishDirection(bullish)
                .build();
    }

    private static double roundScenarioProbability(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Helper method to load the ossified dataset used in tests.
     */
    private static BarSeries loadOssifiedSeries() {
        try (InputStream stream = ElliottWaveAnalysisReportTest.class.getClassLoader()
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

    /**
     * Helper method to validate base64 encoding.
     */
    private static boolean isValidBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
