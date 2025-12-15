/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import java.util.Collection;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Utility for comparing and analyzing Elliott wave scenarios.
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
        if (scenario1.isBullish() != scenario2.isBullish()) {
            score += 0.3;
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
     * Finds the most conservative (lowest) invalidation price across scenarios.
     *
     * <p>
     * This is useful for setting stop-losses that would be valid regardless of
     * which scenario is correct.
     *
     * @param scenarios collection of scenarios to analyze
     * @return lowest invalidation price, or NaN if no valid invalidations
     * @since 0.22.0
     */
    public Num sharedInvalidation(final Collection<ElliottScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return numFactory.numOf(Double.NaN);
        }

        Num sharedInvalidation = null;

        for (final ElliottScenario scenario : scenarios) {
            final Num invalidation = scenario.invalidationPrice();
            if (invalidation == null || invalidation.isNaN()) {
                continue;
            }

            if (sharedInvalidation == null) {
                sharedInvalidation = invalidation;
            } else {
                // For bullish scenarios, take the lower invalidation
                // For bearish, take the higher
                if (scenario.isBullish()) {
                    sharedInvalidation = sharedInvalidation.min(invalidation);
                } else {
                    sharedInvalidation = sharedInvalidation.max(invalidation);
                }
            }
        }

        return sharedInvalidation != null ? sharedInvalidation : numFactory.numOf(Double.NaN);
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

        double total = 0.0;
        int count = 0;

        for (final ElliottScenario scenario : scenarios) {
            final Num confidence = scenario.confidenceScore();
            if (confidence != null && !confidence.isNaN()) {
                total += confidence.doubleValue();
                count++;
            }
        }

        return count > 0 ? numFactory.numOf(total / count) : numFactory.zero();
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
     * Finds the common (overlapping) target range across scenarios.
     *
     * @param scenarios collection of scenarios
     * @return array of [min, max] for overlapping target range, or empty if none
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
            if (target == null || target.isNaN()) {
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

        if (scenario1.isBullish() == scenario2.isBullish()) {
            sb.append("  Direction: AGREE (").append(scenario1.isBullish() ? "bullish" : "bearish").append(")\n");
        } else {
            sb.append("  Direction: DIFFER\n");
        }

        return sb.toString();
    }
}
