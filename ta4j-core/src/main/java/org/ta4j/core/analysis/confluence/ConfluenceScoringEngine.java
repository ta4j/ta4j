/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.confluence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scores confluence pillars with optional family caps and correlation
 * penalties.
 *
 * <p>
 * Raw score is the weighted average of pillar scores. Decorrelated score
 * applies family-level caps and penalties before recomputing an adjusted
 * weighted average.
 *
 * @since 0.22.3
 */
public final class ConfluenceScoringEngine {

    private final Map<String, FamilyPolicy> familyPolicies;

    /**
     * Builds a scoring engine with no caps and no penalties.
     *
     * @since 0.22.3
     */
    public ConfluenceScoringEngine() {
        this(Map.of());
    }

    /**
     * Builds a scoring engine with per-family policies.
     *
     * @param familyPolicies policy map keyed by family id
     * @since 0.22.3
     */
    public ConfluenceScoringEngine(Map<String, FamilyPolicy> familyPolicies) {
        Objects.requireNonNull(familyPolicies, "familyPolicies cannot be null");
        LinkedHashMap<String, FamilyPolicy> copied = new LinkedHashMap<>();
        for (Map.Entry<String, FamilyPolicy> entry : familyPolicies.entrySet()) {
            String family = entry.getKey();
            if (family == null || family.isBlank()) {
                throw new IllegalArgumentException("family policy key cannot be null or blank");
            }
            FamilyPolicy policy = Objects.requireNonNull(entry.getValue(),
                    "family policy cannot be null for family " + family);
            copied.put(family, policy);
        }
        this.familyPolicies = Map.copyOf(copied);
    }

    /**
     * Computes raw and decorrelated confluence scores for the supplied pillar list.
     *
     * @param pillarScores pillar scoring payloads
     * @return confluence scores with diagnostics
     * @since 0.22.3
     */
    public ConfluenceScores score(List<ConfluenceReport.PillarScore> pillarScores) {
        Objects.requireNonNull(pillarScores, "pillarScores cannot be null");
        if (pillarScores.isEmpty()) {
            throw new IllegalArgumentException("pillarScores cannot be empty");
        }

        List<ConfluenceReport.PillarScore> scores = List.copyOf(pillarScores);
        double totalWeight = 0.0d;
        for (ConfluenceReport.PillarScore pillarScore : scores) {
            totalWeight += pillarScore.weight();
        }

        LinkedHashMap<String, FamilyAccumulator> families = new LinkedHashMap<>();
        double rawNumerator = 0.0d;
        int pillarCount = scores.size();
        for (ConfluenceReport.PillarScore pillarScore : scores) {
            double normalizedWeight = normalizeWeight(pillarScore.weight(), totalWeight, pillarCount);
            rawNumerator += normalizedWeight * pillarScore.score();

            String family = resolveFamily(pillarScore);
            FamilyAccumulator accumulator = families.computeIfAbsent(family, key -> new FamilyAccumulator());
            accumulator.totalWeight += normalizedWeight;
            accumulator.weightedScore += normalizedWeight * pillarScore.score();
        }

        double rawScore = rawNumerator;
        LinkedHashMap<String, Double> adjustedWeights = new LinkedHashMap<>();
        double adjustedWeightSum = 0.0d;
        double adjustedScoreSum = 0.0d;
        for (Map.Entry<String, FamilyAccumulator> entry : families.entrySet()) {
            String family = entry.getKey();
            FamilyAccumulator accumulator = entry.getValue();
            FamilyPolicy policy = familyPolicies.getOrDefault(family, FamilyPolicy.DEFAULT);

            double familyAverageScore = accumulator.weightedScore / accumulator.totalWeight;
            double cappedWeight = Math.min(accumulator.totalWeight, policy.capWeight());
            double adjustedWeight = cappedWeight * (1.0d - policy.correlationPenalty());
            if (adjustedWeight <= 0.0d) {
                continue;
            }
            adjustedWeights.put(family, adjustedWeight);
            adjustedWeightSum += adjustedWeight;
            adjustedScoreSum += adjustedWeight * familyAverageScore;
        }

        double decorrelatedScore = adjustedWeightSum > 0.0d ? adjustedScoreSum / adjustedWeightSum : rawScore;
        double penalty = Math.max(0.0d, rawScore - decorrelatedScore);

        LinkedHashMap<String, Double> normalizedEffectiveWeights = new LinkedHashMap<>();
        if (adjustedWeightSum > 0.0d) {
            for (Map.Entry<String, Double> entry : adjustedWeights.entrySet()) {
                normalizedEffectiveWeights.put(entry.getKey(), entry.getValue() / adjustedWeightSum);
            }
        }

        return new ConfluenceScores(rawScore, decorrelatedScore, penalty, normalizedEffectiveWeights);
    }

    private static String resolveFamily(ConfluenceReport.PillarScore pillarScore) {
        if (pillarScore.family() == null || pillarScore.family().isBlank()) {
            return pillarScore.pillar().name();
        }
        return pillarScore.family();
    }

    private static double normalizeWeight(double weight, double totalWeight, int pillarCount) {
        if (totalWeight > 0.0d) {
            return weight / totalWeight;
        }
        return 1.0d / pillarCount;
    }

    /**
     * Family-level policy with cap and correlation penalty in [0, 1].
     *
     * @param capWeight          max normalized weight for the family
     * @param correlationPenalty penalty fraction applied after capping
     * @since 0.22.3
     */
    public record FamilyPolicy(double capWeight, double correlationPenalty) {

        private static final FamilyPolicy DEFAULT = new FamilyPolicy(1.0d, 0.0d);

        public FamilyPolicy {
            validateUnitInterval(capWeight, "capWeight");
            validateUnitInterval(correlationPenalty, "correlationPenalty");
        }
    }

    /**
     * Confluence scoring output.
     *
     * @param rawScore               weighted average score without decorrelation
     * @param decorrelatedScore      score after family caps and penalties
     * @param correlationPenalty     non-negative score penalty in points
     * @param effectiveFamilyWeights normalized effective family weights
     * @since 0.22.3
     */
    public record ConfluenceScores(double rawScore, double decorrelatedScore, double correlationPenalty,
            Map<String, Double> effectiveFamilyWeights) {
        public ConfluenceScores(double rawScore, double decorrelatedScore, double correlationPenalty,
                Map<String, Double> effectiveFamilyWeights) {
            validateScore(rawScore, "rawScore");
            validateScore(decorrelatedScore, "decorrelatedScore");
            validateNonNegative(correlationPenalty, "correlationPenalty");
            Objects.requireNonNull(effectiveFamilyWeights, "effectiveFamilyWeights cannot be null");
            this.rawScore = rawScore;
            this.decorrelatedScore = decorrelatedScore;
            this.correlationPenalty = correlationPenalty;
            this.effectiveFamilyWeights = Map.copyOf(effectiveFamilyWeights);
        }
    }

    private static final class FamilyAccumulator {
        private double totalWeight;
        private double weightedScore;
    }

    private static void validateUnitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(field + " must be finite and in [0, 1]");
        }
    }

    private static void validateScore(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
            throw new IllegalArgumentException(field + " must be finite and in [0, 100]");
        }
    }

    private static void validateNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
    }
}
