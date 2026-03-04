/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class WalkForwardMetricValidationTest {

    @Test
    void metricConstructorsValidateArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgreementMetric<String, Boolean>(" ", 1, (prediction, outcome) -> true));
        assertThrows(IllegalArgumentException.class,
                () -> new BinaryF1Metric<String, Boolean>("f1", 0, (prediction, outcome) -> true, value -> value));
        assertThrows(IllegalArgumentException.class,
                () -> new BrierScoreMetric<String, Boolean>("brier", 0, value -> value ? 1.0 : 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpectedCalibrationErrorMetric<String, Boolean>("ece", 1, 0, value -> value ? 1.0 : 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new LogLossMetric<String, Boolean>("logloss", 0, value -> value ? 1.0 : 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new NdcgMetric<String, Boolean>("ndcg", 0, (prediction, outcome) -> 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TopKHitRateMetric<String, Boolean>("topk", 0, (prediction, outcome) -> true));
    }

    @Test
    void metricHelpersClampAndGroupDeterministically() {
        RankedPrediction<String> p1 = new RankedPrediction<>("p1", 1, 0.6, 0.6, "A");
        RankedPrediction<String> p2 = new RankedPrediction<>("p2", 2, 0.4, 0.4, "B");
        PredictionSnapshot<String> s1 = new PredictionSnapshot<>("fold-1", 10, List.of(p1, p2), Map.of());

        WalkForwardObservation<String, Boolean> o1 = new WalkForwardObservation<>(s1, p1, true, 5);
        WalkForwardObservation<String, Boolean> o2 = new WalkForwardObservation<>(s1, p2, false, 5);

        assertThat(WalkForwardMetric.clamp01(-1.0)).isZero();
        assertThat(WalkForwardMetric.clamp01(2.0)).isEqualTo(1.0);
        assertThat(WalkForwardMetric.clamp01(Double.NaN)).isZero();
        assertThat(WalkForwardMetric.log2(8.0)).isEqualTo(3.0);

        assertThat(WalkForwardMetric.groupBySnapshot(List.of(o1, o2))).containsOnlyKeys("fold-1@10");
        assertThat(WalkForwardMetric.groupBySnapshot(List.of(o1, o2)).get("fold-1@10")).containsExactly(o1, o2);
    }

    @Test
    void metricsReturnNaNWhenNoEligibleRowsExist() {
        RankedPrediction<String> rankTwo = new RankedPrediction<>("p2", 2, 0.5, 0.5, "payload");
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>("fold-1", 1, List.of(rankTwo), Map.of());
        WalkForwardObservation<String, Boolean> row = new WalkForwardObservation<>(snapshot, rankTwo, true, 5);
        List<WalkForwardObservation<String, Boolean>> rows = List.of(row);

        BrierScoreMetric<String, Boolean> brier = new BrierScoreMetric<>("brier", 1, value -> value ? 1.0 : 0.0);
        LogLossMetric<String, Boolean> logLoss = new LogLossMetric<>("logLoss", 1, value -> value ? 1.0 : 0.0);
        ExpectedCalibrationErrorMetric<String, Boolean> ece = new ExpectedCalibrationErrorMetric<>("ece", 1, 5,
                value -> value ? 1.0 : 0.0);
        AgreementMetric<String, Boolean> agreement = new AgreementMetric<>("agreement", 1,
                (prediction, outcome) -> outcome);
        TopKHitRateMetric<String, Boolean> top1 = new TopKHitRateMetric<>("top1", 1, (prediction, outcome) -> outcome);
        NdcgMetric<String, Boolean> ndcg = new NdcgMetric<>("ndcg", 1, (prediction, outcome) -> outcome ? 1.0 : 0.0);

        assertThat(brier.compute(rows)).isNaN();
        assertThat(logLoss.compute(rows)).isNaN();
        assertThat(ece.compute(rows)).isNaN();
        assertThat(agreement.compute(rows)).isNaN();
        assertThat(top1.compute(rows)).isEqualTo(0.0);
        assertThat(ndcg.compute(rows)).isNaN();
    }
}
