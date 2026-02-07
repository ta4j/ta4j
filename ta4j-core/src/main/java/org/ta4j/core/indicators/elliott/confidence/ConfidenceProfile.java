/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Defines a weighted set of confidence factors.
 *
 * <p>
 * Use this record to assemble custom scoring profiles by combining
 * {@link ConfidenceFactor} instances with explicit weights. Profiles are
 * consumed by {@link ConfidenceModel} implementations.
 *
 * @param factors weighted factor list
 * @since 0.22.2
 */
public record ConfidenceProfile(List<WeightedFactor> factors) {

    public ConfidenceProfile {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }

    /**
     * Scores all factors for the given context.
     *
     * @param context confidence context
     * @return aggregated confidence breakdown
     * @since 0.22.2
     */
    public ElliottConfidenceBreakdown score(final ElliottConfidenceContext context) {
        Objects.requireNonNull(context, "context");
        if (factors.isEmpty()) {
            ElliottConfidence confidence = ElliottConfidence.zero(context.numFactory());
            return new ElliottConfidenceBreakdown(confidence, List.of());
        }

        final List<ConfidenceFactorResult> results = new ArrayList<>(factors.size());
        final NumFactory numFactory = context.numFactory();
        Num weightedSum = numFactory.zero();
        Num weightSum = numFactory.zero();

        final Map<ConfidenceFactorCategory, CategoryTotals> categoryTotals = new EnumMap<>(
                ConfidenceFactorCategory.class);

        ConfidenceFactorResult topContributor = null;
        Num topContribution = null;

        // Accumulates weighted factor contributions to overall score
        for (final WeightedFactor factor : factors) {
            ConfidenceFactorResult rawResult = factor.factor().score(context);
            ConfidenceFactorResult weightedResult = rawResult.withWeight(factor.weight());
            results.add(weightedResult);

            Num weight = numFactory.numOf(factor.weight());
            Num contribution = weightedResult.score().multipliedBy(weight);
            weightedSum = weightedSum.plus(contribution);
            weightSum = weightSum.plus(weight);

            if (topContribution == null || contribution.isGreaterThan(topContribution)) {
                topContribution = contribution;
                topContributor = weightedResult;
            }

            categoryTotals.computeIfAbsent(weightedResult.category(), unused -> new CategoryTotals(numFactory))
                    .add(weightedResult.score(), weight);
        }

        Num overallNum = weightSum.isPositive() ? weightedSum.dividedBy(weightSum) : numFactory.zero();

        Num fibScore = scoreFor(categoryTotals, ConfidenceFactorCategory.FIBONACCI, context);
        Num timeScore = scoreFor(categoryTotals, ConfidenceFactorCategory.TIME, context);
        Num altScore = scoreFor(categoryTotals, ConfidenceFactorCategory.ALTERNATION, context);
        Num channelScore = scoreFor(categoryTotals, ConfidenceFactorCategory.CHANNEL, context);
        Num compScore = scoreFor(categoryTotals, ConfidenceFactorCategory.COMPLETENESS, context);

        String reason = topContributor == null ? "Insufficient data"
                : (topContributor.summary() == null || topContributor.summary().isBlank() ? topContributor.name()
                        : topContributor.summary());

        ElliottConfidence confidence = new ElliottConfidence(overallNum, fibScore, timeScore, altScore, channelScore,
                compScore, reason);

        return new ElliottConfidenceBreakdown(confidence, results);
    }

    private Num scoreFor(final Map<ConfidenceFactorCategory, CategoryTotals> totals,
            final ConfidenceFactorCategory category, final ElliottConfidenceContext context) {
        CategoryTotals accumulator = totals.get(category);
        if (accumulator == null || accumulator.weightSum.isZero()) {
            return context.numFactory().zero();
        }
        return accumulator.weightedSum.dividedBy(accumulator.weightSum);
    }

    private static final class CategoryTotals {
        private Num weightedSum;
        private Num weightSum;

        private CategoryTotals(final NumFactory numFactory) {
            weightedSum = numFactory.zero();
            weightSum = numFactory.zero();
        }

        private void add(final Num score, final Num weight) {
            weightedSum = weightedSum.plus(score.multipliedBy(weight));
            weightSum = weightSum.plus(weight);
        }
    }

    /**
     * Weighted factor entry.
     *
     * @param factor confidence factor
     * @param weight weight applied to factor score
     * @since 0.22.2
     */
    public record WeightedFactor(ConfidenceFactor factor, double weight) {

        public WeightedFactor {
            Objects.requireNonNull(factor, "factor");
            if (weight < 0.0) {
                throw new IllegalArgumentException("weight must be non-negative");
            }
        }
    }
}
