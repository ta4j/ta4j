/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class WalkForwardMetricValidationTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void metricConstructorsValidateArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> WalkForwardMetric.agreement(" ", 1, (prediction, outcome) -> true));
        assertThrows(IllegalArgumentException.class,
                () -> WalkForwardMetric.binaryF1("f1", 0, (prediction, outcome) -> true, (Boolean value) -> value));
        assertThrows(IllegalArgumentException.class, () -> WalkForwardMetric.brierScore("brier", 0,
                (Boolean value) -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero()));
        assertThrows(IllegalArgumentException.class, () -> WalkForwardMetric.expectedCalibrationError("ece", 1, 0,
                (Boolean value) -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero()));
        assertThrows(IllegalArgumentException.class, () -> WalkForwardMetric.logLoss("logloss", 0,
                (Boolean value) -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> WalkForwardMetric.ndcg("ndcg", 0, (prediction, outcome) -> NUM_FACTORY.one()));
        assertThrows(IllegalArgumentException.class,
                () -> WalkForwardMetric.topKHitRate("topk", 0, (prediction, outcome) -> true));
    }

    @Test
    void metricHelpersClampAndGroupDeterministically() {
        RankedPrediction<String> p1 = new RankedPrediction<>("p1", 1, NUM_FACTORY.numOf(0.6), NUM_FACTORY.numOf(0.6),
                "A");
        RankedPrediction<String> p2 = new RankedPrediction<>("p2", 2, NUM_FACTORY.numOf(0.4), NUM_FACTORY.numOf(0.4),
                "B");
        PredictionSnapshot<String> s1 = new PredictionSnapshot<>("fold-1", 10, List.of(p1, p2), Map.of());

        WalkForwardObservation<String, Boolean> o1 = new WalkForwardObservation<>(s1, p1, true, 5);
        WalkForwardObservation<String, Boolean> o2 = new WalkForwardObservation<>(s1, p2, false, 5);

        Num clampedLow = WalkForwardMetric.normalizeAndClamp01(-1.0, NUM_FACTORY);
        Num clampedHigh = WalkForwardMetric.normalizeAndClamp01(2.0, NUM_FACTORY);
        Num clampedNaN = WalkForwardMetric.normalizeAndClamp01(Double.NaN, NUM_FACTORY);
        Num log2 = WalkForwardMetric.log2(NUM_FACTORY.numOf(8.0));
        assertThat(clampedLow.isZero()).isTrue();
        assertThat(clampedHigh).isEqualTo(NUM_FACTORY.one());
        assertThat(clampedNaN.isZero()).isTrue();
        assertThat(log2).isEqualTo(NUM_FACTORY.three());

        assertThat(WalkForwardMetric.groupBySnapshot(List.of(o1, o2))).containsOnlyKeys("fold-1@10");
        assertThat(WalkForwardMetric.groupBySnapshot(List.of(o1, o2)).get("fold-1@10")).containsExactly(o1, o2);
    }

    @Test
    void metricsReturnNaNWhenNoEligibleRowsExist() {
        RankedPrediction<String> rankTwo = new RankedPrediction<>("p2", 2, NUM_FACTORY.numOf(0.5),
                NUM_FACTORY.numOf(0.5), "payload");
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>("fold-1", 1, List.of(rankTwo), Map.of());
        WalkForwardObservation<String, Boolean> row = new WalkForwardObservation<>(snapshot, rankTwo, true, 5);
        List<WalkForwardObservation<String, Boolean>> rows = List.of(row);

        WalkForwardMetric<String, Boolean> brier = WalkForwardMetric.brierScore("brier", 1,
                value -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero());
        WalkForwardMetric<String, Boolean> logLoss = WalkForwardMetric.logLoss("logLoss", 1,
                value -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero());
        WalkForwardMetric<String, Boolean> ece = WalkForwardMetric.expectedCalibrationError("ece", 1, 5,
                value -> value ? NUM_FACTORY.one() : NUM_FACTORY.zero());
        WalkForwardMetric<String, Boolean> agreement = WalkForwardMetric.agreement("agreement", 1,
                (prediction, outcome) -> outcome);
        WalkForwardMetric<String, Boolean> top1 = WalkForwardMetric.topKHitRate("top1", 1,
                (prediction, outcome) -> outcome);
        WalkForwardMetric<String, Boolean> ndcg = WalkForwardMetric.ndcg("ndcg", 1,
                (prediction, outcome) -> outcome ? NUM_FACTORY.one() : NUM_FACTORY.zero());

        assertThat(brier.compute(rows).isNaN()).isTrue();
        assertThat(logLoss.compute(rows).isNaN()).isTrue();
        assertThat(ece.compute(rows).isNaN()).isTrue();
        assertThat(agreement.compute(rows).isNaN()).isTrue();
        assertThat(top1.compute(rows).isZero()).isTrue();
        assertThat(ndcg.compute(rows).isNaN()).isTrue();
    }
}
