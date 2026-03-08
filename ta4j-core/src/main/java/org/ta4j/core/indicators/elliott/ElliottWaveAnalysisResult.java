/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Result of running Elliott Wave analysis across one or more degrees.
 *
 * <p>
 * Produced by {@link ElliottWaveAnalysisRunner}. The result contains one
 * {@link ElliottAnalysisResult} per analyzed degree, plus a re-ranked view of
 * the base-degree scenarios after cross-degree validation.
 *
 * @param baseDegree          base degree that drives scenario ranking
 * @param analyses            per-degree analysis snapshots (includes base)
 * @param rankedBaseScenarios base-degree scenarios ranked by composite score
 * @param notes               human-readable notes (insufficient history,
 *                            skipped degrees, etc.)
 * @since 0.22.4
 */
public record ElliottWaveAnalysisResult(ElliottDegree baseDegree, List<DegreeAnalysis> analyses,
        List<BaseScenarioAssessment> rankedBaseScenarios, List<String> notes) {

    public ElliottWaveAnalysisResult {
        Objects.requireNonNull(baseDegree, "baseDegree");
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        rankedBaseScenarios = rankedBaseScenarios == null ? List.of() : List.copyOf(rankedBaseScenarios);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * @return the recommended base-degree scenario assessment (highest composite
     *         score), if present
     * @since 0.22.4
     */
    public Optional<BaseScenarioAssessment> recommendedScenario() {
        return rankedBaseScenarios.isEmpty() ? Optional.empty() : Optional.of(rankedBaseScenarios.get(0));
    }

    /**
     * @return the recommended base-degree scenario (highest composite score), if
     *         present
     * @since 0.22.4
     */
    public Optional<ElliottScenario> recommendedBaseScenario() {
        return recommendedScenario().map(BaseScenarioAssessment::scenario);
    }

    /**
     * Returns the analysis snapshot for a given degree.
     *
     * @param degree degree to look up
     * @return matching analysis snapshot
     * @since 0.22.4
     */
    public Optional<DegreeAnalysis> analysisFor(final ElliottDegree degree) {
        Objects.requireNonNull(degree, "degree");
        for (final DegreeAnalysis analysis : analyses) {
            if (analysis.degree() == degree) {
                return Optional.of(analysis);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the base scenarios re-ranked for how closely they span a target
     * anchor window.
     *
     * <p>
     * This is useful for research and reporting flows that need the best scenario
     * for a known start/end segment rather than the globally best scenario for the
     * full analyzed prefix. Scenarios that start near {@code startIndex} and
     * terminate near {@code endIndex} are preferred ahead of broader ranking ties.
     *
     * @param startIndex expected scenario start pivot index
     * @param endIndex   expected scenario terminal pivot index
     * @return immutable base-scenario view sorted by anchor-span fit first and
     *         composite score second
     * @since 0.22.4
     */
    public List<BaseScenarioAssessment> rankedBaseScenariosForSpan(final int startIndex, final int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex must be >= startIndex");
        }
        if (rankedBaseScenarios.isEmpty()) {
            return List.of();
        }
        return rankedBaseScenarios.stream()
                .sorted(Comparator
                        .comparingDouble((BaseScenarioAssessment assessment) -> anchorSpanScore(assessment.scenario(),
                                startIndex, endIndex))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(BaseScenarioAssessment::compositeScore).reversed())
                        .thenComparing(assessment -> assessment.scenario().id()))
                .toList();
    }

    /**
     * Returns the base scenarios re-ranked for a target anchor window and scenario
     * template.
     *
     * <p>
     * This is the span-aware selection entry point for research flows that need a
     * specific kind of count, such as a bullish five-wave impulse or a bearish
     * corrective {@code A-B-C}, attached to a known anchor span. The returned list
     * is filtered to scenarios that match the requested template, then sorted by
     * anchor fit before composite score.
     *
     * @param startIndex         expected scenario start pivot index
     * @param endIndex           expected scenario terminal pivot index
     * @param scenarioType       required scenario family, or {@code null} to keep
     *                           any family that matches the remaining template
     * @param terminalPhase      required terminal phase
     * @param waveCount          required wave count
     * @param bullishDirection   required direction; {@code null} skips direction
     *                           filtering
     * @param maxAnchorDriftBars soft anchor tolerance used during ranking; smaller
     *                           values penalize drift more aggressively
     * @return immutable filtered base-scenario view sorted by anchor-conditioned
     *         template fit first and composite score second
     * @since 0.22.4
     */
    public List<BaseScenarioAssessment> rankedBaseScenariosForSpan(final int startIndex, final int endIndex,
            final ScenarioType scenarioType, final ElliottPhase terminalPhase, final int waveCount,
            final Boolean bullishDirection, final int maxAnchorDriftBars) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex must be >= startIndex");
        }
        Objects.requireNonNull(terminalPhase, "terminalPhase");
        if (waveCount <= 0) {
            throw new IllegalArgumentException("waveCount must be > 0");
        }
        if (maxAnchorDriftBars < 0) {
            throw new IllegalArgumentException("maxAnchorDriftBars must be >= 0");
        }
        if (rankedBaseScenarios.isEmpty()) {
            return List.of();
        }
        return rankedBaseScenarios.stream()
                .filter(assessment -> matchesScenarioTemplate(assessment.scenario(), scenarioType, terminalPhase,
                        waveCount, bullishDirection))
                .sorted(Comparator
                        .comparingDouble((BaseScenarioAssessment assessment) -> anchorTemplateScore(
                                assessment.scenario(), startIndex, endIndex, maxAnchorDriftBars))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(BaseScenarioAssessment::compositeScore).reversed())
                        .thenComparing(Comparator.comparingDouble(BaseScenarioAssessment::confidenceScore).reversed())
                        .thenComparing(assessment -> assessment.scenario().id()))
                .toList();
    }

    /**
     * Returns the highest-ranked base scenario for a target span and scenario
     * template, if present.
     *
     * @param startIndex         expected scenario start pivot index
     * @param endIndex           expected scenario terminal pivot index
     * @param scenarioType       required scenario family
     * @param terminalPhase      required terminal phase
     * @param waveCount          required wave count
     * @param bullishDirection   required direction; {@code null} skips direction
     *                           filtering
     * @param maxAnchorDriftBars soft anchor tolerance used during ranking
     * @return top matching scenario assessment, if any
     * @since 0.22.4
     */
    public Optional<BaseScenarioAssessment> recommendedBaseScenarioForSpan(final int startIndex, final int endIndex,
            final ScenarioType scenarioType, final ElliottPhase terminalPhase, final int waveCount,
            final Boolean bullishDirection, final int maxAnchorDriftBars) {
        return rankedBaseScenariosForSpan(startIndex, endIndex, scenarioType, terminalPhase, waveCount,
                bullishDirection, maxAnchorDriftBars).stream().findFirst();
    }

    /**
     * Returns the base scenarios re-ranked for a target anchor window using the
     * supplied series to validate pivot dominance and swing progression.
     *
     * <p>
     * This is the core entry point for anchored research and live current-cycle
     * fitting. It prefers scenarios that:
     * <ul>
     * <li>start and end near the requested window boundaries</li>
     * <li>alternate direction cleanly across their swing sequence</li>
     * <li>use pivots that are dominant highs/lows for their local wave spans</li>
     * </ul>
     *
     * @param series             root series that the scenarios are indexed against
     * @param startIndex         expected scenario start pivot index
     * @param endIndex           expected scenario terminal pivot index
     * @param scenarioType       required scenario family, or {@code null} to keep
     *                           any family that matches the remaining template
     * @param terminalPhase      required terminal phase
     * @param waveCount          required wave count
     * @param bullishDirection   required direction; {@code null} skips direction
     *                           filtering
     * @param maxAnchorDriftBars soft anchor tolerance used during ranking
     * @return immutable filtered base-scenario view sorted by anchored window fit
     *         first and composite score second
     * @since 0.22.4
     */
    public List<WindowScenarioAssessment> rankedBaseScenariosForWindow(final BarSeries series, final int startIndex,
            final int endIndex, final ScenarioType scenarioType, final ElliottPhase terminalPhase, final int waveCount,
            final Boolean bullishDirection, final int maxAnchorDriftBars) {
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
        return rankedBaseScenariosForSpan(startIndex, endIndex, scenarioType, terminalPhase, waveCount,
                bullishDirection, maxAnchorDriftBars).stream()
                .map(assessment -> toWindowAssessment(series, assessment, startIndex, endIndex, maxAnchorDriftBars))
                .filter(assessment -> assessment.windowFitScore() > 0.0)
                .sorted(Comparator.comparingDouble(WindowScenarioAssessment::windowFitScore)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(WindowScenarioAssessment::compositeScore).reversed())
                        .thenComparing(Comparator.comparingDouble(WindowScenarioAssessment::confidenceScore).reversed())
                        .thenComparing(assessment -> assessment.scenario().id()))
                .toList();
    }

    /**
     * Returns the highest-ranked anchored window scenario for a target template, if
     * present.
     *
     * @param series             root series that the scenarios are indexed against
     * @param startIndex         expected scenario start pivot index
     * @param endIndex           expected scenario terminal pivot index
     * @param scenarioType       required scenario family
     * @param terminalPhase      required terminal phase
     * @param waveCount          required wave count
     * @param bullishDirection   required direction; {@code null} skips direction
     *                           filtering
     * @param maxAnchorDriftBars soft anchor tolerance used during ranking
     * @return top matching anchored window scenario assessment, if any
     * @since 0.22.4
     */
    public Optional<WindowScenarioAssessment> recommendedBaseScenarioForWindow(final BarSeries series,
            final int startIndex, final int endIndex, final ScenarioType scenarioType, final ElliottPhase terminalPhase,
            final int waveCount, final Boolean bullishDirection, final int maxAnchorDriftBars) {
        return rankedBaseScenariosForWindow(series, startIndex, endIndex, scenarioType, terminalPhase, waveCount,
                bullishDirection, maxAnchorDriftBars).stream().findFirst();
    }

    private static double anchorSpanScore(final ElliottScenario scenario, final int startIndex, final int endIndex) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
        }
        final int actualStart = scenario.swings().getFirst().fromIndex();
        final int actualEnd = scenario.swings().getLast().toIndex();
        final double span = Math.max(1.0, endIndex - startIndex);
        final double startAlignment = 1.0 - Math.min(1.0, Math.abs(actualStart - startIndex) / span);
        final double endAlignment = 1.0 - Math.min(1.0, Math.abs(actualEnd - endIndex) / span);
        return (startAlignment + endAlignment) / 2.0;
    }

    private static boolean matchesScenarioTemplate(final ElliottScenario scenario, final ScenarioType scenarioType,
            final ElliottPhase terminalPhase, final int waveCount, final Boolean bullishDirection) {
        if (scenario == null) {
            return false;
        }
        if (scenarioType != null && scenario.type() != scenarioType) {
            return false;
        }
        if (scenario.currentPhase() != terminalPhase || scenario.waveCount() != waveCount) {
            return false;
        }
        if (bullishDirection == null) {
            return true;
        }
        return scenario.hasKnownDirection() && scenario.isBullish() == bullishDirection.booleanValue();
    }

    private static double anchorTemplateScore(final ElliottScenario scenario, final int startIndex, final int endIndex,
            final int maxAnchorDriftBars) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
        }
        final int actualStart = scenario.swings().getFirst().fromIndex();
        final int actualEnd = scenario.swings().getLast().toIndex();
        final double spanAlignment = anchorSpanScore(scenario, startIndex, endIndex);
        final double driftScale = Math.max(1.0, maxAnchorDriftBars);
        final double startGap = Math.abs(actualStart - startIndex);
        final double endGap = Math.abs(actualEnd - endIndex);
        final double startDriftScore = 1.0 - Math.min(1.0, startGap / driftScale);
        final double endDriftScore = 1.0 - Math.min(1.0, endGap / driftScale);
        final double completionScore = scenario.expectsCompletion() ? 1.0 : 0.5;
        return (spanAlignment + startDriftScore + endDriftScore + completionScore) / 4.0;
    }

    private static WindowScenarioAssessment toWindowAssessment(final BarSeries series,
            final BaseScenarioAssessment assessment, final int startIndex, final int endIndex,
            final int maxAnchorDriftBars) {
        final ElliottScenario scenario = assessment.scenario();
        final double anchorFitScore = anchorTemplateScore(scenario, startIndex, endIndex, maxAnchorDriftBars);
        final double progressionScore = alternatingProgressionScore(scenario);
        final double pivotDominanceScore = pivotDominanceScore(series, scenario);
        final double windowFitScore = clamp(
                (0.45 * anchorFitScore) + (0.35 * pivotDominanceScore) + (0.20 * progressionScore), 0.0, 1.0);
        return new WindowScenarioAssessment(assessment, windowFitScore, anchorFitScore, pivotDominanceScore,
                progressionScore);
    }

    private static double alternatingProgressionScore(final ElliottScenario scenario) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
        }
        final List<ElliottSwing> swings = scenario.swings();
        for (int index = 1; index < swings.size(); index++) {
            final ElliottSwing previous = swings.get(index - 1);
            final ElliottSwing current = swings.get(index);
            if (previous.toIndex() != current.fromIndex()) {
                return 0.0;
            }
            if (previous.toPrice().compareTo(current.fromPrice()) != 0) {
                return 0.0;
            }
            if (previous.isRising() == current.isRising()) {
                return 0.0;
            }
        }
        return 1.0;
    }

    private static double pivotDominanceScore(final BarSeries series, final ElliottScenario scenario) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
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
                return 0.0;
            }
            if (previous.toIndex() != current.fromIndex()) {
                return 0.0;
            }
            pivotIndices.add(previous.toIndex());
            highPivots.add(Boolean.valueOf(previous.isRising()));
        }

        pivotIndices.add(swings.getLast().toIndex());
        highPivots.add(Boolean.valueOf(swings.getLast().isRising()));

        double total = 0.0;
        for (int pointIndex = 0; pointIndex < pivotIndices.size(); pointIndex++) {
            final int pivotIndex = pivotIndices.get(pointIndex);
            if (pivotIndex < series.getBeginIndex() || pivotIndex > series.getEndIndex()) {
                return 0.0;
            }
            final int spanStart = pointIndex == 0 ? pivotIndices.getFirst() : pivotIndices.get(pointIndex - 1);
            final int spanEnd = pointIndex == pivotIndices.size() - 1 ? pivotIndices.getLast()
                    : pivotIndices.get(pointIndex + 1);
            final boolean highPivot = highPivots.get(pointIndex).booleanValue();
            final double pivotPrice = highPivot ? series.getBar(pivotIndex).getHighPrice().doubleValue()
                    : series.getBar(pivotIndex).getLowPrice().doubleValue();
            final double spanExtreme = highPivot ? highestHigh(series, spanStart, spanEnd)
                    : lowestLow(series, spanStart, spanEnd);
            final double spanRange = Math.max(1e-9, segmentRange(series, spanStart, spanEnd));
            total += clamp(1.0 - (Math.abs(spanExtreme - pivotPrice) / spanRange), 0.0, 1.0);
        }
        return total / pivotIndices.size();
    }

    private static double segmentRange(final BarSeries series, final int startIndex, final int endIndex) {
        return Math.max(1e-9, highestHigh(series, startIndex, endIndex) - lowestLow(series, startIndex, endIndex));
    }

    private static double highestHigh(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        double highest = Double.NEGATIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            highest = Math.max(highest, series.getBar(index).getHighPrice().doubleValue());
        }
        return highest;
    }

    private static double lowestLow(final BarSeries series, final int startIndex, final int endIndex) {
        final int fromIndex = Math.max(series.getBeginIndex(), Math.min(startIndex, endIndex));
        final int toIndex = Math.min(series.getEndIndex(), Math.max(startIndex, endIndex));
        double lowest = Double.POSITIVE_INFINITY;
        for (int index = fromIndex; index <= toIndex; index++) {
            lowest = Math.min(lowest, series.getBar(index).getLowPrice().doubleValue());
        }
        return lowest;
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double average(final double... values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double total = 0.0;
        for (final double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double safeConfidenceScore(final double score) {
        return Double.isFinite(score) ? clamp(score, 0.0, 1.0) : 0.0;
    }

    private static double safeConfidenceScore(final Num score) {
        if (score == null || score.isNaN()) {
            return 0.0;
        }
        return safeConfidenceScore(score.doubleValue());
    }

    private static double alignmentScore(final int actualIndex, final int expectedIndex, final double totalSpan) {
        return 1.0 - Math.min(1.0, Math.abs(actualIndex - expectedIndex) / Math.max(1.0, totalSpan));
    }

    private static double scenarioSpacingScore(final ElliottScenario scenario) {
        if (scenario == null || scenario.swings().isEmpty()) {
            return 0.0;
        }
        final List<Integer> pivotIndices = new ArrayList<>(scenario.swings().size() + 1);
        pivotIndices.add(scenario.swings().getFirst().fromIndex());
        for (final ElliottSwing swing : scenario.swings()) {
            pivotIndices.add(swing.toIndex());
        }
        double shortestSpacing = Double.POSITIVE_INFINITY;
        double totalSpacing = 0.0;
        for (int index = 1; index < pivotIndices.size(); index++) {
            final int spacing = pivotIndices.get(index) - pivotIndices.get(index - 1);
            if (spacing <= 0) {
                return 0.0;
            }
            shortestSpacing = Math.min(shortestSpacing, spacing);
            totalSpacing += spacing;
        }
        final double averageSpacing = totalSpacing / Math.max(1, pivotIndices.size() - 1);
        return clamp(shortestSpacing / Math.max(1.0, averageSpacing), 0.0, 1.0);
    }

    /**
     * Validates that a score is finite and inside the unit interval.
     *
     * @param fieldName field name for error messaging
     * @param score     score to validate
     */
    private static void validateUnitIntervalScore(final String fieldName, final double score) {
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be in [0.0, 1.0]");
        }
    }

    /**
     * Snapshot of a single-degree analysis run, including series metadata.
     *
     * @param degree          analyzed degree
     * @param index           evaluated bar index (in the analyzed series)
     * @param barCount        number of bars used for analysis
     * @param barDuration     duration of bars used for analysis
     * @param historyFitScore how well the bar span fits the degree (0.0 - 1.0)
     * @param analysis        analysis result for the degree
     * @since 0.22.4
     */
    public record DegreeAnalysis(ElliottDegree degree, int index, int barCount, Duration barDuration,
            double historyFitScore, ElliottAnalysisResult analysis) {

        public DegreeAnalysis {
            Objects.requireNonNull(degree, "degree");
            Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(barDuration, "barDuration");
            if (barCount < 0) {
                throw new IllegalArgumentException("barCount must be >= 0");
            }
            validateUnitIntervalScore("historyFitScore", historyFitScore);
        }
    }

    /**
     * Cross-degree assessment for a base-degree scenario.
     *
     * @param scenario          base-degree scenario
     * @param confidenceScore   base-degree scenario confidence score (0.0 - 1.0)
     * @param crossDegreeScore  aggregated cross-degree compatibility score
     * @param compositeScore    blended ranking score combining raw confidence,
     *                          structural priority, and cross-degree support
     * @param supportingMatches per-supporting-degree best-match snapshots
     * @since 0.22.4
     */
    public record BaseScenarioAssessment(ElliottScenario scenario, double confidenceScore, double crossDegreeScore,
            double compositeScore, List<SupportingScenarioMatch> supportingMatches) {

        public BaseScenarioAssessment {
            Objects.requireNonNull(scenario, "scenario");
            validateUnitIntervalScore("confidenceScore", confidenceScore);
            validateUnitIntervalScore("crossDegreeScore", crossDegreeScore);
            validateUnitIntervalScore("compositeScore", compositeScore);
            supportingMatches = supportingMatches == null ? List.of() : List.copyOf(supportingMatches);
        }
    }

    /**
     * Anchored-window view of a base scenario assessment.
     *
     * <p>
     * This augments the base cross-degree assessment with start/end span fit, pivot
     * dominance, and swing-progression quality using the caller-supplied series
     * window.
     *
     * @param baseAssessment      underlying base-scenario assessment
     * @param windowFitScore      aggregate anchored window score (0.0 - 1.0)
     * @param anchorFitScore      start/end anchor alignment score (0.0 - 1.0)
     * @param pivotDominanceScore pivot dominance across the scenario's local wave
     *                            spans (0.0 - 1.0)
     * @param progressionScore    alternating/contiguous swing progression score
     *                            (0.0 - 1.0)
     * @since 0.22.4
     */
    public record WindowScenarioAssessment(BaseScenarioAssessment baseAssessment, double windowFitScore,
            double anchorFitScore, double pivotDominanceScore, double progressionScore) {

        public WindowScenarioAssessment {
            Objects.requireNonNull(baseAssessment, "baseAssessment");
            validateUnitIntervalScore("windowFitScore", windowFitScore);
            validateUnitIntervalScore("anchorFitScore", anchorFitScore);
            validateUnitIntervalScore("pivotDominanceScore", pivotDominanceScore);
            validateUnitIntervalScore("progressionScore", progressionScore);
        }

        /**
         * @return underlying Elliott scenario
         * @since 0.22.4
         */
        public ElliottScenario scenario() {
            return baseAssessment.scenario();
        }

        /**
         * @return base confidence score
         * @since 0.22.4
         */
        public double confidenceScore() {
            return baseAssessment.confidenceScore();
        }

        /**
         * @return cross-degree compatibility score
         * @since 0.22.4
         */
        public double crossDegreeScore() {
            return baseAssessment.crossDegreeScore();
        }

        /**
         * @return blended base composite score
         * @since 0.22.4
         */
        public double compositeScore() {
            return baseAssessment.compositeScore();
        }

        /**
         * @return blended structure score combining confidence structure factors and
         *         anchored-window fit quality
         * @since 0.22.4
         */
        public double structureScore() {
            final ElliottConfidence confidence = scenario().confidence();
            return average(safeConfidenceScore(confidence.fibonacciScore()),
                    safeConfidenceScore(confidence.timeProportionScore()),
                    safeConfidenceScore(confidence.completenessScore()), anchorFitScore, pivotDominanceScore);
        }

        /**
         * @return blended rule-quality score for the anchored window
         * @since 0.22.4
         */
        public double ruleScore() {
            final ElliottConfidence confidence = scenario().confidence();
            return average(safeConfidenceScore(confidence.alternationScore()),
                    safeConfidenceScore(confidence.channelScore()), progressionScore);
        }

        /**
         * @return spacing score that rewards evenly distributed pivot spacing within
         *         the anchored window
         * @since 0.22.4
         */
        public double spacingScore() {
            return average(scenarioSpacingScore(scenario()), anchorFitScore, pivotDominanceScore);
        }

        /**
         * @return blended strength score across base confidence, cross-degree support,
         *         composite ranking, and completion quality
         * @since 0.22.4
         */
        public double strengthScore() {
            return average(confidenceScore(), crossDegreeScore(), compositeScore(),
                    safeConfidenceScore(scenario().confidence().completenessScore()));
        }

        /**
         * @return demo-compatible anchored fit score derived from the base composite,
         *         strength, and anchored-window fit metrics
         * @since 0.22.4
         */
        public double fitScore() {
            return average(compositeScore(), strengthScore(), windowFitScore);
        }

        /**
         * @param startIndex expected anchored start index
         * @param endIndex   expected anchored end index
         * @return start-anchor alignment score for this scenario
         * @since 0.22.4
         */
        public double startAlignmentScore(final int startIndex, final int endIndex) {
            if (scenario().swings().isEmpty()) {
                return 0.0;
            }
            final double span = Math.max(1.0, endIndex - startIndex);
            return alignmentScore(scenario().swings().getFirst().fromIndex(), startIndex, span);
        }

        /**
         * @param startIndex expected anchored start index
         * @param endIndex   expected anchored end index
         * @return end-anchor alignment score for this scenario
         * @since 0.22.4
         */
        public double endAlignmentScore(final int startIndex, final int endIndex) {
            if (scenario().swings().isEmpty()) {
                return 0.0;
            }
            final double span = Math.max(1.0, endIndex - startIndex);
            return alignmentScore(scenario().swings().getLast().toIndex(), endIndex, span);
        }

        /**
         * Applies generic anchored-window acceptance checks using caller-supplied
         * thresholds.
         *
         * @param startIndex            expected anchored start index
         * @param endIndex              expected anchored end index
         * @param minimumFitScore       minimum blended fit score
         * @param minimumRuleScore      minimum rule-quality score
         * @param minimumStartAlignment minimum allowed start alignment
         * @param minimumEndAlignment   minimum allowed end alignment
         * @param maxAnchorDriftBars    maximum allowed bar drift at either edge
         * @return {@code true} when the scenario satisfies all supplied thresholds
         * @since 0.22.4
         */
        public boolean passesAnchoredWindowAcceptance(final int startIndex, final int endIndex,
                final double minimumFitScore, final double minimumRuleScore, final double minimumStartAlignment,
                final double minimumEndAlignment, final int maxAnchorDriftBars) {
            validateUnitIntervalScore("minimumFitScore", minimumFitScore);
            validateUnitIntervalScore("minimumRuleScore", minimumRuleScore);
            validateUnitIntervalScore("minimumStartAlignment", minimumStartAlignment);
            validateUnitIntervalScore("minimumEndAlignment", minimumEndAlignment);
            if (maxAnchorDriftBars < 0 || scenario().swings().isEmpty()) {
                return false;
            }
            final int startGapBars = Math.abs(scenario().swings().getFirst().fromIndex() - startIndex);
            final int endGapBars = Math.abs(scenario().swings().getLast().toIndex() - endIndex);
            return fitScore() >= minimumFitScore && ruleScore() >= minimumRuleScore
                    && startAlignmentScore(startIndex, endIndex) >= minimumStartAlignment
                    && endAlignmentScore(startIndex, endIndex) >= minimumEndAlignment
                    && startGapBars <= maxAnchorDriftBars && endGapBars <= maxAnchorDriftBars;
        }
    }

    /**
     * Ranked current-phase fit for a live or open-ended window.
     *
     * <p>
     * This is the core-side equivalent of the old demo-local current-wave fit. It
     * keeps the chosen scenario, the interpreted terminal phase, the blended fit
     * score, the anchored cycle start price, and both the structural and
     * phase-specific invalidation prices together so callers can render charts or
     * summaries without reconstructing those details.
     *
     * @param scenario          selected scenario
     * @param currentPhase      interpreted current phase
     * @param fitScore          blended anchored-window fit score (0.0 - 1.0)
     * @param startPrice        anchored cycle start price
     * @param countLabel        human-readable wave-count label
     * @param invalidationPrice      structural invalidation price for the fit
     * @param phaseInvalidationPrice phase-specific invalidation price for the fit
     * @since 0.22.4
     */
    public record CurrentPhaseAssessment(ElliottScenario scenario, ElliottPhase currentPhase, double fitScore,
            Num startPrice, String countLabel, Num invalidationPrice, Num phaseInvalidationPrice)
            implements Comparable<CurrentPhaseAssessment> {

        public CurrentPhaseAssessment {
            Objects.requireNonNull(scenario, "scenario");
            Objects.requireNonNull(currentPhase, "currentPhase");
            Objects.requireNonNull(startPrice, "startPrice");
            Objects.requireNonNull(countLabel, "countLabel");
            Objects.requireNonNull(phaseInvalidationPrice, "phaseInvalidationPrice");
            validateUnitIntervalScore("fitScore", fitScore);
        }

        @Override
        public int compareTo(final CurrentPhaseAssessment other) {
            return Double.compare(other.fitScore, fitScore);
        }
    }

    /**
     * Ranked current-cycle candidate anchored to a discovered start pivot.
     *
     * @param startIndex  anchored cycle start index
     * @param startPrice  anchored cycle start price
     * @param fit         selected phase fit for this start anchor
     * @param anchorScore score of the discovered start anchor (0.0 - 1.0)
     * @param totalScore  total candidate score after anchor, span, and phase-fit
     *                    blending (0.0 - 1.0)
     * @param rationale   human-readable candidate rationale
     * @since 0.22.4
     */
    public record CurrentCycleCandidate(int startIndex, Num startPrice, CurrentPhaseAssessment fit, double anchorScore,
            double totalScore, String rationale) implements Comparable<CurrentCycleCandidate> {

        public CurrentCycleCandidate {
            Objects.requireNonNull(startPrice, "startPrice");
            Objects.requireNonNull(fit, "fit");
            Objects.requireNonNull(rationale, "rationale");
            validateUnitIntervalScore("anchorScore", anchorScore);
            validateUnitIntervalScore("totalScore", totalScore);
        }

        @Override
        public int compareTo(final CurrentCycleCandidate other) {
            final int totalComparison = Double.compare(other.totalScore, totalScore);
            if (totalComparison != 0) {
                return totalComparison;
            }
            final int fitComparison = fit.compareTo(other.fit);
            if (fitComparison != 0) {
                return fitComparison;
            }
            return Integer.compare(startIndex, other.startIndex);
        }
    }

    /**
     * Ranked current-cycle analysis for an open-ended series window.
     *
     * <p>
     * The runner discovers plausible start anchors directly from the supplied
     * series, ranks candidate bullish counts against the live right edge, and then
     * exposes the preferred and alternate interpretations alongside the full
     * candidate set.
     *
     * @param startIndex cycle start index selected by the winning candidate, or the
     *                   fallback series low when no candidate was accepted
     * @param primary    preferred current-cycle fit, if present
     * @param alternate  alternate current-cycle fit with a different phase, if
     *                   present
     * @param candidates ranked candidate list
     * @since 0.22.4
     */
    public record CurrentCycleAssessment(int startIndex, CurrentPhaseAssessment primary,
            CurrentPhaseAssessment alternate, List<CurrentCycleCandidate> candidates) {

        public CurrentCycleAssessment {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /**
     * Best-match snapshot for a supporting degree.
     *
     * @param degree                supporting degree
     * @param scenarioId            best matching scenario id in the supporting
     *                              degree
     * @param scenarioConfidence    confidence of the supporting scenario
     * @param compatibilityScore    compatibility score between base and supporting
     *                              scenario (0.0 - 1.0)
     * @param weightedCompatibility compatibility weighted by supporting scenario
     *                              confidence (0.0 - 1.0)
     * @param historyFitScore       history fit score for the supporting degree
     *                              series (0.0 - 1.0)
     * @since 0.22.4
     */
    public record SupportingScenarioMatch(ElliottDegree degree, String scenarioId, double scenarioConfidence,
            double compatibilityScore, double weightedCompatibility, double historyFitScore) {

        public SupportingScenarioMatch {
            Objects.requireNonNull(degree, "degree");
            Objects.requireNonNull(scenarioId, "scenarioId");
            validateUnitIntervalScore("scenarioConfidence", scenarioConfidence);
            validateUnitIntervalScore("compatibilityScore", compatibilityScore);
            validateUnitIntervalScore("weightedCompatibility", weightedCompatibility);
            validateUnitIntervalScore("historyFitScore", historyFitScore);
        }
    }
}
