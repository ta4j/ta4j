/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Represents a single Elliott wave interpretation with associated confidence
 * metrics and projections.
 *
 * <p>
 * A scenario captures one possible wave count for the current price action,
 * including:
 * <ul>
 * <li>The current phase (which wave we believe we're in)</li>
 * <li>Confidence scoring for this interpretation</li>
 * <li>Invalidation level (price that would disprove this count)</li>
 * <li>Fibonacci-based price targets</li>
 * </ul>
 *
 * @param id                unique scenario identifier
 * @param currentPhase      the phase this scenario assigns to current price
 *                          action
 * @param swings            swing sequence backing this interpretation
 * @param confidence        confidence metrics for this scenario
 * @param degree            timeframe degree of this wave structure
 * @param invalidationPrice price level that would invalidate this count
 * @param primaryTarget     primary Fibonacci projection target
 * @param fibonacciTargets  list of all Fibonacci-based price targets
 * @param type              pattern type classification
 * @param startIndex        bar index where this wave structure begins
 * @param bullishDirection  explicit direction flag: {@code true} for bullish,
 *                          {@code false} for bearish, {@code null} if unknown
 * @since 0.22.0
 */
public record ElliottScenario(String id, ElliottPhase currentPhase, List<ElliottSwing> swings,
        ElliottConfidence confidence, ElliottDegree degree, Num invalidationPrice, Num primaryTarget,
        List<Num> fibonacciTargets, ScenarioType type, int startIndex, Boolean bullishDirection) {

    public ElliottScenario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currentPhase, "currentPhase");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(degree, "degree");
        Objects.requireNonNull(type, "type");
        swings = swings == null ? List.of() : List.copyOf(swings);
        fibonacciTargets = fibonacciTargets == null ? List.of() : List.copyOf(fibonacciTargets);
        // Derive bullishDirection from swings when present (swings are the source of
        // truth); only use the explicit direction when swings are empty
        if (!swings.isEmpty()) {
            bullishDirection = swings.get(0).isRising();
        }
    }

    /**
     * @return {@code true} if this scenario has high confidence
     * @since 0.22.0
     */
    public boolean isHighConfidence() {
        return confidence.isHighConfidence();
    }

    /**
     * @return {@code true} if this scenario has low confidence
     * @since 0.22.0
     */
    public boolean isLowConfidence() {
        return confidence.isLowConfidence();
    }

    /**
     * @return the overall confidence score (0.0 - 1.0)
     * @since 0.22.0
     */
    public Num confidenceScore() {
        return confidence.overall();
    }

    /**
     * Checks if this scenario represents a bullish structure (rising impulse or
     * corrective move in a bull market).
     *
     * @return {@code true} if this scenario is bullish
     * @throws IllegalStateException if direction cannot be determined (no swings
     *                               and no explicit direction set)
     * @since 0.22.0
     */
    public boolean isBullish() {
        if (bullishDirection == null) {
            throw new IllegalStateException(
                    "Cannot determine direction: scenario '" + id + "' has no swings and no explicit direction set");
        }
        return bullishDirection;
    }

    /**
     * Checks if this scenario represents a bearish structure.
     *
     * @return {@code true} if this scenario is bearish
     * @throws IllegalStateException if direction cannot be determined (no swings
     *                               and no explicit direction set)
     * @since 0.22.0
     */
    public boolean isBearish() {
        if (bullishDirection == null) {
            throw new IllegalStateException(
                    "Cannot determine direction: scenario '" + id + "' has no swings and no explicit direction set");
        }
        return !bullishDirection;
    }

    /**
     * Checks if this scenario has a known direction (bullish or bearish).
     *
     * @return {@code true} if direction is known (either from swings or explicitly
     *         set)
     * @since 0.22.0
     */
    public boolean hasKnownDirection() {
        return bullishDirection != null;
    }

    /**
     * @return number of waves completed in this scenario
     * @since 0.22.0
     */
    public int waveCount() {
        return swings.size();
    }

    /**
     * Checks whether the given price would invalidate this scenario.
     *
     * @param price current price to test
     * @return {@code true} if the price breaches the invalidation level
     * @throws IllegalStateException if direction cannot be determined (no swings
     *                               and no explicit direction set)
     * @since 0.22.0
     */
    public boolean isInvalidatedBy(final Num price) {
        if (Num.isNaNOrNull(invalidationPrice) || Num.isNaNOrNull(price)) {
            return false;
        }
        if (bullishDirection == null) {
            throw new IllegalStateException(
                    "Cannot evaluate invalidation: scenario '" + id + "' has no swings and no explicit direction set. "
                            + "Set bullishDirection explicitly or provide swings to determine direction.");
        }
        // For bullish scenarios, invalidation is below the level
        // For bearish scenarios, invalidation is above the level
        if (bullishDirection) {
            return price.isLessThan(invalidationPrice);
        } else {
            return price.isGreaterThan(invalidationPrice);
        }
    }

    /**
     * @return {@code true} if this scenario expects the structure to complete (wave
     *         5 or wave C)
     * @since 0.22.0
     */
    public boolean expectsCompletion() {
        return currentPhase == ElliottPhase.WAVE5 || currentPhase == ElliottPhase.CORRECTIVE_C;
    }

    /**
     * Creates a builder for constructing scenarios.
     *
     * @return new scenario builder
     * @since 0.22.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link ElliottScenario} instances.
     *
     * @since 0.22.0
     */
    public static final class Builder {

        private String id;
        private ElliottPhase currentPhase = ElliottPhase.NONE;
        private List<ElliottSwing> swings = List.of();
        private ElliottConfidence confidence;
        private ElliottDegree degree = ElliottDegree.MINOR;
        private Num invalidationPrice;
        private Num primaryTarget;
        private List<Num> fibonacciTargets = List.of();
        private ScenarioType type = ScenarioType.UNKNOWN;
        private int startIndex;
        private Boolean bullishDirection;

        private Builder() {
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder currentPhase(final ElliottPhase phase) {
            this.currentPhase = phase;
            return this;
        }

        public Builder swings(final List<ElliottSwing> swings) {
            this.swings = swings;
            return this;
        }

        public Builder confidence(final ElliottConfidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder degree(final ElliottDegree degree) {
            this.degree = degree;
            return this;
        }

        public Builder invalidationPrice(final Num price) {
            this.invalidationPrice = price;
            return this;
        }

        public Builder primaryTarget(final Num target) {
            this.primaryTarget = target;
            return this;
        }

        public Builder fibonacciTargets(final List<Num> targets) {
            this.fibonacciTargets = targets;
            return this;
        }

        public Builder type(final ScenarioType type) {
            this.type = type;
            return this;
        }

        public Builder startIndex(final int index) {
            this.startIndex = index;
            return this;
        }

        /**
         * Sets the explicit direction for this scenario.
         *
         * <p>
         * Use this when the direction is known but swings have not yet been
         * established. <strong>Important:</strong> If swings are provided, the
         * direction will always be derived from them (first swing determines direction)
         * and this explicit setting will be ignored. This explicit setting is only used
         * when the swings list is empty.
         *
         * @param bullish {@code true} for bullish, {@code false} for bearish,
         *                {@code null} if direction is unknown
         * @return this builder
         * @since 0.22.0
         */
        public Builder bullishDirection(final Boolean bullish) {
            this.bullishDirection = bullish;
            return this;
        }

        public ElliottScenario build() {
            return new ElliottScenario(id, currentPhase, swings, confidence, degree, invalidationPrice, primaryTarget,
                    fibonacciTargets, type, startIndex, bullishDirection);
        }
    }
}
