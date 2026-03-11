/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;

/**
 * Result of an Elliott Wave analysis run.
 *
 * <p>
 * Produced by {@link ElliottWaveAnalysisRunner} to capture a complete snapshot
 * of the analysis pipeline for a given bar index. Use this record when you need
 * to inspect raw versus processed swings, scenario rankings, confidence
 * breakdowns, projected channels, and the aggregate trend bias in one place.
 *
 * @param degree               Elliott degree used for analysis
 * @param index                bar index evaluated
 * @param rawSwings            raw detected swings
 * @param processedSwings      swings used for scenario generation after
 *                             optional filtering/compression and lookback
 *                             windowing
 * @param scenarios            scenario set generated from
 *                             {@code processedSwings}
 * @param confidenceBreakdowns confidence breakdowns keyed by scenario id
 * @param channel              projected Elliott channel
 * @param trendBias            aggregate scenario trend bias
 * @param diagnostics          scenario-generation diagnostics for this analysis
 * @since 0.22.2
 */
public record ElliottAnalysisResult(ElliottDegree degree, int index, List<ElliottSwing> rawSwings,
        List<ElliottSwing> processedSwings, ElliottScenarioSet scenarios,
        Map<String, ElliottConfidenceBreakdown> confidenceBreakdowns, ElliottChannel channel,
        ElliottTrendBias trendBias, AnalysisDiagnostics diagnostics) {

    /**
     * Creates an analysis result with empty diagnostics for backward-compatible
     * callers.
     *
     * @since 0.22.4
     */
    public ElliottAnalysisResult(ElliottDegree degree, int index, List<ElliottSwing> rawSwings,
            List<ElliottSwing> processedSwings, ElliottScenarioSet scenarios,
            Map<String, ElliottConfidenceBreakdown> confidenceBreakdowns, ElliottChannel channel,
            ElliottTrendBias trendBias) {
        this(degree, index, rawSwings, processedSwings, scenarios, confidenceBreakdowns, channel, trendBias,
                AnalysisDiagnostics.empty());
    }

    public ElliottAnalysisResult {
        Objects.requireNonNull(degree, "degree");
        rawSwings = rawSwings == null ? List.of() : List.copyOf(rawSwings);
        processedSwings = processedSwings == null ? List.of() : List.copyOf(processedSwings);
        scenarios = scenarios == null ? ElliottScenarioSet.empty(index) : scenarios;
        confidenceBreakdowns = confidenceBreakdowns == null ? Map.of() : Map.copyOf(confidenceBreakdowns);
        channel = channel == null ? new ElliottChannel(NaN, NaN, NaN) : channel;
        trendBias = trendBias == null ? ElliottTrendBias.unknown() : trendBias;
        diagnostics = diagnostics == null ? AnalysisDiagnostics.empty() : diagnostics;
    }

    /**
     * Returns the confidence breakdown for a specific scenario.
     *
     * @param scenario scenario to lookup
     * @return confidence breakdown, if available
     * @since 0.22.2
     */
    public Optional<ElliottConfidenceBreakdown> breakdownFor(final ElliottScenario scenario) {
        if (scenario == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(confidenceBreakdowns.get(scenario.id()));
    }

    /**
     * Scenario-generation diagnostics captured during one analysis pass.
     *
     * @param candidateScenarioCountBeforePrune        scenarios produced before
     *                                                 pruning
     * @param retainedScenarioCount                    scenarios retained after
     *                                                 pruning
     * @param impulseDecompositionBranchCount          impulse decomposition leaf
     *                                                 branches explored
     * @param correctiveDecompositionBranchCount       corrective decomposition leaf
     *                                                 branches explored
     * @param impulseDecompositionPrunedBranchCount    impulse decomposition leaf
     *                                                 branches pruned before full
     *                                                 scoring
     * @param correctiveDecompositionPrunedBranchCount corrective decomposition leaf
     *                                                 branches pruned before full
     *                                                 scoring
     * @since 0.22.4
     */
    public record AnalysisDiagnostics(int candidateScenarioCountBeforePrune, int retainedScenarioCount,
            int impulseDecompositionBranchCount, int correctiveDecompositionBranchCount,
            int impulseDecompositionPrunedBranchCount, int correctiveDecompositionPrunedBranchCount) {

        /**
         * Creates validated analysis diagnostics.
         */
        public AnalysisDiagnostics {
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
            if (impulseDecompositionPrunedBranchCount < 0) {
                throw new IllegalArgumentException("impulseDecompositionPrunedBranchCount must be >= 0");
            }
            if (correctiveDecompositionPrunedBranchCount < 0) {
                throw new IllegalArgumentException("correctiveDecompositionPrunedBranchCount must be >= 0");
            }
        }

        /**
         * @return impulse decomposition leaf branches considered before pruning
         * @since 0.22.4
         */
        public int totalImpulseDecompositionBranchCount() {
            return impulseDecompositionBranchCount + impulseDecompositionPrunedBranchCount;
        }

        /**
         * @return corrective decomposition leaf branches considered before pruning
         * @since 0.22.4
         */
        public int totalCorrectiveDecompositionBranchCount() {
            return correctiveDecompositionBranchCount + correctiveDecompositionPrunedBranchCount;
        }

        /**
         * @return impulse decomposition prune hit rate in `[0,1]`
         * @since 0.22.4
         */
        public double impulsePruningHitRate() {
            int total = totalImpulseDecompositionBranchCount();
            return total == 0 ? 0.0 : impulseDecompositionPrunedBranchCount / (double) total;
        }

        /**
         * @return corrective decomposition prune hit rate in `[0,1]`
         * @since 0.22.4
         */
        public double correctivePruningHitRate() {
            int total = totalCorrectiveDecompositionBranchCount();
            return total == 0 ? 0.0 : correctiveDecompositionPrunedBranchCount / (double) total;
        }

        /**
         * @return empty diagnostics
         * @since 0.22.4
         */
        public static AnalysisDiagnostics empty() {
            return new AnalysisDiagnostics(0, 0, 0, 0, 0, 0);
        }
    }
}
