/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        this.higherDegrees = builder.higherDegrees;
        this.lowerDegrees = builder.lowerDegrees;
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
        this.maxScenarios = builder.maxScenarios;
        this.scenarioSwingWindow = builder.scenarioSwingWindow;

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
                trendBias);
    }

    private int internalScenarioBudget(final BarSeries series) {
        if (!usesDefaultAnalysisRunner || scenarioSwingWindow != 0
                || series.getBarCount() < BROAD_HISTORY_FILTER_BAR_THRESHOLD) {
            return maxScenarios;
        }
        return Math.max(maxScenarios, Math.min(BROAD_HISTORY_INTERNAL_SCENARIO_CAP,
                maxScenarios * BROAD_HISTORY_INTERNAL_SCENARIO_MULTIPLIER));
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
                clippedScenarioSet, clippedBreakdowns, result.channel(), clippedScenarioSet.trendBias());
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
        if (scanStart > endIndex) {
            return null;
        }

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
                    crossDegreeScore, composite, matches));
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
                        assessment.crossDegreeScore(), assessment.compositeScore(), assessment.supportingMatches()))
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
                        assessment.supportingMatches()))
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
                anchoredScenarioSet.trendBias());
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
                rebasedScenarioSet.trendBias());
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
        private SeriesSelector<ElliottDegree> seriesSelector;
        private AnalysisRunner<ElliottDegree, ElliottAnalysisResult> analysisRunner;
        private double baseConfidenceWeight = DEFAULT_BASE_CONFIDENCE_WEIGHT;
        private boolean baseConfidenceWeightExplicit;
        private ElliottLogicProfile logicProfile;

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
         * Profiles tune the default swing-detector sensitivity, enabled pattern
         * families, confidence-model style, and cross-degree confidence weight. Any
         * explicit builder calls made afterwards still take precedence.
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
