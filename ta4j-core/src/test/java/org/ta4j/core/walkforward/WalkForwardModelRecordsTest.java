/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.walkforward.calibration.CalibrationSelection;

class WalkForwardModelRecordsTest {

    @Test
    void rankedPredictionValidatesAndSupportsProbabilityCopy() {
        RankedPrediction<String> prediction = new RankedPrediction<>("p-1", 1, 0.6, 0.7, "bull");
        RankedPrediction<String> copied = prediction.withProbability(0.8);

        assertThat(copied.probability()).isEqualTo(0.8);
        assertThat(copied.confidence()).isEqualTo(0.7);
        assertThat(copied.payload()).isEqualTo("bull");

        assertThrows(IllegalArgumentException.class, () -> new RankedPrediction<>("p-1", 0, 0.2, 0.4, "x"));
        assertThrows(IllegalArgumentException.class, () -> new RankedPrediction<>("p-1", 1, -0.1, 0.4, "x"));
        assertThrows(IllegalArgumentException.class, () -> new RankedPrediction<>("p-1", 1, 0.2, 1.1, "x"));
    }

    @Test
    void predictionSnapshotValidatesAndBuildsStableKey() {
        RankedPrediction<String> prediction = new RankedPrediction<>("p-1", 1, 0.5, 0.5, "payload");
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>("fold-1", 42, List.of(prediction),
                Map.of("source", "test"));

        assertThat(snapshot.snapshotKey()).isEqualTo("fold-1@42");
        assertThat(snapshot.topPredictions()).hasSize(1);
        assertThat(snapshot.metadata()).containsEntry("source", "test");

        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot<>(" ", 1, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PredictionSnapshot<>("fold-1", -1, List.of(), Map.of()));
    }

    @Test
    void observationValidatesHorizonAndExposesFoldId() {
        RankedPrediction<String> prediction = new RankedPrediction<>("p-1", 1, 0.6, 0.6, "payload");
        PredictionSnapshot<String> snapshot = new PredictionSnapshot<>("fold-a", 8, List.of(prediction), Map.of());
        WalkForwardObservation<String, Boolean> observation = new WalkForwardObservation<>(snapshot, prediction, true,
                5);

        assertThat(observation.foldId()).isEqualTo("fold-a");

        assertThrows(IllegalArgumentException.class, () -> new WalkForwardObservation<>(snapshot, prediction, true, 0));
    }

    @Test
    void splitValidatesBoundariesAndComputesCounts() {
        WalkForwardSplit split = new WalkForwardSplit("fold-1", 0, 19, 25, 34, 2, 3, false);
        assertThat(split.trainBarCount()).isEqualTo(20);
        assertThat(split.testBarCount()).isEqualTo(10);

        assertThrows(IllegalArgumentException.class, () -> new WalkForwardSplit(" ", 0, 1, 2, 3, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardSplit("x", -1, 1, 3, 4, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardSplit("x", 0, 1, 1, 4, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardSplit("x", 0, 1, 3, 2, 0, 0, false));
    }

    @Test
    void candidateAndManifestValidateIdentifiers() {
        WalkForwardCandidate<Double> candidate = new WalkForwardCandidate<>("c-1", 0.7);
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("dataset", "candidate", "abc123",
                42L, Map.of("mode", "test"));

        assertThat(candidate.id()).isEqualTo("c-1");
        assertThat(manifest.metadata()).containsEntry("mode", "test");

        assertThrows(IllegalArgumentException.class, () -> new WalkForwardCandidate<>(" ", 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> new WalkForwardExperimentManifest(" ", "candidate", "hash", 1L, Map.of()));
    }

    @Test
    void runtimeReportSupportsEmptyAndFoldValidation() {
        WalkForwardRuntimeReport empty = WalkForwardRuntimeReport.empty();
        assertThat(empty.overallRuntime()).isEqualTo(Duration.ZERO);
        assertThat(empty.foldRuntimes()).isEmpty();

        WalkForwardRuntimeReport.FoldRuntime foldRuntime = new WalkForwardRuntimeReport.FoldRuntime("fold-1",
                Duration.ofMillis(12), 3);
        assertThat(foldRuntime.snapshotCount()).isEqualTo(3);
        assertThrows(IllegalArgumentException.class,
                () -> new WalkForwardRuntimeReport.FoldRuntime("fold-1", Duration.ofMillis(1), -1));
    }

    @Test
    void runResultNormalizesCollectionsAndFindsHoldoutSplit() {
        WalkForwardConfig config = new WalkForwardConfig(20, 10, 10, 1, 1, 5, 3, List.of(2), 1, List.of(1), 99L);
        WalkForwardSplit training = new WalkForwardSplit("fold-1", 0, 29, 31, 40, 1, 1, false);
        WalkForwardSplit holdout = new WalkForwardSplit("holdout", 0, 39, 41, 45, 1, 1, true);
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("dataset", "candidate",
                config.configHash(), config.seed(), Map.of());

        WalkForwardRunResult<String, Boolean> result = new WalkForwardRunResult<>(config, List.of(training, holdout),
                null, null, null, null, null, WalkForwardRuntimeReport.empty(), manifest);

        assertThat(result.snapshots()).isEmpty();
        assertThat(result.observationsByHorizon()).isEmpty();
        assertThat(result.holdoutSplit()).contains(holdout);
    }

    @Test
    void leaderboardAndEntryValidateRequiredValues() {
        WalkForwardConfig config = WalkForwardConfig.defaultConfig();
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("dataset", "candidate",
                config.configHash(), config.seed(), Map.of());
        WalkForwardRunResult<String, Boolean> runResult = new WalkForwardRunResult<>(config, List.of(), List.of(),
                Map.of(), Map.of(), Map.of(), List.of(), WalkForwardRuntimeReport.empty(), manifest);
        WalkForwardCandidate<String> candidate = new WalkForwardCandidate<>("c-1", "ctx");
        WalkForwardObjective.Score score = new WalkForwardObjective.Score(1.0, 1.2, 0.1, true, List.of(), Map.of());

        WalkForwardLeaderboard.Entry<String> entry = new WalkForwardLeaderboard.Entry<>(candidate, score, Map.of(),
                CalibrationSelection.none(), runResult);
        WalkForwardLeaderboard<String> leaderboard = new WalkForwardLeaderboard<>(List.of(entry), 1, 1,
                config.primaryHorizonBars());

        assertThat(leaderboard.entries()).hasSize(1);
        assertThat(leaderboard.entries().get(0).candidate().id()).isEqualTo("c-1");

        assertThrows(IllegalArgumentException.class, () -> new WalkForwardLeaderboard<>(List.of(), -1, 0, 1));
        assertThrows(NullPointerException.class, () -> new WalkForwardLeaderboard.Entry<>(null, score, Map.of(),
                CalibrationSelection.none(), runResult));
    }
}
