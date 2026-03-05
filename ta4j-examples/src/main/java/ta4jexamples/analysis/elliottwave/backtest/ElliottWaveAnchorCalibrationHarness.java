/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveOutcome;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveOutcomeLabeler;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWavePredictionProvider;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardContext;
import org.ta4j.core.indicators.elliott.walkforward.ElliottWaveWalkForwardProfiles;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.walkforward.AnchoredExpandingWalkForwardSplitter;
import org.ta4j.core.walkforward.PredictionSnapshot;
import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.walkforward.WalkForwardEngine;
import org.ta4j.core.walkforward.WalkForwardExperimentManifest;
import org.ta4j.core.walkforward.WalkForwardMetric;
import org.ta4j.core.walkforward.WalkForwardRunResult;
import org.ta4j.core.walkforward.WalkForwardSplit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

/**
 * Replays the locked Elliott walk-forward baseline around curated BTC anchor
 * windows and emits a deterministic JSON report bundle.
 *
 * <p>
 * The harness keeps the existing core walk-forward engine intact. It reuses the
 * baseline config and calibration metrics already shipped in {@code ta4j-core},
 * then layers anchor-window scoring on top of the captured
 * {@link WalkForwardRunResult} snapshots so BTC tops and bottoms can be judged
 * without introducing a parallel evaluation framework.
 *
 * <p>
 * Running the example prints a single JSON payload prefixed with
 * {@value #RESULT_PREFIX}. The report includes the BTC anchor registry version,
 * challenger comparisons versus the locked baseline, promotion-gate outcomes,
 * and portability sanity checks on any ossified non-BTC resources that are
 * already large enough for the fixed walk-forward geometry.
 *
 * @since 0.22.4
 */
public final class ElliottWaveAnchorCalibrationHarness {

    static final String RESULT_PREFIX = "EW_ANCHOR_REPORT: ";
    static final String BTC_RESOURCE = "Coinbase-BTC-USD-PT4H-20160518_20251028.json";
    static final String ETH_RESOURCE = "Coinbase-ETH-USD-PT4H-20160518_20251028.json";
    static final String SP500_RESOURCE = "YahooFinance-SP500-PT1D-20230616_20231011.json";
    static final String BTC_SERIES_NAME = "BTC-USD_PT4H@Coinbase (anchor calibration)";
    static final String ETH_SERIES_NAME = "ETH-USD_PT4H@Coinbase (portability)";
    static final String SP500_SERIES_NAME = "SP500_PT1D@YahooFinance (portability)";
    static final String METRIC_EVENT_AGREEMENT = "rank1EventAgreement";
    static final String METRIC_BRIER = "rank1Brier";
    static final String METRIC_LOG_LOSS = "rank1LogLoss";
    static final String METRIC_ECE = "rank1Ece";

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnchorCalibrationHarness.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .registerTypeAdapter(Duration.class,
                    (JsonSerializer<Duration>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .setPrettyPrinting()
            .create();
    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private ElliottWaveAnchorCalibrationHarness() {
    }

    /**
     * Runs the default BTC anchor-calibration report.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        ReportBundle report = generateDefaultReport();
        LOG.info("Promotion decision: selectedProfile={} promoteChallenger={} rationale={}",
                report.decision().selectedProfileId(), report.decision().promoteChallenger(),
                report.decision().rationale());
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    static ReportBundle generateDefaultReport() {
        BarSeries btcSeries = requireSeries(BTC_RESOURCE, BTC_SERIES_NAME);
        BarSeries ethSeries = loadSeries(ETH_RESOURCE, ETH_SERIES_NAME).orElse(null);
        BarSeries sp500Series = loadSeries(SP500_RESOURCE, SP500_SERIES_NAME).orElse(null);

        WalkForwardConfig config = ElliottWaveWalkForwardProfiles.baselineConfig();
        AnchorRegistry registry = defaultBitcoinAnchors(btcSeries);

        List<CandidateEvaluation> evaluations = defaultProfiles().parallelStream()
                .map(profile -> evaluateCandidate(btcSeries, registry, profile, buildEngine(), config))
                .toList();

        WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> portabilityEngine = buildEngine();

        CandidateEvaluation baseline = evaluations.stream()
                .filter(evaluation -> evaluation.profile().baselineCandidate())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("baseline profile evaluation missing"));

        List<ChallengerAssessment> challengerAssessments = rankChallengers(baseline, evaluations);
        PromotionDecision decision = PromotionDecision.from(baseline, challengerAssessments);
        CandidateEvaluation selected = selectedEvaluation(baseline, challengerAssessments, decision);

        List<PortabilitySummary> portability = List.of(
                evaluatePortability("eth-usd", ETH_RESOURCE, ethSeries, baseline.profile(), selected.profile(),
                        portabilityEngine, config),
                evaluatePortability("sp500", SP500_RESOURCE, sp500Series, baseline.profile(), selected.profile(),
                        portabilityEngine, config));

        BaselinePolicy baselinePolicy = new BaselinePolicy(BTC_RESOURCE, config.primaryHorizonBars(),
                List.copyOf(config.reportingHorizons()), config.configHash(),
                ElliottWaveWalkForwardProfiles.baseline().metadata().getOrDefault("profile", "baseline"));

        return ReportBundle.create("btc-anchor-calibration-v1", btcSeries.getBar(btcSeries.getEndIndex()).getEndTime(),
                registry, baselinePolicy, baseline, challengerAssessments, decision, portability);
    }

    static AnchorRegistry defaultBitcoinAnchors(BarSeries series) {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        List<ElliottWaveAnchorRegistry.ResolvedAnchor> resolvedAnchors = registry.resolve(series, 3);
        List<Anchor> anchors = resolvedAnchors.stream().map(ElliottWaveAnchorCalibrationHarness::toAnchor).toList();
        return new AnchorRegistry(registry.registryId(), registry.datasetResource(), registry.provenance(), anchors);
    }

    static List<CandidateProfile> defaultProfiles() {
        return List.of(CandidateProfile.baseline(),
                CandidateProfile.of("minute-f3-h2l2-max25-sw0", ElliottDegree.MINUTE, 2, 2, 25, 0, 3,
                        "Tighter swing confirmation near transition windows"),
                CandidateProfile.of("minute-f2-h1l1-max25-sw0", ElliottDegree.MINUTE, 1, 1, 25, 0, 2,
                        "Less cross-degree drag when the base count is already clear"),
                CandidateProfile.of("minute-f2-h2l2-max15-sw1", ElliottDegree.MINUTE, 2, 2, 15, 1, 2,
                        "Fewer retained scenarios and a short scenario window to reduce probability crowding"),
                CandidateProfile.of("minor-f2-h2l2-max25-sw0", ElliottDegree.MINOR, 2, 2, 25, 0, 2,
                        "Coarser base degree for longer-cycle structure continuity"));
    }

    static CandidateEvaluation evaluateCandidate(BarSeries series, AnchorRegistry registry, CandidateProfile profile,
            WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> engine,
            WalkForwardConfig config) {
        WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult = engine
                .run(series, profile.context(), config, profile.id(), profile.manifestMetadata());

        Map<Integer, MetricSnapshot> metricsByHorizon = new LinkedHashMap<>();
        for (int horizon : config.allHorizons()) {
            metricsByHorizon.put(horizon, MetricSnapshot.from(runResult, horizon));
        }

        AnchorPartitions anchors = summarizeAnchors(series, registry, runResult);
        return CandidateEvaluation.create(profile, runResult.manifest(), metricsByHorizon, anchors,
                config.primaryHorizonBars());
    }

    static AnchorPartitions summarizeAnchors(BarSeries series, AnchorRegistry registry,
            WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult) {
        Map<String, WalkForwardSplit> splitsById = new LinkedHashMap<>();
        for (WalkForwardSplit split : runResult.splits()) {
            splitsById.put(split.foldId(), split);
        }

        List<AnchorSnapshotMatch> validationMatches = new ArrayList<>();
        List<AnchorSnapshotMatch> holdoutMatches = new ArrayList<>();
        List<AnchorWindowSummary> validationSummaries = new ArrayList<>();
        List<AnchorWindowSummary> holdoutSummaries = new ArrayList<>();

        for (Anchor anchor : registry.anchors()) {
            int anchorIndex = nearestIndex(series, anchor.at());
            WindowBounds bounds = resolveWindowBounds(series, anchorIndex, anchor.toleranceBefore(),
                    anchor.toleranceAfter());
            boolean holdoutAnchor = anchor.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT;
            List<AnchorSnapshotMatch> anchorValidation = new ArrayList<>();
            List<AnchorSnapshotMatch> anchorHoldout = new ArrayList<>();

            for (PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment> snapshot : runResult
                    .snapshots()) {
                if (snapshot.decisionIndex() < bounds.startIndex() || snapshot.decisionIndex() > bounds.endIndex()) {
                    continue;
                }
                WalkForwardSplit split = splitsById.get(snapshot.foldId());
                if (split == null) {
                    continue;
                }
                if (split.holdout() != holdoutAnchor) {
                    continue;
                }

                int bestRank = bestMatchingRank(snapshot.topPredictions(), anchor);
                AnchorSnapshotMatch match = new AnchorSnapshotMatch(anchor.id(), anchor.type(), split.foldId(),
                        split.holdout(), snapshot.decisionIndex(), bestRank);
                if (holdoutAnchor) {
                    anchorHoldout.add(match);
                    holdoutMatches.add(match);
                } else {
                    anchorValidation.add(match);
                    validationMatches.add(match);
                }
            }

            if (!anchorValidation.isEmpty()) {
                validationSummaries.add(AnchorWindowSummary.from(anchor, anchorIndex, bounds, anchorValidation));
            }
            if (!anchorHoldout.isEmpty()) {
                holdoutSummaries.add(AnchorWindowSummary.from(anchor, anchorIndex, bounds, anchorHoldout));
            }
        }

        AnchorAggregate validation = AnchorAggregate.from(validationSummaries, validationMatches);
        AnchorAggregate holdout = AnchorAggregate.from(holdoutSummaries, holdoutMatches);
        double stabilityDegradation = degradation(validation.top3HitRate(), holdout.top3HitRate());
        return new AnchorPartitions(validation, holdout, stabilityDegradation);
    }

    static GateEvaluation evaluatePromotionGates(CandidateEvaluation baseline, CandidateEvaluation challenger) {
        double baselineHoldoutTop3 = baseline.anchors().holdout().top3HitRate();
        double challengerHoldoutTop3 = challenger.anchors().holdout().top3HitRate();
        double baselineHoldoutTop1 = baseline.anchors().holdout().top1HitRate();
        double challengerHoldoutTop1 = challenger.anchors().holdout().top1HitRate();

        double top3Improvement = challengerHoldoutTop3 - baselineHoldoutTop3;
        double top1Improvement = challengerHoldoutTop1 - baselineHoldoutTop1;
        double stabilityDegradation = challenger.anchors().validationToHoldoutTop3Degradation();

        Map<String, Double> baselineHoldoutMetrics = baseline.primaryMetrics().holdout();
        Map<String, Double> challengerHoldoutMetrics = challenger.primaryMetrics().holdout();
        List<String> calibrationMetricNames = List.of(METRIC_BRIER, METRIC_LOG_LOSS, METRIC_ECE);

        int calibrationImprovedCount = 0;
        double maximumRelativeCalibrationDegradation = 0.0;
        boolean calibrationDataAvailable = true;
        for (String metricName : calibrationMetricNames) {
            double baselineMetric = baselineHoldoutMetrics.getOrDefault(metricName, Double.NaN);
            double challengerMetric = challengerHoldoutMetrics.getOrDefault(metricName, Double.NaN);
            if (!Double.isFinite(baselineMetric) || !Double.isFinite(challengerMetric)) {
                calibrationDataAvailable = false;
                maximumRelativeCalibrationDegradation = Double.POSITIVE_INFINITY;
                continue;
            }
            if (challengerMetric < baselineMetric) {
                calibrationImprovedCount++;
            }
            maximumRelativeCalibrationDegradation = Math.max(maximumRelativeCalibrationDegradation,
                    relativeDegradationLowerIsBetter(baselineMetric, challengerMetric));
        }

        boolean top3Gate = Double.isFinite(top3Improvement) && top3Improvement >= 0.10;
        boolean top1Gate = Double.isFinite(top1Improvement) && top1Improvement >= 0.05;
        boolean calibrationImprovementGate = calibrationDataAvailable && calibrationImprovedCount >= 2;
        boolean calibrationDegradationGate = calibrationDataAvailable && maximumRelativeCalibrationDegradation <= 0.05;
        boolean stabilityGate = Double.isFinite(stabilityDegradation) && stabilityDegradation <= 0.10;
        boolean artifactGate = !challenger.manifest().configHash().isBlank() && !challenger.artifactHash().isBlank();

        List<String> notes = new ArrayList<>();
        if (!top3Gate) {
            notes.add("holdout top-3 anchor hit-rate did not improve by at least 10 percentage points");
        }
        if (!top1Gate) {
            notes.add("holdout top-1 anchor hit-rate did not improve by at least 5 percentage points");
        }
        if (!calibrationImprovementGate) {
            notes.add("fewer than two holdout calibration metrics improved");
        }
        if (!calibrationDegradationGate) {
            notes.add("at least one holdout calibration metric degraded by more than 5 percent relative");
        }
        if (!stabilityGate) {
            notes.add("validation-to-holdout top-3 degradation exceeded 10 percentage points");
        }
        if (!artifactGate) {
            notes.add("candidate artifacts were not reproducible");
        }

        return new GateEvaluation(top3Improvement, top1Improvement, calibrationImprovedCount,
                maximumRelativeCalibrationDegradation, stabilityDegradation, top3Gate, top1Gate,
                calibrationImprovementGate, calibrationDegradationGate, stabilityGate, artifactGate, notes.isEmpty(),
                List.copyOf(notes));
    }

    static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute SHA-256 hash", ex);
        }
    }

    static List<ChallengerAssessment> rankChallengers(CandidateEvaluation baseline,
            List<CandidateEvaluation> evaluations) {
        List<ChallengerAssessment> assessments = new ArrayList<>();
        for (CandidateEvaluation evaluation : evaluations) {
            if (evaluation.profile().baselineCandidate()) {
                continue;
            }
            assessments.add(new ChallengerAssessment(evaluation, evaluatePromotionGates(baseline, evaluation)));
        }
        assessments.sort(Comparator
                .comparing((ChallengerAssessment assessment) -> assessment.gates().passed(), Comparator.reverseOrder())
                .thenComparing(Comparator
                        .comparingDouble(
                                (ChallengerAssessment assessment) -> assessment.gates().holdoutTop3Improvement())
                        .reversed())
                .thenComparing(Comparator
                        .comparingDouble(
                                (ChallengerAssessment assessment) -> assessment.gates().holdoutTop1Improvement())
                        .reversed())
                .thenComparing(Comparator
                        .comparingInt(
                                (ChallengerAssessment assessment) -> assessment.gates().calibrationImprovedCount())
                        .reversed())
                .thenComparingDouble(assessment -> assessment.gates().maximumRelativeCalibrationDegradation())
                .thenComparing(assessment -> assessment.evaluation().profile().id()));
        return List.copyOf(assessments);
    }

    private static CandidateEvaluation selectedEvaluation(CandidateEvaluation baseline,
            List<ChallengerAssessment> challengerAssessments, PromotionDecision decision) {
        if (!decision.promoteChallenger() || challengerAssessments.isEmpty()) {
            return baseline;
        }
        return challengerAssessments.getFirst().evaluation();
    }

    private static PortabilitySummary evaluatePortability(String datasetId, String resource, BarSeries series,
            CandidateProfile baselineProfile, CandidateProfile selectedProfile,
            WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> engine,
            WalkForwardConfig config) {
        if (series == null) {
            return PortabilitySummary.skipped(datasetId, resource, "resource failed to load");
        }
        if (!supportsConfig(series, config)) {
            return PortabilitySummary.skipped(datasetId, resource,
                    "ossified series is too short for the locked baseline geometry");
        }

        MetricSnapshot baselineMetrics = evaluateMetricsOnly(series, baselineProfile, engine, config);
        MetricSnapshot selectedMetrics = baselineProfile.id().equals(selectedProfile.id()) ? baselineMetrics
                : evaluateMetricsOnly(series, selectedProfile, engine, config);

        return PortabilitySummary.executed(datasetId, resource, series.getBarCount(), baselineProfile.id(),
                selectedProfile.id(), baselineMetrics, selectedMetrics);
    }

    private static MetricSnapshot evaluateMetricsOnly(BarSeries series, CandidateProfile profile,
            WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> engine,
            WalkForwardConfig config) {
        WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult = engine
                .run(series, profile.context(), config, profile.id(), profile.manifestMetadata());
        return MetricSnapshot.from(runResult, config.primaryHorizonBars());
    }

    private static WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> buildEngine() {
        return new WalkForwardEngine<>(new AnchoredExpandingWalkForwardSplitter(), new ElliottWavePredictionProvider(),
                new ElliottWaveOutcomeLabeler(), metrics());
    }

    private static List<WalkForwardMetric<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome>> metrics() {
        return List.of(
                WalkForwardMetric.agreement(METRIC_EVENT_AGREEMENT, 1,
                        (prediction, outcome) -> outcome != null
                                && outcome.eventOutcome() == ElliottWaveOutcome.EventOutcome.TARGET_FIRST),
                WalkForwardMetric.brierScore(METRIC_BRIER, 1, ElliottWaveAnchorCalibrationHarness::observedTarget),
                WalkForwardMetric.logLoss(METRIC_LOG_LOSS, 1, ElliottWaveAnchorCalibrationHarness::observedTarget),
                WalkForwardMetric.expectedCalibrationError(METRIC_ECE, 1, 10,
                        ElliottWaveAnchorCalibrationHarness::observedTarget));
    }

    private static Num observedTarget(ElliottWaveOutcome outcome) {
        if (outcome == null) {
            return NaN.NaN;
        }
        NumFactory factory = outcome.realizedReturn() != null && !Num.isNaNOrNull(outcome.realizedReturn())
                ? outcome.realizedReturn().getNumFactory()
                : NUM_FACTORY;
        return outcome.eventOutcome() == ElliottWaveOutcome.EventOutcome.TARGET_FIRST ? factory.one() : factory.zero();
    }

    private static boolean supportsConfig(BarSeries series, WalkForwardConfig config) {
        List<WalkForwardSplit> splits = new AnchoredExpandingWalkForwardSplitter().split(series, config);
        return !splits.isEmpty() && splits.stream().anyMatch(WalkForwardSplit::holdout)
                && splits.stream().anyMatch(split -> !split.holdout());
    }

    private static int bestMatchingRank(
            List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predictions, Anchor anchor) {
        int bestRank = Integer.MAX_VALUE;
        for (RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction : predictions) {
            ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = prediction.payload();
            if (assessment == null || !matchesAnchor(assessment.scenario(), anchor)) {
                continue;
            }
            bestRank = Math.min(bestRank, prediction.rank());
        }
        return bestRank == Integer.MAX_VALUE ? 0 : bestRank;
    }

    private static boolean matchesAnchor(ElliottScenario scenario, Anchor anchor) {
        if (scenario == null || !scenario.hasKnownDirection()) {
            return false;
        }
        ElliottPhase phase = scenario.currentPhase();
        if (!anchor.expectedPhases().contains(phase)) {
            return false;
        }
        return switch (phase) {
        case WAVE1, WAVE2, WAVE3, WAVE4, WAVE5 -> scenario.isBullish();
        case CORRECTIVE_A, CORRECTIVE_B, CORRECTIVE_C -> scenario.isBearish();
        default -> false;
        };
    }

    private static int nearestIndex(BarSeries series, Instant target) {
        int nearest = series.getBeginIndex();
        long bestDistance = Long.MAX_VALUE;
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            long distance = Math.abs(series.getBar(i).getEndTime().toEpochMilli() - target.toEpochMilli());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    private static WindowBounds resolveWindowBounds(BarSeries series, int anchorIndex, Duration before,
            Duration after) {
        Instant anchorTime = series.getBar(anchorIndex).getEndTime();
        Instant startTime = anchorTime.minus(before);
        Instant endTime = anchorTime.plus(after);

        int startIndex = anchorIndex;
        while (startIndex > series.getBeginIndex() && !series.getBar(startIndex - 1).getEndTime().isBefore(startTime)) {
            startIndex--;
        }

        int endIndex = anchorIndex;
        while (endIndex < series.getEndIndex() && !series.getBar(endIndex + 1).getEndTime().isAfter(endTime)) {
            endIndex++;
        }

        return new WindowBounds(startIndex, endIndex);
    }

    private static double degradation(double validation, double holdout) {
        if (!Double.isFinite(validation) || !Double.isFinite(holdout)) {
            return Double.NaN;
        }
        return Math.max(0.0, validation - holdout);
    }

    private static double relativeDegradationLowerIsBetter(double baseline, double challenger) {
        if (!Double.isFinite(baseline) || !Double.isFinite(challenger)) {
            return Double.POSITIVE_INFINITY;
        }
        if (baseline == 0.0) {
            return challenger == 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, (challenger - baseline) / Math.abs(baseline));
    }

    private static Anchor toAnchor(ElliottWaveAnchorRegistry.ResolvedAnchor resolvedAnchor) {
        ElliottWaveAnchorRegistry.AnchorSpec spec = resolvedAnchor.spec();
        Duration toleranceBefore = Duration.between(spec.windowStart(), resolvedAnchor.resolvedTime());
        Duration toleranceAfter = Duration.between(resolvedAnchor.resolvedTime(), spec.windowEnd());
        String provenance = spec.source() + (spec.notes().isBlank() ? "" : " " + spec.notes());
        return new Anchor(spec.id(), mapAnchorType(spec.kind()), resolvedAnchor.resolvedTime(), toleranceBefore,
                toleranceAfter, spec.expectedPhases(), resolvedAnchor.partition(), provenance);
    }

    private static AnchorType mapAnchorType(ElliottWaveAnchorRegistry.AnchorKind kind) {
        return kind == ElliottWaveAnchorRegistry.AnchorKind.TOP ? AnchorType.TOP : AnchorType.BOTTOM;
    }

    private static BarSeries requireSeries(String resource, String seriesName) {
        return loadSeries(resource, seriesName)
                .orElseThrow(() -> new IllegalStateException("Unable to load required resource " + resource));
    }

    private static Optional<BarSeries> loadSeries(String resource, String seriesName) {
        return Optional.ofNullable(OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveAnchorCalibrationHarness.class,
                resource, seriesName, LOG));
    }

    private static Map<String, Double> toDoubleMap(Map<String, Num> metricMap) {
        Map<String, Double> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Num> entry : metricMap.entrySet()) {
            converted.put(entry.getKey(), entry.getValue() == null ? Double.NaN : entry.getValue().doubleValue());
        }
        return Map.copyOf(converted);
    }

    private static Map<String, Double> averageNonHoldoutMetrics(Map<String, Map<String, Num>> perFold) {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Num>> foldEntry : perFold.entrySet()) {
            if ("holdout".equals(foldEntry.getKey())) {
                continue;
            }
            for (Map.Entry<String, Num> metricEntry : foldEntry.getValue().entrySet()) {
                double value = metricEntry.getValue() == null ? Double.NaN : metricEntry.getValue().doubleValue();
                if (!Double.isFinite(value)) {
                    continue;
                }
                totals.merge(metricEntry.getKey(), value, Double::sum);
                counts.merge(metricEntry.getKey(), 1, Integer::sum);
            }
        }

        Map<String, Double> averaged = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            averaged.put(entry.getKey(), count == 0 ? Double.NaN : entry.getValue() / count);
        }
        return Map.copyOf(averaged);
    }

    /**
     * Versioned BTC anchor registry.
     */
    record AnchorRegistry(String version, String datasetResource, String provenance, List<Anchor> anchors) {

        AnchorRegistry {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(datasetResource, "datasetResource");
            Objects.requireNonNull(provenance, "provenance");
            anchors = anchors == null ? List.of() : List.copyOf(anchors);
        }
    }

    /**
     * One curated BTC anchor window with provenance and tolerance bounds.
     */
    record Anchor(String id, AnchorType type, Instant at, Duration toleranceBefore, Duration toleranceAfter,
            Set<ElliottPhase> expectedPhases, ElliottWaveAnchorRegistry.AnchorPartition partition, String provenance) {

        Anchor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(toleranceBefore, "toleranceBefore");
            Objects.requireNonNull(toleranceAfter, "toleranceAfter");
            expectedPhases = expectedPhases == null || expectedPhases.isEmpty() ? EnumSet.noneOf(ElliottPhase.class)
                    : EnumSet.copyOf(expectedPhases);
            if (expectedPhases.isEmpty()) {
                throw new IllegalArgumentException("expectedPhases must not be empty");
            }
            Objects.requireNonNull(partition, "partition");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    enum AnchorType {
        TOP, BOTTOM
    }

    /**
     * Coarse Elliott runner profile used as a tuning candidate.
     */
    record CandidateProfile(String id, ElliottDegree degree, int higherDegrees, int lowerDegrees, int maxScenarios,
            int scenarioSwingWindow, int fractalWindow, boolean baselineCandidate, String rationale,
            ElliottWaveWalkForwardContext context) {

        CandidateProfile {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(degree, "degree");
            Objects.requireNonNull(rationale, "rationale");
            Objects.requireNonNull(context, "context");
        }

        static CandidateProfile baseline() {
            ElliottWaveWalkForwardContext context = ElliottWaveWalkForwardProfiles.baseline();
            String id = context.metadata().getOrDefault("profile", "baseline-minute-f2-h2l2-max25-sw0");
            return new CandidateProfile(id, ElliottWaveWalkForwardProfiles.BASELINE_DEGREE, 2, 2, 25, 0, 2, true,
                    "Locked baseline profile used for cross-run comparability", context);
        }

        static CandidateProfile baselineProfile() {
            return baseline();
        }

        static CandidateProfile of(String id, ElliottDegree degree, int higherDegrees, int lowerDegrees,
                int maxScenarios, int scenarioSwingWindow, int fractalWindow, String rationale) {
            ElliottWaveAnalysisRunner runner = ElliottWaveAnalysisRunner.builder()
                    .degree(degree)
                    .higherDegrees(higherDegrees)
                    .lowerDegrees(lowerDegrees)
                    .maxScenarios(maxScenarios)
                    .minConfidence(0.0)
                    .scenarioSwingWindow(scenarioSwingWindow)
                    .swingDetector(SwingDetectors.fractal(fractalWindow))
                    .build();
            Map<String, String> metadata = Map.of("profile", id, "degree", degree.name(), "higherDegrees",
                    String.valueOf(higherDegrees), "lowerDegrees", String.valueOf(lowerDegrees), "maxScenarios",
                    String.valueOf(maxScenarios), "scenarioSwingWindow", String.valueOf(scenarioSwingWindow),
                    "fractalWindow", String.valueOf(fractalWindow));
            ElliottWaveWalkForwardContext context = new ElliottWaveWalkForwardContext(runner, null, 5, metadata);
            return new CandidateProfile(id, degree, higherDegrees, lowerDegrees, maxScenarios, scenarioSwingWindow,
                    fractalWindow, false, rationale, context);
        }

        Map<String, String> manifestMetadata() {
            Map<String, String> manifest = new LinkedHashMap<>(context.metadata());
            manifest.put("rationale", rationale);
            return Map.copyOf(manifest);
        }
    }

    /**
     * Summarized BTC evaluation for one candidate profile.
     */
    record CandidateEvaluation(CandidateProfile profile, WalkForwardExperimentManifest manifest, int primaryHorizonBars,
            Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors, String artifactId,
            String artifactHash) {

        CandidateEvaluation {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(manifest, "manifest");
            metricsByHorizon = metricsByHorizon == null ? Map.of() : Map.copyOf(metricsByHorizon);
            Objects.requireNonNull(anchors, "anchors");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(artifactHash, "artifactHash");
        }

        static CandidateEvaluation create(CandidateProfile profile, WalkForwardExperimentManifest manifest,
                Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors, int primaryHorizonBars) {
            String artifactId = manifest.candidateId() + "|cfg=" + manifest.configHash();
            UnsignedCandidateEvaluation unsigned = new UnsignedCandidateEvaluation(profile, manifest,
                    primaryHorizonBars, metricsByHorizon, anchors, artifactId);
            return new CandidateEvaluation(profile, manifest, primaryHorizonBars, metricsByHorizon, anchors, artifactId,
                    sha256(GSON.toJson(unsigned)));
        }

        MetricSnapshot primaryMetrics() {
            return metricsByHorizon.getOrDefault(primaryHorizonBars, MetricSnapshot.empty());
        }
    }

    /**
     * Challenger result plus gate evaluation against the locked baseline.
     */
    record ChallengerAssessment(CandidateEvaluation evaluation, GateEvaluation gates) {

        ChallengerAssessment {
            Objects.requireNonNull(evaluation, "evaluation");
            Objects.requireNonNull(gates, "gates");
        }
    }

    /**
     * Acceptance-gate outcome for one challenger.
     */
    record GateEvaluation(double holdoutTop3Improvement, double holdoutTop1Improvement, int calibrationImprovedCount,
            double maximumRelativeCalibrationDegradation, double validationToHoldoutTop3Degradation, boolean top3Gate,
            boolean top1Gate, boolean calibrationImprovementGate, boolean calibrationDegradationGate,
            boolean stabilityGate, boolean artifactGate, boolean passed, List<String> notes) {

        GateEvaluation {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    /**
     * Final promote-or-retain decision for the BTC run.
     */
    record PromotionDecision(String selectedProfileId, String challengerProfileId, boolean promoteChallenger,
            String rationale, List<String> followOnHypotheses) {

        PromotionDecision {
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(challengerProfileId, "challengerProfileId");
            Objects.requireNonNull(rationale, "rationale");
            followOnHypotheses = followOnHypotheses == null ? List.of() : List.copyOf(followOnHypotheses);
        }

        static PromotionDecision from(CandidateEvaluation baseline, List<ChallengerAssessment> challengerAssessments) {
            if (challengerAssessments.isEmpty()) {
                return new PromotionDecision(baseline.profile().id(), baseline.profile().id(), false,
                        "Baseline retained because no challenger profiles were evaluated", List.of());
            }

            ChallengerAssessment best = challengerAssessments.getFirst();
            if (best.gates().passed()) {
                return new PromotionDecision(best.evaluation().profile().id(), best.evaluation().profile().id(), true,
                        "Challenger cleared every holdout gate on the locked BTC anchor registry", List.of());
            }

            return new PromotionDecision(baseline.profile().id(), best.evaluation().profile().id(), false,
                    "Baseline retained because the best challenger missed one or more holdout gates",
                    followOnHypotheses(best.gates()));
        }

        private static List<String> followOnHypotheses(GateEvaluation gates) {
            List<String> hypotheses = new ArrayList<>();
            if (!gates.top3Gate() || !gates.top1Gate()) {
                hypotheses.add(
                        "Reduce phase ambiguity near anchor windows by tightening scenario breadth or narrowing the supporting-degree span.");
            }
            if (!gates.calibrationImprovementGate() || !gates.calibrationDegradationGate()) {
                hypotheses.add(
                        "Revisit confidence weighting so rank-1 probabilities separate wave-5 exhaustion from early corrective flips more cleanly.");
            }
            if (!gates.stabilityGate()) {
                hypotheses.add(
                        "The challenger looks too validation-specific; prefer simpler swing geometry before widening the search grid.");
            }
            if (hypotheses.isEmpty()) {
                hypotheses.add(
                        "No follow-on hypothesis was needed because the baseline decision was operational rather than technical.");
            }
            return List.copyOf(hypotheses);
        }
    }

    /**
     * Aggregate near-anchor snapshot scoring split into validation and holdout
     * partitions.
     */
    record AnchorPartitions(AnchorAggregate validation, AnchorAggregate holdout,
            double validationToHoldoutTop3Degradation) {

        AnchorPartitions {
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(holdout, "holdout");
        }
    }

    /**
     * Aggregate snapshot hit rates and rank distribution for one partition.
     */
    record AnchorAggregate(int anchorCount, int sampleCount, double top1HitRate, double top3HitRate,
            Map<String, Integer> bestRankDistribution, List<AnchorWindowSummary> anchorWindows) {

        AnchorAggregate {
            bestRankDistribution = bestRankDistribution == null ? Map.of() : Map.copyOf(bestRankDistribution);
            anchorWindows = anchorWindows == null ? List.of() : List.copyOf(anchorWindows);
        }

        static AnchorAggregate from(List<AnchorWindowSummary> anchorWindows, List<AnchorSnapshotMatch> matches) {
            int top1Hits = 0;
            int top3Hits = 0;
            Map<String, Integer> distribution = new LinkedHashMap<>();
            distribution.put("rank1", 0);
            distribution.put("rank2", 0);
            distribution.put("rank3", 0);
            distribution.put("rank4plus", 0);
            distribution.put("unmatched", 0);

            for (AnchorSnapshotMatch match : matches) {
                if (match.bestRank() == 1) {
                    top1Hits++;
                    top3Hits++;
                    distribution.merge("rank1", 1, Integer::sum);
                } else if (match.bestRank() == 2) {
                    top3Hits++;
                    distribution.merge("rank2", 1, Integer::sum);
                } else if (match.bestRank() == 3) {
                    top3Hits++;
                    distribution.merge("rank3", 1, Integer::sum);
                } else if (match.bestRank() > 3) {
                    distribution.merge("rank4plus", 1, Integer::sum);
                } else {
                    distribution.merge("unmatched", 1, Integer::sum);
                }
            }

            int sampleCount = matches.size();
            return new AnchorAggregate(anchorWindows.size(), sampleCount, safeRate(top1Hits, sampleCount),
                    safeRate(top3Hits, sampleCount), distribution, anchorWindows);
        }

        private static double safeRate(int hits, int count) {
            return count == 0 ? Double.NaN : hits / (double) count;
        }
    }

    /**
     * Per-anchor window summary for replayed decision snapshots.
     */
    record AnchorWindowSummary(String anchorId, AnchorType type, String anchorTimeUtc, String provenance,
            int anchorIndex, int windowStartIndex, int windowEndIndex, int sampleCount, double top1HitRate,
            double top3HitRate, Map<String, Integer> bestRankDistribution) {

        AnchorWindowSummary {
            Objects.requireNonNull(anchorId, "anchorId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(anchorTimeUtc, "anchorTimeUtc");
            Objects.requireNonNull(provenance, "provenance");
            bestRankDistribution = bestRankDistribution == null ? Map.of() : Map.copyOf(bestRankDistribution);
        }

        static AnchorWindowSummary from(Anchor anchor, int anchorIndex, WindowBounds bounds,
                List<AnchorSnapshotMatch> matches) {
            AnchorAggregate aggregate = AnchorAggregate.from(List.of(), matches);
            return new AnchorWindowSummary(anchor.id(), anchor.type(), UTC_TIME.format(anchor.at()),
                    anchor.provenance(), anchorIndex, bounds.startIndex(), bounds.endIndex(), aggregate.sampleCount(),
                    aggregate.top1HitRate(), aggregate.top3HitRate(), aggregate.bestRankDistribution());
        }
    }

    /**
     * Holdout/validation metrics for one horizon.
     */
    record MetricSnapshot(Map<String, Double> global, Map<String, Double> validation, Map<String, Double> holdout) {

        MetricSnapshot {
            global = global == null ? Map.of() : Map.copyOf(global);
            validation = validation == null ? Map.of() : Map.copyOf(validation);
            holdout = holdout == null ? Map.of() : Map.copyOf(holdout);
        }

        static MetricSnapshot from(WalkForwardRunResult<?, ?> runResult, int horizon) {
            return new MetricSnapshot(toDoubleMap(runResult.globalMetricsForHorizon(horizon)),
                    averageNonHoldoutMetrics(runResult.foldMetricsForHorizon(horizon)),
                    toDoubleMap(runResult.foldMetricsForHorizon(horizon).getOrDefault("holdout", Map.of())));
        }

        static MetricSnapshot empty() {
            return new MetricSnapshot(Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * Portability summary for a non-BTC dataset.
     */
    record PortabilitySummary(String datasetId, String resource, String status, String reason, int seriesBars,
            String baselineProfileId, String selectedProfileId, MetricSnapshot baselinePrimaryMetrics,
            MetricSnapshot selectedPrimaryMetrics) {

        PortabilitySummary {
            Objects.requireNonNull(datasetId, "datasetId");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(baselineProfileId, "baselineProfileId");
            Objects.requireNonNull(selectedProfileId, "selectedProfileId");
            Objects.requireNonNull(baselinePrimaryMetrics, "baselinePrimaryMetrics");
            Objects.requireNonNull(selectedPrimaryMetrics, "selectedPrimaryMetrics");
        }

        static PortabilitySummary skipped(String datasetId, String resource, String reason) {
            return new PortabilitySummary(datasetId, resource, "skipped", reason, 0, "", "", MetricSnapshot.empty(),
                    MetricSnapshot.empty());
        }

        static PortabilitySummary executed(String datasetId, String resource, int seriesBars, String baselineProfileId,
                String selectedProfileId, MetricSnapshot baselinePrimaryMetrics,
                MetricSnapshot selectedPrimaryMetrics) {
            return new PortabilitySummary(datasetId, resource, "executed", "metrics collected under locked geometry",
                    seriesBars, baselineProfileId, selectedProfileId, baselinePrimaryMetrics, selectedPrimaryMetrics);
        }
    }

    /**
     * Locked baseline policy reported alongside the BTC tuning results.
     */
    record BaselinePolicy(String datasetResource, int primaryHorizonBars, List<Integer> adjacentHorizons,
            String configHash, String baselineProfileId) {

        BaselinePolicy {
            Objects.requireNonNull(datasetResource, "datasetResource");
            adjacentHorizons = adjacentHorizons == null ? List.of() : List.copyOf(adjacentHorizons);
            Objects.requireNonNull(configHash, "configHash");
            Objects.requireNonNull(baselineProfileId, "baselineProfileId");
        }
    }

    /**
     * Final deterministic report bundle emitted by the harness.
     */
    record ReportBundle(String reportVersion, String generatedAtUtc, String reportHash, AnchorRegistry anchorRegistry,
            BaselinePolicy baselinePolicy, CandidateEvaluation baselineEvaluation,
            List<ChallengerAssessment> challengerAssessments, PromotionDecision decision,
            List<PortabilitySummary> portability) {

        ReportBundle {
            Objects.requireNonNull(reportVersion, "reportVersion");
            Objects.requireNonNull(generatedAtUtc, "generatedAtUtc");
            Objects.requireNonNull(reportHash, "reportHash");
            Objects.requireNonNull(anchorRegistry, "anchorRegistry");
            Objects.requireNonNull(baselinePolicy, "baselinePolicy");
            Objects.requireNonNull(baselineEvaluation, "baselineEvaluation");
            challengerAssessments = challengerAssessments == null ? List.of() : List.copyOf(challengerAssessments);
            Objects.requireNonNull(decision, "decision");
            portability = portability == null ? List.of() : List.copyOf(portability);
        }

        static ReportBundle create(String reportVersion, Instant generatedAt, AnchorRegistry anchorRegistry,
                BaselinePolicy baselinePolicy, CandidateEvaluation baselineEvaluation,
                List<ChallengerAssessment> challengerAssessments, PromotionDecision decision,
                List<PortabilitySummary> portability) {
            String generatedAtUtc = UTC_TIME.format(generatedAt);
            UnsignedReportBundle unsigned = new UnsignedReportBundle(reportVersion, generatedAtUtc, anchorRegistry,
                    baselinePolicy, baselineEvaluation, challengerAssessments, decision, portability);
            return new ReportBundle(reportVersion, generatedAtUtc, sha256(GSON.toJson(unsigned)), anchorRegistry,
                    baselinePolicy, baselineEvaluation, challengerAssessments, decision, portability);
        }

        String toJson() {
            return GSON.toJson(this);
        }
    }

    private record AnchorSnapshotMatch(String anchorId, AnchorType anchorType, String foldId, boolean holdout,
            int decisionIndex, int bestRank) {
    }

    private record WindowBounds(int startIndex, int endIndex) {
    }

    private record UnsignedCandidateEvaluation(CandidateProfile profile, WalkForwardExperimentManifest manifest,
            int primaryHorizonBars, Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors,
            String artifactId) {
    }

    private record UnsignedReportBundle(String reportVersion, String generatedAtUtc, AnchorRegistry anchorRegistry,
            BaselinePolicy baselinePolicy, CandidateEvaluation baselineEvaluation,
            List<ChallengerAssessment> challengerAssessments, PromotionDecision decision,
            List<PortabilitySummary> portability) {
    }
}
