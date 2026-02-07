/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of running Elliott Wave analysis across multiple degrees.
 *
 * <p>
 * Produced by {@link ElliottWaveMultiDegreeAnalyzer}. The result contains one
 * {@link ElliottAnalysisResult} per analyzed degree, plus a re-ranked view of
 * the base-degree scenarios after cross-degree validation.
 *
 * @param baseDegree          base degree that drives scenario ranking
 * @param analyses            per-degree analysis snapshots (includes base)
 * @param rankedBaseScenarios base-degree scenarios ranked by composite score
 * @param notes               human-readable notes (insufficient history,
 *                            skipped degrees, etc.)
 * @since 0.22.2
 */
public record ElliottMultiDegreeAnalysisResult(ElliottDegree baseDegree, List<DegreeAnalysis> analyses,
        List<BaseScenarioAssessment> rankedBaseScenarios, List<String> notes) {

    public ElliottMultiDegreeAnalysisResult {
        Objects.requireNonNull(baseDegree, "baseDegree");
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        rankedBaseScenarios = rankedBaseScenarios == null ? List.of() : List.copyOf(rankedBaseScenarios);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * @return the recommended base-degree scenario assessment (highest composite
     *         score), if present
     * @since 0.22.2
     */
    public Optional<BaseScenarioAssessment> recommendedScenario() {
        return rankedBaseScenarios.isEmpty() ? Optional.empty() : Optional.of(rankedBaseScenarios.get(0));
    }

    /**
     * @return the recommended base-degree scenario (highest composite score), if
     *         present
     * @since 0.22.2
     */
    public Optional<ElliottScenario> recommendedBaseScenario() {
        return recommendedScenario().map(BaseScenarioAssessment::scenario);
    }

    /**
     * Returns the analysis snapshot for a given degree.
     *
     * @param degree degree to look up
     * @return matching analysis snapshot
     * @since 0.22.2
     */
    public Optional<DegreeAnalysis> analysisFor(final ElliottDegree degree) {
        Objects.requireNonNull(degree, "degree");
        for (final DegreeAnalysis analysis : analyses) {
            if (analysis != null && analysis.degree() == degree) {
                return Optional.of(analysis);
            }
        }
        return Optional.empty();
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
     * @since 0.22.2
     */
    public record DegreeAnalysis(ElliottDegree degree, int index, int barCount, Duration barDuration,
            double historyFitScore, ElliottAnalysisResult analysis) {

        public DegreeAnalysis {
            Objects.requireNonNull(degree, "degree");
            Objects.requireNonNull(analysis, "analysis");
            if (barCount < 0) {
                throw new IllegalArgumentException("barCount must be >= 0");
            }
            if (Double.isNaN(historyFitScore) || historyFitScore < 0.0 || historyFitScore > 1.0) {
                throw new IllegalArgumentException("historyFitScore must be in [0.0, 1.0]");
            }
        }
    }

    /**
     * Cross-degree assessment for a base-degree scenario.
     *
     * @param scenario          base-degree scenario
     * @param confidenceScore   base-degree scenario confidence score (0.0 - 1.0)
     * @param crossDegreeScore  aggregated cross-degree compatibility score
     * @param compositeScore    blended score used for ranking
     * @param supportingMatches per-supporting-degree best-match snapshots
     * @since 0.22.2
     */
    public record BaseScenarioAssessment(ElliottScenario scenario, double confidenceScore, double crossDegreeScore,
            double compositeScore, List<SupportingScenarioMatch> supportingMatches) {

        public BaseScenarioAssessment {
            Objects.requireNonNull(scenario, "scenario");
            supportingMatches = supportingMatches == null ? List.of() : List.copyOf(supportingMatches);
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
     * @since 0.22.2
     */
    public record SupportingScenarioMatch(ElliottDegree degree, String scenarioId, double scenarioConfidence,
            double compatibilityScore, double weightedCompatibility, double historyFitScore) {

        public SupportingScenarioMatch {
            Objects.requireNonNull(degree, "degree");
            Objects.requireNonNull(scenarioId, "scenarioId");
        }
    }
}
