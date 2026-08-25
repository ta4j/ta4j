/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    /**
     * Upper bound on simultaneously retained series; studies evaluate one or few
     * series per runner.
     */
    private static final int MAX_CACHED_SERIES = 4;

    /**
     * Bounded LRU rather than a plain map: every bound indicator strongly
     * references its own series, so weak keys would never be collected and an
     * unbounded map would retain every studied series for the runner's life.
     */
    private final Map<BarSeries, Indicator<Num>> boundMomentum = Collections
            .synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<BarSeries, Indicator<Num>> eldest) {
                    return size() > MAX_CACHED_SERIES;
                }
            });

    Wave5MomentumDivergenceRule(final Indicator<Num> momentum) {
        Objects.requireNonNull(momentum, "momentum");
        final BarSeries boundSeries = momentum.getBarSeries();
        // The fixed indicator is bound to exactly one series. Handing it to a
        // runner evaluating another series would splice that asset's pivots
        // onto the first asset's momentum values; fail fast instead.
        this.momentumFactory = series -> {
            if (series != boundSeries) {
                throw new IllegalArgumentException(
                        "fixed momentum indicator is bound to a different series; use the factory constructor");
            }
            return momentum;
        };
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
        // Applicability is decided before any series-specific work so foreign
        // or absent series bindings cannot fail for grammars this rule never
        // scores (for example a fixed momentum indicator bound to another
        // series while the candidate is CORRECTIVE_3).
        if (!isApplicable(candidate)) {
            return RuleEvidence.notApplicable(id(), "wave 5 divergence applies only to five-wave grammars");
        }
        if (series == null) {
            return RuleEvidence.unavailable(id(), "momentum rule requires the evaluated series binding");
        }
        final Indicator<Num> momentum = boundMomentum.computeIfAbsent(series, momentumFactory);
        // A factory handing back an indicator bound to another series would
        // splice foreign momentum values onto this series' pivots; fail fast.
        if (momentum.getBarSeries() != series) {
            throw new IllegalArgumentException("momentum factory returned an indicator bound to a different series");
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
        // isFinite rather than isNaNOrNull: an infinite DoubleNum endpoint
        // survives the NaN check but then either manufactures a divergence
        // score of 1 or yields infinity/infinity = NaN, aborting scored
        // evidence construction.
        if (!Num.isFinite(wave3Momentum) || !Num.isFinite(wave5Momentum)) {
            return RuleEvidence.unavailable(id(), "momentum is missing or non-finite at one or more wave endpoints");
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
            // The rule premise IS divergence; alignment is a failed premise,
            // never a pass, so the +wave5-divergence ablation measures how
            // often five-wave candidates actually diverge.
            return RuleEvidence.fail(id(), observations, "price and momentum are aligned");
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
