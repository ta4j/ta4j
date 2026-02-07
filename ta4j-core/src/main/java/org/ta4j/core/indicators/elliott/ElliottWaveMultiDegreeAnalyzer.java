/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.swing.AdaptiveZigZagConfig;
import org.ta4j.core.indicators.elliott.swing.MinMagnitudeSwingFilter;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.elliott.swing.SwingFilter;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Runs Elliott Wave analysis across multiple degrees and validates base-degree
 * scenarios against neighboring degrees.
 *
 * <p>
 * This orchestrator is intended for workflows where a single-degree analysis is
 * too ambiguous. It re-ranks the base-degree {@link ElliottScenarioSet} by
 * measuring how compatible each base scenario is with supporting degrees (for
 * example: validate {@link ElliottDegree#PRIMARY} scenarios using
 * {@link ElliottDegree#CYCLE} and {@link ElliottDegree#INTERMEDIATE} context).
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
 * <p>
 * The default runner uses {@link ElliottWaveAnalyzer} with volatility-adaptive
 * ZigZag swings and scales swing filtering/compression thresholds by degree
 * distance, so higher degrees retain fewer, larger swings.
 *
 * @since 0.22.2
 */
public final class ElliottWaveMultiDegreeAnalyzer {

    /**
     * Executes a single-degree Elliott wave analysis.
     *
     * <p>
     * This is a pluggable seam. Implementations may delegate to
     * {@link ElliottWaveAnalyzer}, build results from indicator-style analysis
     * ({@link ElliottWaveFacade}), or run on resampled series.
     *
     * @since 0.22.2
     */
    @FunctionalInterface
    public interface AnalysisRunner {

        /**
         * Runs analysis for a given degree.
         *
         * @param series series to analyze
         * @param degree degree to analyze at
         * @return analysis snapshot
         * @since 0.22.2
         */
        ElliottAnalysisResult analyze(BarSeries series, ElliottDegree degree);
    }

    /**
     * Selects the series window (or transformed series) to use for a given degree.
     *
     * @since 0.22.2
     */
    @FunctionalInterface
    public interface SeriesSelector {

        /**
         * Selects a series for the requested degree.
         *
         * @param series root input series
         * @param degree degree to select for
         * @return selected series (may be a subseries)
         * @since 0.22.2
         */
        BarSeries select(BarSeries series, ElliottDegree degree);
    }

    private static final double DEFAULT_BASE_CONFIDENCE_WEIGHT = 0.7;
    private static final double DEFAULT_NEUTRAL_CROSS_DEGREE_SCORE = 0.5;
    private static final int DEFAULT_HIGHER_DEGREES = 1;
    private static final int DEFAULT_LOWER_DEGREES = 1;

    private final ElliottDegree baseDegree;
    private final int higherDegrees;
    private final int lowerDegrees;
    private final SeriesSelector seriesSelector;
    private final AnalysisRunner analysisRunner;
    private final double baseConfidenceWeight;

    private ElliottWaveMultiDegreeAnalyzer(final Builder builder) {
        this.baseDegree = Objects.requireNonNull(builder.baseDegree, "baseDegree");
        this.higherDegrees = builder.higherDegrees;
        this.lowerDegrees = builder.lowerDegrees;
        this.seriesSelector = builder.seriesSelector == null ? defaultSeriesSelector() : builder.seriesSelector;
        this.analysisRunner = builder.analysisRunner == null ? defaultRunner(baseDegree) : builder.analysisRunner;
        this.baseConfidenceWeight = builder.baseConfidenceWeight;
    }

    /**
     * Creates a new builder for the analyzer.
     *
     * @return builder
     * @since 0.22.2
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs multi-degree analysis on the supplied series.
     *
     * @param series root series
     * @return multi-degree analysis result
     * @since 0.22.2
     */
    public ElliottMultiDegreeAnalysisResult analyze(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        final List<String> notes = new ArrayList<>();
        final List<ElliottDegree> degrees = degreesToAnalyze(baseDegree, higherDegrees, lowerDegrees);
        final List<ElliottMultiDegreeAnalysisResult.DegreeAnalysis> degreeAnalyses = new ArrayList<>(degrees.size());

        ElliottAnalysisResult baseResult = null;
        ElliottMultiDegreeAnalysisResult.DegreeAnalysis baseSnapshot = null;

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

            ElliottMultiDegreeAnalysisResult.DegreeAnalysis snapshot = new ElliottMultiDegreeAnalysisResult.DegreeAnalysis(
                    degree, result.index(), barCount, barDuration, historyFitScore, result);
            degreeAnalyses.add(snapshot);

            if (degree == baseDegree) {
                baseResult = result;
                baseSnapshot = snapshot;
            }
        }

        if (baseResult == null || baseSnapshot == null) {
            throw new IllegalStateException("Base degree " + baseDegree + " analysis was not available");
        }

        final List<ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment> ranked = scoreBaseScenarios(baseResult,
                degreeAnalyses);

        return new ElliottMultiDegreeAnalysisResult(baseDegree, degreeAnalyses, ranked, notes);
    }

    private List<ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment> scoreBaseScenarios(
            final ElliottAnalysisResult baseResult,
            final List<ElliottMultiDegreeAnalysisResult.DegreeAnalysis> analyses) {
        final List<ElliottScenario> baseScenarios = baseResult.scenarios().all();
        if (baseScenarios.isEmpty()) {
            return List.of();
        }

        final List<ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment> assessments = new ArrayList<>(
                baseScenarios.size());

        for (final ElliottScenario baseScenario : baseScenarios) {
            if (baseScenario == null) {
                continue;
            }
            double confidence = safeScore(baseScenario.confidenceScore());
            List<ElliottMultiDegreeAnalysisResult.SupportingScenarioMatch> matches = new ArrayList<>();
            double crossDegreeScore = crossDegreeScore(baseScenario, analyses, matches);
            double composite = (baseConfidenceWeight * confidence) + ((1.0 - baseConfidenceWeight) * crossDegreeScore);

            assessments.add(new ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment(baseScenario, confidence,
                    crossDegreeScore, composite, matches));
        }

        assessments.sort(Comparator
                .comparingDouble(ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment::compositeScore)
                .reversed()
                .thenComparing(Comparator
                        .comparingDouble(ElliottMultiDegreeAnalysisResult.BaseScenarioAssessment::confidenceScore)
                        .reversed()));

        return List.copyOf(assessments);
    }

    private double crossDegreeScore(final ElliottScenario baseScenario,
            final List<ElliottMultiDegreeAnalysisResult.DegreeAnalysis> analyses,
            final List<ElliottMultiDegreeAnalysisResult.SupportingScenarioMatch> matchesOut) {
        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;

        for (final ElliottMultiDegreeAnalysisResult.DegreeAnalysis analysis : analyses) {
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

            matchesOut.add(new ElliottMultiDegreeAnalysisResult.SupportingScenarioMatch(analysis.degree(),
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

    private double compatibilityScore(final ElliottScenario baseScenario, final ElliottScenario supportingScenario,
            final ElliottDegree base, final ElliottDegree supporting) {
        if (baseScenario == null || supportingScenario == null) {
            return 0.0;
        }
        if (base == null || supporting == null || base == supporting) {
            return 0.0;
        }

        DegreeRelation relation = supporting.ordinal() < base.ordinal() ? DegreeRelation.HIGHER : DegreeRelation.LOWER;

        double directionScore = directionCompatibility(baseScenario, supportingScenario);
        double structureScore = structureCompatibility(baseScenario, supportingScenario);
        double invalidationScore = invalidationCompatibility(baseScenario, supportingScenario, relation);

        double score = (0.55 * directionScore) + (0.30 * structureScore) + (0.15 * invalidationScore);
        return clamp01(score);
    }

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

    private static double clamp01(final double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

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

    private static AnalysisRunner defaultRunner(final ElliottDegree baseDegree) {
        Objects.requireNonNull(baseDegree, "baseDegree");
        AdaptiveZigZagConfig config = new AdaptiveZigZagConfig(14, 1.0, 0.0, 0.0, 3);
        SwingDetector detector = SwingDetectors.adaptiveZigZag(config);

        return (series, degree) -> {
            Objects.requireNonNull(series, "series");
            Objects.requireNonNull(degree, "degree");

            SwingFilter filter = new MinMagnitudeSwingFilter(scale(baseDegree, degree, 0.20, 1.5, 0.05, 0.60));
            double minAmplitudePct = scale(baseDegree, degree, 0.01, 1.5, 0.0025, 0.05);
            int minBars = Math.max(1, 2 + (baseDegree.ordinal() - degree.ordinal()));
            ElliottSwingCompressor compressor = new ElliottSwingCompressor(new ClosePriceIndicator(series),
                    minAmplitudePct, minBars);

            ElliottWaveAnalyzer analyzer = ElliottWaveAnalyzer.builder()
                    .degree(degree)
                    .swingDetector(detector)
                    .swingFilter(filter)
                    .compressor(compressor)
                    .build();

            return analyzer.analyze(series);
        };
    }

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
     * Builder for {@link ElliottWaveMultiDegreeAnalyzer}.
     *
     * @since 0.22.2
     */
    public static final class Builder {

        private ElliottDegree baseDegree;
        private int higherDegrees = DEFAULT_HIGHER_DEGREES;
        private int lowerDegrees = DEFAULT_LOWER_DEGREES;
        private SeriesSelector seriesSelector;
        private AnalysisRunner analysisRunner;
        private double baseConfidenceWeight = DEFAULT_BASE_CONFIDENCE_WEIGHT;

        private Builder() {
        }

        /**
         * @param baseDegree base degree that drives scenario ranking
         * @return builder
         * @since 0.22.2
         */
        public Builder baseDegree(final ElliottDegree baseDegree) {
            this.baseDegree = Objects.requireNonNull(baseDegree, "baseDegree");
            return this;
        }

        /**
         * @param higherDegrees number of higher degrees to include (0 for none)
         * @return builder
         * @since 0.22.2
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
         * @since 0.22.2
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
         * @since 0.22.2
         */
        public Builder seriesSelector(final SeriesSelector seriesSelector) {
            this.seriesSelector = Objects.requireNonNull(seriesSelector, "seriesSelector");
            return this;
        }

        /**
         * @param analysisRunner analysis runner implementation
         * @return builder
         * @since 0.22.2
         */
        public Builder analysisRunner(final AnalysisRunner analysisRunner) {
            this.analysisRunner = Objects.requireNonNull(analysisRunner, "analysisRunner");
            return this;
        }

        /**
         * Controls the blend between base scenario confidence and cross-degree
         * compatibility.
         *
         * @param weight weight in range [0.0, 1.0] applied to the base confidence
         * @return builder
         * @since 0.22.2
         */
        public Builder baseConfidenceWeight(final double weight) {
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("baseConfidenceWeight must be in [0.0, 1.0]");
            }
            this.baseConfidenceWeight = weight;
            return this;
        }

        /**
         * Builds the analyzer.
         *
         * @return analyzer instance
         * @since 0.22.2
         */
        public ElliottWaveMultiDegreeAnalyzer build() {
            if (baseDegree == null) {
                throw new IllegalStateException("baseDegree must be configured");
            }
            return new ElliottWaveMultiDegreeAnalyzer(this);
        }
    }
}
