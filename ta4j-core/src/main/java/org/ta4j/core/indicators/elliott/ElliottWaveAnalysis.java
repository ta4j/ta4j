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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
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
 * {@link ElliottScenarioSet} by measuring compatibility against the best
 * matching scenarios in the supporting degrees.
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
 * @since 0.22.3
 */
public final class ElliottWaveAnalysis {

    /**
     * Executes a single-degree Elliott wave analysis.
     *
     * <p>
     * This is a pluggable seam. Implementations may run the built-in analysis
     * pipeline, build results from indicator-style analysis
     * ({@link ElliottWaveFacade}), or run on resampled series.
     *
     * @since 0.22.3
     */
    @FunctionalInterface
    public interface AnalysisRunner {

        /**
         * Runs analysis for a given degree.
         *
         * @param series series to analyze
         * @param degree degree to analyze at
         * @return analysis snapshot
         * @since 0.22.3
         */
        ElliottAnalysisResult analyze(BarSeries series, ElliottDegree degree);
    }

    /**
     * Selects the series window (or transformed series) to use for a given degree.
     *
     * @since 0.22.3
     */
    @FunctionalInterface
    public interface SeriesSelector {

        /**
         * Selects a series for the requested degree.
         *
         * @param series root input series
         * @param degree degree to select for
         * @return selected series (may be a subseries)
         * @since 0.22.3
         */
        BarSeries select(BarSeries series, ElliottDegree degree);
    }

    private static final AdaptiveZigZagConfig DEFAULT_ZIGZAG_CONFIG = new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3);

    private static final int DEFAULT_SCENARIO_SWING_WINDOW = 5;

    private static final double DEFAULT_BASE_CONFIDENCE_WEIGHT = 0.7;
    private static final double DEFAULT_NEUTRAL_CROSS_DEGREE_SCORE = 0.5;
    private static final int DEFAULT_HIGHER_DEGREES = 1;
    private static final int DEFAULT_LOWER_DEGREES = 1;
    private static final double DIRECTION_COMPATIBILITY_WEIGHT = 0.55;
    private static final double STRUCTURE_COMPATIBILITY_WEIGHT = 0.30;
    private static final double INVALIDATION_COMPATIBILITY_WEIGHT = 0.15;

    private final ElliottDegree baseDegree;
    private final int higherDegrees;
    private final int lowerDegrees;
    private final SeriesSelector seriesSelector;
    private final AnalysisRunner analysisRunner;
    private final double baseConfidenceWeight;

    // Built-in single-degree analysis pipeline configuration (used by default
    // runner only).
    private final SwingDetector swingDetector;
    private final SwingFilter swingFilter;
    private final Function<NumFactory, ConfidenceModel> confidenceModelFactory;
    private final PatternSet patternSet;
    private final double minConfidence;
    private final int maxScenarios;
    private final int scenarioSwingWindow;

    private ElliottWaveAnalysis(final Builder builder) {
        this.baseDegree = Objects.requireNonNull(builder.baseDegree, "baseDegree");
        this.higherDegrees = builder.higherDegrees;
        this.lowerDegrees = builder.lowerDegrees;
        this.seriesSelector = builder.seriesSelector == null ? defaultSeriesSelector() : builder.seriesSelector;

        this.swingDetector = builder.swingDetector == null ? SwingDetectors.adaptiveZigZag(DEFAULT_ZIGZAG_CONFIG)
                : builder.swingDetector;
        this.swingFilter = builder.swingFilter;
        this.confidenceModelFactory = builder.confidenceModelFactory == null ? ConfidenceProfiles::defaultModel
                : builder.confidenceModelFactory;
        this.patternSet = builder.patternSet == null ? PatternSet.all() : builder.patternSet;
        this.minConfidence = builder.minConfidence;
        this.maxScenarios = builder.maxScenarios;
        this.scenarioSwingWindow = builder.scenarioSwingWindow;

        int supportingDegrees = Math.max(0, higherDegrees) + Math.max(0, lowerDegrees);
        this.baseConfidenceWeight = supportingDegrees == 0 ? 1.0 : builder.baseConfidenceWeight;

        this.analysisRunner = builder.analysisRunner == null ? this::runDefaultAnalysis : builder.analysisRunner;
    }

    /**
     * Creates a new builder.
     *
     * @return builder
     * @since 0.22.3
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
     * @since 0.22.3
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
            }
        }

        if (baseResult == null) {
            throw new IllegalStateException("Base degree " + baseDegree + " analysis was not available");
        }

        final List<ElliottWaveAnalysisResult.BaseScenarioAssessment> ranked = scoreBaseScenarios(baseResult,
                degreeAnalyses);

        return new ElliottWaveAnalysisResult(baseDegree, degreeAnalyses, ranked, notes);
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
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");

        SwingFilter filter = swingFilter;
        if (filter == null) {
            filter = new MinMagnitudeSwingFilter(scale(baseDegree, degree, 0.20, 1.5, 0.05, 0.60));
        }

        ElliottSwingCompressor compressor = new ElliottSwingCompressor(new ClosePriceIndicator(series),
                scale(baseDegree, degree, 0.01, 1.5, 0.0025, 0.05),
                Math.max(1, 2 + (baseDegree.ordinal() - degree.ordinal())));

        return runSingleDegreePipeline(series, degree, swingDetector, filter, compressor);
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
            final SwingDetector detector, final SwingFilter filter, final ElliottSwingCompressor compressor) {
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

        List<ElliottSwing> scenarioSwings = recentSwings(processed, scenarioSwingWindow);
        ElliottChannel channel = computeChannel(series.numFactory(), scenarioSwings, endIndex);

        ConfidenceModel confidenceModel = Objects.requireNonNull(confidenceModelFactory.apply(series.numFactory()),
                "confidenceModelFactory");
        ElliottScenarioGenerator generator = new ElliottScenarioGenerator(series.numFactory(), minConfidence,
                maxScenarios, confidenceModel, patternSet);
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
            List<ElliottWaveAnalysisResult.SupportingScenarioMatch> matches = new ArrayList<>();
            double crossDegreeScore = crossDegreeScore(baseScenario, analyses, matches);
            double composite = (baseConfidenceWeight * confidence) + ((1.0 - baseConfidenceWeight) * crossDegreeScore);

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
    private static SeriesSelector defaultSeriesSelector() {
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

    private enum DegreeRelation {
        HIGHER, LOWER
    }

    private record Match(String supportingScenarioId, double supportingConfidence, double compatibility,
            double weightedCompatibility) {
    }

    /**
     * Builder for {@link ElliottWaveAnalysis}.
     *
     * @since 0.22.3
     */
    public static final class Builder {

        private ElliottDegree baseDegree;
        private int higherDegrees = DEFAULT_HIGHER_DEGREES;
        private int lowerDegrees = DEFAULT_LOWER_DEGREES;
        private SeriesSelector seriesSelector;
        private AnalysisRunner analysisRunner;
        private double baseConfidenceWeight = DEFAULT_BASE_CONFIDENCE_WEIGHT;

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
         * @since 0.22.3
         */
        public Builder degree(final ElliottDegree degree) {
            this.baseDegree = Objects.requireNonNull(degree, "degree");
            return this;
        }

        /**
         * @param higherDegrees number of higher degrees to include (0 for none)
         * @return builder
         * @since 0.22.3
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
         * @since 0.22.3
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
         * @since 0.22.3
         */
        public Builder seriesSelector(final SeriesSelector seriesSelector) {
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
         * @since 0.22.3
         */
        public Builder analysisRunner(final AnalysisRunner analysisRunner) {
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
         * @since 0.22.3
         */
        public Builder baseConfidenceWeight(final double weight) {
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("baseConfidenceWeight must be in [0.0, 1.0]");
            }
            this.baseConfidenceWeight = weight;
            return this;
        }

        /**
         * Configures the swing detector used by the built-in analysis pipeline.
         *
         * @param swingDetector swing detector implementation
         * @return builder
         * @since 0.22.3
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
         * @since 0.22.3
         */
        public Builder swingFilter(final SwingFilter swingFilter) {
            this.swingFilter = swingFilter;
            return this;
        }

        /**
         * @param confidenceModel confidence model to use
         * @return builder
         * @since 0.22.3
         */
        public Builder confidenceModel(final ConfidenceModel confidenceModel) {
            Objects.requireNonNull(confidenceModel, "confidenceModel");
            this.confidenceModelFactory = unused -> confidenceModel;
            return this;
        }

        /**
         * @param confidenceModelFactory factory for confidence models
         * @return builder
         * @since 0.22.3
         */
        public Builder confidenceModelFactory(final Function<NumFactory, ConfidenceModel> confidenceModelFactory) {
            this.confidenceModelFactory = Objects.requireNonNull(confidenceModelFactory, "confidenceModelFactory");
            return this;
        }

        /**
         * @param patternSet enabled pattern set
         * @return builder
         * @since 0.22.3
         */
        public Builder patternSet(final PatternSet patternSet) {
            this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
            return this;
        }

        /**
         * @param minConfidence minimum confidence threshold
         * @return builder
         * @since 0.22.3
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
         * @since 0.22.3
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
         * @return builder
         * @since 0.22.3
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
         * @since 0.22.3
         */
        public ElliottWaveAnalysis build() {
            if (baseDegree == null) {
                throw new IllegalStateException("degree must be configured");
            }
            return new ElliottWaveAnalysis(this);
        }
    }
}
