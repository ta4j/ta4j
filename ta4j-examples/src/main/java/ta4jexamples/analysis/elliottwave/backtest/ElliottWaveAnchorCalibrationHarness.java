/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.PatternSet;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.CompositeSwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
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
import org.ta4j.core.walkforward.WalkForwardRuntimeReport;
import org.ta4j.core.walkforward.WalkForwardSplit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
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
    static final String BTC_RESOURCE = "TradingView-INDEX_BTCUSD-PT1D-20091005_20260306.json";
    static final String ETH_RESOURCE = "Coinbase-ETH-USD-PT1D-20160517_20251028.json";
    static final String SP500_RESOURCE = "YahooFinance-SP500-PT1D-20230616_20231011.json";
    static final String BTC_SERIES_NAME = "INDEX_BTCUSD_PT1D@TradingView (anchor calibration)";
    static final String ETH_SERIES_NAME = "ETH-USD_PT1D@Coinbase (portability)";
    static final String SP500_SERIES_NAME = "SP500_PT1D@YahooFinance (portability)";
    static final String METRIC_EVENT_AGREEMENT = "rank1EventAgreement";
    static final String METRIC_BRIER = "rank1Brier";
    static final String METRIC_LOG_LOSS = "rank1LogLoss";
    static final String METRIC_ECE = "rank1Ece";
    private static final int BTC_HOLDOUT_ANCHOR_COUNT = 2;

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnchorCalibrationHarness.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .registerTypeAdapter(Duration.class,
                    (JsonSerializer<Duration>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .registerTypeAdapter(Double.class, (JsonSerializer<Double>) (value, type,
                    context) -> value == null || !Double.isFinite(value) ? JsonNull.INSTANCE : new JsonPrimitive(value))
            .registerTypeAdapter(double.class, (JsonSerializer<Double>) (value, type,
                    context) -> value == null || !Double.isFinite(value) ? JsonNull.INSTANCE : new JsonPrimitive(value))
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
        Instant startedAt = Instant.now();
        LOG.info("Starting BTC anchor calibration harness");
        ArtifactSink artifactSink = FileArtifactSink.create(defaultArtifactDirectory(startedAt));
        LOG.info("Writing incremental calibration artifacts to {}", artifactSink.outputDirectory());
        ReportBundle report = generateDefaultReport(artifactSink);
        LOG.info("Finished BTC anchor calibration harness in {}", formatElapsed(startedAt));
        LOG.info("Promotion decision: selectedProfile={} promoteChallenger={} rationale={}",
                report.decision().selectedProfileId(), report.decision().promoteChallenger(),
                report.decision().rationale());
        LOG.info("Historical calibration report\n{}", report.historicalCalibrationText());
        LOG.info("Runtime instrumentation report\n{}", report.runtimeInstrumentationText());
        LOG.info("{}{}", RESULT_PREFIX, report.toJson());
    }

    static ElliottWaveAnalysisRunner buildMacroAnalysisRunner(ElliottDegree degree, int higherDegrees, int lowerDegrees,
            int maxScenarios, int scenarioSwingWindow, int fractalWindow) {
        return buildMacroAnalysisRunner(degree, higherDegrees, lowerDegrees, maxScenarios, scenarioSwingWindow,
                fractalWindow, PatternSet.all(), 0.70, ConfidenceProfiles::defaultModel);
    }

    static ElliottWaveAnalysisRunner buildMacroAnalysisRunner(ElliottDegree degree, int higherDegrees, int lowerDegrees,
            int maxScenarios, int scenarioSwingWindow, int fractalWindow, PatternSet patternSet,
            double baseConfidenceWeight, Function<NumFactory, ConfidenceModel> confidenceModelFactory) {
        SwingDetector swingDetector = macroSwingDetector(degree, fractalWindow);
        return ElliottWaveAnalysisRunner.builder()
                .degree(degree)
                .higherDegrees(higherDegrees)
                .lowerDegrees(lowerDegrees)
                .baseConfidenceWeight(baseConfidenceWeight)
                .maxScenarios(maxScenarios)
                .minConfidence(0.0)
                .patternSet(patternSet)
                .confidenceModelFactory(confidenceModelFactory)
                .scenarioSwingWindow(scenarioSwingWindow)
                .swingDetector(swingDetector)
                .seriesSelector((series, ignoredDegree) -> series)
                .build();
    }

    private static SwingDetector macroSwingDetector(ElliottDegree baseDegree, int fractalWindow) {
        int clampedBaseWindow = Math.max(2, fractalWindow);
        return (series, index, analyzedDegree) -> {
            int fastWindow = scaledWindow(baseDegree, analyzedDegree, clampedBaseWindow, 2, 34);
            int slowBaseWindow = Math.max(fastWindow + 1, (clampedBaseWindow * 2) + 1);
            int slowWindow = scaledWindow(baseDegree, analyzedDegree, slowBaseWindow, fastWindow + 1, 89);
            AdaptiveZigZagConfig zigZagConfig = new AdaptiveZigZagConfig(Math.max(8, fastWindow * 4),
                    1.0 + (clampedBaseWindow * 0.08), 0.0, 0.0, Math.max(2, clampedBaseWindow));
            return SwingDetectors
                    .composite(CompositeSwingDetector.Policy.OR, SwingDetectors.adaptiveZigZag(zigZagConfig),
                            SwingDetectors.fractal(fastWindow), SwingDetectors.fractal(slowWindow))
                    .detect(series, index, analyzedDegree);
        };
    }

    private static int scaledWindow(ElliottDegree baseDegree, ElliottDegree analyzedDegree, int baseWindow,
            int minWindow, int maxWindow) {
        int delta = baseDegree.ordinal() - analyzedDegree.ordinal();
        double scaled = baseWindow * Math.pow(1.45, delta);
        int rounded = (int) Math.round(scaled);
        return Math.max(minWindow, Math.min(maxWindow, rounded));
    }

    static ReportBundle generateDefaultReport() {
        return generateDefaultReport(ArtifactSink.noOp());
    }

    static ReportBundle generateDefaultReport(ArtifactSink artifactSink) {
        Instant startedAt = Instant.now();
        LOG.info("Loading BTC anchor registry document");
        ElliottWaveAnchorRegistry registryDocument = ElliottWaveAnchorRegistry
                .load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        LOG.info("Loading BTC calibration series {}", registryDocument.datasetResource());
        BarSeries btcSeries = requireSeries(registryDocument.datasetResource(), BTC_SERIES_NAME);
        LOG.info("Loading portability series {}", ETH_RESOURCE);
        BarSeries ethSeries = loadSeries(ETH_RESOURCE, ETH_SERIES_NAME).orElse(null);
        LOG.info("Loading portability series {}", SP500_RESOURCE);
        BarSeries sp500Series = loadSeries(SP500_RESOURCE, SP500_SERIES_NAME).orElse(null);

        WalkForwardConfig config = ElliottWaveWalkForwardProfiles.baselineConfig();
        AnchorRegistry registry = defaultBitcoinAnchors(registryDocument, btcSeries);
        List<CandidateProfile> profiles = defaultProfiles();
        LOG.info("Resolved {} BTC anchors across {} profiles; BTC bars={}", registry.anchors().size(), profiles.size(),
                btcSeries.getBarCount());

        List<CandidateEvaluation> evaluations = evaluateCandidatesSequentially(btcSeries, registry, profiles, config,
                artifactSink);

        WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> portabilityEngine = buildEngine();

        CandidateEvaluation baseline = evaluations.stream()
                .filter(evaluation -> evaluation.profile().baselineCandidate())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("baseline profile evaluation missing"));

        List<ChallengerAssessment> challengerAssessments = rankChallengers(baseline, evaluations);
        PromotionDecision decision = PromotionDecision.from(baseline, challengerAssessments);
        CandidateEvaluation selected = selectedEvaluation(baseline, challengerAssessments, decision);
        LOG.info("Selected profile {} after challenger ranking in {}", selected.profile().id(),
                formatElapsed(startedAt));
        artifactSink.recordSelectedHistoricalCalibration(selected, HistoricalCalibrationReport.from(selected),
                decision);

        List<PortabilitySummary> portability = List.of(
                evaluatePortabilityWithProgress("eth-usd", ETH_RESOURCE, ethSeries, baseline.profile(),
                        selected.profile(), portabilityEngine, config, artifactSink),
                evaluatePortabilityWithProgress("sp500", SP500_RESOURCE, sp500Series, baseline.profile(),
                        selected.profile(), portabilityEngine, config, artifactSink));

        BaselinePolicy baselinePolicy = new BaselinePolicy(registry.datasetResource(), config.primaryHorizonBars(),
                List.copyOf(config.reportingHorizons()), config.configHash(),
                ElliottWaveWalkForwardProfiles.baseline().metadata().getOrDefault("profile", "baseline"));

        LOG.info("Built BTC anchor calibration report in {}", formatElapsed(startedAt));
        ReportBundle report = ReportBundle.create("btc-anchor-calibration-v2",
                btcSeries.getBar(btcSeries.getEndIndex()).getEndTime(), registry, baselinePolicy, baseline,
                challengerAssessments, decision, portability);
        artifactSink.recordFinalReport(report);
        return report;
    }

    private static List<CandidateEvaluation> evaluateCandidatesSequentially(BarSeries series, AnchorRegistry registry,
            List<CandidateProfile> profiles, WalkForwardConfig config, ArtifactSink artifactSink) {
        List<CandidateEvaluation> evaluations = new ArrayList<>(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            CandidateProfile profile = profiles.get(i);
            Instant startedAt = Instant.now();
            LOG.info("Evaluating calibration profile {}/{}: {} ({})", i + 1, profiles.size(), profile.id(),
                    profile.rationale());
            CandidateEvaluation evaluation = evaluateCandidate(series, registry, profile, buildEngine(), config);
            evaluations.add(evaluation);
            artifactSink.recordCandidateEvaluation(evaluation);
            LOG.info("Finished calibration profile {} in {}", profile.id(), formatElapsed(startedAt));
        }
        return List.copyOf(evaluations);
    }

    private static PortabilitySummary evaluatePortabilityWithProgress(String datasetId, String resource,
            BarSeries series, CandidateProfile baselineProfile, CandidateProfile selectedProfile,
            WalkForwardEngine<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> portabilityEngine,
            WalkForwardConfig config, ArtifactSink artifactSink) {
        Instant startedAt = Instant.now();
        LOG.info("Starting portability check dataset={} resource={}", datasetId, resource);
        PortabilitySummary summary = evaluatePortability(datasetId, resource, series, baselineProfile, selectedProfile,
                portabilityEngine, config);
        artifactSink.recordPortabilitySummary(summary);
        LOG.info("Finished portability check dataset={} in {}", datasetId, formatElapsed(startedAt));
        return summary;
    }

    private static Path defaultArtifactDirectory(Instant startedAt) {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(startedAt);
        return Path.of(".agents", "logs", "ew-anchor-calibration-" + timestamp);
    }

    private static String formatElapsed(Instant startedAt) {
        return formatElapsed(Duration.between(startedAt, Instant.now()));
    }

    private static String formatElapsed(Duration elapsed) {
        long seconds = Math.max(0L, elapsed.getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    static AnchorRegistry defaultBitcoinAnchors(BarSeries series) {
        ElliottWaveAnchorRegistry registry = ElliottWaveAnchorRegistry.load(ElliottWaveAnchorRegistry.DEFAULT_RESOURCE);
        return defaultBitcoinAnchors(registry, series);
    }

    private static AnchorRegistry defaultBitcoinAnchors(ElliottWaveAnchorRegistry registry, BarSeries series) {
        List<ElliottWaveAnchorRegistry.ResolvedAnchor> resolvedAnchors = registry.resolve(series,
                BTC_HOLDOUT_ANCHOR_COUNT);
        List<Anchor> anchors = resolvedAnchors.stream().map(ElliottWaveAnchorCalibrationHarness::toAnchor).toList();
        return new AnchorRegistry(registry.registryId(), registry.datasetResource(), registry.provenance(), anchors);
    }

    static List<CandidateProfile> defaultProfiles() {
        return List.of(CandidateProfile.baseline(),
                CandidateProfile.of("minute-f3-h2l2-max25-sw0", ElliottDegree.MINUTE, 2, 2, 25, 0, 3,
                        "Tighter swing confirmation near transition windows"),
                CandidateProfile.of("minute-f2-h1l1-max25-sw0", ElliottDegree.MINUTE, 1, 1, 25, 0, 2,
                        "Less cross-degree drag when the base count is already clear"),
                CandidateProfile.of("minute-f2-h1l2-max25-sw0", ElliottDegree.MINUTE, 1, 2, 25, 0, 2,
                        "Relax higher-degree drag while keeping lower-degree confirmation for holdout reversals"),
                CandidateProfile.of("minute-f2-h2l1-max25-sw0", ElliottDegree.MINUTE, 2, 1, 25, 0, 2,
                        "Keep higher-degree context but lighten lower-degree confirmation near macro turns"),
                CandidateProfile.of("minute-f2-h1l1-max25-sw1", ElliottDegree.MINUTE, 1, 1, 25, 1, 2,
                        "Keep the best challenger profile but focus retained scenarios nearer the transition window"),
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
        CyclePartitions cycles = summarizeCycles(series, registry, runResult);
        RuntimeInstrumentationSummary runtimeInstrumentation = RuntimeInstrumentationSummary.from(runResult);
        return CandidateEvaluation.create(profile, runResult.manifest(), metricsByHorizon, anchors, cycles,
                runtimeInstrumentation, config.primaryHorizonBars());
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

            if (holdoutAnchor) {
                holdoutSummaries.add(AnchorWindowSummary.from(anchor, anchorIndex, bounds, anchorHoldout));
            } else {
                validationSummaries.add(AnchorWindowSummary.from(anchor, anchorIndex, bounds, anchorValidation));
            }
        }

        AnchorAggregate validation = AnchorAggregate.from(validationSummaries, validationMatches);
        AnchorAggregate holdout = AnchorAggregate.from(holdoutSummaries, holdoutMatches);
        double stabilityDegradation = degradation(validation.top3HitRate(), holdout.top3HitRate());
        return new AnchorPartitions(validation, holdout, stabilityDegradation);
    }

    static CyclePartitions summarizeCycles(BarSeries series, AnchorRegistry registry,
            WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult) {
        Map<String, WalkForwardSplit> splitsById = new LinkedHashMap<>();
        for (WalkForwardSplit split : runResult.splits()) {
            splitsById.put(split.foldId(), split);
        }

        List<CycleSummary> validationCycles = new ArrayList<>();
        List<CycleSummary> holdoutCycles = new ArrayList<>();
        for (CycleTriplet cycle : completedCycles(registry)) {
            int peakAnchorIndex = nearestIndex(series, cycle.peak().at());
            int lowAnchorIndex = nearestIndex(series, cycle.low().at());
            WindowBounds peakBounds = resolveWindowBounds(series, peakAnchorIndex, cycle.peak().toleranceBefore(),
                    cycle.peak().toleranceAfter());
            WindowBounds lowBounds = resolveWindowBounds(series, lowAnchorIndex, cycle.low().toleranceBefore(),
                    cycle.low().toleranceAfter());
            CycleWindowMatch peakMatch = bestCycleWindowMatch(runResult.snapshots(), splitsById, peakBounds,
                    peakAnchorIndex, cycle.peak().partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT,
                    ElliottPhase.WAVE5, true);
            CycleWindowMatch lowMatch = bestCycleWindowMatch(runResult.snapshots(), splitsById, lowBounds,
                    lowAnchorIndex, cycle.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT,
                    ElliottPhase.CORRECTIVE_C, false);
            CycleSummary summary = CycleSummary.from(series, cycle, peakAnchorIndex, peakBounds, peakMatch,
                    lowAnchorIndex, lowBounds, lowMatch);
            if (cycle.partition() == ElliottWaveAnchorRegistry.AnchorPartition.HOLDOUT) {
                holdoutCycles.add(summary);
            } else {
                validationCycles.add(summary);
            }
        }

        CycleAggregate validation = CycleAggregate.from(validationCycles);
        CycleAggregate holdout = CycleAggregate.from(holdoutCycles);
        return new CyclePartitions(validation, holdout,
                degradation(validation.orderedTop3HitRate(), holdout.orderedTop3HitRate()));
    }

    private static List<CycleTriplet> completedCycles(AnchorRegistry registry) {
        List<CycleTriplet> cycles = new ArrayList<>();
        Anchor start = null;
        Anchor peak = null;
        for (Anchor anchor : registry.anchors()) {
            if (anchor.type() == AnchorType.BOTTOM) {
                if (peak == null) {
                    start = anchor;
                    continue;
                }
                Anchor low = anchor;
                String cycleId = start.id() + "->" + peak.id() + "->" + low.id();
                cycles.add(new CycleTriplet(cycleId, start, peak, low, low.partition()));
                start = low;
                peak = null;
                continue;
            }
            if (start != null) {
                peak = anchor;
            }
        }
        return List.copyOf(cycles);
    }

    static GateEvaluation evaluatePromotionGates(CandidateEvaluation baseline, CandidateEvaluation challenger) {
        double baselineHoldoutTop3 = baseline.anchors().holdout().top3HitRate();
        double challengerHoldoutTop3 = challenger.anchors().holdout().top3HitRate();
        double baselineHoldoutTop1 = baseline.anchors().holdout().top1HitRate();
        double challengerHoldoutTop1 = challenger.anchors().holdout().top1HitRate();
        double baselineCycleTop3 = baseline.cycles().holdout().orderedTop3HitRate();
        double challengerCycleTop3 = challenger.cycles().holdout().orderedTop3HitRate();

        double top3Improvement = challengerHoldoutTop3 - baselineHoldoutTop3;
        double top1Improvement = challengerHoldoutTop1 - baselineHoldoutTop1;
        double cycleTop3Improvement = challengerCycleTop3 - baselineCycleTop3;
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
        boolean cycleGate = !Double.isFinite(baselineCycleTop3) || !Double.isFinite(challengerCycleTop3)
                || challengerCycleTop3 >= baselineCycleTop3;
        boolean calibrationImprovementGate = calibrationDataAvailable && calibrationImprovedCount >= 2;
        boolean calibrationDegradationGate = calibrationDataAvailable && maximumRelativeCalibrationDegradation <= 0.05;
        boolean stabilityGate = Double.isFinite(stabilityDegradation) && stabilityDegradation <= 0.10;
        boolean artifactGate = !challenger.manifest().configHash().isBlank() && !challenger.artifactHash().isBlank();

        List<String> notes = new ArrayList<>();
        if (!cycleGate) {
            notes.add("holdout ordered BTC cycle hit-rate regressed");
        }
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

        return new GateEvaluation(cycleTop3Improvement, top3Improvement, top1Improvement, calibrationImprovedCount,
                maximumRelativeCalibrationDegradation, stabilityDegradation, cycleGate, top3Gate, top1Gate,
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
                        .comparingDouble((ChallengerAssessment assessment) -> assessment.gates().cycleTop3Improvement())
                        .reversed())
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

    private static CycleWindowMatch bestCycleWindowMatch(
            List<PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment>> snapshots,
            Map<String, WalkForwardSplit> splitsById, WindowBounds bounds, int anchorIndex, boolean holdoutPartition,
            ElliottPhase phase, boolean bullish) {
        CycleWindowMatch best = null;
        for (PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment> snapshot : snapshots) {
            if (snapshot.decisionIndex() < bounds.startIndex() || snapshot.decisionIndex() > bounds.endIndex()) {
                continue;
            }
            WalkForwardSplit split = splitsById.get(snapshot.foldId());
            if (split == null || split.holdout() != holdoutPartition) {
                continue;
            }

            int bestRank = bestMatchingRank(snapshot.topPredictions(), phase, bullish);
            if (bestRank == 0) {
                continue;
            }

            CycleWindowMatch candidate = new CycleWindowMatch(snapshot.decisionIndex(), bestRank,
                    Math.abs(snapshot.decisionIndex() - anchorIndex));
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int bestMatchingRank(
            List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predictions, ElliottPhase phase,
            boolean bullish) {
        int bestRank = Integer.MAX_VALUE;
        for (RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment> prediction : predictions) {
            ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = prediction.payload();
            if (assessment == null || !matchesPhaseAndDirection(assessment.scenario(), phase, bullish)) {
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

    private static boolean matchesPhaseAndDirection(ElliottScenario scenario, ElliottPhase phase, boolean bullish) {
        if (scenario == null || !scenario.hasKnownDirection() || scenario.currentPhase() != phase) {
            return false;
        }
        return bullish ? scenario.isBullish() : scenario.isBearish();
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
        return immutableSortedMap(converted);
    }

    private static Map<String, Double> averageNonHoldoutMetrics(Map<String, Map<String, Num>> perFold,
            String holdoutFoldId) {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Num>> foldEntry : perFold.entrySet()) {
            if (holdoutFoldId != null && holdoutFoldId.equals(foldEntry.getKey())) {
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
        return immutableSortedMap(averaged);
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSortedMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(source));
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
     * Incremental artifact sink for long-running calibration runs.
     */
    interface ArtifactSink {

        static ArtifactSink noOp() {
            return new ArtifactSink() {
            };
        }

        default Path outputDirectory() {
            return null;
        }

        default void recordCandidateEvaluation(CandidateEvaluation evaluation) {
        }

        default void recordSelectedHistoricalCalibration(CandidateEvaluation evaluation,
                HistoricalCalibrationReport calibration, PromotionDecision decision) {
        }

        default void recordPortabilitySummary(PortabilitySummary summary) {
        }

        default void recordFinalReport(ReportBundle report) {
        }
    }

    /**
     * Filesystem-backed artifact sink for incremental calibration persistence.
     */
    record FileArtifactSink(Path outputDirectory) implements ArtifactSink {

        FileArtifactSink {
            Objects.requireNonNull(outputDirectory, "outputDirectory");
        }

        static FileArtifactSink create(Path outputDirectory) {
            try {
                Files.createDirectories(outputDirectory);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException("Failed to create artifact directory " + outputDirectory, e);
            }
            return new FileArtifactSink(outputDirectory.toAbsolutePath());
        }

        @Override
        public void recordCandidateEvaluation(CandidateEvaluation evaluation) {
            writeJson("btc-candidate-" + safeFileId(evaluation.profile().id()) + ".json", evaluation);
        }

        @Override
        public void recordSelectedHistoricalCalibration(CandidateEvaluation evaluation,
                HistoricalCalibrationReport calibration, PromotionDecision decision) {
            writeJson("btc-selected-candidate.json", evaluation);
            writeJson("btc-promotion-decision.json", decision);
            writeJson("btc-selected-historical-calibration.json", calibration);
            writeText("btc-selected-historical-calibration.txt", calibration.toText());
        }

        @Override
        public void recordPortabilitySummary(PortabilitySummary summary) {
            writeJson("portability-" + safeFileId(summary.datasetId()) + ".json", summary);
        }

        @Override
        public void recordFinalReport(ReportBundle report) {
            writeJson("ew-anchor-report.json", report);
        }

        private void writeJson(String fileName, Object value) {
            writeText(fileName, GSON.toJson(value));
        }

        private void writeText(String fileName, String content) {
            try {
                Files.writeString(outputDirectory.resolve(fileName), content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException("Failed to write artifact " + outputDirectory.resolve(fileName), e);
            }
        }

        private static String safeFileId(String value) {
            return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]+", "-");
        }
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
            ElliottWaveAnalysisRunner macroRunner = buildMacroAnalysisRunner(
                    ElliottWaveWalkForwardProfiles.BASELINE_DEGREE, 2, 2, 25, 0, 2);
            ElliottWaveWalkForwardContext macroContext = new ElliottWaveWalkForwardContext(macroRunner,
                    context.seriesSelector(), context.maxPredictions(), context.metadata());
            return new CandidateProfile(id, ElliottWaveWalkForwardProfiles.BASELINE_DEGREE, 2, 2, 25, 0, 2, true,
                    "Locked baseline profile used for cross-run comparability", macroContext);
        }

        static CandidateProfile baselineProfile() {
            return baseline();
        }

        static CandidateProfile of(String id, ElliottDegree degree, int higherDegrees, int lowerDegrees,
                int maxScenarios, int scenarioSwingWindow, int fractalWindow, String rationale) {
            ElliottWaveAnalysisRunner runner = buildMacroAnalysisRunner(degree, higherDegrees, lowerDegrees,
                    maxScenarios, scenarioSwingWindow, fractalWindow);
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
            return immutableSortedMap(manifest);
        }
    }

    /**
     * Summarized BTC evaluation for one candidate profile.
     */
    record CandidateEvaluation(CandidateProfile profile, WalkForwardExperimentManifest manifest, int primaryHorizonBars,
            Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors, CyclePartitions cycles,
            RuntimeInstrumentationSummary runtimeInstrumentation, String artifactId, String artifactHash) {

        CandidateEvaluation {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(manifest, "manifest");
            metricsByHorizon = immutableSortedMap(metricsByHorizon);
            Objects.requireNonNull(anchors, "anchors");
            Objects.requireNonNull(cycles, "cycles");
            Objects.requireNonNull(runtimeInstrumentation, "runtimeInstrumentation");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(artifactHash, "artifactHash");
        }

        static CandidateEvaluation create(CandidateProfile profile, WalkForwardExperimentManifest manifest,
                Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors, CyclePartitions cycles,
                RuntimeInstrumentationSummary runtimeInstrumentation, int primaryHorizonBars) {
            String artifactId = manifest.candidateId() + "|cfg=" + manifest.configHash();
            Map<Integer, MetricSnapshot> canonicalMetricsByHorizon = immutableSortedMap(metricsByHorizon);
            UnsignedCandidateEvaluation unsigned = new UnsignedCandidateEvaluation(profile, manifest,
                    primaryHorizonBars, canonicalMetricsByHorizon, anchors, cycles, runtimeInstrumentation, artifactId);
            return new CandidateEvaluation(profile, manifest, primaryHorizonBars, canonicalMetricsByHorizon, anchors,
                    cycles, runtimeInstrumentation, artifactId, sha256(GSON.toJson(unsigned)));
        }

        MetricSnapshot primaryMetrics() {
            return metricsByHorizon.getOrDefault(primaryHorizonBars, MetricSnapshot.empty());
        }
    }

    /**
     * Runtime instrumentation summary for one calibration candidate.
     */
    record RuntimeInstrumentationSummary(Duration overallRuntime, Duration minFoldRuntime, Duration maxFoldRuntime,
            Duration averageFoldRuntime, Duration medianFoldRuntime, List<RuntimeFoldSummary> folds,
            List<RuntimeSnapshotSummary> slowestSnapshots) {

        private static final int SLOWEST_SNAPSHOT_LIMIT = 5;

        RuntimeInstrumentationSummary {
            Objects.requireNonNull(overallRuntime, "overallRuntime");
            Objects.requireNonNull(minFoldRuntime, "minFoldRuntime");
            Objects.requireNonNull(maxFoldRuntime, "maxFoldRuntime");
            Objects.requireNonNull(averageFoldRuntime, "averageFoldRuntime");
            Objects.requireNonNull(medianFoldRuntime, "medianFoldRuntime");
            folds = folds == null ? List.of() : List.copyOf(folds);
            slowestSnapshots = slowestSnapshots == null ? List.of() : List.copyOf(slowestSnapshots);
        }

        static RuntimeInstrumentationSummary empty() {
            return new RuntimeInstrumentationSummary(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                    Duration.ZERO, List.of(), List.of());
        }

        static RuntimeInstrumentationSummary from(
                WalkForwardRunResult<ElliottWaveAnalysisResult.BaseScenarioAssessment, ElliottWaveOutcome> runResult) {
            if (runResult == null) {
                return empty();
            }
            WalkForwardRuntimeReport report = runResult.runtimeReport();
            if (report == null) {
                return empty();
            }
            Map<String, SnapshotAnalysisSummary> analysisBySnapshotKey = runResult.snapshots()
                    .stream()
                    .collect(Collectors.toMap(PredictionSnapshot::snapshotKey,
                            RuntimeInstrumentationSummary::summarizeSnapshotAnalysis, (left, right) -> left,
                            LinkedHashMap::new));
            List<RuntimeFoldSummary> folds = report.foldRuntimes()
                    .stream()
                    .map(fold -> RuntimeFoldSummary.from(fold, analysisBySnapshotKey))
                    .toList();
            List<RuntimeSnapshotSummary> slowestSnapshots = report.foldRuntimes()
                    .stream()
                    .flatMap(fold -> fold.snapshotRuntimes()
                            .stream()
                            .map(snapshot -> RuntimeSnapshotSummary.from(fold.foldId(), snapshot,
                                    analysisBySnapshotKey.get(snapshotKey(fold.foldId(), snapshot.decisionIndex())))))
                    .sorted(Comparator.comparing(RuntimeSnapshotSummary::runtime)
                            .reversed()
                            .thenComparing(RuntimeSnapshotSummary::foldId)
                            .thenComparingInt(RuntimeSnapshotSummary::decisionIndex))
                    .limit(SLOWEST_SNAPSHOT_LIMIT)
                    .toList();
            return new RuntimeInstrumentationSummary(report.overallRuntime(), report.minFoldRuntime(),
                    report.maxFoldRuntime(), report.averageFoldRuntime(), report.medianFoldRuntime(), folds,
                    slowestSnapshots);
        }

        String toText() {
            StringBuilder builder = new StringBuilder();
            builder.append("overall=")
                    .append(overallRuntime)
                    .append(", folds=")
                    .append(folds.size())
                    .append(", foldMin=")
                    .append(minFoldRuntime)
                    .append(", foldMax=")
                    .append(maxFoldRuntime)
                    .append(", foldAvg=")
                    .append(averageFoldRuntime)
                    .append(", foldMedian=")
                    .append(medianFoldRuntime)
                    .append(System.lineSeparator());
            for (RuntimeFoldSummary fold : folds) {
                builder.append("fold ")
                        .append(fold.foldId())
                        .append(": runtime=")
                        .append(fold.runtime())
                        .append(", snapshots=")
                        .append(fold.snapshotCount())
                        .append(", snapshotMin=")
                        .append(fold.minSnapshotRuntime())
                        .append(", snapshotMax=")
                        .append(fold.maxSnapshotRuntime())
                        .append(", snapshotAvg=")
                        .append(fold.averageSnapshotRuntime())
                        .append(", snapshotMedian=")
                        .append(fold.medianSnapshotRuntime())
                        .append(", scenariosBeforePruneAvg=")
                        .append(fold.averageCandidateScenarioCountBeforePrune())
                        .append(", scenariosRetainedAvg=")
                        .append(fold.averageRetainedScenarioCount())
                        .append(", impulseBranches=")
                        .append(fold.totalImpulseDecompositionBranchCount())
                        .append(", correctiveBranches=")
                        .append(fold.totalCorrectiveDecompositionBranchCount())
                        .append(System.lineSeparator());
            }
            if (!slowestSnapshots.isEmpty()) {
                builder.append("slowestSnapshots=").append(System.lineSeparator());
                for (RuntimeSnapshotSummary snapshot : slowestSnapshots) {
                    builder.append("  ")
                            .append(snapshot.foldId())
                            .append("@")
                            .append(snapshot.decisionIndex())
                            .append(" runtime=")
                            .append(snapshot.runtime())
                            .append(", predictions=")
                            .append(snapshot.predictionCount())
                            .append(", scenariosBeforePrune=")
                            .append(snapshot.candidateScenarioCountBeforePrune())
                            .append(", scenariosRetained=")
                            .append(snapshot.retainedScenarioCount())
                            .append(", impulseBranches=")
                            .append(snapshot.impulseDecompositionBranchCount())
                            .append(", correctiveBranches=")
                            .append(snapshot.correctiveDecompositionBranchCount())
                            .append(System.lineSeparator());
                }
            }
            return builder.toString().trim();
        }

        private static SnapshotAnalysisSummary summarizeSnapshotAnalysis(
                PredictionSnapshot<ElliottWaveAnalysisResult.BaseScenarioAssessment> snapshot) {
            if (snapshot == null || snapshot.topPredictions().isEmpty()) {
                return SnapshotAnalysisSummary.empty();
            }
            ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = snapshot.topPredictions()
                    .getFirst()
                    .payload();
            if (assessment == null) {
                return SnapshotAnalysisSummary.empty();
            }
            return SnapshotAnalysisSummary.from(assessment.diagnostics());
        }

        private static String snapshotKey(String foldId, int decisionIndex) {
            return foldId + "@" + decisionIndex;
        }
    }

    /**
     * Fold-level runtime instrumentation summary.
     */
    record RuntimeFoldSummary(String foldId, Duration runtime, int snapshotCount, Duration minSnapshotRuntime,
            Duration maxSnapshotRuntime, Duration averageSnapshotRuntime, Duration medianSnapshotRuntime,
            int averageCandidateScenarioCountBeforePrune, int averageRetainedScenarioCount,
            int totalImpulseDecompositionBranchCount, int totalCorrectiveDecompositionBranchCount) {

        RuntimeFoldSummary {
            Objects.requireNonNull(foldId, "foldId");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(minSnapshotRuntime, "minSnapshotRuntime");
            Objects.requireNonNull(maxSnapshotRuntime, "maxSnapshotRuntime");
            Objects.requireNonNull(averageSnapshotRuntime, "averageSnapshotRuntime");
            Objects.requireNonNull(medianSnapshotRuntime, "medianSnapshotRuntime");
        }

        static RuntimeFoldSummary from(WalkForwardRuntimeReport.FoldRuntime foldRuntime,
                Map<String, SnapshotAnalysisSummary> analysisBySnapshotKey) {
            int totalBeforePrune = 0;
            int totalRetained = 0;
            int totalImpulseBranches = 0;
            int totalCorrectiveBranches = 0;
            for (WalkForwardRuntimeReport.SnapshotRuntime snapshotRuntime : foldRuntime.snapshotRuntimes()) {
                SnapshotAnalysisSummary analysis = analysisBySnapshotKey.getOrDefault(RuntimeInstrumentationSummary
                        .snapshotKey(foldRuntime.foldId(), snapshotRuntime.decisionIndex()),
                        SnapshotAnalysisSummary.empty());
                totalBeforePrune += analysis.candidateScenarioCountBeforePrune();
                totalRetained += analysis.retainedScenarioCount();
                totalImpulseBranches += analysis.impulseDecompositionBranchCount();
                totalCorrectiveBranches += analysis.correctiveDecompositionBranchCount();
            }
            int snapshotCount = Math.max(1, foldRuntime.snapshotCount());
            return new RuntimeFoldSummary(foldRuntime.foldId(), foldRuntime.runtime(), foldRuntime.snapshotCount(),
                    foldRuntime.minSnapshotRuntime(), foldRuntime.maxSnapshotRuntime(),
                    foldRuntime.averageSnapshotRuntime(), foldRuntime.medianSnapshotRuntime(),
                    totalBeforePrune / snapshotCount, totalRetained / snapshotCount, totalImpulseBranches,
                    totalCorrectiveBranches);
        }
    }

    /**
     * Slow-snapshot runtime detail surfaced in the candidate report.
     */
    record RuntimeSnapshotSummary(String foldId, int decisionIndex, Duration runtime, int predictionCount,
            int candidateScenarioCountBeforePrune, int retainedScenarioCount, int impulseDecompositionBranchCount,
            int correctiveDecompositionBranchCount) {

        RuntimeSnapshotSummary {
            Objects.requireNonNull(foldId, "foldId");
            Objects.requireNonNull(runtime, "runtime");
        }

        static RuntimeSnapshotSummary from(String foldId, WalkForwardRuntimeReport.SnapshotRuntime snapshotRuntime,
                SnapshotAnalysisSummary analysis) {
            SnapshotAnalysisSummary resolved = analysis == null ? SnapshotAnalysisSummary.empty() : analysis;
            return new RuntimeSnapshotSummary(foldId, snapshotRuntime.decisionIndex(), snapshotRuntime.runtime(),
                    snapshotRuntime.predictionCount(), resolved.candidateScenarioCountBeforePrune(),
                    resolved.retainedScenarioCount(), resolved.impulseDecompositionBranchCount(),
                    resolved.correctiveDecompositionBranchCount());
        }
    }

    /**
     * Scenario-search counters observed for one snapshot analysis.
     */
    record SnapshotAnalysisSummary(int candidateScenarioCountBeforePrune, int retainedScenarioCount,
            int impulseDecompositionBranchCount, int correctiveDecompositionBranchCount) {

        SnapshotAnalysisSummary {
            if (candidateScenarioCountBeforePrune < 0) {
                throw new IllegalArgumentException("candidateScenarioCountBeforePrune must be >= 0");
            }
            if (retainedScenarioCount < 0) {
                throw new IllegalArgumentException("retainedScenarioCount must be >= 0");
            }
            if (impulseDecompositionBranchCount < 0) {
                throw new IllegalArgumentException("impulseDecompositionBranchCount must be >= 0");
            }
            if (correctiveDecompositionBranchCount < 0) {
                throw new IllegalArgumentException("correctiveDecompositionBranchCount must be >= 0");
            }
        }

        static SnapshotAnalysisSummary empty() {
            return new SnapshotAnalysisSummary(0, 0, 0, 0);
        }

        static SnapshotAnalysisSummary from(ElliottAnalysisResult.AnalysisDiagnostics diagnostics) {
            ElliottAnalysisResult.AnalysisDiagnostics resolved = diagnostics == null
                    ? ElliottAnalysisResult.AnalysisDiagnostics.empty()
                    : diagnostics;
            return new SnapshotAnalysisSummary(resolved.candidateScenarioCountBeforePrune(),
                    resolved.retainedScenarioCount(), resolved.impulseDecompositionBranchCount(),
                    resolved.correctiveDecompositionBranchCount());
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
    record GateEvaluation(double cycleTop3Improvement, double holdoutTop3Improvement, double holdoutTop1Improvement,
            int calibrationImprovedCount, double maximumRelativeCalibrationDegradation,
            double validationToHoldoutTop3Degradation, boolean cycleGate, boolean top3Gate, boolean top1Gate,
            boolean calibrationImprovementGate, boolean calibrationDegradationGate, boolean stabilityGate,
            boolean artifactGate, boolean passed, List<String> notes) {

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
            if (!gates.cycleGate()) {
                hypotheses.add(
                        "Treat completed BTC cycles as first-class validation targets so bullish wave-5 peaks and bearish corrective-C lows cannot regress behind walk-forward calibration gains.");
            }
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
     * Aggregate completed BTC cycle fits split by cycle-completion partition.
     */
    record CyclePartitions(CycleAggregate validation, CycleAggregate holdout,
            double validationToHoldoutTop3Degradation) {

        CyclePartitions {
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(holdout, "holdout");
        }
    }

    /**
     * Aggregate ordered cycle hit-rates for one partition.
     */
    record CycleAggregate(int cycleCount, int topWindowMatchedCount, int lowWindowMatchedCount,
            double orderedTop1HitRate, double orderedTop3HitRate, List<CycleSummary> cycles) {

        CycleAggregate {
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
        }

        static CycleAggregate from(List<CycleSummary> cycles) {
            int topMatchedCount = 0;
            int lowMatchedCount = 0;
            int orderedTop1Hits = 0;
            int orderedTop3Hits = 0;
            for (CycleSummary cycle : cycles) {
                if (cycle.topBestRank() > 0) {
                    topMatchedCount++;
                }
                if (cycle.lowBestRank() > 0) {
                    lowMatchedCount++;
                }
                if (cycle.orderedTop1Hit()) {
                    orderedTop1Hits++;
                }
                if (cycle.orderedTop3Hit()) {
                    orderedTop3Hits++;
                }
            }
            int cycleCount = cycles.size();
            return new CycleAggregate(cycleCount, topMatchedCount, lowMatchedCount,
                    safeRate(orderedTop1Hits, cycleCount), safeRate(orderedTop3Hits, cycleCount), cycles);
        }

        private static double safeRate(int hits, int count) {
            return count == 0 ? Double.NaN : hits / (double) count;
        }
    }

    /**
     * Per-cycle summary for a completed bottom-top-bottom BTC sequence.
     */
    record CycleSummary(String partition, String cycleId, String startAnchorId, String peakAnchorId, String lowAnchorId,
            String startTimeUtc, String peakTimeUtc, String lowTimeUtc, String provenance, int peakAnchorIndex,
            int peakWindowStartIndex, int peakWindowEndIndex, int topBestRank, int topDecisionIndex,
            String topDecisionTimeUtc, int topDistanceBars, int lowAnchorIndex, int lowWindowStartIndex,
            int lowWindowEndIndex, int lowBestRank, int lowDecisionIndex, String lowDecisionTimeUtc,
            int lowDistanceBars, boolean orderedTop1Hit, boolean orderedTop3Hit) {

        CycleSummary {
            Objects.requireNonNull(partition, "partition");
            Objects.requireNonNull(cycleId, "cycleId");
            Objects.requireNonNull(startAnchorId, "startAnchorId");
            Objects.requireNonNull(peakAnchorId, "peakAnchorId");
            Objects.requireNonNull(lowAnchorId, "lowAnchorId");
            Objects.requireNonNull(startTimeUtc, "startTimeUtc");
            Objects.requireNonNull(peakTimeUtc, "peakTimeUtc");
            Objects.requireNonNull(lowTimeUtc, "lowTimeUtc");
            Objects.requireNonNull(provenance, "provenance");
        }

        static CycleSummary from(BarSeries series, CycleTriplet cycle, int peakAnchorIndex, WindowBounds peakBounds,
                CycleWindowMatch topMatch, int lowAnchorIndex, WindowBounds lowBounds, CycleWindowMatch lowMatch) {
            int topBestRank = topMatch == null ? 0 : topMatch.bestRank();
            int lowBestRank = lowMatch == null ? 0 : lowMatch.bestRank();
            int topDecisionIndex = topMatch == null ? -1 : topMatch.decisionIndex();
            int lowDecisionIndex = lowMatch == null ? -1 : lowMatch.decisionIndex();
            String topDecisionTimeUtc = decisionTimeUtc(series, topDecisionIndex);
            String lowDecisionTimeUtc = decisionTimeUtc(series, lowDecisionIndex);
            int topDistanceBars = topMatch == null ? -1 : topMatch.distanceBars();
            int lowDistanceBars = lowMatch == null ? -1 : lowMatch.distanceBars();
            boolean ordered = topDecisionIndex >= 0 && lowDecisionIndex >= 0 && topDecisionIndex < lowDecisionIndex;
            boolean orderedTop3Hit = ordered && topBestRank > 0 && topBestRank <= 3 && lowBestRank > 0
                    && lowBestRank <= 3;
            boolean orderedTop1Hit = ordered && topBestRank == 1 && lowBestRank == 1;
            String provenance = cycle.start().provenance() + " | " + cycle.peak().provenance() + " | "
                    + cycle.low().provenance();
            return new CycleSummary(cycle.partition().name().toLowerCase(), cycle.id(), cycle.start().id(),
                    cycle.peak().id(), cycle.low().id(), UTC_TIME.format(cycle.start().at()),
                    UTC_TIME.format(cycle.peak().at()), UTC_TIME.format(cycle.low().at()), provenance, peakAnchorIndex,
                    peakBounds.startIndex(), peakBounds.endIndex(), topBestRank, topDecisionIndex, topDecisionTimeUtc,
                    topDistanceBars, lowAnchorIndex, lowBounds.startIndex(), lowBounds.endIndex(), lowBestRank,
                    lowDecisionIndex, lowDecisionTimeUtc, lowDistanceBars, orderedTop1Hit, orderedTop3Hit);
        }

        private static String decisionTimeUtc(BarSeries series, int decisionIndex) {
            if (decisionIndex < series.getBeginIndex() || decisionIndex > series.getEndIndex()) {
                return "";
            }
            return UTC_TIME.format(series.getBar(decisionIndex).getEndTime());
        }
    }

    /**
     * Human-oriented historical calibration view over the selected candidate's
     * completed BTC cycles.
     */
    record HistoricalCalibrationReport(String profileId, int cycleCount, int matchedPeakCount, int matchedLowCount,
            int orderedTop1HitCount, int orderedTop3HitCount, List<HistoricalCycleCalibration> cycles) {

        HistoricalCalibrationReport {
            Objects.requireNonNull(profileId, "profileId");
            cycles = cycles == null ? List.of() : List.copyOf(cycles);
        }

        static HistoricalCalibrationReport from(CandidateEvaluation evaluation) {
            List<HistoricalCycleCalibration> cycles = new ArrayList<>();
            cycles.addAll(
                    evaluation.cycles().validation().cycles().stream().map(HistoricalCycleCalibration::from).toList());
            cycles.addAll(
                    evaluation.cycles().holdout().cycles().stream().map(HistoricalCycleCalibration::from).toList());

            int matchedPeakCount = 0;
            int matchedLowCount = 0;
            int orderedTop1HitCount = 0;
            int orderedTop3HitCount = 0;
            for (HistoricalCycleCalibration cycle : cycles) {
                if (cycle.peakMatched()) {
                    matchedPeakCount++;
                }
                if (cycle.lowMatched()) {
                    matchedLowCount++;
                }
                if (cycle.orderedTop1Hit()) {
                    orderedTop1HitCount++;
                }
                if (cycle.orderedTop3Hit()) {
                    orderedTop3HitCount++;
                }
            }
            return new HistoricalCalibrationReport(evaluation.profile().id(), cycles.size(), matchedPeakCount,
                    matchedLowCount, orderedTop1HitCount, orderedTop3HitCount, cycles);
        }

        String toText() {
            StringBuilder builder = new StringBuilder();
            builder.append("profile=").append(profileId).append(System.lineSeparator());
            builder.append("cycles=")
                    .append(cycleCount)
                    .append(", matchedPeaks=")
                    .append(matchedPeakCount)
                    .append(", matchedLows=")
                    .append(matchedLowCount)
                    .append(", orderedTop1=")
                    .append(orderedTop1HitCount)
                    .append(", orderedTop3=")
                    .append(orderedTop3HitCount)
                    .append(System.lineSeparator());
            for (HistoricalCycleCalibration cycle : cycles) {
                builder.append(cycle.partition())
                        .append(" ")
                        .append(cycle.cycleId())
                        .append(": peak ")
                        .append(cycle.peakAnchorId())
                        .append(" expected ")
                        .append(cycle.expectedPeakTimeUtc())
                        .append(" matched ")
                        .append(cycle.peakMatchedTimeUtc().isBlank() ? "none" : cycle.peakMatchedTimeUtc())
                        .append(" rank=")
                        .append(cycle.peakBestRank())
                        .append(" deltaBars=")
                        .append(cycle.peakDistanceBars())
                        .append(" | low ")
                        .append(cycle.lowAnchorId())
                        .append(" expected ")
                        .append(cycle.expectedLowTimeUtc())
                        .append(" matched ")
                        .append(cycle.lowMatchedTimeUtc().isBlank() ? "none" : cycle.lowMatchedTimeUtc())
                        .append(" rank=")
                        .append(cycle.lowBestRank())
                        .append(" deltaBars=")
                        .append(cycle.lowDistanceBars())
                        .append(System.lineSeparator());
            }
            return builder.toString().trim();
        }
    }

    /**
     * One completed historical BTC cycle expressed as expected versus matched
     * walk-forward decisions.
     */
    record HistoricalCycleCalibration(String partition, String cycleId, String startAnchorId, String peakAnchorId,
            String lowAnchorId, String expectedPeakTimeUtc, String peakMatchedTimeUtc, int peakBestRank,
            int peakDistanceBars, boolean peakMatched, String expectedLowTimeUtc, String lowMatchedTimeUtc,
            int lowBestRank, int lowDistanceBars, boolean lowMatched, boolean orderedTop1Hit, boolean orderedTop3Hit) {

        HistoricalCycleCalibration {
            Objects.requireNonNull(partition, "partition");
            Objects.requireNonNull(cycleId, "cycleId");
            Objects.requireNonNull(startAnchorId, "startAnchorId");
            Objects.requireNonNull(peakAnchorId, "peakAnchorId");
            Objects.requireNonNull(lowAnchorId, "lowAnchorId");
            Objects.requireNonNull(expectedPeakTimeUtc, "expectedPeakTimeUtc");
            Objects.requireNonNull(peakMatchedTimeUtc, "peakMatchedTimeUtc");
            Objects.requireNonNull(expectedLowTimeUtc, "expectedLowTimeUtc");
            Objects.requireNonNull(lowMatchedTimeUtc, "lowMatchedTimeUtc");
        }

        static HistoricalCycleCalibration from(CycleSummary cycle) {
            return new HistoricalCycleCalibration(cycle.partition(), cycle.cycleId(), cycle.startAnchorId(),
                    cycle.peakAnchorId(), cycle.lowAnchorId(), cycle.peakTimeUtc(), cycle.topDecisionTimeUtc(),
                    cycle.topBestRank(), cycle.topDistanceBars(), cycle.topBestRank() > 0, cycle.lowTimeUtc(),
                    cycle.lowDecisionTimeUtc(), cycle.lowBestRank(), cycle.lowDistanceBars(), cycle.lowBestRank() > 0,
                    cycle.orderedTop1Hit(), cycle.orderedTop3Hit());
        }
    }

    /**
     * Aggregate snapshot hit rates and rank distribution for one partition.
     */
    record AnchorAggregate(int anchorCount, int sampleCount, double top1HitRate, double top3HitRate,
            Map<String, Integer> bestRankDistribution, List<AnchorWindowSummary> anchorWindows) {

        AnchorAggregate {
            bestRankDistribution = immutableSortedMap(bestRankDistribution);
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
            bestRankDistribution = immutableSortedMap(bestRankDistribution);
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
            global = immutableSortedMap(global);
            validation = immutableSortedMap(validation);
            holdout = immutableSortedMap(holdout);
        }

        static MetricSnapshot from(WalkForwardRunResult<?, ?> runResult, int horizon) {
            Map<String, Map<String, Num>> foldMetrics = runResult.foldMetricsForHorizon(horizon);
            String holdoutFoldId = runResult.holdoutSplit().map(WalkForwardSplit::foldId).orElse(null);
            return new MetricSnapshot(toDoubleMap(runResult.globalMetricsForHorizon(horizon)),
                    averageNonHoldoutMetrics(foldMetrics, holdoutFoldId),
                    toDoubleMap(holdoutFoldId == null ? Map.of() : foldMetrics.getOrDefault(holdoutFoldId, Map.of())));
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

        HistoricalCalibrationReport selectedHistoricalCalibration() {
            return HistoricalCalibrationReport.from(selectedEvaluation());
        }

        String historicalCalibrationText() {
            return selectedHistoricalCalibration().toText();
        }

        String runtimeInstrumentationText() {
            return selectedEvaluation().runtimeInstrumentation().toText();
        }

        private CandidateEvaluation selectedEvaluation() {
            if (decision.promoteChallenger()) {
                for (ChallengerAssessment assessment : challengerAssessments) {
                    if (assessment.evaluation().profile().id().equals(decision.selectedProfileId())) {
                        return assessment.evaluation();
                    }
                }
            }
            return baselineEvaluation;
        }
    }

    private record AnchorSnapshotMatch(String anchorId, AnchorType anchorType, String foldId, boolean holdout,
            int decisionIndex, int bestRank) {
    }

    private record WindowBounds(int startIndex, int endIndex) {
    }

    private record CycleTriplet(String id, Anchor start, Anchor peak, Anchor low,
            ElliottWaveAnchorRegistry.AnchorPartition partition) {
    }

    private record CycleWindowMatch(int decisionIndex, int bestRank,
            int distanceBars) implements Comparable<CycleWindowMatch> {

        @Override
        public int compareTo(CycleWindowMatch other) {
            int rankComparison = Integer.compare(bestRank, other.bestRank);
            if (rankComparison != 0) {
                return rankComparison;
            }
            int distanceComparison = Integer.compare(distanceBars, other.distanceBars);
            if (distanceComparison != 0) {
                return distanceComparison;
            }
            return Integer.compare(decisionIndex, other.decisionIndex);
        }
    }

    private record UnsignedCandidateEvaluation(CandidateProfile profile, WalkForwardExperimentManifest manifest,
            int primaryHorizonBars, Map<Integer, MetricSnapshot> metricsByHorizon, AnchorPartitions anchors,
            CyclePartitions cycles, RuntimeInstrumentationSummary runtimeInstrumentation, String artifactId) {
    }

    private record UnsignedReportBundle(String reportVersion, String generatedAtUtc, AnchorRegistry anchorRegistry,
            BaselinePolicy baselinePolicy, CandidateEvaluation baselineEvaluation,
            List<ChallengerAssessment> challengerAssessments, PromotionDecision decision,
            List<PortabilitySummary> portability) {
    }
}
