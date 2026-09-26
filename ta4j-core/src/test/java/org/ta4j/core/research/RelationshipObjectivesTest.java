/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.event.BinningStrategy;
import org.ta4j.core.analysis.event.EventMutualInformationConfig;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedBooleanIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator;
import org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.research.ParameterResearch.ObjectiveEvaluation;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.RankedCandidate;
import org.ta4j.core.research.ParameterResearch.SearchPlan;
import org.ta4j.core.research.ParameterResearch.TerminationReason;

class RelationshipObjectivesTest {

    private static BarSeries waveSeries(int size) {
        double[] closes = new double[size];
        for (int i = 0; i < size; i++) {
            closes[i] = 100 + 8 * Math.sin(i / 3.0) + 0.2 * i;
        }
        return new MockBarSeriesBuilder().withData(closes).build();
    }

    private static BarSeries waveSeriesWithTail(int size, double tailSlope) {
        double[] closes = new double[size];
        for (int i = 0; i < size; i++) {
            closes[i] = i < 45 ? 100 + 8 * Math.sin(i / 3.0) + 0.2 * i : 100 + 8 * Math.sin(i / 3.0) + tailSlope * i;
        }
        return new MockBarSeriesBuilder().withData(closes).build();
    }

    private static Indicator<Boolean> risingEvents(BarSeries series, int lookback) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        Boolean[] events = new Boolean[series.getBarCount()];
        for (int i = 0; i < events.length; i++) {
            int index = begin + i;
            events[i] = index - lookback >= begin
                    && close.getValue(index).isGreaterThan(close.getValue(index - lookback));
        }
        return new FixedBooleanIndicator(series, events);
    }

    private static Indicator<Num> momentum(BarSeries series, int lookback) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        Num[] values = new Num[series.getBarCount()];
        for (int i = 0; i < values.length; i++) {
            int index = begin + i;
            Num base = index - lookback >= begin ? close.getValue(index - lookback) : close.getValue(begin);
            values[i] = close.getValue(index).minus(base);
        }
        return new FixedIndicator<>(series, values);
    }

    private static Indicator<Boolean> noEvents(BarSeries series) {
        Boolean[] events = new Boolean[series.getBarCount()];
        java.util.Arrays.fill(events, false);
        return new FixedBooleanIndicator(series, events);
    }

    private static Indicator<Num> invertedPreviousClose(BarSeries series, int shift) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        Num[] values = new Num[series.getBarCount()];
        for (int i = 0; i < values.length; i++) {
            int index = begin + i;
            Num shifted = index - shift >= begin ? close.getValue(index - shift) : close.getValue(begin);
            values[i] = shifted.negate();
        }
        return new FixedIndicator<>(series, values);
    }

    private static FixedBooleanIndicator alwaysTrue(BarSeries series) {
        Boolean[] events = new Boolean[series.getBarCount()];
        java.util.Arrays.fill(events, true);
        return new FixedBooleanIndicator(series, events);
    }

    private static FixedBooleanIndicator nextBarUp(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        Boolean[] events = new Boolean[series.getBarCount()];
        for (int i = 0; i < events.length; i++) {
            int index = begin + i;
            events[i] = index < end && close.getValue(index + 1).isGreaterThan(close.getValue(index));
        }
        return new FixedBooleanIndicator(series, events);
    }

    @Test
    void eventSynchronizationF1SelectsMatchingLookback() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookbackStep", 1, 4)
                .candidate(
                        (window, parameters) -> risingEvents(window.series(), parameters.intValue("lookbackStep") * 3))
                .maximize(RelationshipObjectives.eventSynchronizationF1(windowSeries -> risingEvents(windowSeries, 6),
                        12, 1))
                .search(SearchPlan.grid(20))
                .topK(1)
                .run();

        assertThat(report.failedEvaluations()).isEmpty();
        assertThat(report.trainingLeaderboard()).hasSize(1);
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.parameters().intValue("lookbackStep")).isEqualTo(2);
        assertThat(winner.trainingScore().doubleValue()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void eventSynchronizationF1AcceptsZeroTolerance() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookbackStep", 1, 4)
                .candidate((window, parameters) -> risingEvents(window.series(), parameters.intValue("lookbackStep")))
                .maximize(RelationshipObjectives.eventSynchronizationF1(windowSeries -> risingEvents(windowSeries, 6),
                        12, 0))
                .search(SearchPlan.grid(10))
                .topK(1)
                .run();

        assertThat(report.failedEvaluations()).isEmpty();
        assertThat(report.trainingLeaderboard()).hasSize(1);
    }

    @Test
    void leadLagCorrelationSelectsMatchingShift() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("shift", 1, 4)
                .candidate((window,
                        parameters) -> (Indicator<Num>) new PreviousValueIndicator(
                                new ClosePriceIndicator(window.series()), parameters.intValue("shift")))
                .maximize(RelationshipObjectives.leadLagCorrelation(
                        windowSeries -> new PreviousValueIndicator(new ClosePriceIndicator(windowSeries), 2), 20, 0, 0))
                .search(SearchPlan.grid(10))
                .topK(1)
                .run();

        assertThat(report.failedEvaluations()).isEmpty();
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.parameters().intValue("shift")).isEqualTo(2);
        assertThat(winner.trainingScore().doubleValue()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void leadLagCorrelationScoresAbsoluteCorrelation() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("shift", 1, 4)
                .candidate((window,
                        parameters) -> (Indicator<Num>) invertedPreviousClose(window.series(),
                                parameters.intValue("shift")))
                .maximize(RelationshipObjectives.leadLagCorrelation(
                        windowSeries -> new PreviousValueIndicator(new ClosePriceIndicator(windowSeries), 2), 20, 0, 0))
                .search(SearchPlan.grid(10))
                .topK(1)
                .run();

        assertThat(report.failedEvaluations()).isEmpty();
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.parameters().intValue("shift")).isEqualTo(2);
        assertThat(winner.trainingScore().doubleValue()).isCloseTo(1.0, within(1e-6));
        assertThat(winner.trainingMetrics()).containsKey("selectedCorrelation");
        assertThat(winner.trainingMetrics().get("selectedCorrelation").doubleValue()).isNegative();
    }

    @Test
    void dynamicTimeWarpingMinimizesForMatchingSmoothing() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("period", 2, 5)
                .candidate((window,
                        parameters) -> (Indicator<Num>) new SMAIndicator(new ClosePriceIndicator(window.series()),
                                parameters.intValue("period")))
                .minimize(RelationshipObjectives.dynamicTimeWarpingDistance(
                        windowSeries -> new SMAIndicator(new ClosePriceIndicator(windowSeries), 3), 12,
                        DynamicTimeWarpingDistanceIndicator.Config.shapeComparison(2)))
                .search(SearchPlan.grid(10))
                .topK(1)
                .run();

        assertThat(report.failedEvaluations()).isEmpty();
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.parameters().intValue("period")).isEqualTo(3);
        assertThat(winner.trainingScore().doubleValue()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void dynamicTimeWarpingRejectsWindowBelowTwoBars() {
        assertThrows(IllegalArgumentException.class,
                () -> RelationshipObjectives.dynamicTimeWarpingDistance(
                        windowSeries -> new ClosePriceIndicator(windowSeries), 1,
                        DynamicTimeWarpingDistanceIndicator.Config.shapeComparison(2)));
    }

    @Test
    void eventMutualInformationRanksFiniteScores() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookback", 1, 3)
                .candidate((window, parameters) -> momentum(window.series(), parameters.intValue("lookback")))
                .maximize(RelationshipObjectives.eventMutualInformation(RelationshipObjectivesTest::nextBarUp,
                        new EventMutualInformationConfig(1, 1, 4, BinningStrategy.EQUAL_FREQUENCY), true))
                .search(SearchPlan.grid(10))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(3);
        assertThat(report.failedEvaluations()).isEmpty();
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.trainingScore().doubleValue()).isBetween(0.0, 1.0);
        assertThat(winner.trainingMetrics()).containsKeys("mutualInformationNats", "normalizedMutualInformation",
                "targetEntropyNats", "sampleCount", "positiveTargetRate");
    }

    @Test
    void eventMutualInformationOmitsUndefinedMetrics() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookback", 1, 2)
                .candidate((window, parameters) -> momentum(window.series(), parameters.intValue("lookback")))
                .maximize(RelationshipObjectives.eventMutualInformation(RelationshipObjectivesTest::alwaysTrue,
                        new EventMutualInformationConfig(1, 1, 4, BinningStrategy.EQUAL_FREQUENCY), false))
                .search(SearchPlan.grid(10))
                .run();

        assertThat(report.counts().attempted()).isEqualTo(2);
        assertThat(report.failedEvaluations()).isEmpty();
        RankedCandidate winner = report.trainingLeaderboard().getFirst();
        assertThat(winner.trainingScore().doubleValue()).isCloseTo(0.0, within(1e-9));
        assertThat(winner.trainingMetrics()).containsKeys("mutualInformationNats", "targetEntropyNats", "sampleCount",
                "positiveTargetRate");
        assertThat(winner.trainingMetrics()).doesNotContainKey("normalizedMutualInformation");
    }

    @Test
    void holdoutRebuildKeepsRelationshipObjectivesValid() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookback", 1, 4)
                .candidate((window, parameters) -> risingEvents(window.series(), parameters.intValue("lookback")))
                .maximize(RelationshipObjectives.eventSynchronizationF1(windowSeries -> risingEvents(windowSeries, 2),
                        12, 1))
                .search(SearchPlan.grid(10))
                .holdoutBarCount(15)
                .topK(2)
                .run();

        assertThat(report.trainingWindow().endIndex()).isEqualTo(44);
        assertThat(report.holdoutWindow()).isPresent();
        assertThat(report.holdoutWindow().orElseThrow().startIndex()).isEqualTo(45);
        assertThat(report.failedEvaluations()).isEmpty();
        assertThat(report.holdoutLeaderboard()).hasSize(2);
        for (RankedCandidate candidate : report.holdoutLeaderboard()) {
            assertThat(Num.isFinite(candidate.holdoutScore())).isTrue();
        }
    }

    @Test
    void eventlessWindowFailsFactually() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(60))
                .integer("lookback", 1, 2)
                .candidate((window, parameters) -> noEvents(window.series()))
                .maximize(RelationshipObjectives.eventSynchronizationF1(RelationshipObjectivesTest::noEvents, 12, 1))
                .search(SearchPlan.grid(10))
                .run();

        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
        assertThat(report.failedEvaluations()).hasSize(2);
        assertThat(report.failedEvaluations().getFirst().reason()).contains("no events");
    }

    @Test
    void leakingRelationshipObjectiveChangesScoreButProductionDoesNot() {
        BarSeries seriesA = waveSeriesWithTail(60, 0.2);
        BarSeries seriesB = waveSeriesWithTail(60, 3.0);

        ParameterResearchReport productionA = productionRun(seriesA);
        ParameterResearchReport productionB = productionRun(seriesB);
        assertThat(trainingScoresById(productionA)).isEqualTo(trainingScoresById(productionB));

        ParameterResearchReport leakingA = leakingRun(seriesA);
        ParameterResearchReport leakingB = leakingRun(seriesB);
        assertThat(trainingScoresById(leakingA)).isNotEqualTo(trainingScoresById(leakingB));
    }

    private static ParameterResearchReport productionRun(BarSeries series) {
        return ParameterResearch.builder(series)
                .integer("lookback", 1, 4)
                .candidate((window, parameters) -> risingEvents(window.series(), parameters.intValue("lookback")))
                .maximize(RelationshipObjectives.eventSynchronizationF1(windowSeries -> risingEvents(windowSeries, 2),
                        12, 1))
                .search(SearchPlan.grid(10))
                .holdoutBarCount(15)
                .topK(2)
                .run();
    }

    /**
     * Negative control: the candidate and the reference are deliberately built from
     * the full series (the reference reads five bars ahead of each bar), so
     * changing the holdout tail changes the training scores. The production
     * objective must never exhibit this leak.
     */
    private static ParameterResearchReport leakingRun(BarSeries series) {
        return ParameterResearch.builder(series)
                .integer("lookback", 1, 4)
                .candidate((window,
                        parameters) -> (Indicator<Boolean>) risingEvents(series, parameters.intValue("lookback")))
                .maximize(leakingF1(series))
                .search(SearchPlan.grid(10))
                .holdoutBarCount(15)
                .topK(2)
                .run();
    }

    private static ParameterResearch.ObjectiveFunction<Indicator<Boolean>> leakingF1(BarSeries fullSeries) {
        return (predicted, window) -> {
            Indicator<Boolean> leakingReference = futureRiseEvents(fullSeries, 5, 4.0);
            EventSynchronizationIndicator synchronization = new EventSynchronizationIndicator(predicted,
                    leakingReference, 12, 1);
            EventSynchronizationIndicator.Result result = synchronization.getResult(window.series().getEndIndex());
            return ObjectiveEvaluation.of(result.f1Score());
        };
    }

    private static Indicator<Boolean> futureRiseEvents(BarSeries series, int horizon, double threshold) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        Boolean[] events = new Boolean[series.getBarCount()];
        for (int i = 0; i < events.length; i++) {
            int index = begin + i;
            events[i] = index + horizon <= end
                    && close.getValue(index + horizon).minus(close.getValue(index)).doubleValue() > threshold;
        }
        return new FixedBooleanIndicator(series, events);
    }

    @Test
    void leadLagCorrelationRejectsWindowShorterThanTwoBars() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> RelationshipObjectives
                .leadLagCorrelation(windowSeries -> new ClosePriceIndicator(windowSeries), 1, 0, 0));
        assertThat(exception.getMessage()).contains("2");
    }

    @Test
    void leadLagCorrelationRequiresFullLagRange() {
        // Each lag is compared over an equally sized aligned window; trailing
        // lags shift that window into the past, so they warm up later. When the
        // training window is shorter than the worst lag's warm-up boundary the
        // profile contains undefined points and the objective must fail
        // instead of scoring on a truncated lag range.
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(6))
                .integer("shift", 1, 2)
                .candidate((window,
                        parameters) -> (Indicator<Num>) new PreviousValueIndicator(
                                new ClosePriceIndicator(window.series()), parameters.intValue("shift")))
                .maximize(RelationshipObjectives.leadLagCorrelation(
                        windowSeries -> new PreviousValueIndicator(new ClosePriceIndicator(windowSeries), 2), 20, -2,
                        2))
                .search(SearchPlan.grid(10))
                .run();

        assertThat(report.failedEvaluations()).hasSize(2);
        assertThat(report.trainingLeaderboard()).isEmpty();
        assertThat(report.terminationReason()).isEqualTo(TerminationReason.NO_VALID_CANDIDATES);
    }

    @Test
    void leadLagCorrelationExplainsOneBarFailure() {
        ParameterResearchReport report = ParameterResearch.builder(waveSeries(1))
                .integer("shift", 1, 1)
                .candidate((window,
                        parameters) -> (Indicator<Num>) new PreviousValueIndicator(
                                new ClosePriceIndicator(window.series()), parameters.intValue("shift")))
                .maximize(RelationshipObjectives.leadLagCorrelation(
                        windowSeries -> new PreviousValueIndicator(new ClosePriceIndicator(windowSeries), 1), 20, 0, 0))
                .search(SearchPlan.grid(10))
                .run();

        assertThat(report.failedEvaluations()).singleElement()
                .extracting(failed -> failed.reason())
                .asString()
                .contains("bars");
    }

    private static Map<String, Double> trainingScoresById(ParameterResearchReport report) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (RankedCandidate candidate : report.trainingLeaderboard()) {
            scores.put(candidate.candidateId(), candidate.trainingScore().doubleValue());
        }
        return scores;
    }
}
