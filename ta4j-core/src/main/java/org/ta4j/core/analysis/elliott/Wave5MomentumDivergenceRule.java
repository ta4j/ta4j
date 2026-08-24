/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Adds soft momentum evidence when the fifth-wave price extreme diverges from
 * the third-wave momentum reading.
 */
final class Wave5MomentumDivergenceRule implements RelationshipRule {

    private final Indicator<Num> momentum;

    Wave5MomentumDivergenceRule(final Indicator<Num> momentum) {
        this.momentum = Objects.requireNonNull(momentum, "momentum");
    }

    @Override
    public String id() {
        return "wave5-divergence";
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate) {
        if (!isApplicable(candidate)) {
            return RuleEvidence.notApplicable(id(), "wave 5 divergence applies only to five-wave grammars");
        }

        // Pivot 5 is the wave-5 ENDPOINT; pivot 4 is the wave-4 trough/peak.
        final int wave3Index = candidate.pivots().get(3).pivotIndex();
        final int wave5Index = candidate.pivots().get(5).pivotIndex();
        if (!isAvailableIndex(wave3Index) || !isAvailableIndex(wave5Index)) {
            return RuleEvidence.unavailable(id(), "momentum is unavailable at one or more wave endpoints");
        }
        final Num wave3Momentum;
        final Num wave5Momentum;
        try {
            wave3Momentum = momentum.getValue(wave3Index);
            wave5Momentum = momentum.getValue(wave5Index);
        } catch (IndexOutOfBoundsException exception) {
            return RuleEvidence.unavailable(id(), "momentum is missing at one or more wave endpoints");
        }
        if (Num.isNaNOrNull(wave3Momentum) || Num.isNaNOrNull(wave5Momentum)) {
            return RuleEvidence.unavailable(id(), "momentum is missing or NaN at one or more wave endpoints");
        }

        final Num wave3MomentumValue = wave3Momentum;
        final Num wave5MomentumValue = wave5Momentum;
        final Num wave3Price = candidate.pivots().get(3).price();
        final Num wave5Price = candidate.pivots().get(5).price();
        final List<String> observations = new ArrayList<>(
                List.of("wave 3 momentum=" + wave3MomentumValue, "wave 5 momentum=" + wave5MomentumValue,
                        "wave 3 end price=" + wave3Price, "wave 5 end price=" + wave5Price));
        // Divergence predicate in Num domain; the soft score below stays
        // double because it feeds report math only.
        final boolean divergence = candidate.direction() == WaveDirection.BULLISH
                ? wave5Price.isGreaterThan(wave3Price) && wave5MomentumValue.isLessThan(wave3MomentumValue)
                : wave5Price.isLessThan(wave3Price) && wave5MomentumValue.isGreaterThan(wave3MomentumValue);
        if (!divergence) {
            observations.add("aligned");
            return RuleEvidence.pass(id(), observations, "price and momentum are aligned");
        }

        final double wave3MomentumDouble = wave3MomentumValue.doubleValue();
        final double momentumMagnitude = Math.abs(wave3MomentumDouble);
        final double magnitudeDifference = Math.abs(wave3MomentumDouble - wave5MomentumValue.doubleValue());
        final double score = momentumMagnitude == 0.0d ? 1.0d : Math.min(1.0d, magnitudeDifference / momentumMagnitude);
        return RuleEvidence.scored(id(), score, observations, "price and momentum diverge at wave 5");
    }

    private boolean isAvailableIndex(final int index) {
        // Indicator unstable bars count from the series begin; translate into
        // absolute bar indices so sub-series windows are handled correctly.
        final int unstableFloor = momentum.getBarSeries().getBeginIndex() + momentum.getCountOfUnstableBars();
        return index >= unstableFloor && index >= momentum.getBarSeries().getBeginIndex()
                && index <= momentum.getBarSeries().getEndIndex();
    }

    private boolean isApplicable(final TopologyCandidate candidate) {
        return candidate.grammar() == TopologyGrammar.MOTIVE_5 || candidate.grammar() == TopologyGrammar.CYCLE_5_3;
    }
}
