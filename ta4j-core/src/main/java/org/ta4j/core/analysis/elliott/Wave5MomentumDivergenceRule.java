/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Adds soft momentum evidence when the fifth-wave price extreme diverges from
 * the third-wave momentum reading.
 */
final class Wave5MomentumDivergenceRule implements RelationshipRule {

    /**
     * Momentum indicators are series-scoped; the factory is keyed by the evaluated
     * series so one runner instance can study several series without silently
     * reading one series' momentum values against another's pivots.
     */
    private final Function<BarSeries, Indicator<Num>> momentumFactory;
    private final Map<BarSeries, Indicator<Num>> boundMomentum = new HashMap<>();

    Wave5MomentumDivergenceRule(final Indicator<Num> momentum) {
        Objects.requireNonNull(momentum, "momentum");
        this.momentumFactory = series -> momentum;
    }

    Wave5MomentumDivergenceRule(final Function<BarSeries, Indicator<Num>> momentumFactory) {
        this.momentumFactory = Objects.requireNonNull(momentumFactory, "momentumFactory");
    }

    @Override
    public String id() {
        return "wave5-divergence";
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate) {
        return RuleEvidence.unavailable(id(), "momentum rule requires the evaluated series binding");
    }

    @Override
    public RuleEvidence evaluate(final TopologyCandidate candidate, final BarSeries series) {
        if (series == null) {
            return RuleEvidence.unavailable(id(), "momentum rule requires the evaluated series binding");
        }
        final Indicator<Num> momentum = boundMomentum.computeIfAbsent(series, momentumFactory);
        if (!isApplicable(candidate)) {
            return RuleEvidence.notApplicable(id(), "wave 5 divergence applies only to five-wave grammars");
        }

        // Pivot 5 is the wave-5 ENDPOINT; pivot 4 is the wave-4 trough/peak.
        final int wave3Index = candidate.pivots().get(3).pivotIndex();
        final int wave5Index = candidate.pivots().get(5).pivotIndex();
        if (!isAvailableIndex(momentum, wave3Index) || !isAvailableIndex(momentum, wave5Index)) {
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
        // Divergence predicate and its magnitude ratio both stay in Num
        // domain; see the scored branch below.
        final boolean divergence = candidate.direction() == WaveDirection.BULLISH
                ? wave5Price.isGreaterThan(wave3Price) && wave5MomentumValue.isLessThan(wave3MomentumValue)
                : wave5Price.isLessThan(wave3Price) && wave5MomentumValue.isGreaterThan(wave3MomentumValue);
        if (!divergence) {
            observations.add("aligned");
            return RuleEvidence.pass(id(), observations, "price and momentum are aligned");
        }

        // Divergence magnitude arithmetic stays in the active Num domain so
        // DecimalNum magnitudes beyond double range cannot collapse to NaN and
        // abort the study; only the bounded final score narrows to double.
        final Num momentumMagnitude = wave3MomentumValue.abs();
        final Num magnitudeDifference = wave3MomentumValue.minus(wave5MomentumValue).abs();
        final double score;
        if (momentumMagnitude.isZero()) {
            score = 1.0d;
        } else if (magnitudeDifference.isGreaterThan(momentumMagnitude)) {
            score = 1.0d;
        } else {
            score = magnitudeDifference.dividedBy(momentumMagnitude).doubleValue();
        }
        return RuleEvidence.scored(id(), score, observations, "price and momentum diverge at wave 5");
    }

    private boolean isAvailableIndex(final Indicator<Num> momentum, final int index) {
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
