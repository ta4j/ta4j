/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.AnalysisRunner;
import org.ta4j.core.analysis.SeriesSelector;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.CompositeSwingDetector;
import org.ta4j.core.indicators.elliott.swing.MinMagnitudeSwingFilter;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectorResult;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.swing.SwingFilter;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Runs Elliott Wave one-shot analysis, optionally validating scenarios across
 * neighboring degrees.
 *
 * <p>
 * This is the analysis entry point for workflows where you want a complete,
 * end-to-end snapshot (swings, channel, scenarios, confidence breakdowns, trend
 * bias) rather than per-bar indicator outputs.
 *
 * <p>
 * If configured with supporting degrees ({@link Builder#higherDegrees(int)} and
 * {@link Builder#lowerDegrees(int)}), the analysis re-ranks the base-degree
 * {@link ElliottScenarioSet} by blending base confidence, structural breadth,
 * and compatibility against the best matching scenarios in the supporting
 * degrees.
 *
 * <p>
 * For indicator-style access (rules/strategies and chart overlays), use
 * {@link ElliottWaveFacade}.
 *
 * <p>
 * "Multi-timeframe" behavior is achieved by:
 * <ul>
 * <li>Running each degree on a potentially different series slice (lookback
 * window) via {@link SeriesSelector}.</li>
 * <li>Optionally providing a custom {@link AnalysisRunner} that works on
 * resampled/aggregated series (for example using
 * {@code BarSeriesUtils.aggregateBars(...)}) before analysis.</li>
 * </ul>
 *
 * @since 0.22.4
 */
public final class ElliottWaveAnalysisRunner {

    private static final AdaptiveZigZagConfig DEFAULT_ZIGZAG_CONFIG = new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3);
    private static final int DEFAULT_FAST_FRACTAL_WINDOW = 3;
    private static final int DEFAULT_SLOW_FRACTAL_WINDOW = 8;

    /**
     * Default scenario window.
     *
     * <p>
     * {@code 0} means the runner forwards the full processed swing history into
     * scenario generation so decomposition search can operate on broader structures
     * by default.
     */
    private static final int DEFAULT_SCENARIO_SWING_WINDOW = 0;
    private static final int BROAD_HISTORY_FILTER_BAR_THRESHOLD = 250;
    private static final int DEFAULT_COMPRESSOR_MIN_BARS = 2;
    private static final int BROAD_HISTORY_COMPRESSOR_MIN_BARS = 1;
    private static final int BROAD_HISTORY_INTERNAL_SCENARIO_MULTIPLIER = 40;
    private static final int BROAD_HISTORY_INTERNAL_SCENARIO_CAP = 1000;
    private static final int WINDOW_ANCHOR_SNAP_MIN_BARS = 8;
    private static final int WINDOW_ANCHOR_SNAP_MAX_BARS = 60;
    private static final double WINDOW_ANCHOR_SNAP_FRACTION = 0.15;
    private static final int CURRENT_CYCLE_MIN_WINDOW_BARS = 12;
    private static final double CURRENT_CYCLE_MIN_WINDOW_FRACTION = 0.05;
    private static final int CURRENT_CYCLE_START_CANDIDATE_LIMIT = 16;
    private static final int CURRENT_CYCLE_MAX_ANCHOR_DRIFT_BARS = 3;
    private static final double CURRENT_CYCLE_MIN_PIVOT_DOMINANCE = 0.55;
    private static final double CURRENT_CYCLE_MIN_TERMINAL_DOMINANCE = 0.75;
    private static final double CURRENT_CYCLE_FIT_SCORE_WEIGHT = 0.75;
    private static final double CURRENT_CYCLE_DOMINANCE_WEIGHT = 0.15;
    private static final double CURRENT_CYCLE_TERMINAL_WEIGHT = 0.10;
    private static final int MACRO_PIVOT_GRAPH_MAX_PIVOTS = 24;
    private static final double MACRO_PIVOT_GRAPH_MIN_DOMINANCE = 0.55;
    private static final int CANONICAL_SEARCH_BEAM_WIDTH = 12;
    private static final int CANONICAL_SEARCH_MAX_CANDIDATES = 128;
    private static final double CANONICAL_SEARCH_CONTIGUITY_BONUS = 0.15;
    private static final double CANONICAL_SEARCH_ALTERNATION_BONUS = 0.10;
    private static final double CANONICAL_SEARCH_GAP_PENALTY_PER_PIVOT = 0.05;

    private static final double DEFAULT_BASE_CONFIDENCE_WEIGHT = 0.7;
    private static final double DEFAULT_NEUTRAL_CROSS_DEGREE_SCORE = 0.5;
    private static final int DEFAULT_HIGHER_DEGREES = 1;
    private static final int DEFAULT_LOWER_DEGREES = 1;
    private static final double STRUCTURAL_SELECTION_WEIGHT = 0.20;
    private static final double DIRECTION_COMPATIBILITY_WEIGHT = 0.55;
    private static final double STRUCTURE_COMPATIBILITY_WEIGHT = 0.30;
    private static final double INVALIDATION_COMPATIBILITY_WEIGHT = 0.15;

    private final ElliottDegree baseDegree;
    private final int higherDegrees;
    private final int lowerDegrees;
    private final SeriesSelector<ElliottDegree> seriesSelector;
    private final AnalysisRunner<ElliottDegree, ElliottAnalysisResult> analysisRunner;
    private final double baseConfidenceWeight;
    private final boolean usesDefaultAnalysisRunner;
    private final ElliottLogicProfile logicProfile;

    // Built-in single-degree analysis pipeline configuration (used by default
    // runner only).
    private final SwingDetector swingDetector;
    private final SwingFilter swingFilter;
    private final Function<NumFactory, ConfidenceModel> confidenceModelFactory;
    private final PatternSet patternSet;
    private final double minConfidence;
    private final int maxScenarios;
    private final int scenarioSwingWindow;

    private ElliottWaveAnalysisRunner(final Builder builder) {
        this.baseDegree = Objects.requireNonNull(builder.baseDegree, "baseDegree");
        this.logicProfile = builder.logicProfile;
        this.higherDegrees = builder.higherDegreesExplicit ? builder.higherDegrees
                : logicProfile == null ? builder.higherDegrees : logicProfile.higherDegrees();
        this.lowerDegrees = builder.lowerDegreesExplicit ? builder.lowerDegrees
                : logicProfile == null ? builder.lowerDegrees : logicProfile.lowerDegrees();
        this.maxScenarios = builder.maxScenariosExplicit ? builder.maxScenarios
                : logicProfile == null ? builder.maxScenarios : logicProfile.maxScenarios();
        this.scenarioSwingWindow = builder.scenarioSwingWindowExplicit ? builder.scenarioSwingWindow
                : logicProfile == null ? builder.scenarioSwingWindow : logicProfile.scenarioSwingWindow();
        this.seriesSelector = builder.seriesSelector == null ? defaultSeriesSelector() : builder.seriesSelector;

        this.swingDetector = builder.swingDetector == null
                ? defaultHierarchicalSwingDetector(baseDegree,
                        logicProfile == null ? DEFAULT_FAST_FRACTAL_WINDOW : logicProfile.baseFractalWindow())
                : builder.swingDetector;
        this.swingFilter = builder.swingFilter;
        this.confidenceModelFactory = builder.confidenceModelFactory == null
                ? defaultConfidenceModelFactory(logicProfile)
                : builder.confidenceModelFactory;
        this.patternSet = builder.patternSet == null
                ? (logicProfile == null ? PatternSet.all() : logicProfile.patternSet())
                : builder.patternSet;
        this.minConfidence = builder.minConfidence;

        int supportingDegrees = Math.max(0, higherDegrees) + Math.max(0, lowerDegrees);
        this.baseConfidenceWeight = supportingDegrees == 0 ? 1.0
                : (builder.baseConfidenceWeightExplicit ? builder.baseConfidenceWeight
                        : logicProfile == null ? builder.baseConfidenceWeight : logicProfile.baseConfidenceWeight());

        this.usesDefaultAnalysisRunner = builder.analysisRunner == null;
        this.analysisRunner = usesDefaultAnalysisRunner ? this::runDefaultAnalysis : builder.analysisRunner;
    }

    /**
     * Creates a new builder.
     *
     * @return builder
     * @since 0.22.4
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs analysis on the supplied series.
     *
     * <p>
     * The result always contains the base-degree analysis and scenario ranking. If
     * {@link Builder#higherDegrees(int)} or {@link Builder#lowerDegrees(int)} are
     * configured to positive values, supporting degree analyses are included and
     * used to re-rank base-degree scenarios.
     *
     * @param series root series
     * @return analysis result
     * @since 0.22.4
     */
    public ElliottWaveAnalysisResult analyze(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        final List<String> notes = new ArrayList<>();
        final List<ElliottDegree> degrees = degreesToAnalyze(baseDegree, higherDegrees, lowerDegrees);
        final List<ElliottWaveAnalysisResult.DegreeAnalysis> degreeAnalyses = new ArrayList<>(degrees.size());

        ElliottAnalysisResult baseResult = null;
        int baseAnalysisIndex = -1;

        for (final ElliottDegree degree : degrees) {
            BarSeries selected = seriesSelector.select(series, degree);
            if (selected == null || selected.isEmpty()) {
                notes.add("Skipped " + degree + " analysis: selected series was empty");
                continue;
            }

            Duration barDuration = selected.getFirstBar().getTimePeriod();
            int barCount = selected.getBarCount();
            double historyFitScore = safeHistoryFitScore(degree, barDuration, barCount, notes);

            ElliottAnalysisResult result = analysisRunner.analyze(selected, degree);
            if (result == null) {
                notes.add("Skipped " + degree + " analysis: runner returned null result");
                continue;
            }

            ElliottWaveAnalysisResult.DegreeAnalysis snapshot = new ElliottWaveAnalysisResult.DegreeAnalysis(degree,
                    result.index(), barCount, barDuration, historyFitScore, result);
            degreeAnalyses.add(snapshot);

            if (degree == baseDegree) {
                baseResult = result;
                baseAnalysisIndex = degreeAnalyses.size() - 1;
            }
        }

        if (baseResult == null) {
            throw new IllegalStateException("Base degree " + baseDegree + " analysis was not available");
        }

        List<ElliottWaveAnalysisResult.BaseScenarioAssessment> ranked = scoreBaseScenarios(baseResult, degreeAnalyses);
        if (baseAnalysisIndex >= 0 && (baseResult.scenarios().size() > maxScenarios || ranked.size() > maxScenarios)) {
            List<ElliottWaveAnalysisResult.BaseScenarioAssessment> clippedRanked = ranked.stream()
                    .limit(maxScenarios)
                    .toList();
            ElliottAnalysisResult clippedBaseResult = clipAnalysisResult(baseResult, clippedRanked);
            ElliottWaveAnalysisResult.DegreeAnalysis baseSnapshot = degreeAnalyses.get(baseAnalysisIndex);
            degreeAnalyses.set(baseAnalysisIndex,
                    new ElliottWaveAnalysisResult.DegreeAnalysis(baseSnapshot.degree(), baseSnapshot.index(),
                            baseSnapshot.barCount(), baseSnapshot.barDuration(), baseSnapshot.historyFitScore(),
                            clippedBaseResult));
        }

        return new ElliottWaveAnalysisResult(baseDegree, degreeAnalyses, ranked, notes);
    }

    /**
     * Runs analysis on an anchor-bounded window and rebases the result back to the
     * original series indices.
     *
     * <p>
     * This is useful for truth-set validation and segment-level studies where the
     * caller knows the intended start/end anchors and wants the core runner to work
     * on that bounded history directly rather than on a longer prefix.
     *
     * <p>
     * When the built-in default runner finds a plausible scenario that starts or
     * ends just inside the requested span, the returned base-degree scenarios are
     * snapped to the requested window boundaries. This reduces left-edge and
     * right-edge instability in anchored research workflows without affecting the
     * full-series {@link #analyze(BarSeries)} path.
     *
     * @param series     root series
     * @param startIndex inclusive window start index in the root series
     * @param endIndex   inclusive window end index in the root series
     * @return analysis result with all swings and scenarios rebased to the original
     *         series indices
     * @since 0.22.4
     */
    public ElliottWaveAnalysisResult analyzeWindow(final BarSeries series, final int startIndex, final int endIndex) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }
        if (startIndex < series.getBeginIndex()) {
            throw new IllegalArgumentException("startIndex must be >= series begin index");
        }
        if (endIndex > series.getEndIndex()) {
            throw new IllegalArgumentException("endIndex must be <= series end index");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex must be >= startIndex");
        }
        if (startIndex == series.getBeginIndex() && endIndex == series.getEndIndex()) {
            return analyze(series);
        }

        final BarSeries window = series.getSubSeries(startIndex, endIndex + 1);
        final ElliottWaveAnalysisResult windowed = analyze(window);
        return anchorWindowResult(series, rebaseAnalysisResult(windowed, startIndex), startIndex, endIndex);
    }

    /**
     * Selects the preferred anchored-window scenario for a target template and also
     * reports whether that selection satisfied the configured acceptance gate.
     *
     * <p>
     * This keeps anchored-window acceptance and fallback selection inside the core
     * runner so callers do not need to reimplement window ranking or acceptance
     * threshold logic on top of {@link ElliottWaveAnalysisResult}.
     *
     * @param series                root series
     * @param startIndex            inclusive window start index in the root series
     * @param endIndex              inclusive window end index in the root series
     * @param scenarioType          required scenario family
     * @param terminalPhase         required terminal phase
     * @param waveCount             required wave count
     * @param bullishDirection      required direction; {@code null} skips direction
     *                              filtering
     * @param maxAnchorDriftBars    soft anchor tolerance used during ranking and
     *                              acceptance
     * @param minimumFitScore       minimum blended fit score
     * @param minimumRuleScore      minimum rule-quality score
     * @param minimumStartAlignment minimum allowed start alignment
     * @param minimumEndAlignment   minimum allowed end alignment
     * @return selected anchored-window scenario plus its accepted/fallback status,
     *         if any scenario matched
     * @since 0.22.4
     */
    public Optional<AnchoredWindowSelection> selectAcceptedOrFallbackBaseScenarioForWindow(final BarSeries series,
            final int startIndex, final int endIndex, final ScenarioType scenarioType, final ElliottPhase terminalPhase,
            final int waveCount, final Boolean bullishDirection, final int maxAnchorDriftBars,
            final double minimumFitScore, final double minimumRuleScore, final double minimumStartAlignment,
            final double minimumEndAlignment) {
        final ElliottWaveAnalysisResult analysis = analyzeWindow(series, startIndex, endIndex);
        return analysis
                .recommendedAcceptedOrFallbackBaseScenarioForWindow(series, startIndex, endIndex, scenarioType,
                        terminalPhase, waveCount, bullishDirection, maxAnchorDriftBars, minimumFitScore,
                        minimumRuleScore, minimumStartAlignment, minimumEndAlignment)
                .map(assessment -> new AnchoredWindowSelection(assessment,
                        assessment.passesAnchoredWindowAcceptance(startIndex, endIndex, minimumFitScore,
                                minimumRuleScore, minimumStartAlignment, minimumEndAlignment, maxAnchorDriftBars)));
    }

    /**
     * Selects the best accepted terminal leg for an anchored window, falling back
     * to the strongest matching scenario when the acceptance gate is missed.
     *
     * <p>
     * This is the semantic convenience entry point for completed macro-leg fitting.
     * Bullish windows map to a five-wave impulse ending in
     * {@link ElliottPhase#WAVE5}. Bearish windows map to a three-wave corrective
     * path ending in {@link ElliottPhase#CORRECTIVE_C}.
     *
     * @param series                root series
     * @param startIndex            inclusive window start index in the root series
     * @param endIndex              inclusive window end index in the root series
     * @param bullish               {@code true} for bullish impulse legs,
     *                              {@code false} for bearish corrective legs
     * @param maxAnchorDriftBars    soft anchor tolerance used during ranking and
     *                              acceptance
     * @param minimumFitScore       minimum blended fit score
     * @param minimumRuleScore      minimum rule-quality score
     * @param minimumStartAlignment minimum allowed start alignment
     * @param minimumEndAlignment   minimum allowed end alignment
     * @return selected anchored terminal-leg scenario plus its accepted/fallback
     *         status, if any scenario matched
     * @since 0.22.4
     */
    public Optional<AnchoredWindowSelection> selectAcceptedOrFallbackTerminalLegForWindow(final BarSeries series,
            final int startIndex, final int endIndex, final boolean bullish, final int maxAnchorDriftBars,
            final double minimumFitScore, final double minimumRuleScore, final double minimumStartAlignment,
            final double minimumEndAlignment) {
        final ScenarioType expectedType = bullish ? ScenarioType.IMPULSE : null;
        final ElliottPhase expectedPhase = bullish ? ElliottPhase.WAVE5 : ElliottPhase.CORRECTIVE_C;
        final int expectedWaveCount = bullish ? 5 : 3;
        final Boolean expectedDirection = bullish ? Boolean.TRUE : Boolean.FALSE;
        return selectAcceptedOrFallbackBaseScenarioForWindow(series, startIndex, endIndex, expectedType, expectedPhase,
                expectedWaveCount, expectedDirection, maxAnchorDriftBars, minimumFitScore, minimumRuleScore,
                minimumStartAlignment, minimumEndAlignment);
    }

    /**
     * Discovers and ranks the current bullish cycle directly from the supplied
     * series.
     *
     * <p>
     * The runner uses its processed swing map to seed plausible cycle lows, then
     * analyzes anchored windows ending at the live right edge to rank bullish
     * {@code 1-5} progressions. This keeps live current-cycle fitting on the same
     * core path as anchored historical window fitting instead of leaving that logic
     * in example-layer code.
     *
     * @param series series to analyze
     * @return ranked current-cycle assessment
     * @since 0.22.4
     */
    public ElliottWaveAnalysisResult.CurrentCycleAssessment analyzeCurrentCycle(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        final double totalRange = windowRange(series, beginIndex, endIndex);
        final ElliottWaveAnalysisResult fullAnalysis = analyze(series);
        final ElliottAnalysisResult baseAnalysis = fullAnalysis.analysisFor(baseDegree).orElseThrow().analysis();
        final List<CurrentCycleStartCandidate> startCandidates = discoverCurrentCycleStartCandidates(series,
                fullAnalysis);
        final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates = new ArrayList<>();
        final int minimumWindowBars = Math.max(CURRENT_CYCLE_MIN_WINDOW_BARS,
                (int) Math.ceil(series.getBarCount() * CURRENT_CYCLE_MIN_WINDOW_FRACTION));

        for (final CurrentCycleStartCandidate startCandidate : startCandidates) {
            final int startIndex = startCandidate.barIndex();
            if (endIndex - startIndex < minimumWindowBars) {
                continue;
            }

            final double spanScore = clamp01((endIndex - startIndex) / (double) Math.max(1, series.getBarCount() - 1));
            final double advanceScore = clamp01((highPrice(series, endIndex) - startCandidate.price().doubleValue())
                    / Math.max(1.0e-9, totalRange));
            final double startIntegrityScore = currentCycleStartIntegrityScore(series, startIndex, totalRange);
            if (startIntegrityScore < 0.20) {
                continue;
            }

            final ElliottWaveAnalysisResult windowAnalysis = analyzeWindow(series, startIndex, endIndex);
            for (int phase = 1; phase <= 5; phase++) {
                final Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> fit = fitPartialLegForWindow(series,
                        windowAnalysis, startIndex, endIndex, true, phase);
                if (fit.isEmpty()) {
                    continue;
                }
                final ElliottWaveAnalysisResult.CurrentPhaseAssessment phaseFit = fit.orElseThrow();
                if (phaseFit.fitScore() < 0.25) {
                    continue;
                }
                final double phaseProgressScore = phase / 5.0;
                final double totalScore = clamp01(
                        (0.54 * phaseFit.fitScore()) + (0.14 * startCandidate.normalizedScore()) + (0.10 * spanScore)
                                + (0.10 * advanceScore) + (0.04 * phaseProgressScore) + (0.08 * startIntegrityScore));
                final String rationale = "Series-native current-cycle anchor at "
                        + series.getBar(startIndex).getEndTime() + " using " + phaseFit.countLabel();
                candidates.add(new ElliottWaveAnalysisResult.CurrentCycleCandidate(startIndex, startCandidate.price(),
                        phaseFit, startCandidate.normalizedScore(), totalScore, rationale));
            }
        }

        if (candidates.isEmpty()) {
            final int fallbackStartIndex = lowestLowIndex(series, beginIndex, endIndex);
            final ElliottWaveAnalysisResult fallbackAnalysis = analyzeWindow(series, fallbackStartIndex, endIndex);
            for (int phase = 1; phase <= 5; phase++) {
                final Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> fit = fitPartialLegForWindow(series,
                        fallbackAnalysis, fallbackStartIndex, endIndex, true, phase);
                fit.ifPresent(phaseFit -> candidates.add(new ElliottWaveAnalysisResult.CurrentCycleCandidate(
                        fallbackStartIndex, lowPriceNum(series, fallbackStartIndex), phaseFit, 1.0, phaseFit.fitScore(),
                        "Fallback current-cycle anchor")));
            }
        }

        final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> rankedCandidates = rankCurrentCycleCandidatesWithCanonicalSearch(
                candidates, baseAnalysis.processedSwings(), endIndex);
        ElliottWaveAnalysisResult.CurrentCycleCandidate primary = null;
        ElliottWaveAnalysisResult.CurrentCycleCandidate alternate = null;
        for (final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate : rankedCandidates) {
            if (primary == null || candidate.compareTo(primary) < 0) {
                if (primary != null && primary.fit().currentPhase() != candidate.fit().currentPhase()) {
                    alternate = primary;
                }
                primary = candidate;
                continue;
            }
            if ((alternate == null || candidate.compareTo(alternate) < 0)
                    && candidate.fit().currentPhase() != primary.fit().currentPhase()) {
                alternate = candidate;
            }
        }

        final int defaultStartIndex = lowestLowIndex(series, beginIndex, endIndex);
        return new ElliottWaveAnalysisResult.CurrentCycleAssessment(
                primary == null ? defaultStartIndex : primary.startIndex(), primary == null ? null : primary.fit(),
                alternate == null ? null : alternate.fit(), rankedCandidates);
    }

    /**
     * Promotes the processed swing sequence for one analysis snapshot into a
     * reusable pivot graph.
     *
     * <p>
     * The graph keeps pivot direction, bar index, timestamp, price, degree
     * provenance, and the runner's configured fractal confirmation span so later
     * macro-structure search can consume pivot-level data without reconstructing it
     * ad hoc from swings.
     *
     * @param series   root series that supplied the timestamps
     * @param analysis analysis snapshot whose processed swings seed the graph
     * @return reusable macro pivot graph for the processed swing path
     * @since 0.22.4
     */
    MacroPivotGraph buildMacroPivotGraph(final BarSeries series, final ElliottAnalysisResult analysis) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(analysis, "analysis");
        if (analysis.processedSwings().isEmpty()) {
            return MacroPivotGraph.empty(higherDegrees, lowerDegrees);
        }

        final List<ElliottSwing> swings = analysis.processedSwings();
        final List<MacroPivot> pivots = new ArrayList<>(swings.size() + 1);
        for (final org.ta4j.core.indicators.elliott.swing.SwingPivot pivot : SwingDetectorResult.fromSwings(swings)
                .pivots()) {
            final int barIndex = pivot.index();
            if (barIndex < series.getBeginIndex() || barIndex > series.getEndIndex()) {
                continue;
            }
            pivots.add(new MacroPivot(pivot.type() == org.ta4j.core.indicators.elliott.swing.SwingPivotType.HIGH,
                    barIndex, series.getBar(barIndex).getEndTime(), pivot.price(),
                    pivotDegreeProvenance(swings, barIndex)));
        }
        return new MacroPivotGraph(pruneMacroPivots(series, pivots), higherDegrees, lowerDegrees);
    }

    /**
     * Searches for the strongest alternating macro path through a bounded
     * candidate-leg set.
     *
     * <p>
     * This is the first global search primitive for the canonical single-engine
     * effort. It uses beam search over alternating bullish and bearish legs,
     * combines local fit with continuity/coherence bonuses, and keeps only a small
     * bounded frontier at each expansion step.
     *
     * @param candidates candidate legs sorted or unsorted
     * @return best alternating canonical path, if any candidate survives
     * @since 0.22.4
     */
    Optional<CanonicalStructurePath> searchCanonicalStructure(final List<CanonicalLegCandidate> candidates) {
        return searchCanonicalStructurePaths(candidates).stream().findFirst();
    }

    List<CanonicalLegCandidate> boundCanonicalCandidates(final List<CanonicalLegCandidate> candidates) {
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CanonicalLegCandidate::fitScore)
                        .reversed()
                        .thenComparingInt(CanonicalLegCandidate::startPivotIndex)
                        .thenComparingInt(CanonicalLegCandidate::endPivotIndex))
                .limit(CANONICAL_SEARCH_MAX_CANDIDATES)
                .sorted(Comparator.comparingInt(CanonicalLegCandidate::startPivotIndex)
                        .thenComparingInt(CanonicalLegCandidate::endPivotIndex)
                        .thenComparing(Comparator.comparingDouble(CanonicalLegCandidate::fitScore).reversed()))
                .toList();
    }

    List<CanonicalStructurePath> searchCanonicalStructurePaths(final List<CanonicalLegCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            return List.of();
        }

        final List<CanonicalLegCandidate> ordered = boundCanonicalCandidates(candidates);
        List<CanonicalStructurePath> beam = List.of(CanonicalStructurePath.empty());
        for (final CanonicalLegCandidate candidate : ordered) {
            final List<CanonicalStructurePath> frontier = new ArrayList<>(beam);
            for (final CanonicalStructurePath path : beam) {
                if (path.canAppend(candidate)) {
                    frontier.add(path.append(candidate));
                }
            }
            beam = frontier.stream()
                    .sorted(CanonicalStructurePath.ORDERING)
                    .limit(CANONICAL_SEARCH_BEAM_WIDTH)
                    .toList();
        }
        return beam.stream().filter(path -> !path.legs().isEmpty()).sorted(CanonicalStructurePath.ORDERING).toList();
    }

    List<ElliottWaveAnalysisResult.CurrentCycleCandidate> rankCurrentCycleCandidatesWithCanonicalSearch(
            final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> candidates,
            final List<ElliottSwing> processedSwings, final int endIndex) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(processedSwings, "processedSwings");
        if (candidates.isEmpty()) {
            return List.of();
        }

        final Map<String, ElliottWaveAnalysisResult.CurrentCycleCandidate> byId = new LinkedHashMap<>();
        final List<CanonicalLegCandidate> canonicalCandidates = new ArrayList<>(candidates.size() * 2);
        for (int index = 0; index < candidates.size(); index++) {
            final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = candidates.get(index);
            final String id = currentCycleCanonicalCandidateId(candidate, index);
            byId.put(id, candidate);
            canonicalCandidates
                    .add(new CanonicalLegCandidate(id, candidate.startIndex(), endIndex, true, candidate.totalScore()));
            final int precursorStartIndex = predecessorHighPivotIndex(processedSwings, candidate.startIndex());
            if (precursorStartIndex >= 0 && precursorStartIndex < candidate.startIndex()) {
                canonicalCandidates
                        .add(new CanonicalLegCandidate("precursor-" + id, precursorStartIndex, candidate.startIndex(),
                                false, canonicalPrecursorScore(candidate, precursorStartIndex, endIndex)));
            }
        }

        final LinkedHashMap<String, ElliottWaveAnalysisResult.CurrentCycleCandidate> ranked = new LinkedHashMap<>();
        for (final CanonicalStructurePath path : searchCanonicalStructurePaths(canonicalCandidates)) {
            final CanonicalLegCandidate terminalLeg = path.legs().getLast();
            if (!terminalLeg.bullish()) {
                continue;
            }
            final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate = byId.get(terminalLeg.id());
            if (candidate == null) {
                continue;
            }
            final String key = candidate.fit().scenario().id() + "|" + candidate.fit().currentPhase() + "|"
                    + candidate.startIndex();
            ranked.putIfAbsent(key,
                    new ElliottWaveAnalysisResult.CurrentCycleCandidate(candidate.startIndex(), candidate.startPrice(),
                            candidate.fit(), candidate.anchorScore(), canonicalCurrentCycleScore(path),
                            candidate.rationale() + " via canonical path"));
        }

        final List<ElliottWaveAnalysisResult.CurrentCycleCandidate> ordered = new ArrayList<>(ranked.values());
        candidates.stream().sorted().forEach(candidate -> {
            final String key = candidate.fit().scenario().id() + "|" + candidate.fit().currentPhase() + "|"
                    + candidate.startIndex();
            if (!ranked.containsKey(key)) {
                ordered.add(candidate);
            }
        });
        ordered.sort(null);
        return List.copyOf(ordered);
    }

    /**
     * Runs the built-in single-degree pipeline with default noise filtering and
     * swing compression parameters scaled to the requested degree.
     *
     * @param series series to analyze
     * @param degree degree to analyze
     * @return one-degree Elliott analysis snapshot
     */
    private ElliottAnalysisResult runDefaultAnalysis(final BarSeries series, final ElliottDegree degree) {
        return runDefaultAnalysis(series, degree, internalScenarioBudget(series));
    }

    private ElliottAnalysisResult runDefaultAnalysis(final BarSeries series, final ElliottDegree degree,
            final int scenarioBudget) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");

        SwingFilter filter = swingFilter;
        if (filter == null) {
            filter = new MinMagnitudeSwingFilter(defaultMinRelativeMagnitude(series, degree));
        }

        ElliottSwingCompressor compressor = defaultSwingCompressor(series, degree);

        return runSingleDegreePipeline(series, degree, swingDetector, filter, compressor, scenarioBudget);
    }

    private double defaultMinRelativeMagnitude(final BarSeries series, final ElliottDegree degree) {
        final double standardThreshold = scale(baseDegree, degree, 0.20, 1.5, 0.05, 0.60);
        if (scenarioSwingWindow != 0 || series.getBarCount() < BROAD_HISTORY_FILTER_BAR_THRESHOLD) {
            return standardThreshold;
        }
        final double broadHistoryThreshold = scale(baseDegree, degree, 0.005, 1.35, 0.001, 0.10);
        return Math.min(standardThreshold, broadHistoryThreshold);
    }

    private ElliottSwingCompressor defaultSwingCompressor(final BarSeries series, final ElliottDegree degree) {
        if (scenarioSwingWindow != 0 || series.getBarCount() < BROAD_HISTORY_FILTER_BAR_THRESHOLD) {
            return new ElliottSwingCompressor(new ClosePriceIndicator(series),
                    scale(baseDegree, degree, 0.01, 1.5, 0.0025, 0.05),
                    Math.max(1, DEFAULT_COMPRESSOR_MIN_BARS + (baseDegree.ordinal() - degree.ordinal())));
        }
        final double broadHistoryPercentage = scale(baseDegree, degree, 0.002, 1.15, 0.0005, 0.01);
        return new ElliottSwingCompressor(new ClosePriceIndicator(series), broadHistoryPercentage,
                BROAD_HISTORY_COMPRESSOR_MIN_BARS);
    }

    /**
     * Builds the default hierarchical swing detector used by the runner when no
     * custom detector is supplied.
     *
     * <p>
     * The detector blends volatility-sensitive ZigZag pivots with fast and slow
     * fractal confirmations so macro structures are less likely to disappear into
     * short-term noise.
     *
     * @param baseDegree configured runner base degree
     * @return hierarchical detector composed from existing detector primitives
     */
    static SwingDetector defaultHierarchicalSwingDetector(final ElliottDegree baseDegree) {
        return defaultHierarchicalSwingDetector(baseDegree, DEFAULT_FAST_FRACTAL_WINDOW);
    }

    static SwingDetector defaultHierarchicalSwingDetector(final ElliottDegree baseDegree, final int baseFractalWindow) {
        Objects.requireNonNull(baseDegree, "baseDegree");
        final int clampedBaseWindow = Math.max(2, baseFractalWindow);
        return (series, index, degree) -> {
            int fastWindow = scaleWindow(baseDegree, degree, clampedBaseWindow, 2, 21);
            int slowBaseWindow = Math.max(fastWindow + 1, (clampedBaseWindow * 2) + 1);
            int slowWindow = scaleWindow(baseDegree, degree, slowBaseWindow, fastWindow + 1, 55);
            AdaptiveZigZagConfig zigZagConfig = new AdaptiveZigZagConfig(Math.max(8, fastWindow * 4),
                    1.0 + (clampedBaseWindow * 0.08), 0.0, 0.0, Math.max(2, clampedBaseWindow));
            return SwingDetectors
                    .composite(CompositeSwingDetector.Policy.OR, SwingDetectors.adaptiveZigZag(zigZagConfig),
                            SwingDetectors.fractal(fastWindow), SwingDetectors.fractal(slowWindow))
                    .detect(series, index, degree);
        };
    }

    private static Function<NumFactory, ConfidenceModel> defaultConfidenceModelFactory(
            final ElliottLogicProfile logicProfile) {
        if (logicProfile != null && logicProfile.patternAwareConfidence()) {
            return ConfidenceProfiles::patternAwareModel;
        }
        return ConfidenceProfiles::defaultModel;
    }

    /**
     * Executes the core one-degree flow: detect swings, apply optional filtering
     * and compression, build scenarios, and compute confidence breakdowns.
     *
     * @param series     input series
     * @param degree     analysis degree
     * @param detector   swing detector
     * @param filter     optional swing filter
     * @param compressor optional swing compressor
     * @return one-degree Elliott analysis snapshot
     */
    private ElliottAnalysisResult runSingleDegreePipeline(final BarSeries series, final ElliottDegree degree,
            final SwingDetector detector, final SwingFilter filter, final ElliottSwingCompressor compressor,
            final int scenarioBudget) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        Objects.requireNonNull(detector, "detector");

        int endIndex = series.getEndIndex();
        SwingDetectorResult detection = Objects.requireNonNull(detector.detect(series, endIndex, degree),
                "swingDetector.detect");
        List<ElliottSwing> rawSwings = detection.swings();

        List<ElliottSwing> processed = rawSwings;
        if (filter != null) {
            List<ElliottSwing> filtered = filter.filter(processed);
            processed = filtered == null ? List.of() : filtered;
        }
        if (compressor != null) {
            List<ElliottSwing> compressed = compressor.compress(processed);
            processed = compressed == null ? List.of() : compressed;
        }

        List<ElliottSwing> scenarioInput = appendTerminalExtensionIfNeeded(processed, series, endIndex, degree);
        List<ElliottSwing> scenarioSwings = recentSwings(scenarioInput, scenarioSwingWindow);
        ElliottChannel channel = computeChannel(series.numFactory(), scenarioSwings, endIndex);

        ConfidenceModel confidenceModel = Objects.requireNonNull(confidenceModelFactory.apply(series.numFactory()),
                "confidenceModelFactory");
        ElliottScenarioGenerator generator = new ElliottScenarioGenerator(series.numFactory(), minConfidence,
                scenarioBudget, confidenceModel, patternSet);
        ElliottScenarioSet scenarios = generator.generate(scenarioSwings, degree, channel, endIndex);
        ElliottTrendBias trendBias = scenarios.trendBias();

        Map<String, ElliottConfidenceBreakdown> breakdowns = new HashMap<>();
        for (ElliottScenario scenario : scenarios.all()) {
            ElliottConfidenceBreakdown breakdown = confidenceModel.score(scenario.swings(), scenario.currentPhase(),
                    channel, scenario.type());
            breakdowns.put(scenario.id(), breakdown);
        }

        return new ElliottAnalysisResult(degree, endIndex, rawSwings, scenarioSwings, scenarios, breakdowns, channel,
                trendBias, generator.lastDiagnostics());
    }

    private int internalScenarioBudget(final BarSeries series) {
        if (!usesDefaultAnalysisRunner || scenarioSwingWindow != 0
                || series.getBarCount() < BROAD_HISTORY_FILTER_BAR_THRESHOLD) {
            return maxScenarios;
        }
        return Math.max(maxScenarios, Math.min(BROAD_HISTORY_INTERNAL_SCENARIO_CAP,
                maxScenarios * BROAD_HISTORY_INTERNAL_SCENARIO_MULTIPLIER));
    }

    private List<CurrentCycleStartCandidate> discoverCurrentCycleStartCandidates(final BarSeries series,
            final ElliottWaveAnalysisResult analysis) {
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        final double totalRange = windowRange(series, beginIndex, endIndex);
        final double absoluteLow = lowestLow(series, beginIndex, endIndex);
        final Map<Integer, CurrentCycleStartAccumulator> accumulators = new LinkedHashMap<>();
        final List<Integer> processedLowPivots = analysis.analysisFor(baseDegree)
                .map(ElliottWaveAnalysisResult.DegreeAnalysis::analysis)
                .map(ElliottAnalysisResult::processedSwings)
                .map(ElliottWaveAnalysisRunner::lowPivotIndices)
                .orElse(List.of());

        for (final int pivotIndex : processedLowPivots) {
            seedCurrentCycleStartCandidate(series, accumulators, pivotIndex,
                    currentCycleProcessedPivotScore(series, pivotIndex, absoluteLow, totalRange));
        }

        seedCurrentCycleStartCandidate(series, accumulators, lowestLowIndex(series, beginIndex, endIndex), 12.0);
        final int trailingHalfStart = beginIndex + ((endIndex - beginIndex) / 2);
        seedCurrentCycleStartCandidate(series, accumulators, lowestLowIndex(series, trailingHalfStart, endIndex), 14.0);
        final int trailingThirdStart = beginIndex + (((endIndex - beginIndex) * 2) / 3);
        seedCurrentCycleStartCandidate(series, accumulators, lowestLowIndex(series, trailingThirdStart, endIndex),
                16.0);

        final List<CurrentCycleStartCandidate> rankedCandidates = accumulators.values()
                .stream()
                .map(accumulator -> accumulator.toCandidate(beginIndex, endIndex))
                .sorted(Comparator.comparingDouble(CurrentCycleStartCandidate::rawScore)
                        .reversed()
                        .thenComparingInt(CurrentCycleStartCandidate::barIndex))
                .toList();
        final CurrentCycleStartCandidate preservedStartCandidate = processedLowPivots.stream()
                .filter(accumulators::containsKey)
                .min(Integer::compareTo)
                .map(accumulators::get)
                .map(accumulator -> accumulator.toCandidate(beginIndex, endIndex))
                .orElse(null);
        final List<CurrentCycleStartCandidate> candidates = selectCurrentCycleStartCandidates(rankedCandidates,
                preservedStartCandidate);
        if (!candidates.isEmpty()) {
            return List.copyOf(candidates);
        }
        final int lowestIndex = lowestLowIndex(series, beginIndex, endIndex);
        return List.of(new CurrentCycleStartCandidate(lowestIndex, lowPriceNum(series, lowestIndex), 1.0, 1.0));
    }

    private static List<CurrentCycleStartCandidate> selectCurrentCycleStartCandidates(
            final List<CurrentCycleStartCandidate> rankedCandidates,
            final CurrentCycleStartCandidate preservedCandidate) {
        if (rankedCandidates.isEmpty()) {
            return List.of();
        }
        final List<CurrentCycleStartCandidate> selected = new ArrayList<>(CURRENT_CYCLE_START_CANDIDATE_LIMIT);
        if (preservedCandidate != null) {
            selected.add(preservedCandidate);
        }
        for (final CurrentCycleStartCandidate candidate : rankedCandidates) {
            if (selected.size() >= CURRENT_CYCLE_START_CANDIDATE_LIMIT) {
                break;
            }
            if (preservedCandidate != null && candidate.barIndex() == preservedCandidate.barIndex()) {
                continue;
            }
            selected.add(candidate);
        }
        return selected.stream().sorted(Comparator.comparingInt(CurrentCycleStartCandidate::barIndex)).toList();
    }

    private void seedCurrentCycleStartCandidate(final BarSeries series,
            final Map<Integer, CurrentCycleStartAccumulator> accumulators, final int barIndex, final double score) {
        if (barIndex < series.getBeginIndex() || barIndex > series.getEndIndex()) {
            return;
        }
        accumulators
                .computeIfAbsent(barIndex,
                        ignored -> new CurrentCycleStartAccumulator(barIndex, lowPriceNum(series, barIndex)))
                .add(score);
    }

    private static List<Integer> lowPivotIndices(final List<ElliottSwing> swings) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        final List<Integer> pivotIndices = new ArrayList<>(swings.size() + 1);
        for (final ElliottSwing swing : swings) {
            pivotIndices.add(swing.isRising() ? swing.fromIndex() : swing.toIndex());
        }
        return pivotIndices.stream().distinct().toList();
    }

    private double currentCycleProcessedPivotScore(final BarSeries series, final int pivotIndex,
            final double absoluteLow, final double totalRange) {
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        final double recencyScore = clamp01((pivotIndex - beginIndex) / (double) Math.max(1, endIndex - beginIndex));
        final double extremityScore = clamp01(
                1.0 - ((lowPrice(series, pivotIndex) - absoluteLow) / Math.max(1.0e-9, totalRange)));
        final double advanceScore = clamp01((highestHigh(series, pivotIndex, endIndex) - lowPrice(series, pivotIndex))
                / Math.max(1.0e-9, totalRange));
        return 1.5 + (2.0 * recencyScore) + (3.0 * extremityScore) + (2.0 * advanceScore);
    }

    private double currentCycleStartIntegrityScore(final BarSeries series, final int startIndex,
            final double totalRange) {
        final double startPrice = lowPrice(series, startIndex);
        double lowestAfterStart = startPrice;
        for (int index = startIndex; index <= series.getEndIndex(); index++) {
            lowestAfterStart = Math.min(lowestAfterStart, lowPrice(series, index));
        }
        if (lowestAfterStart >= startPrice) {
            return 1.0;
        }
        final double breach = (startPrice - lowestAfterStart) / Math.max(1.0e-9, totalRange);
        return clamp01(1.0 - (breach / 0.08));
    }

    /**
     * Fits a partial local leg inside an anchored window.
     *
     * <p>
     * Bullish phases map to impulse progressions {@code 1..5}. Bearish phases map
     * to corrective progressions {@code A..C}. This keeps partial-window scoring on
     * the same core fitter path used by live current-cycle inference.
     *
     * @param series     root series
     * @param analysis   precomputed anchored-window analysis
     * @param startIndex inclusive window start index in the root series
     * @param endIndex   inclusive window end index in the root series
     * @param bullish    {@code true} for bullish impulse progressions,
     *                   {@code false} for bearish corrective progressions
     * @param phase      bullish phase {@code 1..5} or bearish corrective phase
     *                   {@code 1..3}
     * @return normalized partial-leg assessment, if the requested family fits the
     *         window
     */
    Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> fitPartialLegForWindow(final BarSeries series,
            final ElliottWaveAnalysisResult analysis, final int startIndex, final int endIndex, final boolean bullish,
            final int phase) {
        if (endIndex <= startIndex) {
            return Optional.empty();
        }
        final ElliottPhase currentPhase = currentPhase(bullish, phase);
        if (currentPhase == null) {
            return Optional.empty();
        }
        final ScenarioType scenarioType = bullish ? ScenarioType.IMPULSE : null;
        final Boolean expectedDirection = Boolean.valueOf(bullish);
        final Optional<ElliottWaveAnalysisResult.WindowScenarioAssessment> anchored = analysis
                .recommendedBaseScenarioForWindow(series, startIndex, endIndex, scenarioType, currentPhase, phase,
                        expectedDirection, CURRENT_CYCLE_MAX_ANCHOR_DRIFT_BARS);
        if (anchored.isPresent()) {
            final ElliottWaveAnalysisResult.WindowScenarioAssessment assessment = anchored.orElseThrow();
            return toCurrentPhaseAssessment(series, startIndex, endIndex, currentPhase, assessment.scenario(),
                    assessment.windowFitScore(), bullish);
        }
        final Optional<ElliottWaveAnalysisResult.BaseScenarioAssessment> anchoredSpan = analysis
                .recommendedBaseScenarioForSpan(startIndex, endIndex, scenarioType, currentPhase, phase,
                        expectedDirection, CURRENT_CYCLE_MAX_ANCHOR_DRIFT_BARS);
        if (anchoredSpan.isEmpty()) {
            return Optional.empty();
        }
        final ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = anchoredSpan.orElseThrow();
        return toCurrentPhaseAssessment(series, startIndex, endIndex, currentPhase, assessment.scenario(),
                assessment.compositeScore(), bullish);
    }

    private Optional<ElliottWaveAnalysisResult.CurrentPhaseAssessment> toCurrentPhaseAssessment(final BarSeries series,
            final int startIndex, final int endIndex, final ElliottPhase currentPhase, final ElliottScenario scenario,
            final double baseFitScore, final boolean bullish) {
        final ElliottScenario normalizedScenario = normalizeCurrentCycleScenario(series, scenario);
        final ElliottSwing firstSwing = normalizedScenario.swings().getFirst();
        final ElliottSwing lastSwing = normalizedScenario.swings().getLast();
        if (Math.abs(firstSwing.fromIndex() - startIndex) > CURRENT_CYCLE_MAX_ANCHOR_DRIFT_BARS
                || Math.abs(lastSwing.toIndex() - endIndex) > CURRENT_CYCLE_MAX_ANCHOR_DRIFT_BARS) {
            return Optional.empty();
        }
        final Optional<PivotDominanceSummary> dominanceSummary = pivotDominanceSummary(series, normalizedScenario);
        if (dominanceSummary.isEmpty()) {
            return Optional.empty();
        }
        final PivotDominanceSummary dominance = dominanceSummary.orElseThrow();
        if (dominance.minimumPivotDominance() < CURRENT_CYCLE_MIN_PIVOT_DOMINANCE
                || dominance.terminalPivotDominance() < CURRENT_CYCLE_MIN_TERMINAL_DOMINANCE) {
            return Optional.empty();
        }
        final double fitScore = clamp01((CURRENT_CYCLE_FIT_SCORE_WEIGHT * baseFitScore)
                + (CURRENT_CYCLE_DOMINANCE_WEIGHT * dominance.averagePivotDominance())
                + (CURRENT_CYCLE_TERMINAL_WEIGHT * dominance.terminalPivotDominance()));
        final Num structuralInvalidation = normalizedScenario.invalidationPrice();
        final Num phaseInvalidation = currentPhaseInvalidation(normalizedScenario, currentPhase);
        return Optional.of(new ElliottWaveAnalysisResult.CurrentPhaseAssessment(normalizedScenario, currentPhase,
                fitScore, bullish ? lowPriceNum(series, startIndex) : highPriceNum(series, startIndex),
                currentCountLabel(bullish, scenario.waveCount()), structuralInvalidation, phaseInvalidation));
    }

    private static ElliottPhase currentPhase(final boolean bullish, final int phase) {
        if (bullish) {
            return switch (phase) {
            case 1 -> ElliottPhase.WAVE1;
            case 2 -> ElliottPhase.WAVE2;
            case 3 -> ElliottPhase.WAVE3;
            case 4 -> ElliottPhase.WAVE4;
            case 5 -> ElliottPhase.WAVE5;
            default -> null;
            };
        }
        return switch (phase) {
        case 1 -> ElliottPhase.CORRECTIVE_A;
        case 2 -> ElliottPhase.CORRECTIVE_B;
        case 3 -> ElliottPhase.CORRECTIVE_C;
        default -> null;
        };
    }

    private ElliottDegree pivotDegreeProvenance(final List<ElliottSwing> swings, final int pivotIndex) {
        for (final ElliottSwing swing : swings) {
            if (swing.fromIndex() == pivotIndex || swing.toIndex() == pivotIndex) {
                return swing.degree();
            }
        }
        return baseDegree;
    }

    private String currentCycleCanonicalCandidateId(final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate,
            final int ordinal) {
        return "current-" + ordinal + "-" + candidate.startIndex() + "-" + candidate.fit().currentPhase() + "-"
                + candidate.fit().scenario().id();
    }

    private int predecessorHighPivotIndex(final List<ElliottSwing> swings, final int startIndex) {
        int fallback = -1;
        for (final ElliottSwing swing : swings) {
            if (!swing.isRising() && swing.toIndex() == startIndex) {
                return swing.fromIndex();
            }
            if (swing.isRising() && swing.toIndex() < startIndex) {
                fallback = swing.toIndex();
            } else if (!swing.isRising() && swing.fromIndex() < startIndex) {
                fallback = swing.fromIndex();
            }
        }
        return fallback;
    }

    private double canonicalPrecursorScore(final ElliottWaveAnalysisResult.CurrentCycleCandidate candidate,
            final int precursorStartIndex, final int endIndex) {
        final double spanScore = clamp01(
                (candidate.startIndex() - precursorStartIndex) / (double) Math.max(1, endIndex - precursorStartIndex));
        return clamp01((0.65 * candidate.anchorScore()) + (0.35 * spanScore));
    }

    private double canonicalCurrentCycleScore(final CanonicalStructurePath path) {
        final CanonicalLegCandidate terminalLeg = path.legs().getLast();
        if (path.legs().size() == 1) {
            return clamp01(terminalLeg.fitScore());
        }
        final double supportingContext = Math.max(0.0, path.score() - terminalLeg.fitScore())
                / Math.max(1, path.legs().size() - 1);
        return clamp01(terminalLeg.fitScore() + (0.25 * supportingContext));
    }

    private List<MacroPivot> pruneMacroPivots(final BarSeries series, final List<MacroPivot> pivots) {
        if (pivots.size() <= MACRO_PIVOT_GRAPH_MAX_PIVOTS) {
            return List.copyOf(pivots);
        }

        final boolean[] keep = new boolean[pivots.size()];
        final boolean[] required = new boolean[pivots.size()];
        keep[0] = true;
        keep[pivots.size() - 1] = true;
        required[0] = true;
        required[pivots.size() - 1] = true;

        int strongestHigh = -1;
        int strongestLow = -1;
        for (int index = 0; index < pivots.size(); index++) {
            final MacroPivot pivot = pivots.get(index);
            if (pivot.highPivot()) {
                if (strongestHigh < 0 || pivot.price().isGreaterThan(pivots.get(strongestHigh).price())) {
                    strongestHigh = index;
                }
            } else if (strongestLow < 0 || pivot.price().isLessThan(pivots.get(strongestLow).price())) {
                strongestLow = index;
            }
        }
        if (strongestHigh >= 0) {
            keep[strongestHigh] = true;
            required[strongestHigh] = true;
        }
        if (strongestLow >= 0) {
            keep[strongestLow] = true;
            required[strongestLow] = true;
        }

        final List<MacroPivotRank> ranked = new ArrayList<>(Math.max(0, pivots.size() - 2));
        for (int index = 1; index < pivots.size() - 1; index++) {
            final MacroPivot pivot = pivots.get(index);
            final double dominance = pivotDominanceScore(series, pivot.barIndex(), pivots.get(index - 1).barIndex(),
                    pivots.get(index + 1).barIndex(), pivot.highPivot());
            ranked.add(new MacroPivotRank(index, dominance));
        }

        ranked.stream()
                .filter(rank -> rank.dominance() >= MACRO_PIVOT_GRAPH_MIN_DOMINANCE)
                .sorted(Comparator.comparingDouble(MacroPivotRank::dominance)
                        .reversed()
                        .thenComparingInt(MacroPivotRank::index))
                .forEach(rank -> keep[rank.index()] = true);

        int retained = 0;
        for (final boolean retainedPivot : keep) {
            if (retainedPivot) {
                retained++;
            }
        }
        if (retained < MACRO_PIVOT_GRAPH_MAX_PIVOTS) {
            for (final MacroPivotRank rank : ranked.stream()
                    .sorted(Comparator.comparingDouble(MacroPivotRank::dominance)
                            .reversed()
                            .thenComparingInt(MacroPivotRank::index))
                    .toList()) {
                if (retained >= MACRO_PIVOT_GRAPH_MAX_PIVOTS) {
                    break;
                }
                if (!keep[rank.index()]) {
                    keep[rank.index()] = true;
                    retained++;
                }
            }
        }
        if (retained > MACRO_PIVOT_GRAPH_MAX_PIVOTS) {
            for (final MacroPivotRank rank : ranked.stream()
                    .sorted(Comparator.comparingDouble(MacroPivotRank::dominance)
                            .thenComparingInt(MacroPivotRank::index))
                    .toList()) {
                if (retained <= MACRO_PIVOT_GRAPH_MAX_PIVOTS) {
                    break;
                }
                if (keep[rank.index()] && !required[rank.index()]) {
                    keep[rank.index()] = false;
                    retained--;
                }
            }
        }

        final List<MacroPivot> pruned = new ArrayList<>(Math.min(MACRO_PIVOT_GRAPH_MAX_PIVOTS, pivots.size()));
        for (int index = 0; index < pivots.size(); index++) {
            if (keep[index]) {
                pruned.add(pivots.get(index));
            }
        }
        return List.copyOf(pruned);
    }

    private Num currentPhaseInvalidation(final ElliottScenario scenario, final ElliottPhase currentPhase) {
        if (scenario == null || currentPhase == null) {
            return null;
        }
        final Num structuralInvalidation = scenario.invalidationPrice();
        if (!currentPhase.isImpulse() || scenario.swings().isEmpty()) {
            return structuralInvalidation;
        }
        final List<ElliottSwing> swings = scenario.swings();
        return switch (currentPhase) {
        case WAVE3 -> swings.size() >= 2 ? swings.get(1).toPrice() : structuralInvalidation;
        case WAVE4 -> swings.size() >= 1 ? swings.get(0).toPrice() : structuralInvalidation;
        case WAVE5 -> swings.size() >= 4 ? swings.get(3).toPrice() : structuralInvalidation;
        default -> structuralInvalidation;
        };
    }

    private ElliottScenario normalizeCurrentCycleScenario(final BarSeries series, final ElliottScenario scenario) {
        if (scenario == null || scenario.swings().isEmpty() || !scenario.hasKnownDirection()) {
            return scenario;
        }
        final boolean normalizeBullishImpulse = scenario.isBullish() && scenario.currentPhase().isImpulse()
                && scenario.type() == ScenarioType.IMPULSE;
        final boolean normalizeBearishCorrective = !scenario.isBullish() && scenario.type().isCorrective()
                && scenario.currentPhase().isCorrective();
        if (!normalizeBullishImpulse && !normalizeBearishCorrective) {
            return scenario;
        }

        final List<ElliottSwing> normalizedSwings = normalizeAlternatingWindowSwings(series, scenario.swings());
        if (normalizedSwings.equals(scenario.swings())) {
            return scenario;
        }

        final ElliottScenario.Builder builder = ElliottScenario.builder()
                .id(scenario.id())
                .currentPhase(scenario.currentPhase())
                .swings(normalizedSwings)
                .confidence(scenario.confidence())
                .degree(scenario.degree())
                .invalidationPrice(scenario.invalidationPrice())
                .primaryTarget(scenario.primaryTarget())
                .fibonacciTargets(scenario.fibonacciTargets())
                .type(scenario.type())
                .startIndex(normalizedSwings.getFirst().fromIndex())
                .bullishDirection(scenario.isBullish());
        return builder.build();
    }

    private List<ElliottSwing> normalizeAlternatingWindowSwings(final BarSeries series,
            final List<ElliottSwing> swings) {
        if (swings == null || swings.size() < 2) {
            return swings == null ? List.of() : swings;
        }

        final int pivotCount = swings.size() + 1;
        final int[] pivotIndices = new int[pivotCount];
        final Num[] pivotPrices = new Num[pivotCount];
        final boolean[] highPivots = new boolean[pivotCount];

        final ElliottSwing firstSwing = swings.getFirst();
        pivotIndices[0] = firstSwing.fromIndex();
        pivotPrices[0] = firstSwing.fromPrice();
        highPivots[0] = !firstSwing.isRising();

        for (int index = 0; index < swings.size(); index++) {
            final ElliottSwing swing = swings.get(index);
            pivotIndices[index + 1] = swing.toIndex();
            pivotPrices[index + 1] = swing.toPrice();
            highPivots[index + 1] = swing.isRising();
        }

        boolean changed = false;
        for (int pointIndex = 1; pointIndex < pivotCount - 1; pointIndex++) {
            final int interiorStart = pivotIndices[pointIndex - 1] + 1;
            final int interiorEnd = pivotIndices[pointIndex + 1] - 1;
            if (interiorStart > interiorEnd) {
                continue;
            }

            final boolean highPivot = highPivots[pointIndex];
            final int snappedIndex = highPivot ? highestHighIndex(series, interiorStart, interiorEnd)
                    : lowestLowIndex(series, interiorStart, interiorEnd);
            final Num snappedPrice = highPivot ? highPriceNum(series, snappedIndex) : lowPriceNum(series, snappedIndex);
            if (snappedIndex != pivotIndices[pointIndex] || snappedPrice.compareTo(pivotPrices[pointIndex]) != 0) {
                pivotIndices[pointIndex] = snappedIndex;
                pivotPrices[pointIndex] = snappedPrice;
                changed = true;
            }
        }

        if (!changed) {
            return swings;
        }

        final List<ElliottSwing> normalizedSwings = new ArrayList<>(swings.size());
        for (int index = 0; index < swings.size(); index++) {
            final ElliottSwing swing = swings.get(index);
            normalizedSwings.add(new ElliottSwing(pivotIndices[index], pivotIndices[index + 1], pivotPrices[index],
                    pivotPrices[index + 1], swing.degree()));
        }
        return List.copyOf(normalizedSwings);
    }

    private Optional<PivotDominanceSummary> pivotDominanceSummary(final BarSeries series,
            final ElliottScenario scenario) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return Optional.empty();
        }

        final List<ElliottSwing> swings = scenario.swings();
        final List<Integer> pivotIndices = new ArrayList<>(swings.size() + 1);
        final List<Boolean> highPivots = new ArrayList<>(swings.size() + 1);
        pivotIndices.add(swings.getFirst().fromIndex());
        highPivots.add(Boolean.valueOf(!swings.getFirst().isRising()));

        for (int index = 1; index < swings.size(); index++) {
            final ElliottSwing previous = swings.get(index - 1);
            final ElliottSwing current = swings.get(index);
            if (previous.isRising() == current.isRising()) {
                return Optional.empty();
            }
            if (previous.toIndex() != current.fromIndex()) {
                return Optional.empty();
            }
            if (previous.toPrice().compareTo(current.fromPrice()) != 0) {
                return Optional.empty();
            }
            pivotIndices.add(previous.toIndex());
            highPivots.add(Boolean.valueOf(previous.isRising()));
        }

        pivotIndices.add(swings.getLast().toIndex());
        highPivots.add(Boolean.valueOf(swings.getLast().isRising()));

        if (pivotIndices.size() < 2) {
            return Optional.empty();
        }

        double total = 0.0;
        double minimum = 1.0;
        for (int pointIndex = 1; pointIndex < pivotIndices.size(); pointIndex++) {
            final int pivotIndex = pivotIndices.get(pointIndex);
            if (pivotIndex < series.getBeginIndex() || pivotIndex > series.getEndIndex()) {
                return Optional.empty();
            }
            final int spanStart = pivotIndices.get(pointIndex - 1);
            final int spanEnd = pointIndex == pivotIndices.size() - 1 ? pivotIndices.getLast()
                    : pivotIndices.get(pointIndex + 1);
            final double dominance = pivotDominanceScore(series, pivotIndex, spanStart, spanEnd,
                    highPivots.get(pointIndex).booleanValue());
            total += dominance;
            minimum = Math.min(minimum, dominance);
        }
        final double terminalDominance = pivotDominanceScore(series, pivotIndices.getLast(),
                pivotIndices.get(pivotIndices.size() - 2), pivotIndices.getLast(), highPivots.getLast().booleanValue());
        return Optional.of(
                new PivotDominanceSummary(total / Math.max(1, pivotIndices.size() - 1), minimum, terminalDominance));
    }

    private double pivotDominanceScore(final BarSeries series, final int pivotIndex, final int spanStart,
            final int spanEnd, final boolean highPivot) {
        final double pivotPrice = highPivot ? highPrice(series, pivotIndex) : lowPrice(series, pivotIndex);
        final double spanExtreme = highPivot ? highestHigh(series, spanStart, spanEnd)
                : lowestLow(series, spanStart, spanEnd);
        final double spanRange = Math.max(1.0e-9, windowRange(series, spanStart, spanEnd));
        return clamp01(1.0 - (Math.abs(spanExtreme - pivotPrice) / spanRange));
    }

    private static String bullishCountLabel(final int phase) {
        return switch (phase) {
        case 1 -> "Bullish 1";
        case 2 -> "Bullish 1-2";
        case 3 -> "Bullish 1-2-3";
        case 4 -> "Bullish 1-2-3-4";
        case 5 -> "Bullish 1-2-3-4-5";
        default -> "Bullish";
        };
    }

    private static String bearishCountLabel(final int phase) {
        return switch (phase) {
        case 1 -> "Bearish A";
        case 2 -> "Bearish A-B";
        case 3 -> "Bearish A-B-C";
        default -> "Bearish";
        };
    }

    private static String currentCountLabel(final boolean bullish, final int phase) {
        return bullish ? bullishCountLabel(phase) : bearishCountLabel(phase);
    }

    private static int lowestLowIndex(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        int bestIndex = fromIndex;
        double bestPrice = Double.POSITIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            final double candidate = lowPrice(series, index);
            if (candidate < bestPrice) {
                bestPrice = candidate;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static double windowRange(final BarSeries series, final int startIndex, final int endIndex) {
        return Math.max(1.0e-9, highestHigh(series, startIndex, endIndex) - lowestLow(series, startIndex, endIndex));
    }

    private static double highestHigh(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        double highest = Double.NEGATIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            highest = Math.max(highest, highPrice(series, index));
        }
        return highest;
    }

    private static double lowestLow(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        double lowest = Double.POSITIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            lowest = Math.min(lowest, lowPrice(series, index));
        }
        return lowest;
    }

    private static double highPrice(final BarSeries series, final int index) {
        return series.getBar(index).getHighPrice().doubleValue();
    }

    private static double lowPrice(final BarSeries series, final int index) {
        return series.getBar(index).getLowPrice().doubleValue();
    }

    private static Num highPriceNum(final BarSeries series, final int index) {
        return series.getBar(index).getHighPrice();
    }

    private static Num lowPriceNum(final BarSeries series, final int index) {
        return series.getBar(index).getLowPrice();
    }

    private static int highestHighIndex(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        int bestIndex = fromIndex;
        double bestPrice = Double.NEGATIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            final double candidate = highPrice(series, index);
            if (candidate > bestPrice) {
                bestPrice = candidate;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private ElliottAnalysisResult clipAnalysisResult(final ElliottAnalysisResult result,
            final List<ElliottWaveAnalysisResult.BaseScenarioAssessment> ranked) {
        if (result == null || result.scenarios().size() <= maxScenarios) {
            return result;
        }

        final List<ElliottScenario> clippedScenarios = ranked.stream()
                .map(ElliottWaveAnalysisResult.BaseScenarioAssessment::scenario)
                .limit(maxScenarios)
                .sorted(ElliottScenarioSet.byConfidenceDescending())
                .toList();
        final Map<String, ElliottConfidenceBreakdown> clippedBreakdowns = new LinkedHashMap<>();
        for (final ElliottScenario scenario : clippedScenarios) {
            final ElliottConfidenceBreakdown breakdown = result.confidenceBreakdowns().get(scenario.id());
            if (breakdown != null) {
                clippedBreakdowns.put(scenario.id(), breakdown);
            }
        }
        final ElliottScenarioSet clippedScenarioSet = ElliottScenarioSet.of(clippedScenarios, result.index());
        return new ElliottAnalysisResult(result.degree(), result.index(), result.rawSwings(), result.processedSwings(),
                clippedScenarioSet, clippedBreakdowns, result.channel(), clippedScenarioSet.trendBias(),
                result.diagnostics());
    }

    private List<ElliottSwing> appendTerminalExtensionIfNeeded(final List<ElliottSwing> swings, final BarSeries series,
            final int endIndex, final ElliottDegree degree) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        final ElliottSwing lastSwing = swings.getLast();
        if (lastSwing.toIndex() >= endIndex) {
            return List.copyOf(swings);
        }

        final ProvisionalTerminal terminal = findTerminalProjection(series, lastSwing, endIndex);
        if (terminal == null || terminal.index() <= lastSwing.toIndex()) {
            return List.copyOf(swings);
        }
        final boolean nextSwingRising = !lastSwing.isRising();
        if (nextSwingRising && terminal.price().isLessThanOrEqual(lastSwing.toPrice())) {
            return List.copyOf(swings);
        }
        if (!nextSwingRising && terminal.price().isGreaterThanOrEqual(lastSwing.toPrice())) {
            return List.copyOf(swings);
        }

        final List<ElliottSwing> extended = new ArrayList<>(swings.size() + 1);
        extended.addAll(swings);
        extended.add(
                new ElliottSwing(lastSwing.toIndex(), terminal.index(), lastSwing.toPrice(), terminal.price(), degree));
        return List.copyOf(extended);
    }

    private ProvisionalTerminal findTerminalProjection(final BarSeries series, final ElliottSwing lastSwing,
            final int endIndex) {
        final int scanStart = Math.min(endIndex, lastSwing.toIndex() + 1);
        int bestIndex = lastSwing.toIndex();
        Num bestPrice = lastSwing.toPrice();
        for (int index = scanStart; index <= endIndex; index++) {
            final Bar bar = series.getBar(index);
            final boolean nextSwingRising = !lastSwing.isRising();
            final Num candidate = nextSwingRising ? bar.getHighPrice() : bar.getLowPrice();
            if (nextSwingRising) {
                if (candidate.isGreaterThan(bestPrice)) {
                    bestIndex = index;
                    bestPrice = candidate;
                }
            } else if (candidate.isLessThan(bestPrice)) {
                bestIndex = index;
                bestPrice = candidate;
            }
        }
        return new ProvisionalTerminal(bestIndex, bestPrice);
    }

    /**
     * Produces ranked base-degree scenario assessments by blending base confidence
     * with cross-degree compatibility.
     *
     * @param baseResult base-degree result
     * @param analyses   all degree snapshots, including supporting degrees
     * @return ranked scenario assessments (highest composite score first)
     */
    private List<ElliottWaveAnalysisResult.BaseScenarioAssessment> scoreBaseScenarios(
            final ElliottAnalysisResult baseResult, final List<ElliottWaveAnalysisResult.DegreeAnalysis> analyses) {
        final List<ElliottScenario> baseScenarios = baseResult.scenarios().all();
        if (baseScenarios.isEmpty()) {
            return List.of();
        }

        final List<ElliottWaveAnalysisResult.BaseScenarioAssessment> assessments = new ArrayList<>(
                baseScenarios.size());

        for (final ElliottScenario baseScenario : baseScenarios) {
            if (baseScenario == null) {
                continue;
            }
            double confidence = safeScore(baseScenario.confidenceScore());
            double rankedConfidence = rankedConfidenceScore(baseScenario, baseResult.processedSwings(), confidence);
            List<ElliottWaveAnalysisResult.SupportingScenarioMatch> matches = new ArrayList<>();
            double crossDegreeScore = crossDegreeScore(baseScenario, analyses, matches);
            double composite = (baseConfidenceWeight * rankedConfidence)
                    + ((1.0 - baseConfidenceWeight) * crossDegreeScore);

            assessments.add(new ElliottWaveAnalysisResult.BaseScenarioAssessment(baseScenario, confidence,
                    crossDegreeScore, composite, matches, baseResult.diagnostics()));
        }

        assessments
                .sort(Comparator.comparingDouble(ElliottWaveAnalysisResult.BaseScenarioAssessment::compositeScore)
                        .reversed()
                        .thenComparing(Comparator
                                .comparingDouble(ElliottWaveAnalysisResult.BaseScenarioAssessment::confidenceScore)
                                .reversed()));

        return List.copyOf(assessments);
    }

    private double rankedConfidenceScore(final ElliottScenario scenario, final List<ElliottSwing> processedSwings,
            final double rawConfidence) {
        double structuralPriority = scenarioStructuralPriority(scenario, processedSwings);
        return blend(rawConfidence, structuralPriority, STRUCTURAL_SELECTION_WEIGHT);
    }

    private double scenarioStructuralPriority(final ElliottScenario scenario,
            final List<ElliottSwing> processedSwings) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
        }
        if (processedSwings == null || processedSwings.isEmpty()) {
            return phaseProgressScore(scenario.currentPhase());
        }

        final ElliottSwing firstProcessed = processedSwings.getFirst();
        final ElliottSwing lastProcessed = processedSwings.getLast();
        final ElliottSwing firstScenario = scenario.swings().getFirst();
        final ElliottSwing lastScenario = scenario.swings().getLast();

        final double totalSpan = Math.max(1.0, lastProcessed.toIndex() - firstProcessed.fromIndex());
        final double scenarioSpan = Math.max(1.0, lastScenario.toIndex() - firstScenario.fromIndex());
        final double coverageScore = clamp01(scenarioSpan / totalSpan);
        final double startAlignmentScore = alignmentScore(firstScenario.fromIndex(), firstProcessed.fromIndex(),
                totalSpan);
        final double endAlignmentScore = alignmentScore(lastScenario.toIndex(), lastProcessed.toIndex(), totalSpan);
        final double anchorSpanScore = clamp01((coverageScore + startAlignmentScore + endAlignmentScore) / 3.0);
        final double completionScore = phaseProgressScore(scenario.currentPhase());
        final double terminalCompletionScore = terminalCompletionScore(scenario, lastScenario.toIndex(),
                lastProcessed.toIndex());
        final double waveRichnessScore = scenario.currentPhase().isCorrective() ? clamp01(scenario.waveCount() / 3.0)
                : clamp01(scenario.waveCount() / 5.0);
        final double confidenceCompletenessScore = safeScore(scenario.confidence().completenessScore());
        final double patternAlignmentScore = scenarioPatternAlignmentScore(scenario);
        final double spacingScore = swingSpacingScore(scenario.swings());
        return clamp01((anchorSpanScore + completionScore + terminalCompletionScore + waveRichnessScore
                + confidenceCompletenessScore + patternAlignmentScore + spacingScore) / 7.0);
    }

    private static double alignmentScore(final int actualIndex, final int expectedIndex, final double totalSpan) {
        return clamp01(1.0 - (Math.abs(actualIndex - expectedIndex) / Math.max(1.0, totalSpan)));
    }

    private static double terminalCompletionScore(final ElliottScenario scenario, final int scenarioEndIndex,
            final int targetEndIndex) {
        if (scenario == null || scenario.currentPhase() == null) {
            return 0.0;
        }
        final double endAlignment = alignmentScore(scenarioEndIndex, targetEndIndex,
                Math.max(1.0, Math.abs(targetEndIndex)));
        if (scenario.expectsCompletion()) {
            return endAlignment;
        }
        return clamp01((endAlignment * 0.55) + (phaseProgressScore(scenario.currentPhase()) * 0.45));
    }

    private static double swingSpacingScore(final List<ElliottSwing> swings) {
        if (swings == null || swings.isEmpty()) {
            return 0.0;
        }
        if (swings.size() == 1) {
            return 1.0;
        }
        final List<Integer> indices = new ArrayList<>(swings.size() + 1);
        indices.add(swings.getFirst().fromIndex());
        for (final ElliottSwing swing : swings) {
            indices.add(swing.toIndex());
        }
        return spacingBalanceScore(indices);
    }

    private static double spacingBalanceScore(final List<Integer> indices) {
        if (indices == null || indices.size() < 2) {
            return 0.0;
        }
        double minDuration = Double.POSITIVE_INFINITY;
        double maxDuration = Double.NEGATIVE_INFINITY;
        double totalDuration = 0.0;
        for (int index = 1; index < indices.size(); index++) {
            final double duration = Math.max(1.0, indices.get(index) - indices.get(index - 1));
            minDuration = Math.min(minDuration, duration);
            maxDuration = Math.max(maxDuration, duration);
            totalDuration += duration;
        }
        final double averageDuration = totalDuration / (indices.size() - 1);
        final double minBalance = clamp01(minDuration / Math.max(1.0, averageDuration));
        final double maxBalance = clamp01(averageDuration / Math.max(1.0, maxDuration));
        return clamp01((minBalance + maxBalance) / 2.0);
    }

    private static double scenarioPatternAlignmentScore(final ElliottScenario scenario) {
        if (scenario == null) {
            return 0.0;
        }
        if (scenario.type() == ScenarioType.UNKNOWN) {
            return 0.35;
        }
        if (scenario.currentPhase().isImpulse() && scenario.type().isImpulse()) {
            return 1.0;
        }
        if (scenario.currentPhase().isCorrective() && scenario.type().isCorrective()) {
            return 1.0;
        }
        if (scenario.currentPhase() == ElliottPhase.NONE) {
            return 0.5;
        }
        return 0.55;
    }

    private static double phaseProgressScore(final ElliottPhase phase) {
        if (phase == null || phase == ElliottPhase.NONE) {
            return 0.0;
        }
        if (phase.isImpulse()) {
            return clamp01(0.20 + (phase.impulseIndex() * 0.16));
        }
        if (phase.isCorrective()) {
            return clamp01(0.30 + (phase.correctiveIndex() * 0.25));
        }
        return 0.0;
    }

    private static double blend(final double primary, final double secondary, final double secondaryWeight) {
        return clamp01((primary * (1.0 - secondaryWeight)) + (secondary * secondaryWeight));
    }

    /**
     * Aggregates compatibility against supporting-degree best matches. Each
     * supporting degree contributes proportionally to its history fit score.
     *
     * @param baseScenario base-degree scenario to score
     * @param analyses     per-degree analyses
     * @param matchesOut   collector for best supporting matches
     * @return normalized cross-degree score in {@code [0.0, 1.0]}
     */
    private double crossDegreeScore(final ElliottScenario baseScenario,
            final List<ElliottWaveAnalysisResult.DegreeAnalysis> analyses,
            final List<ElliottWaveAnalysisResult.SupportingScenarioMatch> matchesOut) {
        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;

        for (final ElliottWaveAnalysisResult.DegreeAnalysis analysis : analyses) {
            if (analysis == null) {
                continue;
            }
            if (analysis.degree() == baseDegree) {
                continue;
            }
            ElliottScenarioSet scenarios = analysis.analysis().scenarios();
            List<ElliottScenario> candidates = scenarios == null ? List.of() : scenarios.all();
            if (candidates.isEmpty()) {
                continue;
            }

            Match bestMatch = bestMatch(baseScenario, candidates, analysis.degree());
            if (bestMatch == null) {
                continue;
            }

            matchesOut.add(new ElliottWaveAnalysisResult.SupportingScenarioMatch(analysis.degree(),
                    bestMatch.supportingScenarioId, bestMatch.supportingConfidence, bestMatch.compatibility,
                    bestMatch.weightedCompatibility, analysis.historyFitScore()));

            double layerWeight = analysis.historyFitScore();
            totalWeightedScore += layerWeight * bestMatch.compatibility;
            totalWeight += layerWeight;
        }

        if (totalWeight <= 0.0) {
            return DEFAULT_NEUTRAL_CROSS_DEGREE_SCORE;
        }

        return clamp01(totalWeightedScore / totalWeight);
    }

    /**
     * Finds the most compatible supporting scenario for the provided base scenario.
     * The selection criterion is weighted compatibility
     * ({@code compatibility * supportingConfidence}).
     *
     * @param baseScenario     base-degree scenario
     * @param candidates       candidate supporting scenarios
     * @param supportingDegree degree of the supporting scenarios
     * @return best match, or {@code null} when no valid candidate exists
     */
    private Match bestMatch(final ElliottScenario baseScenario, final List<ElliottScenario> candidates,
            final ElliottDegree supportingDegree) {
        Match best = null;
        for (final ElliottScenario supporting : candidates) {
            if (supporting == null) {
                continue;
            }
            double compatibility = compatibilityScore(baseScenario, supporting, baseDegree, supportingDegree);
            double supportingConfidence = safeScore(supporting.confidenceScore());
            double weightedCompatibility = compatibility * supportingConfidence;
            if (best == null || weightedCompatibility > best.weightedCompatibility) {
                best = new Match(supporting.id(), supportingConfidence, compatibility, weightedCompatibility);
            }
        }
        return best;
    }

    /**
     * Computes scenario compatibility for a base/supporting pair by blending
     * directional, structural, and invalidation consistency.
     *
     * @param baseScenario       base-degree scenario
     * @param supportingScenario supporting-degree scenario
     * @param base               base degree
     * @param supporting         supporting degree
     * @return compatibility score in {@code [0.0, 1.0]}
     */
    private double compatibilityScore(final ElliottScenario baseScenario, final ElliottScenario supportingScenario,
            final ElliottDegree base, final ElliottDegree supporting) {
        if (baseScenario == null || supportingScenario == null) {
            return 0.0;
        }
        if (base == null || supporting == null || base == supporting) {
            return 0.0;
        }

        DegreeRelation relation = supporting.isHigherOrEqual(base) ? DegreeRelation.HIGHER : DegreeRelation.LOWER;

        double directionScore = directionCompatibility(baseScenario, supportingScenario);
        double structureScore = structureCompatibility(baseScenario, supportingScenario);
        double invalidationScore = invalidationCompatibility(baseScenario, supportingScenario, relation);

        double score = (DIRECTION_COMPATIBILITY_WEIGHT * directionScore)
                + (STRUCTURE_COMPATIBILITY_WEIGHT * structureScore)
                + (INVALIDATION_COMPATIBILITY_WEIGHT * invalidationScore);
        return clamp01(score);
    }

    /**
     * Scores directional agreement between base and supporting scenarios.
     *
     * @param baseScenario       base scenario
     * @param supportingScenario supporting scenario
     * @return directional compatibility score in {@code [0.0, 1.0]}
     */
    private double directionCompatibility(final ElliottScenario baseScenario,
            final ElliottScenario supportingScenario) {
        if (!baseScenario.hasKnownDirection() || !supportingScenario.hasKnownDirection()) {
            return 0.5;
        }

        boolean sameDirection = baseScenario.isBullish() == supportingScenario.isBullish();
        if (sameDirection) {
            return 1.0;
        }

        if (baseScenario.type().isCorrective() || supportingScenario.type().isCorrective()) {
            return 0.6;
        }

        return 0.0;
    }

    /**
     * Scores structural agreement (impulse/corrective family) between scenarios.
     *
     * @param baseScenario       base scenario
     * @param supportingScenario supporting scenario
     * @return structure compatibility score in {@code [0.0, 1.0]}
     */
    private double structureCompatibility(final ElliottScenario baseScenario,
            final ElliottScenario supportingScenario) {
        ScenarioType baseType = baseScenario.type();
        ScenarioType supportingType = supportingScenario.type();

        if (baseType == ScenarioType.UNKNOWN || supportingType == ScenarioType.UNKNOWN) {
            return 0.5;
        }

        if (baseType.isImpulse() && supportingType.isImpulse()) {
            return 1.0;
        }
        if (baseType.isCorrective() && supportingType.isCorrective()) {
            return 0.9;
        }
        if ((baseType.isImpulse() && supportingType.isCorrective())
                || (baseType.isCorrective() && supportingType.isImpulse())) {
            return 0.7;
        }

        return 0.5;
    }

    /**
     * Scores invalidation-level compatibility using degree relation semantics:
     * higher degrees should generally be looser; lower degrees should generally be
     * tighter.
     *
     * @param baseScenario       base scenario
     * @param supportingScenario supporting scenario
     * @param relation           relationship of supporting degree to base degree
     * @return invalidation compatibility score in {@code [0.0, 1.0]}
     */
    private double invalidationCompatibility(final ElliottScenario baseScenario,
            final ElliottScenario supportingScenario, final DegreeRelation relation) {
        if (!baseScenario.hasKnownDirection() || !supportingScenario.hasKnownDirection()) {
            return 0.5;
        }
        if (baseScenario.isBullish() != supportingScenario.isBullish()) {
            return 0.5;
        }

        Num baseInvalidation = baseScenario.invalidationPrice();
        Num supportingInvalidation = supportingScenario.invalidationPrice();
        if (!Num.isValid(baseInvalidation) || !Num.isValid(supportingInvalidation)) {
            return 0.5;
        }

        boolean bullish = baseScenario.isBullish();
        boolean supportingLooser;
        if (bullish) {
            supportingLooser = supportingInvalidation.isLessThanOrEqual(baseInvalidation);
        } else {
            supportingLooser = supportingInvalidation.isGreaterThanOrEqual(baseInvalidation);
        }

        boolean ok;
        if (relation == DegreeRelation.HIGHER) {
            ok = supportingLooser;
        } else if (bullish) {
            ok = supportingInvalidation.isGreaterThanOrEqual(baseInvalidation);
        } else {
            ok = supportingInvalidation.isLessThanOrEqual(baseInvalidation);
        }
        return ok ? 1.0 : 0.0;
    }

    /**
     * Safely computes {@link ElliottDegree#historyFitScore(Duration, int)} while
     * collecting diagnostics in {@code notes}.
     *
     * @param degree      degree to evaluate
     * @param barDuration duration per bar
     * @param barCount    number of bars
     * @param notes       collector for human-readable diagnostics
     * @return history-fit score in {@code [0.0, 1.0]}
     */
    private double safeHistoryFitScore(final ElliottDegree degree, final Duration barDuration, final int barCount,
            final List<String> notes) {
        if (barDuration == null || barDuration.isZero() || barDuration.isNegative()) {
            return 0.0;
        }
        if (barCount <= 0) {
            return 0.0;
        }
        try {
            double score = degree.historyFitScore(barDuration, barCount);
            if (score < 1.0) {
                notes.add("Degree " + degree + " has limited history fit: " + String.format("%.2f", score));
            }
            return score;
        } catch (RuntimeException ex) {
            notes.add("Failed to compute history fit for " + degree + ": " + ex.getMessage());
            return 0.0;
        }
    }

    /**
     * Converts a {@link Num} score to a finite unit-interval double.
     *
     * @param score numeric score
     * @return clamped score in {@code [0.0, 1.0]}; invalid values return {@code 0}
     */
    private double safeScore(final Num score) {
        if (!Num.isValid(score)) {
            return 0.0;
        }
        double value = score.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return clamp01(value);
    }

    /**
     * Clamps a score to the unit interval and guards against NaN.
     *
     * @param value value to clamp
     * @return clamped value in {@code [0.0, 1.0]}
     */
    private static double clamp01(final double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private ElliottWaveAnalysisResult rebaseAnalysisResult(final ElliottWaveAnalysisResult result,
            final int indexOffset) {
        final List<ElliottWaveAnalysisResult.DegreeAnalysis> rebasedAnalyses = result.analyses()
                .stream()
                .map(analysis -> new ElliottWaveAnalysisResult.DegreeAnalysis(analysis.degree(),
                        analysis.index() + indexOffset, analysis.barCount(), analysis.barDuration(),
                        analysis.historyFitScore(), rebaseAnalysisSnapshot(analysis.analysis(), indexOffset)))
                .toList();
        final List<ElliottWaveAnalysisResult.BaseScenarioAssessment> rebasedRanked = result.rankedBaseScenarios()
                .stream()
                .map(assessment -> new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                        rebaseScenario(assessment.scenario(), indexOffset), assessment.confidenceScore(),
                        assessment.crossDegreeScore(), assessment.compositeScore(), assessment.supportingMatches(),
                        assessment.diagnostics()))
                .toList();
        return new ElliottWaveAnalysisResult(result.baseDegree(), rebasedAnalyses, rebasedRanked, result.notes());
    }

    private ElliottWaveAnalysisResult anchorWindowResult(final BarSeries rootSeries,
            final ElliottWaveAnalysisResult result, final int startIndex, final int endIndex) {
        final ElliottWaveAnalysisResult.DegreeAnalysis baseSnapshot = result.analysisFor(baseDegree).orElse(null);
        if (baseSnapshot == null) {
            return result;
        }

        final ElliottAnalysisResult anchoredBase = anchorWindowAnalysisSnapshot(rootSeries, baseSnapshot.analysis(),
                startIndex, endIndex);
        final List<ElliottWaveAnalysisResult.DegreeAnalysis> anchoredAnalyses = result.analyses()
                .stream()
                .map(analysis -> analysis.degree() == baseDegree
                        ? new ElliottWaveAnalysisResult.DegreeAnalysis(analysis.degree(), analysis.index(),
                                analysis.barCount(), analysis.barDuration(), analysis.historyFitScore(), anchoredBase)
                        : analysis)
                .toList();
        final List<ElliottWaveAnalysisResult.BaseScenarioAssessment> anchoredRanked = result.rankedBaseScenarios()
                .stream()
                .map(assessment -> new ElliottWaveAnalysisResult.BaseScenarioAssessment(
                        anchorWindowScenario(rootSeries, assessment.scenario(), startIndex, endIndex),
                        assessment.confidenceScore(), assessment.crossDegreeScore(), assessment.compositeScore(),
                        assessment.supportingMatches(), assessment.diagnostics()))
                .toList();
        return new ElliottWaveAnalysisResult(result.baseDegree(), anchoredAnalyses, anchoredRanked, result.notes());
    }

    private ElliottAnalysisResult anchorWindowAnalysisSnapshot(final BarSeries rootSeries,
            final ElliottAnalysisResult analysis, final int startIndex, final int endIndex) {
        final List<ElliottScenario> anchoredScenarios = analysis.scenarios()
                .all()
                .stream()
                .map(scenario -> anchorWindowScenario(rootSeries, scenario, startIndex, endIndex))
                .toList();
        final ElliottScenarioSet anchoredScenarioSet = ElliottScenarioSet.of(anchoredScenarios, analysis.index());
        return new ElliottAnalysisResult(analysis.degree(), analysis.index(), analysis.rawSwings(),
                analysis.processedSwings(), anchoredScenarioSet, analysis.confidenceBreakdowns(), analysis.channel(),
                anchoredScenarioSet.trendBias(), analysis.diagnostics());
    }

    private ElliottScenario anchorWindowScenario(final BarSeries rootSeries, final ElliottScenario scenario,
            final int startIndex, final int endIndex) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return scenario;
        }

        final ElliottSwing firstSwing = scenario.swings().getFirst();
        final ElliottSwing lastSwing = scenario.swings().getLast();
        final int snapBars = anchorWindowSnapBars(startIndex, endIndex);
        final boolean snapStart = Math.abs(firstSwing.fromIndex() - startIndex) <= snapBars;
        final boolean snapEnd = Math.abs(lastSwing.toIndex() - endIndex) <= snapBars;
        if (!snapStart && !snapEnd) {
            return scenario;
        }

        final List<ElliottSwing> anchoredSwings = new ArrayList<>(scenario.swings().size());
        for (int index = 0; index < scenario.swings().size(); index++) {
            final ElliottSwing swing = scenario.swings().get(index);
            final boolean first = index == 0;
            final boolean last = index == scenario.swings().size() - 1;
            final int fromIndex = first && snapStart ? startIndex : swing.fromIndex();
            final int toIndex = last && snapEnd ? endIndex : swing.toIndex();
            final Num fromPrice = first && snapStart ? boundaryPrice(rootSeries, startIndex, !swing.isRising())
                    : swing.fromPrice();
            final Num toPrice = last && snapEnd ? boundaryPrice(rootSeries, endIndex, swing.isRising())
                    : swing.toPrice();
            anchoredSwings.add(new ElliottSwing(fromIndex, toIndex, fromPrice, toPrice, swing.degree()));
        }

        final ElliottScenario.Builder builder = ElliottScenario.builder()
                .id(scenario.id())
                .currentPhase(scenario.currentPhase())
                .swings(anchoredSwings)
                .confidence(scenario.confidence())
                .degree(scenario.degree())
                .invalidationPrice(scenario.invalidationPrice())
                .primaryTarget(scenario.primaryTarget())
                .fibonacciTargets(scenario.fibonacciTargets())
                .type(scenario.type())
                .startIndex(anchoredSwings.getFirst().fromIndex());
        if (scenario.hasKnownDirection()) {
            builder.bullishDirection(scenario.isBullish());
        }
        return builder.build();
    }

    private static int anchorWindowSnapBars(final int startIndex, final int endIndex) {
        final int span = Math.max(1, endIndex - startIndex);
        return Math.max(WINDOW_ANCHOR_SNAP_MIN_BARS,
                Math.min(WINDOW_ANCHOR_SNAP_MAX_BARS, (int) Math.round(span * WINDOW_ANCHOR_SNAP_FRACTION)));
    }

    private static Num boundaryPrice(final BarSeries rootSeries, final int index, final boolean highPivot) {
        final Bar bar = rootSeries.getBar(index);
        return highPivot ? bar.getHighPrice() : bar.getLowPrice();
    }

    private ElliottAnalysisResult rebaseAnalysisSnapshot(final ElliottAnalysisResult analysis, final int indexOffset) {
        final List<ElliottSwing> rebasedRaw = rebaseSwings(analysis.rawSwings(), indexOffset);
        final List<ElliottSwing> rebasedProcessed = rebaseSwings(analysis.processedSwings(), indexOffset);
        final List<ElliottScenario> rebasedScenarios = analysis.scenarios()
                .all()
                .stream()
                .map(scenario -> rebaseScenario(scenario, indexOffset))
                .toList();
        final ElliottScenarioSet rebasedScenarioSet = ElliottScenarioSet.of(rebasedScenarios,
                analysis.index() + indexOffset);
        return new ElliottAnalysisResult(analysis.degree(), analysis.index() + indexOffset, rebasedRaw,
                rebasedProcessed, rebasedScenarioSet, analysis.confidenceBreakdowns(), analysis.channel(),
                rebasedScenarioSet.trendBias(), analysis.diagnostics());
    }

    private List<ElliottSwing> rebaseSwings(final List<ElliottSwing> swings, final int indexOffset) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        return swings.stream()
                .map(swing -> new ElliottSwing(swing.fromIndex() + indexOffset, swing.toIndex() + indexOffset,
                        swing.fromPrice(), swing.toPrice(), swing.degree()))
                .toList();
    }

    private ElliottScenario rebaseScenario(final ElliottScenario scenario, final int indexOffset) {
        final ElliottScenario.Builder builder = ElliottScenario.builder()
                .id(scenario.id())
                .currentPhase(scenario.currentPhase())
                .swings(rebaseSwings(scenario.swings(), indexOffset))
                .confidence(scenario.confidence())
                .degree(scenario.degree())
                .invalidationPrice(scenario.invalidationPrice())
                .primaryTarget(scenario.primaryTarget())
                .fibonacciTargets(scenario.fibonacciTargets())
                .type(scenario.type())
                .startIndex(scenario.startIndex() + indexOffset);
        if (scenario.hasKnownDirection()) {
            builder.bullishDirection(scenario.isBullish());
        }
        return builder.build();
    }

    private record ProvisionalTerminal(int index, Num price) {
    }

    /**
     * Core-selected anchored-window scenario plus whether it satisfied the
     * configured acceptance gate.
     *
     * @param assessment selected anchored-window assessment
     * @param accepted   {@code true} when the selection passed the acceptance gate;
     *                   {@code false} when it is the strongest fallback
     * @since 0.22.4
     */
    public record AnchoredWindowSelection(ElliottWaveAnalysisResult.WindowScenarioAssessment assessment,
            boolean accepted) {

        public AnchoredWindowSelection {
            Objects.requireNonNull(assessment, "assessment");
        }
    }

    /**
     * Reusable pivot graph derived from one processed swing path.
     *
     * @param pivots        ordered pivot sequence
     * @param higherDegrees configured fractal confirmation degrees above the base
     * @param lowerDegrees  configured fractal confirmation degrees below the base
     * @since 0.22.4
     */
    record MacroPivotGraph(List<MacroPivot> pivots, int higherDegrees, int lowerDegrees) {

        MacroPivotGraph {
            pivots = pivots == null ? List.of() : List.copyOf(pivots);
        }

        static MacroPivotGraph empty(final int higherDegrees, final int lowerDegrees) {
            return new MacroPivotGraph(List.of(), higherDegrees, lowerDegrees);
        }
    }

    /**
     * One pivot in the reusable macro graph.
     *
     * @param highPivot {@code true} when the pivot is a high, {@code false} when it
     *                  is a low
     * @param barIndex  bar index of the pivot in the root series
     * @param time      bar timestamp in UTC
     * @param price     pivot price
     * @param degree    swing-degree provenance associated with this pivot
     * @since 0.22.4
     */
    record MacroPivot(boolean highPivot, int barIndex, Instant time, Num price, ElliottDegree degree) {

        MacroPivot {
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(price, "price");
            Objects.requireNonNull(degree, "degree");
        }
    }

    private record MacroPivotRank(int index, double dominance) {
    }

    /**
     * One candidate macro leg for bounded global search.
     *
     * @param id              stable candidate id for diagnostics/tests
     * @param startPivotIndex inclusive pivot index where the leg begins
     * @param endPivotIndex   inclusive pivot index where the leg ends
     * @param bullish         leg direction
     * @param fitScore        local leg fit score in {@code [0,1]}
     * @since 0.22.4
     */
    record CanonicalLegCandidate(String id, int startPivotIndex, int endPivotIndex, boolean bullish, double fitScore) {

        CanonicalLegCandidate {
            Objects.requireNonNull(id, "id");
            if (endPivotIndex <= startPivotIndex) {
                throw new IllegalArgumentException("endPivotIndex must be > startPivotIndex");
            }
        }
    }

    /**
     * One bounded global-search path through alternating candidate legs.
     *
     * @param legs  ordered alternating legs
     * @param score aggregate coherence score
     * @since 0.22.4
     */
    record CanonicalStructurePath(List<CanonicalLegCandidate> legs, double score) {

        private static final Comparator<CanonicalStructurePath> ORDERING = Comparator
                .comparingDouble(CanonicalStructurePath::score)
                .reversed()
                .thenComparing(Comparator.comparingInt((CanonicalStructurePath path) -> path.legs().size()).reversed())
                .thenComparingInt(CanonicalStructurePath::terminalPivotIndex);

        CanonicalStructurePath {
            legs = legs == null ? List.of() : List.copyOf(legs);
        }

        static CanonicalStructurePath empty() {
            return new CanonicalStructurePath(List.of(), 0.0);
        }

        boolean canAppend(final CanonicalLegCandidate candidate) {
            if (legs.isEmpty()) {
                return true;
            }
            final CanonicalLegCandidate last = legs.getLast();
            return last.bullish() != candidate.bullish() && candidate.startPivotIndex() >= last.endPivotIndex();
        }

        CanonicalStructurePath append(final CanonicalLegCandidate candidate) {
            final List<CanonicalLegCandidate> nextLegs = new ArrayList<>(legs.size() + 1);
            nextLegs.addAll(legs);
            nextLegs.add(candidate);
            return new CanonicalStructurePath(nextLegs, score + incrementalScore(candidate));
        }

        private double incrementalScore(final CanonicalLegCandidate candidate) {
            if (legs.isEmpty()) {
                return candidate.fitScore();
            }
            final CanonicalLegCandidate last = legs.getLast();
            final int gap = Math.max(0, candidate.startPivotIndex() - last.endPivotIndex());
            final double continuity = gap == 0 ? CANONICAL_SEARCH_CONTIGUITY_BONUS
                    : Math.max(0.0, CANONICAL_SEARCH_CONTIGUITY_BONUS - (gap * CANONICAL_SEARCH_GAP_PENALTY_PER_PIVOT));
            return candidate.fitScore() + CANONICAL_SEARCH_ALTERNATION_BONUS + continuity;
        }

        private int terminalPivotIndex() {
            return legs.isEmpty() ? Integer.MAX_VALUE : legs.getLast().endPivotIndex();
        }
    }

    private record CurrentCycleStartCandidate(int barIndex, Num price, double rawScore, double normalizedScore) {
    }

    private record PivotDominanceSummary(double averagePivotDominance, double minimumPivotDominance,
            double terminalPivotDominance) {
    }

    private static final class CurrentCycleStartAccumulator {

        private final int barIndex;
        private final Num price;
        private double rawScore;

        private CurrentCycleStartAccumulator(final int barIndex, final Num price) {
            this.barIndex = barIndex;
            this.price = price;
        }

        private void add(final double score) {
            rawScore += score;
        }

        private CurrentCycleStartCandidate toCandidate(final int startIndex, final int endIndex) {
            final double segmentLength = Math.max(1.0, endIndex - startIndex);
            final double normalized = clamp01(rawScore / Math.max(1.0, segmentLength / 25.0));
            return new CurrentCycleStartCandidate(barIndex, price, rawScore, normalized);
        }
    }

    /**
     * Returns the most recent swings bounded by the configured scenario window.
     *
     * @param swings swing sequence
     * @param window requested maximum window size
     * @return bounded immutable swing list
     */
    private static List<ElliottSwing> recentSwings(final List<ElliottSwing> swings, final int window) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        if (window <= 0 || swings.size() <= window) {
            return List.copyOf(swings);
        }
        return List.copyOf(swings.subList(swings.size() - window, swings.size()));
    }

    /**
     * Builds a simple projected price channel from the most recent two rising and
     * two falling swings.
     *
     * @param numFactory numeric factory for arithmetic
     * @param swings     candidate swings
     * @param index      projection target index
     * @return projected channel, or a NaN channel when projection is not possible
     */
    private static ElliottChannel computeChannel(final NumFactory numFactory, final List<ElliottSwing> swings,
            final int index) {
        if (swings == null || swings.size() < 4) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        List<ElliottSwing> rising = latestSwingsByDirection(swings, true);
        List<ElliottSwing> falling = latestSwingsByDirection(swings, false);
        if (rising.size() < 2 || falling.size() < 2) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        PivotLine upper = projectLine(numFactory, rising.get(rising.size() - 2), rising.get(rising.size() - 1), index);
        PivotLine lower = projectLine(numFactory, falling.get(falling.size() - 2), falling.get(falling.size() - 1),
                index);
        if (!upper.isValid() || !lower.isValid()) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        Num median = upper.value.plus(lower.value).dividedBy(numFactory.two());
        return new ElliottChannel(upper.value, lower.value, median);
    }

    /**
     * Selects the latest two swings matching the requested direction.
     *
     * @param swings swing sequence
     * @param rising {@code true} for rising swings; {@code false} for falling
     * @return up to two swings in chronological order
     */
    private static List<ElliottSwing> latestSwingsByDirection(final List<ElliottSwing> swings, final boolean rising) {
        List<ElliottSwing> filtered = new ArrayList<>(2);
        for (int i = swings.size() - 1; i >= 0 && filtered.size() < 2; i--) {
            ElliottSwing swing = swings.get(i);
            if (swing.isRising() == rising) {
                filtered.add(swing);
            }
        }
        Collections.reverse(filtered);
        return filtered;
    }

    /**
     * Projects a pivot line at {@code index} using two swing endpoints.
     *
     * @param numFactory numeric factory for arithmetic
     * @param older      older swing
     * @param newer      newer swing
     * @param index      target index for projection
     * @return projected pivot line, or invalid when projection inputs are
     *         insufficient
     */
    private static PivotLine projectLine(final NumFactory numFactory, final ElliottSwing older,
            final ElliottSwing newer, final int index) {
        if (older == null || newer == null) {
            return PivotLine.invalid();
        }
        int span = newer.toIndex() - older.toIndex();
        if (span == 0) {
            return PivotLine.invalid();
        }
        Num spanNum = numFactory.numOf(span);
        if (spanNum.isZero()) {
            return PivotLine.invalid();
        }
        Num slope = newer.toPrice().minus(older.toPrice()).dividedBy(spanNum);
        int distance = index - newer.toIndex();
        Num projected = newer.toPrice().plus(slope.multipliedBy(numFactory.numOf(distance)));
        if (Num.isNaNOrNull(projected)) {
            return PivotLine.invalid();
        }
        return new PivotLine(projected);
    }

    /**
     * Lightweight projected line wrapper used for channel boundary calculations.
     *
     * @param value projected price value
     */
    private record PivotLine(Num value) {

        /**
         * @return invalid pivot line marker
         */
        private static PivotLine invalid() {
            return new PivotLine(NaN);
        }

        /**
         * @return {@code true} when the projected value is finite
         */
        private boolean isValid() {
            return Num.isValid(value);
        }
    }

    /**
     * Builds the ordered degree list to analyze: higher degrees (outer to inner),
     * base degree, then lower degrees (inner to outer).
     *
     * @param base   base degree
     * @param higher number of higher degrees to include
     * @param lower  number of lower degrees to include
     * @return ordered immutable degree list
     */
    private static List<ElliottDegree> degreesToAnalyze(final ElliottDegree base, final int higher, final int lower) {
        Objects.requireNonNull(base, "base");

        List<ElliottDegree> higherDegrees = new ArrayList<>(Math.max(0, higher));
        ElliottDegree cursor = base;
        for (int i = 0; i < higher; i++) {
            ElliottDegree next = cursor.higherDegree();
            if (next == cursor) {
                break;
            }
            higherDegrees.add(next);
            cursor = next;
        }
        Collections.reverse(higherDegrees);

        List<ElliottDegree> degrees = new ArrayList<>(higherDegrees.size() + 1 + Math.max(0, lower));
        degrees.addAll(higherDegrees);
        degrees.add(base);

        cursor = base;
        for (int i = 0; i < lower; i++) {
            ElliottDegree next = cursor.lowerDegree();
            if (next == cursor) {
                break;
            }
            degrees.add(next);
            cursor = next;
        }

        return List.copyOf(degrees);
    }

    /**
     * Creates the default selector that trims each degree to its recommended
     * maximum history window while preserving the latest bars.
     *
     * @return default series selector
     */
    private static SeriesSelector<ElliottDegree> defaultSeriesSelector() {
        return (series, degree) -> {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(degree, "degree");
            if (series.isEmpty()) {
                return series;
            }
            final Duration barDuration = series.getFirstBar().getTimePeriod();
            if (barDuration == null || barDuration.isZero() || barDuration.isNegative()) {
                return series;
            }

            final ElliottDegree.RecommendedHistory history = degree.recommendedHistoryDays();
            if (!history.hasMax()) {
                return series;
            }

            final double daysPerBar = barDuration.toMillis() / (double) Duration.ofDays(1).toMillis();
            if (daysPerBar <= 0.0) {
                return series;
            }

            final int availableBars = series.getBarCount();
            final int maxBars = Math.max(1, (int) Math.floor(history.maxDays() / daysPerBar));
            final int targetBars = Math.min(availableBars, maxBars);
            if (targetBars >= availableBars) {
                return series;
            }

            final int endExclusive = series.getEndIndex() + 1;
            final int startIndex = Math.max(series.getBeginIndex(), endExclusive - targetBars);
            return series.getSubSeries(startIndex, endExclusive);
        };
    }

    /**
     * Scales a base value by degree distance and clamps it to configured bounds.
     *
     * @param base            base degree
     * @param target          target degree
     * @param baseValue       value at the base degree
     * @param factorPerDegree multiplicative factor per degree step
     * @param minValue        lower clamp bound
     * @param maxValue        upper clamp bound
     * @return scaled and clamped value
     */
    private static double scale(final ElliottDegree base, final ElliottDegree target, final double baseValue,
            final double factorPerDegree, final double minValue, final double maxValue) {
        int delta = base.ordinal() - target.ordinal();
        double value = baseValue * Math.pow(factorPerDegree, delta);
        if (value < minValue) {
            return minValue;
        }
        if (value > maxValue) {
            return maxValue;
        }
        return value;
    }

    private static int scaleWindow(final ElliottDegree base, final ElliottDegree target, final int baseWindow,
            final int minWindow, final int maxWindow) {
        double scaled = scale(base, target, baseWindow, 1.45, minWindow, maxWindow);
        return Math.max(minWindow, Math.min(maxWindow, (int) Math.round(scaled)));
    }

    private enum DegreeRelation {
        HIGHER, LOWER
    }

    private record Match(String supportingScenarioId, double supportingConfidence, double compatibility,
            double weightedCompatibility) {
    }

    /**
     * Builder for {@link ElliottWaveAnalysisRunner}.
     *
     * @since 0.22.4
     */
    public static final class Builder {

        private ElliottDegree baseDegree;
        private int higherDegrees = DEFAULT_HIGHER_DEGREES;
        private int lowerDegrees = DEFAULT_LOWER_DEGREES;
        private boolean higherDegreesExplicit;
        private boolean lowerDegreesExplicit;
        private boolean maxScenariosExplicit;
        private boolean scenarioSwingWindowExplicit;
        private SeriesSelector<ElliottDegree> seriesSelector;
        private AnalysisRunner<ElliottDegree, ElliottAnalysisResult> analysisRunner;
        private double baseConfidenceWeight = DEFAULT_BASE_CONFIDENCE_WEIGHT;
        private boolean baseConfidenceWeightExplicit;
        private ElliottLogicProfile logicProfile = ElliottLogicProfile.ORTHODOX_CLASSICAL;

        private SwingDetector swingDetector;
        private SwingFilter swingFilter;
        private Function<NumFactory, ConfidenceModel> confidenceModelFactory;
        private PatternSet patternSet;
        private double minConfidence = ElliottScenarioGenerator.DEFAULT_MIN_CONFIDENCE;
        private int maxScenarios = ElliottScenarioGenerator.DEFAULT_MAX_SCENARIOS;
        private int scenarioSwingWindow = DEFAULT_SCENARIO_SWING_WINDOW;

        private Builder() {
        }

        /**
         * @param degree base degree that drives scenario ranking
         * @return builder
         * @since 0.22.4
         */
        public Builder degree(final ElliottDegree degree) {
            this.baseDegree = Objects.requireNonNull(degree, "degree");
            return this;
        }

        /**
         * @param higherDegrees number of higher degrees to include (0 for none)
         * @return builder
         * @since 0.22.4
         */
        public Builder higherDegrees(final int higherDegrees) {
            if (higherDegrees < 0) {
                throw new IllegalArgumentException("higherDegrees must be >= 0");
            }
            this.higherDegrees = higherDegrees;
            this.higherDegreesExplicit = true;
            return this;
        }

        /**
         * @param lowerDegrees number of lower degrees to include (0 for none)
         * @return builder
         * @since 0.22.4
         */
        public Builder lowerDegrees(final int lowerDegrees) {
            if (lowerDegrees < 0) {
                throw new IllegalArgumentException("lowerDegrees must be >= 0");
            }
            this.lowerDegrees = lowerDegrees;
            this.lowerDegreesExplicit = true;
            return this;
        }

        /**
         * @param seriesSelector series selector used per degree
         * @return builder
         * @since 0.22.4
         */
        public Builder seriesSelector(final SeriesSelector<ElliottDegree> seriesSelector) {
            this.seriesSelector = Objects.requireNonNull(seriesSelector, "seriesSelector");
            return this;
        }

        /**
         * Overrides the built-in analysis pipeline for each degree.
         *
         * <p>
         * Use this when you want to run analysis on resampled series, reuse
         * indicator-style analysis ({@link ElliottWaveFacade}), or apply custom
         * filtering/compression beyond the defaults.
         *
         * @param analysisRunner analysis runner implementation
         * @return builder
         * @since 0.22.4
         */
        public Builder analysisRunner(final AnalysisRunner<ElliottDegree, ElliottAnalysisResult> analysisRunner) {
            this.analysisRunner = Objects.requireNonNull(analysisRunner, "analysisRunner");
            return this;
        }

        /**
         * Controls the blend between base scenario confidence and cross-degree
         * compatibility.
         *
         * <p>
         * When no supporting degrees are configured, the analysis automatically uses
         * weight=1.0 to preserve single-degree confidence ordering.
         *
         * @param weight weight in range [0.0, 1.0] applied to the base confidence
         * @return builder
         * @since 0.22.4
         */
        public Builder baseConfidenceWeight(final double weight) {
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("baseConfidenceWeight must be in [0.0, 1.0]");
            }
            this.baseConfidenceWeight = weight;
            this.baseConfidenceWeightExplicit = true;
            return this;
        }

        /**
         * Applies a reusable Elliott logic profile to the built-in runner pipeline.
         *
         * <p>
         * Profiles tune the default swing-detector sensitivity, supporting-degree
         * confirmation depth, enabled pattern families, confidence-model style, and
         * cross-degree confidence weight. Any explicit builder calls made afterwards
         * still take precedence.
         *
         * @param logicProfile logic profile to apply
         * @return builder
         * @since 0.22.4
         */
        public Builder logicProfile(final ElliottLogicProfile logicProfile) {
            this.logicProfile = Objects.requireNonNull(logicProfile, "logicProfile");
            return this;
        }

        /**
         * Configures the swing detector used by the built-in analysis pipeline.
         *
         * @param swingDetector swing detector implementation
         * @return builder
         * @since 0.22.4
         */
        public Builder swingDetector(final SwingDetector swingDetector) {
            this.swingDetector = Objects.requireNonNull(swingDetector, "swingDetector");
            return this;
        }

        /**
         * Configures the swing filter used by the built-in analysis pipeline.
         *
         * @param swingFilter swing filter to apply after detection
         * @return builder
         * @since 0.22.4
         */
        public Builder swingFilter(final SwingFilter swingFilter) {
            this.swingFilter = swingFilter;
            return this;
        }

        /**
         * @param confidenceModel confidence model to use
         * @return builder
         * @since 0.22.4
         */
        public Builder confidenceModel(final ConfidenceModel confidenceModel) {
            Objects.requireNonNull(confidenceModel, "confidenceModel");
            this.confidenceModelFactory = unused -> confidenceModel;
            return this;
        }

        /**
         * @param confidenceModelFactory factory for confidence models
         * @return builder
         * @since 0.22.4
         */
        public Builder confidenceModelFactory(final Function<NumFactory, ConfidenceModel> confidenceModelFactory) {
            this.confidenceModelFactory = Objects.requireNonNull(confidenceModelFactory, "confidenceModelFactory");
            return this;
        }

        /**
         * @param patternSet enabled pattern set
         * @return builder
         * @since 0.22.4
         */
        public Builder patternSet(final PatternSet patternSet) {
            this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
            return this;
        }

        /**
         * @param minConfidence minimum confidence threshold
         * @return builder
         * @since 0.22.4
         */
        public Builder minConfidence(final double minConfidence) {
            if (minConfidence < 0.0 || minConfidence > 1.0) {
                throw new IllegalArgumentException("minConfidence must be in [0.0, 1.0]");
            }
            this.minConfidence = minConfidence;
            return this;
        }

        /**
         * @param maxScenarios maximum scenarios to retain
         * @return builder
         * @since 0.22.4
         */
        public Builder maxScenarios(final int maxScenarios) {
            if (maxScenarios <= 0) {
                throw new IllegalArgumentException("maxScenarios must be positive");
            }
            this.maxScenarios = maxScenarios;
            this.maxScenariosExplicit = true;
            return this;
        }

        /**
         * @param scenarioSwingWindow number of swings passed to scenario generation
         *                            ({@code 0} disables windowing and uses all
         *                            available swings)
         * @return builder
         * @since 0.22.4
         */
        public Builder scenarioSwingWindow(final int scenarioSwingWindow) {
            if (scenarioSwingWindow < 0) {
                throw new IllegalArgumentException("scenarioSwingWindow must be >= 0");
            }
            this.scenarioSwingWindow = scenarioSwingWindow;
            this.scenarioSwingWindowExplicit = true;
            return this;
        }

        /**
         * Builds the analysis entry point.
         *
         * @return analysis instance
         * @since 0.22.4
         */
        public ElliottWaveAnalysisRunner build() {
            if (baseDegree == null) {
                throw new IllegalStateException("degree must be configured");
            }
            return new ElliottWaveAnalysisRunner(this);
        }
    }
}
