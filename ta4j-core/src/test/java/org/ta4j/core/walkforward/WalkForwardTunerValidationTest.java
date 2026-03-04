/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.walkforward.calibration.CalibrationGate;
import org.ta4j.core.walkforward.calibration.CalibrationMode;
import org.ta4j.core.walkforward.metric.AgreementMetric;

class WalkForwardTunerValidationTest {

    @Test
    void constructorRejectsInvalidParameters() {
        WalkForwardEngine<String, String, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(),
                (fullSeries, decisionIndex, context) -> List.of(new RankedPrediction<>("p", 1, 0.5, 0.5, "payload")),
                (fullSeries, decisionIndex, horizonBars, prediction) -> true,
                List.of(new AgreementMetric<>("agreement", 1, (prediction, outcome) -> outcome)));
        WalkForwardObjective objective = new WeightedWalkForwardObjective(Map.of("agreement", 1.0), Map.of(), Map.of(),
                0.0);

        assertThrows(IllegalArgumentException.class, () -> new WalkForwardTuner<>(engine, objective, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardTuner<>(engine, objective, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardTuner<>(engine, objective, 1, 1,
                CalibrationMode.NONE, CalibrationGate.defaultGate(), 0, outcome -> 1.0));
    }

    @Test
    void tuneReturnsEmptyLeaderboardForEmptyCandidateSet() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(180)).build();
        WalkForwardConfig config = new WalkForwardConfig(80, 30, 30, 0, 0, 0, 5, List.of(), 1, List.of(), 1L);
        WalkForwardEngine<String, String, Boolean> engine = new WalkForwardEngine<>(
                new AnchoredExpandingWalkForwardSplitter(),
                (fullSeries, decisionIndex, context) -> List.of(new RankedPrediction<>("p", 1, 0.5, 0.5, "payload")),
                (fullSeries, decisionIndex, horizonBars, prediction) -> true,
                List.of(new AgreementMetric<>("agreement", 1, (prediction, outcome) -> outcome)));
        WalkForwardObjective objective = new WeightedWalkForwardObjective(Map.of("agreement", 1.0), Map.of(), Map.of(),
                0.0);
        WalkForwardTuner<String, String, Boolean> tuner = new WalkForwardTuner<>(engine, objective, 3, 2);

        WalkForwardLeaderboard<String> leaderboard = tuner.tune(series, List.of(), config);

        assertThat(leaderboard.entries()).isEmpty();
        assertThat(leaderboard.evaluatedCount()).isZero();
        assertThat(leaderboard.keptCount()).isZero();
        assertThat(leaderboard.primaryHorizonBars()).isEqualTo(config.primaryHorizonBars());
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.1);
        }
        return prices;
    }
}
