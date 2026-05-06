/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.demo;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;

import ta4jexamples.analysis.elliottwave.ElliottWaveIndicatorSuiteDemo;
import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

/**
 * Demonstrates multi-degree Elliott Wave analysis, validating scenarios across
 * neighboring degrees and re-ranking base-degree outcomes.
 *
 * <p>
 * This demo uses an ossified BTC-USD dataset from classpath resources and runs
 * an auto-selected base-degree analysis, plus one supporting degree higher and
 * lower.
 *
 * @since 0.22.4
 */
public class ElliottWaveMultiDegreeAnalysisDemo {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMultiDegreeAnalysisDemo.class);
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231020.json";

    public static void main(String[] args) {
        BarSeries series = loadSeries(DEFAULT_OHLCV_RESOURCE);
        if (series == null || series.isEmpty()) {
            LOG.error("No series available for multi-degree Elliott Wave analysis");
            return;
        }

        ElliottDegree baseDegree = ElliottWaveIndicatorSuiteDemo.autoSelectDegree(series);

        ElliottWaveAnalysisRunner analyzer = ElliottWaveAnalysisRunner.builder()
                .degree(baseDegree)
                .higherDegrees(1)
                .lowerDegrees(1)
                .swingDetector(SwingDetectors.adaptiveZigZag(new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 1)))
                .build();

        ElliottWaveAnalysisResult result = analyzer.analyze(series);
        LOG.info("Auto-selected base degree: {}", baseDegree);

        for (ElliottWaveAnalysisResult.DegreeAnalysis analysis : result.analyses()) {
            LOG.info("{} Degree {}: bars={} duration={} historyFit={} trendBias={}", series.getName(),
                    analysis.degree(), analysis.barCount(), analysis.barDuration(),
                    String.format("%.2f", analysis.historyFitScore()), analysis.analysis().trendBias().direction());
            analysis.analysis().scenarios().base().ifPresent(base -> logScenario("  Base", base));
        }

        Optional<ElliottWaveAnalysisResult.BaseScenarioAssessment> recommended = result.recommendedScenario();
        if (recommended.isEmpty()) {
            LOG.warn("No base-degree scenarios were produced");
            return;
        }

        ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = recommended.orElseThrow();
        ElliottScenario scenario = assessment.scenario();
        LOG.info("Recommended base scenario: id={} phase={} type={} confidence={} crossDegree={} composite={}",
                scenario.id(), scenario.currentPhase(), scenario.type(),
                String.format("%.2f", assessment.confidenceScore()),
                String.format("%.2f", assessment.crossDegreeScore()),
                String.format("%.2f", assessment.compositeScore()));

        List<ElliottWaveAnalysisResult.SupportingScenarioMatch> matches = assessment.supportingMatches();
        for (ElliottWaveAnalysisResult.SupportingScenarioMatch match : matches) {
            LOG.info("  Match {}: scenarioId={} compat={} weightedCompat={} historyFit={}", match.degree(),
                    match.scenarioId(), String.format("%.2f", match.compatibilityScore()),
                    String.format("%.2f", match.weightedCompatibility()),
                    String.format("%.2f", match.historyFitScore()));
        }

        for (String note : result.notes()) {
            LOG.info("Note: {}", note);
        }
    }

    private static void logScenario(final String label, final ElliottScenario scenario) {
        LOG.info("{} scenarioId={} phase={} type={} confidence={}% direction={}", label, scenario.id(),
                scenario.currentPhase(), scenario.type(), String.format("%.1f", scenario.confidence().asPercentage()),
                scenario.hasKnownDirection() ? (scenario.isBullish() ? "bullish" : "bearish") : "unknown");
    }

    private static BarSeries loadSeries(final String resource) {
        return OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveMultiDegreeAnalysisDemo.class, resource,
                "BTC-USD_PT1D@Coinbase (ossified)", LOG);
    }
}
