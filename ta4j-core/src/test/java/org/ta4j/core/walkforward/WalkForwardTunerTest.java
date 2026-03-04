/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class WalkForwardTunerTest {

    @Test
    void tunerRanksCandidatesAndAppliesPlattCalibration() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(420)).build();
        NumFactory numFactory = series.numFactory();
        WalkForwardConfig config = new WalkForwardConfig(120, 60, 30, 2, 2, 0, 5, List.of(), 1, List.of(), 7L);

        PredictionProvider<Double, Double> provider = (fullSeries, decisionIndex, context) -> {
            double probability = Math.max(0.01, Math.min(0.99, context));
            return List.of(new RankedPrediction<>("top", 1, numFactory.numOf(probability),
                    numFactory.numOf(probability), probability));
        };
        OutcomeLabeler<Double, Boolean> labeler = (fullSeries, decisionIndex, horizonBars, prediction) -> fullSeries
                .getBar(decisionIndex + horizonBars)
                .getClosePrice()
                .isGreaterThan(fullSeries.getBar(decisionIndex).getClosePrice());

        List<WalkForwardMetric<Double, Boolean>> metrics = List.of(
                WalkForwardMetric.agreement("eventAgreement", 1, (prediction, outcome) -> outcome),
                WalkForwardMetric.brierScore("brier", 1, outcome -> outcome ? numFactory.one() : numFactory.zero()),
                WalkForwardMetric.expectedCalibrationError("ece", 1, 10,
                        outcome -> outcome ? numFactory.one() : numFactory.zero()));

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(), provider, labeler, metrics);

        WalkForwardObjective objective = WalkForwardObjective.weighted(Map.of("eventAgreement", numFactory.one(),
                "brier", numFactory.minusOne(), "ece", numFactory.minusOne()), Map.of(), Map.of(), numFactory.zero());

        WalkForwardTuner<Double, Double, Boolean> tuner = new WalkForwardTuner<>(engine, objective, 2, 1,
                WalkForwardTuner.CalibrationMode.PLATT, WalkForwardTuner.CalibrationGate.defaultGate(), 1,
                outcome -> outcome ? numFactory.one() : numFactory.zero());

        WalkForwardLeaderboard<Double> leaderboard = tuner.tune(series,
                List.of(new WalkForwardCandidate<>("c-1", 0.9), new WalkForwardCandidate<>("c-2", 0.6)), config);

        assertThat(leaderboard.entries()).hasSize(2);
        assertThat(leaderboard.entries().get(0).calibrationSelection().selected()).isEqualTo("platt");
        assertThat(leaderboard.entries().get(0).globalMetrics()).containsKeys("brier", "ece", "eventAgreement");
        assertThat(leaderboard.entries().get(0).objectiveScore().totalScore().isNaN()).isFalse();
    }

    @Test
    void isotonicChallengerRespectsMinimumSampleGate() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(240)).build();
        NumFactory numFactory = series.numFactory();
        WalkForwardConfig config = new WalkForwardConfig(100, 40, 40, 0, 0, 0, 4, List.of(), 1, List.of(), 42L);

        PredictionProvider<Double, Double> provider = (fullSeries, decisionIndex, context) -> List
                .of(new RankedPrediction<>("top", 1, numFactory.numOf(context), numFactory.numOf(context), context));
        OutcomeLabeler<Double, Boolean> labeler = (fullSeries, decisionIndex, horizonBars, prediction) -> true;

        List<WalkForwardMetric<Double, Boolean>> metrics = List.of(
                WalkForwardMetric.brierScore("brier", 1, outcome -> outcome ? numFactory.one() : numFactory.zero()),
                WalkForwardMetric.expectedCalibrationError("ece", 1, 10,
                        outcome -> outcome ? numFactory.one() : numFactory.zero()));

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(), provider, labeler, metrics);

        WalkForwardObjective objective = WalkForwardObjective.weighted(Map.of("brier", numFactory.minusOne()), Map.of(),
                Map.of(), numFactory.zero());

        WalkForwardTuner<Double, Double, Boolean> tuner = new WalkForwardTuner<>(engine, objective, 1, 1,
                WalkForwardTuner.CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER,
                new WalkForwardTuner.CalibrationGate(10000, numFactory.numOf(0.001), numFactory.numOf(0.1)), 1,
                outcome -> outcome ? numFactory.one() : numFactory.zero());

        WalkForwardLeaderboard<Double> leaderboard = tuner.tune(series, List.of(new WalkForwardCandidate<>("c-1", 0.7)),
                config);

        assertThat(leaderboard.entries()).hasSize(1);
        assertThat(leaderboard.entries().get(0).calibrationSelection().selected()).isEqualTo("platt");
    }

    @Test
    void holdoutValidatorProducesSignOffReport() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(320)).build();
        NumFactory numFactory = series.numFactory();
        WalkForwardConfig config = new WalkForwardConfig(120, 50, 50, 0, 0, 30, 4, List.of(), 1, List.of(), 42L);

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(),
                (fullSeries, decisionIndex, context) -> List
                        .of(new RankedPrediction<>("top", 1, numFactory.numOf(0.8), numFactory.numOf(0.8), 0.8)),
                (fullSeries, decisionIndex, horizonBars, prediction) -> true,
                List.of(WalkForwardMetric.agreement("eventAgreement", 1, (prediction, outcome) -> outcome)));

        WalkForwardRunResult<Double, Boolean> result = engine.run(series, 0.8, config, "candidate-a", Map.of());
        WalkForwardHoldoutValidator validator = new WalkForwardHoldoutValidator();
        WalkForwardHoldoutValidator.Report report = validator.validate(result, "candidate-a",
                Map.of("eventAgreement", numFactory.numOf(0.9)), Map.of());

        assertThat(report.candidateId()).isEqualTo("candidate-a");
        assertThat(report.horizonBars()).isEqualTo(config.primaryHorizonBars());
        assertThat(report.metricValues()).containsKey("eventAgreement");
        assertThat(report.passed()).isTrue();
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.2);
        }
        return prices;
    }
}
