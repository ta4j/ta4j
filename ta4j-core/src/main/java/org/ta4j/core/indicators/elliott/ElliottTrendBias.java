/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Captures the aggregate directional bias across Elliott wave scenarios.
 *
 * @param direction     directional bias classification
 * @param score         signed bias score (-1.0 to 1.0). Positive values
 *                      indicate bullish dominance, negative values indicate
 *                      bearish dominance, and values near zero indicate
 *                      neutrality.
 * @param bullishWeight sum of confidence weights for bullish scenarios
 * @param bearishWeight sum of confidence weights for bearish scenarios
 * @param bullishCount  number of bullish scenarios contributing to the bias
 * @param bearishCount  number of bearish scenarios contributing to the bias
 * @param unknownCount  number of scenarios without known direction
 * @param consensus     {@code true} when high-confidence scenarios agree on
 *                      direction
 * @since 0.22.2
 */
public record ElliottTrendBias(Direction direction, double score, double bullishWeight, double bearishWeight,
        int bullishCount, int bearishCount, int unknownCount, boolean consensus) {

    /** Default threshold under which bias is treated as neutral. */
    public static final double DEFAULT_NEUTRAL_THRESHOLD = 0.15;

    public ElliottTrendBias {
        Objects.requireNonNull(direction, "direction");
    }

    /**
     * @return {@code true} when the bias is bullish
     * @since 0.22.2
     */
    public boolean isBullish() {
        return direction == Direction.BULLISH;
    }

    /**
     * @return {@code true} when the bias is bearish
     * @since 0.22.2
     */
    public boolean isBearish() {
        return direction == Direction.BEARISH;
    }

    /**
     * @return {@code true} when the bias is neutral
     * @since 0.22.2
     */
    public boolean isNeutral() {
        return direction == Direction.NEUTRAL;
    }

    /**
     * @return {@code true} when no directional bias could be computed
     * @since 0.22.2
     */
    public boolean isUnknown() {
        return direction == Direction.UNKNOWN;
    }

    /**
     * @return absolute strength of the bias (0.0-1.0), or NaN when unknown
     * @since 0.22.2
     */
    public double strength() {
        if (Double.isNaN(score)) {
            return Double.NaN;
        }
        return Math.abs(score);
    }

    /**
     * @return total number of scenarios considered
     * @since 0.22.2
     */
    public int totalScenarios() {
        return bullishCount + bearishCount + unknownCount;
    }

    /**
     * @return number of scenarios with a known direction
     * @since 0.22.2
     */
    public int knownDirectionCount() {
        return bullishCount + bearishCount;
    }

    /**
     * Creates an {@link ElliottTrendBias} from the given scenarios using the
     * default neutral threshold.
     *
     * @param scenarios scenarios to evaluate
     * @return computed trend bias
     * @since 0.22.2
     */
    public static ElliottTrendBias fromScenarios(final List<ElliottScenario> scenarios) {
        return fromScenarios(scenarios, DEFAULT_NEUTRAL_THRESHOLD);
    }

    /**
     * Creates an {@link ElliottTrendBias} from the given scenarios.
     *
     * @param scenarios        scenarios to evaluate
     * @param neutralThreshold absolute score below which bias is neutral
     * @return computed trend bias
     * @since 0.22.2
     */
    public static ElliottTrendBias fromScenarios(final List<ElliottScenario> scenarios, final double neutralThreshold) {
        Objects.requireNonNull(scenarios, "scenarios");
        if (scenarios.isEmpty()) {
            return unknown();
        }

        double bullishWeight = 0.0;
        double bearishWeight = 0.0;
        int bullishCount = 0;
        int bearishCount = 0;
        int unknownCount = 0;

        Boolean consensusDirection = null;
        boolean consensusBroken = false;

        for (ElliottScenario scenario : scenarios) {
            if (scenario == null) {
                unknownCount++;
                continue;
            }

            if (scenario.isHighConfidence() && scenario.hasKnownDirection() && !consensusBroken) {
                boolean bullish = scenario.isBullish();
                if (consensusDirection == null) {
                    consensusDirection = bullish;
                } else if (consensusDirection != bullish) {
                    consensusBroken = true;
                    consensusDirection = null;
                }
            }

            if (!scenario.hasKnownDirection()) {
                unknownCount++;
                continue;
            }

            Num confidence = scenario.confidenceScore();
            if (!Num.isValid(confidence)) {
                unknownCount++;
                continue;
            }

            double weight = confidence.doubleValue();
            if (Double.isNaN(weight) || Double.isInfinite(weight)) {
                unknownCount++;
                continue;
            }

            if (scenario.isBullish()) {
                bullishWeight += weight;
                bullishCount++;
            } else {
                bearishWeight += weight;
                bearishCount++;
            }
        }

        double totalWeight = bullishWeight + bearishWeight;
        if (totalWeight <= 0.0) {
            return unknown();
        }

        double score = (bullishWeight - bearishWeight) / totalWeight;
        Direction direction;
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            direction = Direction.UNKNOWN;
        } else if (Math.abs(score) < neutralThreshold) {
            direction = Direction.NEUTRAL;
        } else if (score > 0.0) {
            direction = Direction.BULLISH;
        } else {
            direction = Direction.BEARISH;
        }

        boolean consensus = consensusDirection != null && !consensusBroken;
        return new ElliottTrendBias(direction, score, bullishWeight, bearishWeight, bullishCount, bearishCount,
                unknownCount, consensus);
    }

    /**
     * @return a bias instance representing unknown direction
     * @since 0.22.2
     */
    public static ElliottTrendBias unknown() {
        return new ElliottTrendBias(Direction.UNKNOWN, Double.NaN, 0.0, 0.0, 0, 0, 0, false);
    }

    /**
     * Directional bias classification.
     *
     * @since 0.22.2
     */
    public enum Direction {
        BULLISH, BEARISH, NEUTRAL, UNKNOWN
    }
}
