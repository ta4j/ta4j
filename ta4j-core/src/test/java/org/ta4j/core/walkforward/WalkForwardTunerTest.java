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
import org.ta4j.core.walkforward.calibration.CalibrationGate;
import org.ta4j.core.walkforward.calibration.CalibrationMode;
import org.ta4j.core.walkforward.metric.AgreementMetric;
import org.ta4j.core.walkforward.metric.BrierScoreMetric;
import org.ta4j.core.walkforward.metric.ExpectedCalibrationErrorMetric;

class WalkForwardTunerTest {

    @Test
    void tunerRanksCandidatesAndAppliesPlattCalibration() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(420)).build();
        WalkForwardConfig config = new WalkForwardConfig(120, 60, 30, 2, 2, 0, 5, List.of(), 1, List.of(), 7L);

        PredictionProvider<Double, Double> provider = (fullSeries, decisionIndex, context) -> {
            double p = Math.max(0.01, Math.min(0.99, context));
            return List.of(new RankedPrediction<>("top", 1, p, p, p));
        };
        OutcomeLabeler<Double, Boolean> labeler = (fullSeries, decisionIndex, horizonBars, prediction) -> fullSeries
                .getBar(decisionIndex + horizonBars)
                .getClosePrice()
                .isGreaterThan(fullSeries.getBar(decisionIndex).getClosePrice());

        List<WalkForwardMetric<Double, Boolean>> metrics = List.of(
                new AgreementMetric<>("eventAgreement", 1, (prediction, outcome) -> outcome),
                new BrierScoreMetric<>("brier", 1, outcome -> outcome ? 1.0 : 0.0),
                new ExpectedCalibrationErrorMetric<>("ece", 1, 10, outcome -> outcome ? 1.0 : 0.0));

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(), provider, labeler, metrics);

        WalkForwardObjective objective = new WeightedWalkForwardObjective(
                Map.of("eventAgreement", 1.0, "brier", -1.0, "ece", -1.0), Map.of(), Map.of(), 0.0);

        WalkForwardTuner<Double, Double, Boolean> tuner = new WalkForwardTuner<>(engine, objective, 2, 1,
                CalibrationMode.PLATT, CalibrationGate.defaultGate(), 1, outcome -> outcome ? 1.0 : 0.0);

        WalkForwardLeaderboard<Double> leaderboard = tuner.tune(series,
                List.of(new WalkForwardCandidate<>("c-1", 0.9), new WalkForwardCandidate<>("c-2", 0.6)), config);

        assertThat(leaderboard.entries()).hasSize(2);
        assertThat(leaderboard.entries().get(0).calibrationSelection().selected()).isEqualTo("platt");
        assertThat(leaderboard.entries().get(0).globalMetrics()).containsKeys("brier", "ece", "eventAgreement");
        assertThat(leaderboard.entries().get(0).objectiveScore().totalScore()).isFinite();
    }

    @Test
    void isotonicChallengerRespectsMinimumSampleGate() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(240)).build();
        WalkForwardConfig config = new WalkForwardConfig(100, 40, 40, 0, 0, 0, 4, List.of(), 1, List.of(), 42L);

        PredictionProvider<Double, Double> provider = (fullSeries, decisionIndex, context) -> List
                .of(new RankedPrediction<>("top", 1, context, context, context));
        OutcomeLabeler<Double, Boolean> labeler = (fullSeries, decisionIndex, horizonBars, prediction) -> true;

        List<WalkForwardMetric<Double, Boolean>> metrics = List.of(
                new BrierScoreMetric<>("brier", 1, outcome -> outcome ? 1.0 : 0.0),
                new ExpectedCalibrationErrorMetric<>("ece", 1, 10, outcome -> outcome ? 1.0 : 0.0));

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(), provider, labeler, metrics);

        WalkForwardObjective objective = new WeightedWalkForwardObjective(Map.of("brier", -1.0), Map.of(), Map.of(),
                0.0);

        WalkForwardTuner<Double, Double, Boolean> tuner = new WalkForwardTuner<>(engine, objective, 1, 1,
                CalibrationMode.PLATT_WITH_ISOTONIC_CHALLENGER, new CalibrationGate(10000, 0.001, 0.1), 1,
                outcome -> outcome ? 1.0 : 0.0);

        WalkForwardLeaderboard<Double> leaderboard = tuner.tune(series, List.of(new WalkForwardCandidate<>("c-1", 0.7)),
                config);

        assertThat(leaderboard.entries()).hasSize(1);
        assertThat(leaderboard.entries().get(0).calibrationSelection().selected()).isEqualTo("platt");
    }

    @Test
    void holdoutValidatorProducesSignOffReport() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(320)).build();
        WalkForwardConfig config = new WalkForwardConfig(120, 50, 50, 0, 0, 30, 4, List.of(), 1, List.of(), 42L);

        WalkForwardEngine<Double, Double, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(),
                (fullSeries, decisionIndex, context) -> List.of(new RankedPrediction<>("top", 1, 0.8, 0.8, 0.8)),
                (fullSeries, decisionIndex, horizonBars, prediction) -> true,
                List.of(new AgreementMetric<>("eventAgreement", 1, (prediction, outcome) -> outcome)));

        WalkForwardRunResult<Double, Boolean> result = engine.run(series, 0.8, config, "candidate-a", Map.of());
        WalkForwardHoldoutValidator validator = new WalkForwardHoldoutValidator();
        WalkForwardHoldoutValidator.Report report = validator.validate(result, "candidate-a",
                Map.of("eventAgreement", 0.9), Map.of());

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
