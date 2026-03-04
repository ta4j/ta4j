/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.RankedPrediction;

class ElliottWaveOutcomeLabelerTest {

    @Test
    void returnsTargetFirstWhenPrimaryTargetReachedBeforeInvalidation() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 101, 102, 104, 106).build();
        ElliottScenario scenario = scenario(series, true, 103, 95, Boolean.TRUE);

        ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario, 0.8, 0.5, 0.7, List.of());
        RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction = new RankedPrediction<>(
                scenario.id(), 1, series.numFactory().numOf(0.7), series.numFactory().numOf(0.8), assessment);

        ElliottWaveOutcomeLabeler labeler = new ElliottWaveOutcomeLabeler();
        ElliottWaveOutcome outcome = labeler.label(series, 0, 3, prediction);

        assertThat(outcome.eventOutcome()).isEqualTo(ElliottWaveOutcome.EventOutcome.TARGET_FIRST);
        assertThat(outcome.reachedPrimaryTarget()).isTrue();
        assertThat(outcome.breachedInvalidation()).isFalse();
        assertThat(outcome.phaseProgression()).isEqualTo(ElliottWaveOutcome.PhaseProgression.ADVANCING);
    }

    @Test
    void returnsInvalidationFirstWhenInvalidationBreached() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 99, 94, 96, 97).build();
        ElliottScenario scenario = scenario(series, true, 105, 95, Boolean.TRUE);

        ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario, 0.8, 0.5, 0.7, List.of());
        RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction = new RankedPrediction<>(
                scenario.id(), 1, series.numFactory().numOf(0.7), series.numFactory().numOf(0.8), assessment);

        ElliottWaveOutcomeLabeler labeler = new ElliottWaveOutcomeLabeler();
        ElliottWaveOutcome outcome = labeler.label(series, 0, 3, prediction);

        assertThat(outcome.eventOutcome()).isEqualTo(ElliottWaveOutcome.EventOutcome.INVALIDATION_FIRST);
        assertThat(outcome.reachedPrimaryTarget()).isFalse();
        assertThat(outcome.breachedInvalidation()).isTrue();
    }

    @Test
    void returnsUnknownProgressionWhenDirectionIsUnknown() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 101, 102, 103).build();
        ElliottScenario scenario = scenario(series, true, 103, 95, null);

        ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario, 0.8, 0.5, 0.7, List.of());
        RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction = new RankedPrediction<>(
                scenario.id(), 1, series.numFactory().numOf(0.7), series.numFactory().numOf(0.8), assessment);

        ElliottWaveOutcomeLabeler labeler = new ElliottWaveOutcomeLabeler();
        ElliottWaveOutcome outcome = labeler.label(series, 0, 2, prediction);

        assertThat(outcome.eventOutcome()).isEqualTo(ElliottWaveOutcome.EventOutcome.NEITHER);
        assertThat(outcome.phaseProgression()).isEqualTo(ElliottWaveOutcome.PhaseProgression.UNKNOWN);
    }

    private static ElliottScenario scenario(BarSeries series, boolean bullish, double target, double invalidation,
            Boolean direction) {
        Num targetNum = series.numFactory().numOf(target);
        Num invalidationNum = series.numFactory().numOf(invalidation);

        return ElliottScenario.builder()
                .id("scenario-" + bullish + "-" + direction)
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINOR)
                .primaryTarget(targetNum)
                .invalidationPrice(invalidationNum)
                .type(ScenarioType.IMPULSE)
                .startIndex(series.getBeginIndex())
                .bullishDirection(direction)
                .build();
    }
}
