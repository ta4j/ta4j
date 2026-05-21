/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.AnalysisRunner;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottWaveAnalysisRunnerMultiDegreeTest {

    @Test
    void reranksBaseScenariosUsingSupportingDegreeContext() {
        BarSeries series = buildDailySeries(1100);
        NumFactory factory = series.numFactory();

        ElliottScenario bullishPrimary = scenario(factory, "primary-bull", ElliottDegree.PRIMARY, 0.90, true,
                factory.numOf(95));
        ElliottScenario bearishPrimary = scenario(factory, "primary-bear", ElliottDegree.PRIMARY, 0.80, false,
                factory.numOf(200));

        ElliottScenarioSet primaryScenarios = ElliottScenarioSet.of(List.of(bullishPrimary, bearishPrimary),
                series.getEndIndex());

        ElliottScenario bearishCycle = scenario(factory, "cycle-bear", ElliottDegree.CYCLE, 0.90, false,
                factory.numOf(210));
        ElliottScenarioSet cycleScenarios = ElliottScenarioSet.of(List.of(bearishCycle), series.getEndIndex());

        ElliottScenario bearishIntermediate = scenario(factory, "intermediate-bear", ElliottDegree.INTERMEDIATE, 0.85,
                false, factory.numOf(190));
        ElliottScenarioSet intermediateScenarios = ElliottScenarioSet.of(List.of(bearishIntermediate),
                series.getEndIndex());

        AnalysisRunner<ElliottDegree, ElliottAnalysisResult> runner = (selected, degree) -> switch (degree) {
        case PRIMARY -> analysis(degree, selected, primaryScenarios);
        case CYCLE -> analysis(degree, selected, cycleScenarios);
        case INTERMEDIATE -> analysis(degree, selected, intermediateScenarios);
        default -> throw new IllegalArgumentException("Unexpected degree: " + degree);
        };

        ElliottWaveAnalysisRunner analyzer = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(1)
                .lowerDegrees(1)
                .analysisRunner(runner)
                .build();

        ElliottWaveAnalysisResult result = analyzer.analyze(series);

        assertThat(result.recommendedBaseScenario()).isPresent();
        assertThat(result.recommendedBaseScenario().orElseThrow().id()).isEqualTo("primary-bear");

        assertThat(result.analysisFor(ElliottDegree.CYCLE)).isPresent();
        assertThat(result.analysisFor(ElliottDegree.PRIMARY)).isPresent();
        assertThat(result.analysisFor(ElliottDegree.INTERMEDIATE)).isPresent();

        int cycleBars = result.analysisFor(ElliottDegree.CYCLE).orElseThrow().barCount();
        int primaryBars = result.analysisFor(ElliottDegree.PRIMARY).orElseThrow().barCount();
        int intermediateBars = result.analysisFor(ElliottDegree.INTERMEDIATE).orElseThrow().barCount();
        assertThat(cycleBars).isGreaterThanOrEqualTo(primaryBars);
        assertThat(primaryBars).isGreaterThanOrEqualTo(intermediateBars);
        assertThat(intermediateBars).isGreaterThan(0);
    }

    @Test
    void favorsBroaderCompletedStructuresWhenConfidenceIsClose() {
        BarSeries series = buildDailySeries(220);
        NumFactory factory = series.numFactory();

        ElliottScenario recentFragment = scenario(factory, "recent-fragment", ElliottDegree.PRIMARY, ElliottPhase.WAVE3,
                0.90,
                List.of(new ElliottSwing(120, 150, factory.numOf(160), factory.numOf(210), ElliottDegree.PRIMARY),
                        new ElliottSwing(150, 175, factory.numOf(210), factory.numOf(190), ElliottDegree.PRIMARY),
                        new ElliottSwing(175, 210, factory.numOf(190), factory.numOf(250), ElliottDegree.PRIMARY)),
                factory.numOf(150));
        ElliottScenario broaderCompletion = scenario(factory, "broader-completion", ElliottDegree.PRIMARY,
                ElliottPhase.WAVE5, 0.87,
                List.of(new ElliottSwing(0, 35, factory.hundred(), factory.numOf(150), ElliottDegree.PRIMARY),
                        new ElliottSwing(35, 70, factory.numOf(150), factory.numOf(125), ElliottDegree.PRIMARY),
                        new ElliottSwing(70, 115, factory.numOf(125), factory.numOf(210), ElliottDegree.PRIMARY),
                        new ElliottSwing(115, 155, factory.numOf(210), factory.numOf(175), ElliottDegree.PRIMARY),
                        new ElliottSwing(155, 210, factory.numOf(175), factory.numOf(260), ElliottDegree.PRIMARY)),
                factory.numOf(175));

        ElliottScenarioSet primaryScenarios = ElliottScenarioSet.of(List.of(recentFragment, broaderCompletion),
                series.getEndIndex());
        AnalysisRunner<ElliottDegree, ElliottAnalysisResult> runner = (selected, degree) -> switch (degree) {
        case PRIMARY -> analysis(degree, selected, primaryScenarios);
        default -> analysis(degree, selected, ElliottScenarioSet.empty(selected.getEndIndex()));
        };

        ElliottWaveAnalysisRunner analyzer = ElliottWaveAnalysisRunner.builder()
                .degree(ElliottDegree.PRIMARY)
                .higherDegrees(0)
                .lowerDegrees(0)
                .analysisRunner(runner)
                .build();

        ElliottWaveAnalysisResult result = analyzer.analyze(series);

        assertThat(result.recommendedBaseScenario()).isPresent();
        assertThat(result.recommendedBaseScenario().orElseThrow().id()).isEqualTo("broader-completion");
    }

    @Test
    void builderRequiresDegree() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ElliottWaveAnalysisRunner.builder().build());
        assertThat(exception).hasMessage("degree must be configured");
    }

    @Test
    void rejectsInvalidConfidenceWeight() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ElliottWaveAnalysisRunner.builder().degree(ElliottDegree.PRIMARY).baseConfidenceWeight(1.1));
        assertThat(exception).hasMessage("baseConfidenceWeight must be in [0.0, 1.0]");
    }

    private static ElliottAnalysisResult analysis(final ElliottDegree degree, final BarSeries series,
            final ElliottScenarioSet scenarios) {
        ElliottTrendBias bias = scenarios.trendBias();
        return new ElliottAnalysisResult(degree, series.getEndIndex(), List.of(), List.of(), scenarios, Map.of(), null,
                bias);
    }

    private static ElliottScenario scenario(final NumFactory factory, final String id, final ElliottDegree degree,
            final double confidence, final boolean bullish, final Num invalidationPrice) {
        List<ElliottSwing> swings = bullish
                ? List.of(new ElliottSwing(0, 5, factory.hundred(), factory.numOf(120), degree))
                : List.of(new ElliottSwing(0, 5, factory.numOf(120), factory.hundred(), degree));
        return scenario(factory, id, degree, ElliottPhase.WAVE3, confidence, swings, invalidationPrice);
    }

    private static ElliottScenario scenario(final NumFactory factory, final String id, final ElliottDegree degree,
            final ElliottPhase phase, final double confidence, final List<ElliottSwing> swings,
            final Num invalidationPrice) {
        Num score = factory.numOf(confidence);
        ElliottConfidence confidenceRecord = new ElliottConfidence(score, score, score, score, score, score, "test");

        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .swings(swings)
                .confidence(confidenceRecord)
                .degree(degree)
                .invalidationPrice(invalidationPrice)
                .type(ScenarioType.IMPULSE)
                .build();
    }

    private static BarSeries buildDailySeries(final int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withName("MultiDegreeAnalyzerTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2022-01-01T00:00:00Z");
        for (int i = 0; i < barCount; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i + 1)))
                    .openPrice(100)
                    .highPrice(110)
                    .lowPrice(90)
                    .closePrice(100)
                    .volume(1)
                    .add();
        }
        return series;
    }
}
