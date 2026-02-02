/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.io.InputStream;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalyzer;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceFactorResult;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.CompositeSwingDetector;
import org.ta4j.core.indicators.elliott.swing.MinMagnitudeSwingFilter;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates adaptive ZigZag and composite swing detection for Elliott Wave
 * analysis.
 *
 * @since 0.22.2
 */
public class ElliottWaveAdaptiveSwingAnalysis {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAdaptiveSwingAnalysis.class);
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";

    public static void main(String[] args) {
        BarSeries series = loadSeries();
        if (series == null || series.isEmpty()) {
            LOG.error("No data available for adaptive swing analysis");
            return;
        }

        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3);
        SwingDetector detector = SwingDetectors.composite(CompositeSwingDetector.Policy.AND, SwingDetectors.fractal(5),
                SwingDetectors.adaptiveZigZag(config));

        ElliottWaveAnalyzer analyzer = ElliottWaveAnalyzer.builder()
                .degree(ElliottDegree.PRIMARY)
                .swingDetector(detector)
                .swingFilter(new MinMagnitudeSwingFilter(0.2))
                .build();

        ElliottAnalysisResult result = analyzer.analyze(series);
        LOG.info("Adaptive swing analysis complete. Trend bias: {}", result.trendBias().direction());
        result.scenarios().base().ifPresent(base -> logScenario("BASE", base, result));
        List<ElliottScenario> alternatives = result.scenarios().alternatives();
        for (int i = 0; i < Math.min(2, alternatives.size()); i++) {
            logScenario("ALT " + (i + 1), alternatives.get(i), result);
        }
    }

    private static void logScenario(String label, ElliottScenario scenario, ElliottAnalysisResult result) {
        LOG.info("{} SCENARIO: {} ({}) - confidence={}%%", label, scenario.currentPhase(), scenario.type(),
                String.format("%.1f", scenario.confidence().asPercentage()));
        ElliottConfidenceBreakdown breakdown = result.breakdownFor(scenario).orElse(null);
        if (breakdown == null) {
            return;
        }
        for (ConfidenceFactorResult factor : breakdown.factors()) {
            LOG.info("  Factor: {} score={} weight={} diagnostics={}", factor.name(),
                    String.format("%.2f", factor.score().doubleValue()), String.format("%.2f", factor.weight()),
                    factor.diagnostics());
        }
    }

    private static BarSeries loadSeries() {
        try (InputStream stream = ElliottWaveAdaptiveSwingAnalysis.class.getClassLoader()
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
