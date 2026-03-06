/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveOutcome;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.walkforward.WalkForwardExperimentManifest;
import org.ta4j.core.walkforward.PredictionSnapshot;
import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardRunResult;
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

class ElliottWaveAnchorCalibrationHarnessTest {

    @Test
    void defaultBitcoinAnchorsLoadsResolvedVersionedRegistry() {
        BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveAnchorCalibrationHarnessTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                org.apache.logging.log4j.LogManager.getLogger(ElliottWaveAnchorCalibrationHarnessTest.class));

        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);

        assertEquals("btc-cycle-anchors-v1", registry.version());
        assertEquals(ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, registry.datasetResource());
        assertTrue(registry.provenance().contains("widely cited BTC turning points"));
        assertEquals(12, registry.anchors().size());
        assertEquals(3,
                registry.anchors()
                        .stream()
                        .filter(anchor -> anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT)
                        .count());
        registry.anchors().forEach(anchor -> {
            assertFalse(anchor.provenance().isBlank());
            assertFalse(anchor.toleranceBefore().isNegative());
            assertFalse(anchor.toleranceAfter().isNegative());
            assertFalse(anchor.expectedPhases().isEmpty());
        });
    }

    @Test
    void evaluatePromotionGatesPassesWhenChallengerClearsEveryThreshold() {
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation baseline = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.baselineProfile(), 0.20, 0.40, 0.26, 0.48, 0.20,
                0.50, 0.30, 0.08);
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation challenger = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.of("minute-f3",
                        org.ta4j.core.indicators.elliott.ElliottDegree.MINUTE, 2, 2, 25, 0, 3, "tighter turns"),
                0.36, 0.60, 0.34, 0.60, 0.18, 0.45, 0.26, 0.05);

        ElliottWaveAnchorCalibrationHarness.GateEvaluation gates = ElliottWaveAnchorCalibrationHarness
                .evaluatePromotionGates(baseline, challenger);
        ElliottWaveAnchorCalibrationHarness.PromotionDecision decision = ElliottWaveAnchorCalibrationHarness.PromotionDecision
                .from(baseline,
                        List.of(new ElliottWaveAnchorCalibrationHarness.ChallengerAssessment(challenger, gates)));

        assertTrue(gates.passed());
        assertTrue(gates.notes().isEmpty());
        assertTrue(decision.promoteChallenger());
        assertEquals(challenger.profile().id(), decision.selectedProfileId());
    }

    @Test
    void evaluatePromotionGatesRetainsBaselineWhenChallengerMissesHoldoutAndCalibrationRules() {
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation baseline = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.baselineProfile(), 0.20, 0.40, 0.26, 0.48, 0.20,
                0.50, 0.30, 0.08);
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation challenger = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.of("minute-f2-tight",
                        org.ta4j.core.indicators.elliott.ElliottDegree.MINUTE, 1, 1, 15, 1, 2, "narrower context"),
                0.25, 0.44, 0.21, 0.30, 0.24, 0.56, 0.34, 0.14);

        ElliottWaveAnchorCalibrationHarness.GateEvaluation gates = ElliottWaveAnchorCalibrationHarness
                .evaluatePromotionGates(baseline, challenger);
        ElliottWaveAnchorCalibrationHarness.PromotionDecision decision = ElliottWaveAnchorCalibrationHarness.PromotionDecision
                .from(baseline,
                        List.of(new ElliottWaveAnchorCalibrationHarness.ChallengerAssessment(challenger, gates)));

        assertFalse(gates.passed());
        assertFalse(gates.notes().isEmpty());
        assertFalse(decision.promoteChallenger());
        assertEquals(baseline.profile().id(), decision.selectedProfileId());
        assertFalse(decision.followOnHypotheses().isEmpty());
    }

    @Test
    void summarizeAnchorsUsesToleranceWindowsAndRankDistribution() {
        BarSeries series = syntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "synthetic-v1", "synthetic-btc.json", "synthetic provenance",
                List.of(anchor("validation-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        series.getBar(2).getEndTime(), Duration.ofHours(4), Duration.ofHours(4),
                        Set.of(ElliottPhase.WAVE5), ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION,
                        "validation top"),
                        anchor("holdout-bottom", ElliottWaveAnchorCalibrationHarness.AnchorType.BOTTOM,
                                series.getBar(4).getEndTime(), Duration.ofHours(4), Duration.ofHours(4),
                                Set.of(ElliottPhase.CORRECTIVE_C), ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT,
                                "holdout bottom")));

        ElliottWaveAnchorCalibrationHarness.AnchorPartitions partitions = ElliottWaveAnchorCalibrationHarness
                .summarizeAnchors(series, registry, syntheticRunResult(series));

        assertEquals(1, partitions.validation().anchorCount());
        assertEquals(1, partitions.validation().sampleCount());
        assertEquals(1.0, partitions.validation().top1HitRate(), 1.0e-10);
        assertEquals(1.0, partitions.validation().top3HitRate(), 1.0e-10);
        assertEquals(1, partitions.validation().bestRankDistribution().get("rank1"));

        assertEquals(1, partitions.holdout().anchorCount());
        assertEquals(2, partitions.holdout().sampleCount());
        assertEquals(0.0, partitions.holdout().top1HitRate(), 1.0e-10);
        assertEquals(0.5, partitions.holdout().top3HitRate(), 1.0e-10);
        assertEquals(1, partitions.holdout().bestRankDistribution().get("rank2"));
        assertEquals(1, partitions.holdout().bestRankDistribution().get("unmatched"));
        assertEquals(0.5, partitions.validationToHoldoutTop3Degradation(), 1.0e-10);

        ElliottWaveAnchorCalibrationHarness.AnchorWindowSummary validationWindow = partitions.validation()
                .anchorWindows()
                .getFirst();
        ElliottWaveAnchorCalibrationHarness.AnchorWindowSummary holdoutWindow = partitions.holdout()
                .anchorWindows()
                .getFirst();
        assertEquals(1, validationWindow.windowStartIndex());
        assertEquals(3, validationWindow.windowEndIndex());
        assertEquals(3, holdoutWindow.windowStartIndex());
        assertEquals(5, holdoutWindow.windowEndIndex());
    }

    @Test
    void summarizeAnchorsUsesBarEndTimeToResolveAnchorIndex() {
        BarSeries series = syntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "synthetic-v1", "synthetic-btc.json", "synthetic provenance",
                List.of(anchor("validation-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        series.getBar(2).getEndTime(), Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                        ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION, "validation top")));
        WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult = new WalkForwardRunResult<>(
                new WalkForwardConfig(2, 1, 1, 0, 0, 1, 1, List.of(2), 3, List.of(1), 42L),
                List.of(new WalkForwardSplit("validation-fold", 0, 1, 2, 2, 0, 0, false)),
                List.of(snapshot("validation-fold", 2,
                        rankedPrediction(series, "validation-top", 1, ElliottPhase.WAVE5, true))),
                Map.of(), Map.of(), Map.of(), List.of(), WalkForwardRuntimeReport.empty(),
                new WalkForwardExperimentManifest("synthetic-btc", "candidate", "cfg-hash", 42L,
                        Map.of("profile", "synthetic")));

        ElliottWaveAnchorCalibrationHarness.AnchorPartitions partitions = ElliottWaveAnchorCalibrationHarness
                .summarizeAnchors(series, registry, runResult);

        assertEquals(1, partitions.validation().sampleCount());
        assertEquals(1.0, partitions.validation().top1HitRate(), 1.0e-10);
        assertEquals(0, partitions.holdout().sampleCount());
    }

    @Test
    void summarizeAnchorsIgnoresSnapshotsFromTheWrongPartition() {
        BarSeries series = syntheticSeries();
        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "synthetic-v1", "synthetic-btc.json", "synthetic provenance",
                List.of(anchor("holdout-top", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        series.getBar(2).getEndTime(), Duration.ZERO, Duration.ZERO, Set.of(ElliottPhase.WAVE5),
                        ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT, "holdout top")));
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("synthetic-btc", "candidate",
                "cfg-hash", 42L, Map.of("profile", "synthetic"));
        WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult = new WalkForwardRunResult<>(
                new WalkForwardConfig(2, 1, 1, 0, 0, 1, 1, List.of(2), 3, List.of(1), 42L),
                List.of(new WalkForwardSplit("validation-fold", 0, 1, 2, 2, 0, 0, false),
                        new WalkForwardSplit("holdout", 0, 1, 2, 2, 0, 0, true)),
                List.of(snapshot("validation-fold", 2,
                        rankedPrediction(series, "validation-top", 1, ElliottPhase.WAVE5, true)),
                        snapshot("holdout", 2, rankedPrediction(series, "holdout-top", 1, ElliottPhase.WAVE5, true))),
                Map.of(), Map.of(), Map.of(), List.of(), WalkForwardRuntimeReport.empty(), manifest);

        ElliottWaveAnchorCalibrationHarness.AnchorPartitions partitions = ElliottWaveAnchorCalibrationHarness
                .summarizeAnchors(series, registry, runResult);

        assertEquals(0, partitions.validation().anchorCount());
        assertEquals(0, partitions.validation().sampleCount());
        assertEquals(1, partitions.holdout().anchorCount());
        assertEquals(1, partitions.holdout().sampleCount());
        assertEquals(1.0, partitions.holdout().top1HitRate(), 1.0e-10);
    }

    @Test
    void rankChallengersKeepsPassingCandidateAheadOfFailingButHigherGainCandidate() {
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation baseline = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.baselineProfile(), 0.20, 0.40, 0.26, 0.48, 0.20,
                0.50, 0.30, 0.08);
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation passing = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.of("minute-f3-pass",
                        org.ta4j.core.indicators.elliott.ElliottDegree.MINUTE, 2, 2, 25, 0, 3, "tighter turns"),
                0.36, 0.60, 0.34, 0.60, 0.18, 0.45, 0.26, 0.05);
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation failing = evaluation(
                ElliottWaveAnchorCalibrationHarness.CandidateProfile.of("minute-f3-fail",
                        org.ta4j.core.indicators.elliott.ElliottDegree.MINUTE, 2, 2, 25, 0, 4, "overfit turns"),
                0.42, 0.72, 0.44, 0.80, 0.32, 0.70, 0.44, 0.04);

        List<ElliottWaveAnchorCalibrationHarness.ChallengerAssessment> ranked = ElliottWaveAnchorCalibrationHarness
                .rankChallengers(baseline, List.of(baseline, failing, passing));

        assertEquals(List.of("minute-f3-pass", "minute-f3-fail"),
                ranked.stream().map(assessment -> assessment.evaluation().profile().id()).toList());
        assertTrue(ranked.getFirst().gates().passed());
        assertFalse(ranked.get(1).gates().passed());
    }

    @Test
    void metricSnapshotUsesActualHoldoutFoldIdInsteadOfLiteralName() {
        int horizon = 2;
        Map<String, Num> globalMetrics = Map.of("rank1Brier", DoubleNumFactory.getInstance().numOf(0.22));
        Map<String, Num> validationFoldMetrics = Map.of("rank1Brier", DoubleNumFactory.getInstance().numOf(0.18));
        Map<String, Num> holdoutFoldMetrics = Map.of("rank1Brier", DoubleNumFactory.getInstance().numOf(0.31),
                "rank1LogLoss", DoubleNumFactory.getInstance().numOf(0.48));
        WalkForwardRunResult<String, Boolean> runResult = new WalkForwardRunResult<>(
                new WalkForwardConfig(2, 1, 1, 0, 0, 1, 1, List.of(horizon), 3, List.of(1), 42L),
                List.of(new WalkForwardSplit("validation-fold", 0, 1, 2, 2, 0, 0, false),
                        new WalkForwardSplit("final-holdout", 0, 1, 2, 2, 0, 0, true)),
                List.of(), Map.of(), Map.of(horizon, globalMetrics),
                Map.of(horizon, Map.of("validation-fold", validationFoldMetrics, "final-holdout", holdoutFoldMetrics)),
                List.of(), WalkForwardRuntimeReport.empty(), new WalkForwardExperimentManifest("synthetic-btc",
                        "candidate", "cfg-hash", 42L, Map.of("profile", "synthetic")));

        ElliottWaveAnchorCalibrationHarness.MetricSnapshot snapshot = ElliottWaveAnchorCalibrationHarness.MetricSnapshot
                .from(runResult, horizon);

        assertEquals(0.22, snapshot.global().get("rank1Brier"), 1.0e-10);
        assertEquals(0.18, snapshot.validation().get("rank1Brier"), 1.0e-10);
        assertEquals(0.31, snapshot.holdout().get("rank1Brier"), 1.0e-10);
        assertEquals(0.48, snapshot.holdout().get("rank1LogLoss"), 1.0e-10);
    }

    @Test
    void candidateAndReportHashesStayDeterministicForEquivalentInputs() {
        ElliottWaveAnchorCalibrationHarness.CandidateProfile profile = ElliottWaveAnchorCalibrationHarness.CandidateProfile
                .of("minute-f3", ElliottDegree.MINUTE, 2, 2, 25, 0, 3, "tighter turns");
        int horizon = org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles.baselineConfig()
                .primaryHorizonBars();
        LinkedHashMap<String, String> manifestMetadataA = new LinkedHashMap<>();
        manifestMetadataA.put("scenarioSwingWindow", "0");
        manifestMetadataA.put("profile", profile.id());
        LinkedHashMap<String, String> manifestMetadataB = new LinkedHashMap<>();
        manifestMetadataB.put("profile", profile.id());
        manifestMetadataB.put("scenarioSwingWindow", "0");
        WalkForwardExperimentManifest manifestA = new WalkForwardExperimentManifest("btc", profile.id(), "cfg-hash",
                42L, manifestMetadataA);
        WalkForwardExperimentManifest manifestB = new WalkForwardExperimentManifest("btc", profile.id(), "cfg-hash",
                42L, manifestMetadataB);
        ElliottWaveAnchorCalibrationHarness.MetricSnapshot primaryMetricsA = new ElliottWaveAnchorCalibrationHarness.MetricSnapshot(
                orderedDoubleMap("rank1LogLoss", 0.45, "rank1Brier", 0.18),
                orderedDoubleMap("rank1Brier", 0.17, "rank1Ece", 0.23),
                orderedDoubleMap("rank1Ece", 0.24, "rank1Brier", 0.16, "rank1LogLoss", 0.45));
        ElliottWaveAnchorCalibrationHarness.MetricSnapshot primaryMetricsB = new ElliottWaveAnchorCalibrationHarness.MetricSnapshot(
                orderedDoubleMap("rank1Brier", 0.18, "rank1LogLoss", 0.45),
                orderedDoubleMap("rank1Ece", 0.23, "rank1Brier", 0.17),
                orderedDoubleMap("rank1LogLoss", 0.45, "rank1Brier", 0.16, "rank1Ece", 0.24));
        ElliottWaveAnchorCalibrationHarness.MetricSnapshot secondaryMetricsA = new ElliottWaveAnchorCalibrationHarness.MetricSnapshot(
                orderedDoubleMap("rank1Brier", 0.21), orderedDoubleMap("rank1Brier", 0.20),
                orderedDoubleMap("rank1Brier", 0.19));
        ElliottWaveAnchorCalibrationHarness.MetricSnapshot secondaryMetricsB = new ElliottWaveAnchorCalibrationHarness.MetricSnapshot(
                orderedDoubleMap("rank1Brier", 0.21), orderedDoubleMap("rank1Brier", 0.20),
                orderedDoubleMap("rank1Brier", 0.19));
        ElliottWaveAnchorCalibrationHarness.AnchorPartitions anchors = new ElliottWaveAnchorCalibrationHarness.AnchorPartitions(
                new ElliottWaveAnchorCalibrationHarness.AnchorAggregate(2, 2, 0.5, 1.0,
                        orderedIntMap("rank3", 0, "rank1", 1, "unmatched", 0, "rank4plus", 0, "rank2", 1), List.of()),
                new ElliottWaveAnchorCalibrationHarness.AnchorAggregate(1, 1, 1.0, 1.0,
                        orderedIntMap("unmatched", 0, "rank2", 0, "rank1", 1, "rank4plus", 0, "rank3", 0), List.of()),
                0.0);
        Map<Integer, ElliottWaveAnchorCalibrationHarness.MetricSnapshot> metricsByHorizonA = new LinkedHashMap<>();
        metricsByHorizonA.put(horizon + 30, secondaryMetricsA);
        metricsByHorizonA.put(horizon, primaryMetricsA);
        Map<Integer, ElliottWaveAnchorCalibrationHarness.MetricSnapshot> metricsByHorizonB = new LinkedHashMap<>();
        metricsByHorizonB.put(horizon, primaryMetricsB);
        metricsByHorizonB.put(horizon + 30, secondaryMetricsB);

        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation first = ElliottWaveAnchorCalibrationHarness.CandidateEvaluation
                .create(profile, manifestA, metricsByHorizonA, anchors, horizon);
        ElliottWaveAnchorCalibrationHarness.CandidateEvaluation second = ElliottWaveAnchorCalibrationHarness.CandidateEvaluation
                .create(profile, manifestB, metricsByHorizonB, anchors, horizon);

        assertEquals(first.artifactId(), second.artifactId());
        assertEquals(first.artifactHash(), second.artifactHash());

        ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry = new ElliottWaveAnchorCalibrationHarness.AnchorRegistry(
                "btc-cycle-anchors-v1", ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, "deterministic provenance",
                List.of(anchor("btc-anchor", ElliottWaveAnchorCalibrationHarness.AnchorType.TOP,
                        Instant.parse("2024-03-14T00:00:00Z"), Duration.ofHours(4), Duration.ofHours(4),
                        Set.of(ElliottPhase.WAVE5), ElliottWaveAnchorRegistry.AnchorPartition.VALIDATION,
                        "deterministic anchor")));
        ElliottWaveAnchorCalibrationHarness.BaselinePolicy baselinePolicy = new ElliottWaveAnchorCalibrationHarness.BaselinePolicy(
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, horizon,
                org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles.baselineConfig()
                        .reportingHorizons(),
                manifestA.configHash(), profile.id());
        ElliottWaveAnchorCalibrationHarness.PromotionDecision decision = ElliottWaveAnchorCalibrationHarness.PromotionDecision
                .from(first, List.of());

        ElliottWaveAnchorCalibrationHarness.ReportBundle reportA = ElliottWaveAnchorCalibrationHarness.ReportBundle
                .create("btc-anchor-calibration-v1", Instant.parse("2025-10-28T00:00:00Z"), registry, baselinePolicy,
                        first, List.of(), decision,
                        List.of(ElliottWaveAnchorCalibrationHarness.PortabilitySummary.skipped("eth-usd",
                                ElliottWaveAnchorCalibrationHarness.ETH_RESOURCE, "synthetic test")));
        ElliottWaveAnchorCalibrationHarness.ReportBundle reportB = ElliottWaveAnchorCalibrationHarness.ReportBundle
                .create("btc-anchor-calibration-v1", Instant.parse("2025-10-28T00:00:00Z"), registry, baselinePolicy,
                        second, List.of(), decision,
                        List.of(ElliottWaveAnchorCalibrationHarness.PortabilitySummary.skipped("eth-usd",
                                ElliottWaveAnchorCalibrationHarness.ETH_RESOURCE, "synthetic test")));

        assertEquals(manifestA.configHash(), reportA.baselinePolicy().configHash());
        assertEquals(reportA.reportHash(), reportB.reportHash());
        assertEquals(reportA.toJson(), reportB.toJson());
    }

    @Test
    void orderedMapHelpersRejectOddEntryCounts() {
        IllegalArgumentException doubleEntries = assertThrows(IllegalArgumentException.class,
                () -> orderedDoubleMap("rank1Brier", 0.21, "rank1LogLoss"));
        IllegalArgumentException intEntries = assertThrows(IllegalArgumentException.class,
                () -> orderedIntMap("rank1", 1, "rank2"));

        assertEquals("entries must contain complete key/value pairs", doubleEntries.getMessage());
        assertEquals("entries must contain complete key/value pairs", intEntries.getMessage());
    }

    private static ElliottWaveAnchorCalibrationHarness.CandidateEvaluation evaluation(
            ElliottWaveAnchorCalibrationHarness.CandidateProfile profile, double validationTop1, double validationTop3,
            double holdoutTop1, double holdoutTop3, double holdoutBrier, double holdoutLogLoss, double holdoutEce,
            double top3Degradation) {
        int horizon = org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles.baselineConfig()
                .primaryHorizonBars();
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("btc", profile.id(), "cfg-hash", 42L,
                Map.of("profile", profile.id()));
        Map<Integer, ElliottWaveAnchorCalibrationHarness.MetricSnapshot> metrics = Map.of(horizon,
                new ElliottWaveAnchorCalibrationHarness.MetricSnapshot(Map.of(),
                        Map.of("rank1Brier", holdoutBrier, "rank1LogLoss", holdoutLogLoss, "rank1Ece", holdoutEce),
                        Map.of("rank1Brier", holdoutBrier, "rank1LogLoss", holdoutLogLoss, "rank1Ece", holdoutEce)));
        ElliottWaveAnchorCalibrationHarness.AnchorAggregate validation = new ElliottWaveAnchorCalibrationHarness.AnchorAggregate(
                4, 4, validationTop1, validationTop3,
                Map.of("rank1", 2, "rank2", 1, "rank3", 0, "rank4plus", 0, "unmatched", 1), List.of());
        ElliottWaveAnchorCalibrationHarness.AnchorAggregate holdout = new ElliottWaveAnchorCalibrationHarness.AnchorAggregate(
                3, 3, holdoutTop1, holdoutTop3,
                Map.of("rank1", 1, "rank2", 1, "rank3", 0, "rank4plus", 0, "unmatched", 1), List.of());
        ElliottWaveAnchorCalibrationHarness.AnchorPartitions anchors = new ElliottWaveAnchorCalibrationHarness.AnchorPartitions(
                validation, holdout, top3Degradation);
        return new ElliottWaveAnchorCalibrationHarness.CandidateEvaluation(profile, manifest, horizon, metrics, anchors,
                profile.id() + "|cfg=cfg-hash", "artifact-" + profile.id());
    }

    private static WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> syntheticRunResult(
            BarSeries series) {
        WalkForwardConfig config = new WalkForwardConfig(2, 1, 1, 0, 0, 1, 1, List.of(2), 3, List.of(1), 42L);
        WalkForwardExperimentManifest manifest = new WalkForwardExperimentManifest("synthetic-btc", "candidate",
                config.configHash(), 42L, Map.of("profile", "synthetic"));
        List<WalkForwardSplit> splits = List.of(new WalkForwardSplit("validation-fold", 0, 1, 2, 2, 0, 0, false),
                new WalkForwardSplit("holdout", 0, 3, 4, 5, 0, 0, true));
        List<PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment>> snapshots = List.of(
                snapshot("validation-fold", 2, rankedPrediction(series, "validation-top", 1, ElliottPhase.WAVE5, true),
                        rankedPrediction(series, "validation-noise", 2, ElliottPhase.WAVE3, true)),
                snapshot("holdout", 4, rankedPrediction(series, "holdout-noise", 1, ElliottPhase.WAVE2, true),
                        rankedPrediction(series, "holdout-bottom", 2, ElliottPhase.CORRECTIVE_C, false)),
                snapshot("holdout", 5, rankedPrediction(series, "holdout-miss", 1, ElliottPhase.WAVE3, true)));
        return new WalkForwardRunResult<>(config, splits, snapshots, Map.of(), Map.of(), Map.of(), List.of(),
                WalkForwardRuntimeReport.empty(), manifest);
    }

    private static PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment> snapshot(String foldId,
            int decisionIndex, RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>... predictions) {
        return new PredictionSnapshot<>(foldId, decisionIndex, List.of(predictions), Map.of());
    }

    private static RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> rankedPrediction(BarSeries series,
            String predictionId, int rank, ElliottPhase phase, boolean bullish) {
        Num probability = series.numFactory().numOf(rank == 1 ? 0.70 : 0.45);
        Num confidence = series.numFactory().numOf(rank == 1 ? 0.65 : 0.40);
        ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                scenario(series, predictionId, phase, bullish), 0.6, 0.6, 0.6, List.of());
        return new RankedPrediction<>(predictionId, rank, probability, confidence, assessment);
    }

    private static ElliottScenario scenario(BarSeries series, String id, ElliottPhase phase, boolean bullish) {
        ScenarioType type = phase == ElliottPhase.CORRECTIVE_C ? ScenarioType.CORRECTIVE_ZIGZAG : ScenarioType.IMPULSE;
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(phase)
                .confidence(ElliottConfidence.zero(series.numFactory()))
                .degree(ElliottDegree.MINOR)
                .primaryTarget(series.numFactory().numOf(120.0))
                .invalidationPrice(series.numFactory().numOf(80.0))
                .type(type)
                .startIndex(series.getBeginIndex())
                .bullishDirection(bullish)
                .build();
    }

    private static ElliottWaveAnchorCalibrationHarness.Anchor anchor(String id,
            ElliottWaveAnchorCalibrationHarness.AnchorType type, Instant at, Duration toleranceBefore,
            Duration toleranceAfter, Set<ElliottPhase> expectedPhases,
            ElliottWaveAnchorRegistry.AnchorPartition partition, String provenance) {
        return new ElliottWaveAnchorCalibrationHarness.Anchor(id, type, at, toleranceBefore, toleranceAfter,
                expectedPhases, partition, provenance);
    }

    private static Map<String, Double> orderedDoubleMap(Object... entries) {
        requireKeyValuePairs(entries);
        LinkedHashMap<String, Double> ordered = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            ordered.put((String) entries[index], (Double) entries[index + 1]);
        }
        return ordered;
    }

    private static Map<String, Integer> orderedIntMap(Object... entries) {
        requireKeyValuePairs(entries);
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            ordered.put((String) entries[index], (Integer) entries[index + 1]);
        }
        return ordered;
    }

    private static void requireKeyValuePairs(Object[] entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain complete key/value pairs");
        }
    }

    private static BarSeries syntheticSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-anchor-series").build();
        Instant firstEndTime = Instant.parse("2024-01-01T04:00:00Z");
        double[][] values = { { 100.0, 102.0, 99.0, 101.0 }, { 101.0, 105.0, 100.0, 104.0 },
                { 104.0, 120.0, 103.0, 118.0 }, { 118.0, 119.0, 95.0, 98.0 }, { 98.0, 100.0, 80.0, 82.0 },
                { 82.0, 88.0, 81.0, 86.0 } };
        for (int index = 0; index < values.length; index++) {
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(4))
                    .endTime(firstEndTime.plus(Duration.ofHours(index * 4L)))
                    .openPrice(values[index][0])
                    .highPrice(values[index][1])
                    .lowPrice(values[index][2])
                    .closePrice(values[index][3])
                    .volume(1.0)
                    .amount(values[index][3])
                    .trades(1)
                    .build());
        }
        return series;
    }
}
