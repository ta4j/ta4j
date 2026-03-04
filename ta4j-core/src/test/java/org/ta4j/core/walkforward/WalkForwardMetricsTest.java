/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.walkforward.metric.AgreementMetric;
import org.ta4j.core.walkforward.metric.BinaryF1Metric;
import org.ta4j.core.walkforward.metric.BrierScoreMetric;
import org.ta4j.core.walkforward.metric.ExpectedCalibrationErrorMetric;
import org.ta4j.core.walkforward.metric.LogLossMetric;
import org.ta4j.core.walkforward.metric.NdcgMetric;
import org.ta4j.core.walkforward.metric.TopKHitRateMetric;

class WalkForwardMetricsTest {

    @Test
    void probabilisticMetricsMatchExpectedValuesOnSimpleFixture() {
        List<WalkForwardObservation<String, Double>> rows = List.of(observation("fold-1", 10, 1, 0.9, 1.0),
                observation("fold-1", 11, 1, 0.1, 0.0), observation("fold-1", 12, 1, 0.8, 1.0),
                observation("fold-1", 13, 1, 0.2, 0.0));

        BrierScoreMetric<String, Double> brier = new BrierScoreMetric<>("brier", 1, value -> value);
        LogLossMetric<String, Double> logLoss = new LogLossMetric<>("logLoss", 1, value -> value);
        ExpectedCalibrationErrorMetric<String, Double> ece = new ExpectedCalibrationErrorMetric<>("ece", 1, 4,
                value -> value);

        assertThat(brier.compute(rows)).isCloseTo(0.025, within(1.0e-9));
        assertThat(logLoss.compute(rows)).isCloseTo(0.164252033, within(1.0e-6));
        assertThat(ece.compute(rows)).isCloseTo(0.15, within(1.0e-9));
    }

    @Test
    void rankingMetricsAndAgreementWorkAcrossSnapshots() {
        List<WalkForwardObservation<String, Boolean>> rows = List.of(observation("fold-1", 1, 1, 0.7, true),
                observation("fold-1", 1, 2, 0.2, false), observation("fold-1", 2, 1, 0.6, false),
                observation("fold-1", 2, 2, 0.3, true));

        TopKHitRateMetric<String, Boolean> top1Hit = new TopKHitRateMetric<>("top1", 1,
                (prediction, outcome) -> outcome);
        TopKHitRateMetric<String, Boolean> top2Hit = new TopKHitRateMetric<>("top2", 2,
                (prediction, outcome) -> outcome);
        NdcgMetric<String, Boolean> ndcg = new NdcgMetric<>("ndcg", 2, (prediction, outcome) -> outcome ? 1.0 : 0.0);
        BinaryF1Metric<String, Boolean> f1 = new BinaryF1Metric<>("f1", 1,
                (prediction, outcome) -> prediction.probability() >= 0.5, value -> value);
        AgreementMetric<String, Boolean> agreement = new AgreementMetric<>("agreement", 1,
                (prediction, outcome) -> outcome);

        assertThat(top1Hit.compute(rows)).isEqualTo(0.5);
        assertThat(top2Hit.compute(rows)).isEqualTo(1.0);
        assertThat(ndcg.compute(rows)).isCloseTo(0.815464876, within(1.0e-6));
        assertThat(f1.compute(rows)).isCloseTo(0.666666666, within(1.0e-6));
        assertThat(agreement.compute(rows)).isEqualTo(0.5);
    }

    @Test
    void weightedObjectiveAppliesGuardrailsAndVariancePenalty() {
        WeightedWalkForwardObjective objective = new WeightedWalkForwardObjective(
                Map.of("eventAgreement", 1.0, "brier", -1.0), Map.of("eventAgreement", 0.6), Map.of("brier", 0.3), 0.5);

        WalkForwardObjective.Score passing = objective.evaluate(Map.of("eventAgreement", 0.8, "brier", 0.1),
                Map.of("fold-1", Map.of("eventAgreement", 0.8, "brier", 0.1), "fold-2",
                        Map.of("eventAgreement", 0.6, "brier", 0.2)));
        assertThat(passing.guardrailPassed()).isTrue();
        assertThat(passing.totalScore()).isLessThan(passing.weightedScore());

        WalkForwardObjective.Score failing = objective.evaluate(Map.of("eventAgreement", 0.2, "brier", 0.4),
                Map.of("fold-1", Map.of("eventAgreement", 0.2, "brier", 0.4)));
        assertThat(failing.guardrailPassed()).isFalse();
        assertThat(failing.totalScore()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    private static WalkForwardObservation<String, Double> observation(String foldId, int decisionIndex, int rank,
            double probability, double outcome) {
        RankedPrediction<String> prediction = new RankedPrediction<>(foldId + "-" + decisionIndex + "-" + rank, rank,
                probability, probability, "p" + rank);
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>(foldId, decisionIndex, List.of(prediction),
                Map.of());
        return new WalkForwardObservation<>(snapshot, prediction, outcome, 5);
    }

    private static WalkForwardObservation<String, Boolean> observation(String foldId, int decisionIndex, int rank,
            double probability, boolean outcome) {
        RankedPrediction<String> prediction = new RankedPrediction<>(foldId + "-" + decisionIndex + "-" + rank, rank,
                probability, probability, "p" + rank);
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>(foldId, decisionIndex, List.of(prediction),
                Map.of());
        return new WalkForwardObservation<>(snapshot, prediction, outcome, 5);
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
