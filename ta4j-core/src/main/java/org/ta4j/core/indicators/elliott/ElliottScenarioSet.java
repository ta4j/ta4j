/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.num.Num;

/**
 * Immutable container holding ranked alternative Elliott wave scenarios.
 *
 * <p>
 * Scenarios are maintained in descending order by confidence score. The
 * {@link #base()} method returns the highest-confidence interpretation (base
 * case), while {@link #alternatives()} provides all other valid scenarios.
 *
 * @since 0.22.0
 */
public final class ElliottScenarioSet {

    private final List<ElliottScenario> scenarios;
    private final int barIndex;

    private ElliottScenarioSet(final List<ElliottScenario> scenarios, final int barIndex) {
        this.scenarios = List.copyOf(scenarios);
        this.barIndex = barIndex;
    }

    /**
     * Creates an empty scenario set.
     *
     * @param barIndex the bar index this set represents
     * @return empty scenario set
     * @since 0.22.0
     */
    public static ElliottScenarioSet empty(final int barIndex) {
        return new ElliottScenarioSet(List.of(), barIndex);
    }

    /**
     * Creates a scenario set from the provided scenarios, sorted by confidence.
     *
     * @param scenarios list of scenarios (will be sorted by confidence descending)
     * @param barIndex  the bar index this set represents
     * @return new scenario set with sorted scenarios
     * @since 0.22.0
     */
    public static ElliottScenarioSet of(final List<ElliottScenario> scenarios, final int barIndex) {
        Objects.requireNonNull(scenarios, "scenarios");
        if (scenarios.isEmpty()) {
            return empty(barIndex);
        }

        final List<ElliottScenario> sorted = scenarios.stream()
                .filter(Objects::nonNull)
                .sorted(byConfidenceDescending())
                .toList();

        return new ElliottScenarioSet(sorted, barIndex);
    }

    /**
     * @return comparator that sorts scenarios by confidence score descending
     * @since 0.22.0
     */
    public static Comparator<ElliottScenario> byConfidenceDescending() {
        return (a, b) -> {
            final Num aScore = a.confidenceScore();
            final Num bScore = b.confidenceScore();
            if (Num.isNaNOrNull(aScore)) {
                return 1;
            }
            if (Num.isNaNOrNull(bScore)) {
                return -1;
            }
            return bScore.compareTo(aScore);
        };
    }

    /**
     * @return the bar index this scenario set represents
     * @since 0.22.0
     */
    public int barIndex() {
        return barIndex;
    }

    /**
     * Returns the base case (highest-confidence) scenario.
     *
     * @return the highest-confidence scenario, or empty if no scenarios exist
     * @since 0.22.0
     */
    public Optional<ElliottScenario> base() {
        return scenarios.isEmpty() ? Optional.empty() : Optional.of(scenarios.get(0));
    }

    /**
     * @return all scenarios except the base case, sorted by confidence descending
     * @since 0.22.0
     */
    public List<ElliottScenario> alternatives() {
        if (scenarios.size() <= 1) {
            return List.of();
        }
        return scenarios.subList(1, scenarios.size());
    }

    /**
     * @return all scenarios including base case, sorted by confidence descending
     * @since 0.22.0
     */
    public List<ElliottScenario> all() {
        return scenarios;
    }

    /**
     * @return number of scenarios in this set
     * @since 0.22.0
     */
    public int size() {
        return scenarios.size();
    }

    /**
     * @return {@code true} if no scenarios exist
     * @since 0.22.0
     */
    public boolean isEmpty() {
        return scenarios.isEmpty();
    }

    /**
     * Filters scenarios to those predicting a specific phase.
     *
     * @param phase the phase to filter by
     * @return new scenario set containing only matching scenarios
     * @since 0.22.0
     */
    public ElliottScenarioSet byPhase(final ElliottPhase phase) {
        Objects.requireNonNull(phase, "phase");
        final List<ElliottScenario> filtered = scenarios.stream().filter(s -> s.currentPhase() == phase).toList();
        return new ElliottScenarioSet(filtered, barIndex);
    }

    /**
     * Filters scenarios to those of a specific pattern type.
     *
     * @param type the scenario type to filter by
     * @return new scenario set containing only matching scenarios
     * @since 0.22.0
     */
    public ElliottScenarioSet byType(final ScenarioType type) {
        Objects.requireNonNull(type, "type");
        final List<ElliottScenario> filtered = scenarios.stream().filter(s -> s.type() == type).toList();
        return new ElliottScenarioSet(filtered, barIndex);
    }

    /**
     * @return count of scenarios with high confidence (>= 0.7)
     * @since 0.22.0
     */
    public int highConfidenceCount() {
        return (int) scenarios.stream().filter(ElliottScenario::isHighConfidence).count();
    }

    /**
     * @return count of scenarios with low confidence (< 0.3)
     * @since 0.22.0
     */
    public int lowConfidenceCount() {
        return (int) scenarios.stream().filter(ElliottScenario::isLowConfidence).count();
    }

    /**
     * Determines the consensus phase across all high-confidence scenarios.
     *
     * @return the agreed-upon phase if all high-confidence scenarios match,
     *         otherwise {@link ElliottPhase#NONE}
     * @since 0.22.0
     */
    public ElliottPhase consensus() {
        final List<ElliottScenario> highConfidence = scenarios.stream()
                .filter(ElliottScenario::isHighConfidence)
                .toList();

        if (highConfidence.isEmpty()) {
            return ElliottPhase.NONE;
        }

        final ElliottPhase first = highConfidence.get(0).currentPhase();
        final boolean allMatch = highConfidence.stream().allMatch(s -> s.currentPhase() == first);

        return allMatch ? first : ElliottPhase.NONE;
    }

    /**
     * Computes the aggregate directional bias across all scenarios.
     *
     * @return trend bias snapshot
     * @since 0.22.2
     */
    public ElliottTrendBias trendBias() {
        return trendBias(ElliottTrendBias.DEFAULT_NEUTRAL_THRESHOLD);
    }

    /**
     * Computes the aggregate directional bias across all scenarios using a custom
     * neutral threshold.
     *
     * @param neutralThreshold absolute score below which bias is neutral
     * @return trend bias snapshot
     * @since 0.22.2
     */
    public ElliottTrendBias trendBias(final double neutralThreshold) {
        return ElliottTrendBias.fromScenarios(scenarios, neutralThreshold);
    }

    /**
     * Calculates the confidence spread between the base case and next-best
     * scenario.
     *
     * @return confidence difference, or 0 if fewer than 2 scenarios exist
     * @since 0.22.0
     */
    public double confidenceSpread() {
        if (scenarios.size() < 2) {
            return 0.0;
        }
        final Num baseCase = scenarios.get(0).confidenceScore();
        final Num secondary = scenarios.get(1).confidenceScore();
        if (Num.isNaNOrNull(baseCase) || Num.isNaNOrNull(secondary)) {
            return 0.0;
        }
        return baseCase.minus(secondary).abs().doubleValue();
    }

    /**
     * Checks whether there is strong consensus (single high-confidence scenario or
     * large spread).
     *
     * @return {@code true} if confidence spread exceeds 0.3 or only one
     *         high-confidence scenario
     * @since 0.22.0
     */
    public boolean hasStrongConsensus() {
        final int highCount = highConfidenceCount();
        if (highCount == 0) {
            return false;
        }
        if (highCount == 1) {
            return true;
        }
        return confidenceSpread() >= 0.3;
    }

    /**
     * Returns scenarios that would be invalidated by the given price.
     *
     * <p>
     * Scenarios without a known direction are excluded (they cannot be evaluated
     * for invalidation).
     *
     * @param price price level to test
     * @return list of scenarios invalidated by this price
     * @since 0.22.0
     */
    public List<ElliottScenario> invalidatedBy(final Num price) {
        if (Num.isNaNOrNull(price)) {
            return List.of();
        }
        return scenarios.stream()
                .filter(ElliottScenario::hasKnownDirection)
                .filter(s -> s.isInvalidatedBy(price))
                .toList();
    }

    /**
     * Returns scenarios that remain valid at the given price.
     *
     * <p>
     * Scenarios without a known direction are retained (they cannot be evaluated
     * for invalidation so they are conservatively kept).
     *
     * @param price price level to test
     * @return new scenario set with only valid scenarios
     * @since 0.22.0
     */
    public ElliottScenarioSet validAt(final Num price) {
        if (Num.isNaNOrNull(price)) {
            return this;
        }
        final List<ElliottScenario> valid = scenarios.stream()
                .filter(s -> !s.hasKnownDirection() || !s.isInvalidatedBy(price))
                .toList();
        return new ElliottScenarioSet(valid, barIndex);
    }

    /**
     * Returns a summary string describing the scenario distribution.
     *
     * @return human-readable summary
     * @since 0.22.0
     */
    public String summary() {
        if (isEmpty()) {
            return "No scenarios";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(scenarios.size()).append(" scenario(s): ");

        final Optional<ElliottScenario> baseCaseOpt = base();
        if (baseCaseOpt.isPresent()) {
            final ElliottScenario bc = baseCaseOpt.get();
            sb.append("Base case=")
                    .append(bc.currentPhase())
                    .append(" (")
                    .append(String.format("%.1f%%", bc.confidence().asPercentage()))
                    .append(")");
        }

        if (scenarios.size() > 1) {
            sb.append(", ").append(scenarios.size() - 1).append(" alternative(s)");
        }

        final ElliottPhase consensusPhase = consensus();
        if (consensusPhase != ElliottPhase.NONE) {
            sb.append(", consensus=").append(consensusPhase);
        }

        return sb.toString();
    }
}
