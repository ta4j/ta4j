/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectorResult;
import org.ta4j.core.indicators.elliott.swing.SwingFilter;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class ElliottWaveAnalysisRunnerTest {

    @Test
    void appliesSwingFiltersBeforeScenarioGeneration() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(110), degree),
                new ElliottSwing(4, 6, factory.numOf(110), factory.numOf(130), degree));

        SwingDetector detector = (s, index, deg) -> SwingDetectorResult.fromSwings(swings);
        SwingFilter filter = input -> input.subList(0, 2);
        ConfidenceModel model = (input, phase, channel,
                type) -> new ElliottConfidenceBreakdown(new ElliottConfidence(series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), series.numFactory().numOf(0.9),
                        series.numFactory().numOf(0.9), series.numFactory().numOf(0.9), "stub"), List.of());

        ElliottWaveAnalysisRunner analysis = ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(0)
                .lowerDegrees(0)
                .swingDetector(detector)
                .swingFilter(filter)
                .minConfidence(0.0)
                .confidenceModel(model)
                .build();

        ElliottWaveAnalysisResult result = analysis.analyze(series);
        ElliottAnalysisResult base = result.analysisFor(degree).orElseThrow().analysis();

        assertThat(base.rawSwings()).hasSize(3);
        assertThat(base.processedSwings()).hasSize(2);
        assertThat(base.scenarios().isEmpty()).isFalse();
        assertThat(base.confidenceBreakdowns()).hasSize(base.scenarios().size());
    }

    @Test
    void buildRequiresDegree() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ElliottWaveAnalysisRunner.builder().build());
        assertThat(exception).hasMessage("degree must be configured");
    }

    @Test
    void rejectsInvalidConfidenceThresholds() {
        IllegalArgumentException low = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().minConfidence(-0.01));
        assertThat(low).hasMessage("minConfidence must be in [0.0, 1.0]");

        IllegalArgumentException high = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().minConfidence(1.01));
        assertThat(high).hasMessage("minConfidence must be in [0.0, 1.0]");
    }

    @Test
    void rejectsInvalidScenarioParameters() {
        IllegalArgumentException maxScenarios = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().maxScenarios(0));
        assertThat(maxScenarios).hasMessage("maxScenarios must be positive");

        IllegalArgumentException window = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().scenarioSwingWindow(-1));
        assertThat(window).hasMessage("scenarioSwingWindow must be >= 0");
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("AnalyzerTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 8; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i)))
                    .openPrice(100 + i)
                    .highPrice(120 + i)
                    .lowPrice(90 + i)
                    .closePrice(110 + i)
                    .volume(1000)
                    .add();
        }
        return series;
    }

}
