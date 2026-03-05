/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class WalkForwardMetricsTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void probabilisticMetricsMatchExpectedValuesOnSimpleFixture() {
        List<WalkForwardObservation<String, Double>> rows = List.of(observation("fold-1", 10, 1, 0.9, 1.0),
                observation("fold-1", 11, 1, 0.1, 0.0), observation("fold-1", 12, 1, 0.8, 1.0),
                observation("fold-1", 13, 1, 0.2, 0.0));

        WalkForwardMetric<String, Double> brier = WalkForwardMetric.brierScore("brier", 1,
                value -> NUM_FACTORY.numOf(value));
        WalkForwardMetric<String, Double> logLoss = WalkForwardMetric.logLoss("logLoss", 1,
                value -> NUM_FACTORY.numOf(value));
        WalkForwardMetric<String, Double> ece = WalkForwardMetric.expectedCalibrationError("ece", 1, 4,
                value -> NUM_FACTORY.numOf(value));

        assertThat(brier.compute(rows).doubleValue()).isCloseTo(0.025, within(1.0e-9));
        assertThat(logLoss.compute(rows).doubleValue()).isCloseTo(0.164252033, within(1.0e-6));
        assertThat(ece.compute(rows).doubleValue()).isCloseTo(0.15, within(1.0e-9));
    }

    @Test
    void rankingMetricsAndAgreementWorkAcrossSnapshots() {
        List<WalkForwardObservation<String, Boolean>> rows = List.of(observation("fold-1", 1, 1, 0.7, true),
                observation("fold-1", 1, 2, 0.2, false), observation("fold-1", 2, 1, 0.6, false),
                observation("fold-1", 2, 2, 0.3, true));

        WalkForwardMetric<String, Boolean> top1Hit = WalkForwardMetric.topKHitRate("top1", 1,
                (prediction, outcome) -> outcome);
        WalkForwardMetric<String, Boolean> top2Hit = WalkForwardMetric.topKHitRate("top2", 2,
                (prediction, outcome) -> outcome);
        WalkForwardMetric<String, Boolean> ndcg = WalkForwardMetric.ndcg("ndcg", 2,
                (prediction, outcome) -> outcome ? NUM_FACTORY.one() : NUM_FACTORY.zero());
        WalkForwardMetric<String, Boolean> f1 = WalkForwardMetric.binaryF1("f1", 1,
                (prediction, outcome) -> prediction.probability().isGreaterThanOrEqual(NUM_FACTORY.numOf(0.5)),
                value -> value);
        WalkForwardMetric<String, Boolean> agreement = WalkForwardMetric.agreement("agreement", 1,
                (prediction, outcome) -> outcome);

        assertThat(top1Hit.compute(rows).doubleValue()).isEqualTo(0.5);
        assertThat(top2Hit.compute(rows).doubleValue()).isEqualTo(1.0);
        assertThat(ndcg.compute(rows).doubleValue()).isCloseTo(0.815464876, within(1.0e-6));
        assertThat(f1.compute(rows).doubleValue()).isCloseTo(0.666666666, within(1.0e-6));
        assertThat(agreement.compute(rows).doubleValue()).isEqualTo(0.5);
    }

    @Test
    void weightedObjectiveAppliesGuardrailsAndVariancePenalty() {
        WalkForwardObjective objective = WalkForwardObjective.weighted(
                Map.of("eventAgreement", NUM_FACTORY.one(), "brier", NUM_FACTORY.minusOne()),
                Map.of("eventAgreement", NUM_FACTORY.numOf(0.6)), Map.of("brier", NUM_FACTORY.numOf(0.3)),
                NUM_FACTORY.numOf(0.5));

        WalkForwardObjective.Score passing = objective.evaluate(
                Map.of("eventAgreement", NUM_FACTORY.numOf(0.8), "brier", NUM_FACTORY.numOf(0.1)),
                Map.of("fold-1", Map.of("eventAgreement", NUM_FACTORY.numOf(0.8), "brier", NUM_FACTORY.numOf(0.1)),
                        "fold-2", Map.of("eventAgreement", NUM_FACTORY.numOf(0.6), "brier", NUM_FACTORY.numOf(0.2))));
        assertThat(passing.guardrailPassed()).isTrue();
        assertThat(passing.totalScore()).isLessThan(passing.weightedScore());

        WalkForwardObjective.Score failing = objective.evaluate(
                Map.of("eventAgreement", NUM_FACTORY.numOf(0.2), "brier", NUM_FACTORY.numOf(0.4)),
                Map.of("fold-1", Map.of("eventAgreement", NUM_FACTORY.numOf(0.2), "brier", NUM_FACTORY.numOf(0.4))));
        assertThat(failing.guardrailPassed()).isFalse();
        assertThat(failing.totalScore().isNaN()).isTrue();
    }

    private static WalkForwardObservation<String, Double> observation(String foldId, int decisionIndex, int rank,
            double probability, double outcome) {
        RankedPrediction<String> prediction = new RankedPrediction<>(foldId + "-" + decisionIndex + "-" + rank, rank,
                NUM_FACTORY.numOf(probability), NUM_FACTORY.numOf(probability), "p" + rank);
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>(foldId, decisionIndex, List.of(prediction),
                Map.of());
        return new WalkForwardObservation<>(snapshot, prediction, outcome, 5);
    }

    private static WalkForwardObservation<String, Boolean> observation(String foldId, int decisionIndex, int rank,
            double probability, boolean outcome) {
        RankedPrediction<String> prediction = new RankedPrediction<>(foldId + "-" + decisionIndex + "-" + rank, rank,
                NUM_FACTORY.numOf(probability), NUM_FACTORY.numOf(probability), "p" + rank);
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>(foldId, decisionIndex, List.of(prediction),
                Map.of());
        return new WalkForwardObservation<>(snapshot, prediction, outcome, 5);
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
