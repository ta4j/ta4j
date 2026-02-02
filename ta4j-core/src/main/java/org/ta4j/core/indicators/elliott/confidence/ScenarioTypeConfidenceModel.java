/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.NumFactory;

/**
 * Confidence model that selects profiles by {@link ScenarioType}.
 *
 * @since 0.22.2
 */
public final class ScenarioTypeConfidenceModel implements ConfidenceModel {

    private final NumFactory numFactory;
    private final ElliottFibonacciValidator validator;
    private final ConfidenceProfile defaultProfile;
    private final Map<ScenarioType, ConfidenceProfile> profileOverrides;

    private ScenarioTypeConfidenceModel(final NumFactory numFactory, final ConfidenceProfile defaultProfile,
            final Map<ScenarioType, ConfidenceProfile> profileOverrides) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.validator = new ElliottFibonacciValidator(numFactory);
        this.defaultProfile = Objects.requireNonNull(defaultProfile, "defaultProfile");
        this.profileOverrides = Map.copyOf(profileOverrides);
    }

    @Override
    public ElliottConfidenceBreakdown score(final List<ElliottSwing> swings, final ElliottPhase phase,
            final ElliottChannel channel, final ScenarioType scenarioType) {
        ScenarioType resolvedType = scenarioType == null ? ScenarioType.UNKNOWN : scenarioType;
        ConfidenceProfile profile = profileOverrides.getOrDefault(resolvedType, defaultProfile);
        List<ElliottSwing> safeSwings = swings == null ? List.of() : swings;
        ElliottPhase safePhase = phase == null ? ElliottPhase.NONE : phase;
        ElliottConfidenceContext context = new ElliottConfidenceContext(safeSwings, safePhase, channel, validator,
                numFactory);
        return profile.score(context);
    }

    /**
     * Creates a builder for a scenario-type aware model.
     *
     * @param numFactory numeric factory
     * @return builder
     * @since 0.22.2
     */
    public static Builder builder(final NumFactory numFactory) {
        return new Builder(numFactory);
    }

    /**
     * Builder for {@link ScenarioTypeConfidenceModel}.
     *
     * @since 0.22.2
     */
    public static final class Builder {

        private final NumFactory numFactory;
        private ConfidenceProfile defaultProfile;
        private final Map<ScenarioType, ConfidenceProfile> overrides = new EnumMap<>(ScenarioType.class);

        private Builder(final NumFactory numFactory) {
            this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        }

        /**
         * Sets the default profile.
         *
         * @param profile default profile
         * @return builder
         * @since 0.22.2
         */
        public Builder defaultProfile(final ConfidenceProfile profile) {
            this.defaultProfile = profile;
            return this;
        }

        /**
         * Overrides a profile for a scenario type.
         *
         * @param type    scenario type
         * @param profile profile override
         * @return builder
         * @since 0.22.2
         */
        public Builder overrideProfile(final ScenarioType type, final ConfidenceProfile profile) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(profile, "profile");
            overrides.put(type, profile);
            return this;
        }

        /**
         * Builds the model.
         *
         * @return confidence model
         * @since 0.22.2
         */
        public ScenarioTypeConfidenceModel build() {
            if (defaultProfile == null) {
                throw new IllegalStateException("defaultProfile must be provided");
            }
            return new ScenarioTypeConfidenceModel(numFactory, defaultProfile, overrides);
        }
    }
}
