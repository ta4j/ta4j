/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysis;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates how pattern-specific confidence profiles influence scenario
 * ranking.
 *
 * @since 0.22.2
 */
public class ElliottWavePatternProfileDemo {

    private static final Logger LOG = LogManager.getLogger(ElliottWavePatternProfileDemo.class);
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";

    /**
     * Runs the pattern profile comparison demo.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        BarSeries series = loadSeries();
        if (series == null || series.isEmpty()) {
            LOG.error("No data available for pattern profile demo");
            return;
        }

        ElliottWaveAnalysis defaultAnalyzer = ElliottWaveAnalysis.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(SwingDetectors.fractal(5))
                .confidenceModelFactory(ConfidenceProfiles::defaultModel)
                .build();

        ElliottWaveAnalysis patternAwareAnalyzer = ElliottWaveAnalysis.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(SwingDetectors.fractal(5))
                .confidenceModelFactory(ConfidenceProfiles::patternAwareModel)
                .build();

        ElliottWaveAnalysisResult defaultSnapshot = defaultAnalyzer.analyze(series);
        ElliottWaveAnalysisResult patternSnapshot = patternAwareAnalyzer.analyze(series);

        ElliottAnalysisResult defaultResult = defaultSnapshot.analysisFor(ElliottDegree.PRIMARY)
                .orElseThrow()
                .analysis();
        ElliottAnalysisResult patternResult = patternSnapshot.analysisFor(ElliottDegree.PRIMARY)
                .orElseThrow()
                .analysis();

        logBaseScenario("Default profile", defaultResult);
        logBaseScenario("Pattern-aware profile", patternResult);
    }

    /**
     * Logs the base scenario for the provided analysis result.
     *
     * @param label  label for the output
     * @param result analysis result to inspect
     */
    private static void logBaseScenario(String label, ElliottAnalysisResult result) {
        ElliottScenario base = result.scenarios().base().orElse(null);
        if (base == null) {
            LOG.info("{}: No scenarios available", label);
            return;
        }
        LOG.info("{}: {} ({}) confidence={}%%", label, base.currentPhase(), base.type(),
                String.format("%.1f", base.confidence().asPercentage()));
    }

    /**
     * Loads the ossified BTC-USD dataset from classpath resources.
     *
     * @return loaded bar series, or {@code null} if unavailable
     */
    private static BarSeries loadSeries() {
        try (InputStream stream = ElliottWavePatternProfileDemo.class.getClassLoader()
                .getResourceAsStream(DEFAULT_OHLCV_RESOURCE)) {
            if (stream == null) {
                LOG.error("Missing resource: {}", DEFAULT_OHLCV_RESOURCE);
                return null;
            }
            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            if (loaded == null) {
                LOG.error("Failed to load resource: {}", DEFAULT_OHLCV_RESOURCE);
                return null;
            }
            BarSeries series = new BaseBarSeriesBuilder().withName("BTC-USD_PT1D@Coinbase (ossified)").build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            LOG.error("Failed to load dataset: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
