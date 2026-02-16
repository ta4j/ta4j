/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.Collection;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Utility for comparing and analyzing Elliott wave scenarios.
 *
 * <p>
 * Use this helper when you need to quantify divergence between scenarios,
 * compute shared invalidation levels, or derive consensus metrics across a
 * scenario set for reporting or strategy logic.
 *
 * @since 0.22.0
 */
public final class ElliottScenarioComparator {

    private final NumFactory numFactory;

    /**
     * Creates a comparator using the specified factory.
     *
     * @param numFactory factory for numeric operations
     * @since 0.22.0
     */
    public ElliottScenarioComparator(final NumFactory numFactory) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
    }

    /**
     * Calculates a divergence score between two scenarios (0.0 - 1.0).
     *
     * <p>
     * Higher scores indicate more divergent interpretations. The score considers:
     * <ul>
     * <li>Phase difference (impulsive vs corrective)</li>
     * <li>Direction (bullish vs bearish)</li>
     * <li>Structure type</li>
     * </ul>
     *
     * @param scenario1 first scenario
     * @param scenario2 second scenario
     * @return divergence score (0.0 = identical, 1.0 = completely divergent)
     * @since 0.22.0
     */
    public Num divergenceScore(final ElliottScenario scenario1, final ElliottScenario scenario2) {
        if (scenario1 == null || scenario2 == null) {
            return numFactory.one();
        }

        double score = 0.0;

        // Phase divergence (0.0 - 0.4)
        if (scenario1.currentPhase() != scenario2.currentPhase()) {
            score += 0.2;
            // Extra penalty for impulse vs corrective mismatch
            if (scenario1.currentPhase().isImpulse() != scenario2.currentPhase().isImpulse()) {
                score += 0.2;
            }
        }

        // Direction divergence (0.0 - 0.3)
        // Only compare direction if both scenarios have a known direction
        if (scenario1.hasKnownDirection() && scenario2.hasKnownDirection()) {
            if (scenario1.isBullish() != scenario2.isBullish()) {
                score += 0.3;
            }
        } else {
            // Unknown direction counts as partial divergence
            score += 0.15;
        }

        // Type divergence (0.0 - 0.3)
        if (scenario1.type() != scenario2.type()) {
            score += 0.15;
            if (scenario1.type().isImpulse() != scenario2.type().isImpulse()) {
                score += 0.15;
            }
        }

        return numFactory.numOf(Math.min(1.0, score));
    }

    /**
     * Finds the shared invalidation price across scenarios with consistent
     * direction.
     *
     * <p>
     * For bullish scenarios, returns the lowest (most conservative) invalidation.
     * For bearish scenarios, returns the highest (most conservative) invalidation.
     * If scenarios have mixed directions (bullish and bearish), returns NaN since
     * their invalidation levels are not comparable.
     *
     * @param scenarios collection of scenarios to analyze
     * @return shared invalidation price, or NaN if no valid invalidations or mixed
     *         directions
     * @since 0.22.0
     */
    public Num sharedInvalidation(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return NaN;
        }

        Num sharedInvalidation = null;
        Boolean sharedDirection = null; // true = bullish, false = bearish

        for (final ElliottScenario scenario : scenarios) {
            final Num invalidation = scenario.invalidationPrice();
            if (Num.isNaNOrNull(invalidation)) {
                continue;
            }

            // Skip scenarios without a known direction
            if (!scenario.hasKnownDirection()) {
                continue;
            }

            final boolean bullish = scenario.isBullish();

            if (sharedDirection == null) {
                // First valid scenario establishes the direction
                sharedDirection = bullish;
                sharedInvalidation = invalidation;
            } else if (sharedDirection != bullish) {
                // Mixed directions - invalidation prices are not comparable
                return NaN;
            } else {
                // Same direction - compute shared invalidation
                // For bullish: take the lower (more conservative stop-loss)
                // For bearish: take the higher (more conservative stop-loss)
                if (bullish) {
                    sharedInvalidation = sharedInvalidation.min(invalidation);
                } else {
                    sharedInvalidation = sharedInvalidation.max(invalidation);
                }
            }
        }

        return sharedInvalidation != null ? sharedInvalidation : NaN;
    }

    /**
     * Determines the consensus phase across high-confidence scenarios.
     *
     * @param scenarios collection of scenarios
     * @return agreed-upon phase, or NONE if no consensus
     * @since 0.22.0
     */
    public ElliottPhase consensusPhase(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return ElliottPhase.NONE;
        }

        ElliottPhase consensus = null;

        for (final ElliottScenario scenario : scenarios) {
            if (!scenario.isHighConfidence()) {
                continue;
            }

            if (consensus == null) {
                consensus = scenario.currentPhase();
            } else if (consensus != scenario.currentPhase()) {
                return ElliottPhase.NONE; // No consensus
            }
        }

        return consensus != null ? consensus : ElliottPhase.NONE;
    }

    /**
     * Calculates the average confidence across all scenarios.
     *
     * @param scenarios collection of scenarios
     * @return average confidence (0.0 - 1.0)
     * @since 0.22.0
     */
    public Num averageConfidence(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return numFactory.zero();
        }

        Num total = numFactory.zero();
        int count = 0;

        for (final ElliottScenario scenario : scenarios) {
            final Num confidence = scenario.confidenceScore();
            if (Num.isValid(confidence)) {
                total = total.plus(confidence);
                count++;
            }
        }

        return count > 0 ? total.dividedBy(numFactory.numOf(count)) : numFactory.zero();
    }

    /**
     * Checks whether all high-confidence scenarios agree on direction.
     *
     * @param scenarios collection of scenarios
     * @return {@code true} if all high-confidence scenarios are same direction
     * @since 0.22.0
     */
    public boolean hasDirectionalConsensus(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return false;
        }

        Boolean consensus = null;

        for (final ElliottScenario scenario : scenarios) {
            if (!scenario.isHighConfidence()) {
                continue;
            }

            // Skip scenarios without a known direction
            if (!scenario.hasKnownDirection()) {
                continue;
            }

            final boolean bullish = scenario.isBullish();

            if (consensus == null) {
                consensus = bullish;
            } else if (consensus != bullish) {
                return false;
            }
        }

        return consensus != null;
    }

    /**
     * Computes the bounds (min, max) of all primary targets across scenarios.
     *
     * <p>
     * This returns the span of primary target prices, useful for understanding the
     * range of potential targets. Note: this is the global min/max, not the
     * intersection of per-scenario target ranges.
     *
     * @param scenarios collection of scenarios
     * @return array of [min, max] for target bounds, or empty if no valid targets
     * @since 0.22.0
     */
    public Num[] commonTargetRange(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return new Num[0];
        }

        Num minTarget = null;
        Num maxTarget = null;

        for (final ElliottScenario scenario : scenarios) {
            final Num target = scenario.primaryTarget();
            if (Num.isNaNOrNull(target)) {
                continue;
            }

            if (minTarget == null) {
                minTarget = target;
                maxTarget = target;
            } else {
                minTarget = minTarget.min(target);
                maxTarget = maxTarget.max(target);
            }
        }

        if (minTarget == null) {
            return new Num[0];
        }

        return new Num[] { minTarget, maxTarget };
    }

    /**
     * Generates a human-readable comparison summary.
     *
     * @param scenario1 first scenario
     * @param scenario2 second scenario
     * @return comparison summary string
     * @since 0.22.0
     */
    public String compareSummary(final ElliottScenario scenario1, final ElliottScenario scenario2) {
        if (scenario1 == null || scenario2 == null) {
            return "Cannot compare null scenarios";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Scenario comparison:\n");

        sb.append("  ")
                .append(scenario1.id())
                .append(": ")
                .append(scenario1.currentPhase())
                .append(" (")
                .append(String.format("%.1f%%", scenario1.confidence().asPercentage()))
                .append(")\n");

        sb.append("  ")
                .append(scenario2.id())
                .append(": ")
                .append(scenario2.currentPhase())
                .append(" (")
                .append(String.format("%.1f%%", scenario2.confidence().asPercentage()))
                .append(")\n");

        final Num divergence = divergenceScore(scenario1, scenario2);
        sb.append("  Divergence: ").append(String.format("%.1f%%", divergence.doubleValue() * 100)).append("\n");

        if (scenario1.currentPhase() == scenario2.currentPhase()) {
            sb.append("  Phase: AGREE\n");
        } else {
            sb.append("  Phase: DIFFER\n");
        }

        if (scenario1.hasKnownDirection() && scenario2.hasKnownDirection()) {
            if (scenario1.isBullish() == scenario2.isBullish()) {
                sb.append("  Direction: AGREE (").append(scenario1.isBullish() ? "bullish" : "bearish").append(")\n");
            } else {
                sb.append("  Direction: DIFFER\n");
            }
        } else {
            sb.append("  Direction: UNKNOWN (one or both scenarios lack direction info)\n");
        }

        return sb.toString();
    }
}
