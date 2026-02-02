/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.List;

import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.NumFactory;

/**
 * Factory helpers for confidence profiles.
 *
 * @since 0.22.2
 */
public final class ConfidenceProfiles {

    private ConfidenceProfiles() {
    }

    /**
     * Default profile matching the existing confidence weights.
     *
     * @param numFactory numeric factory
     * @return confidence profile
     * @since 0.22.2
     */
    public static ConfidenceProfile defaultProfile(final NumFactory numFactory) {
        return profileWithWeights(numFactory, ElliottConfidenceScorer.DEFAULT_FIBONACCI_WEIGHT,
                ElliottConfidenceScorer.DEFAULT_TIME_WEIGHT, ElliottConfidenceScorer.DEFAULT_ALTERNATION_WEIGHT,
                ElliottConfidenceScorer.DEFAULT_CHANNEL_WEIGHT, ElliottConfidenceScorer.DEFAULT_COMPLETENESS_WEIGHT);
    }

    /**
     * Default model using the same profile for every scenario type.
     *
     * @param numFactory numeric factory
     * @return confidence model
     * @since 0.22.2
     */
    public static ScenarioTypeConfidenceModel defaultModel(final NumFactory numFactory) {
        ConfidenceProfile profile = defaultProfile(numFactory);
        return ScenarioTypeConfidenceModel.builder(numFactory).defaultProfile(profile).build();
    }

    /**
     * Pattern-aware model with distinct weights for corrective patterns.
     *
     * @param numFactory numeric factory
     * @return confidence model
     * @since 0.22.2
     */
    public static ScenarioTypeConfidenceModel patternAwareModel(final NumFactory numFactory) {
        ConfidenceProfile impulseProfile = defaultProfile(numFactory);
        ConfidenceProfile correctiveProfile = profileWithWeights(numFactory, 0.30, 0.25, 0.10, 0.20, 0.15);
        return ScenarioTypeConfidenceModel.builder(numFactory)
                .defaultProfile(impulseProfile)
                .overrideProfile(ScenarioType.CORRECTIVE_ZIGZAG, correctiveProfile)
                .overrideProfile(ScenarioType.CORRECTIVE_FLAT, correctiveProfile)
                .overrideProfile(ScenarioType.CORRECTIVE_TRIANGLE, correctiveProfile)
                .overrideProfile(ScenarioType.CORRECTIVE_COMPLEX, correctiveProfile)
                .build();
    }

    private static ConfidenceProfile profileWithWeights(final NumFactory numFactory, final double fibWeight,
            final double timeWeight, final double alternationWeight, final double channelWeight,
            final double completenessWeight) {
        ElliottConfidenceScorer scorer = new ElliottConfidenceScorer(numFactory);
        List<ConfidenceProfile.WeightedFactor> factors = List.of(
                new ConfidenceProfile.WeightedFactor(new FibonacciRelationshipFactor(), fibWeight),
                new ConfidenceProfile.WeightedFactor(new TimeProportionFactor(scorer), timeWeight),
                new ConfidenceProfile.WeightedFactor(new TimeAlternationFactor(scorer), alternationWeight),
                new ConfidenceProfile.WeightedFactor(new ChannelAdherenceFactor(scorer), channelWeight),
                new ConfidenceProfile.WeightedFactor(new StructureCompletenessFactor(scorer), completenessWeight));
        return new ConfidenceProfile(factors);
    }
}
