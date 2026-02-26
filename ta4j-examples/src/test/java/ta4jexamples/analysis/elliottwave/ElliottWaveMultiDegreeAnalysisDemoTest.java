/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;

class ElliottWaveMultiDegreeAnalysisDemoTest {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMultiDegreeAnalysisDemoTest.class);
    private static final String OSSIFIED_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231020.json";

    @Test
    void ossifiedDatasetProducesRecommendedBaseScenario() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveMultiDegreeAnalysisDemo.class,
                OSSIFIED_OHLCV_RESOURCE, "BTC-USD_PT1D@Coinbase (ossified)", LOG);
        assertNotNull(series, "Series should load from ossified classpath resource");
        assertFalse(series.isEmpty(), "Series should contain bars");

        ElliottWaveAnalysisRunner analyzer = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .swingDetector(SwingDetectors.adaptiveZigZag(new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 1)))
                .build();

        ElliottWaveAnalysisResult result = analyzer.analyze(series);
        assertTrue(result.recommendedScenario().isPresent(),
                "Multi-degree analysis should produce a recommended base scenario");

        Optional<ElliottWaveAnalysisResult.DegreeAnalysis> primary = result.analyses()
                .stream()
                .filter(analysis -> analysis.degree() == ElliottDegree.PRIMARY)
                .findFirst();
        assertTrue(primary.isPresent(), "Primary degree analysis should be available");
        assertFalse(primary.orElseThrow().analysis().rawSwings().isEmpty(),
                "Primary degree should have detected swings");
    }
}
