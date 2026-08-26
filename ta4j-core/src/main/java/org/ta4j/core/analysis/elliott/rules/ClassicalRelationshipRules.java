/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

import java.util.List;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Factory for the hard classical relationship rules.
 */
public final class ClassicalRelationshipRules {

    private ClassicalRelationshipRules() {
    }

    public static List<RelationshipRule> classicalRelationships() {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule());
    }

    /**
     * The full "classical-all" preset: the three hard rules plus the momentum-soft
     * wave-5 divergence rule built on the supplied indicator.
     *
     * @param momentum momentum indicator over the studied series
     * @return immutable list of all four classical rules
     */
    public static List<RelationshipRule> classicalRelationships(final Indicator<Num> momentum) {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule(),
                new Wave5MomentumDivergenceRule(momentum));
    }

    /**
     * The "classical-all" preset with per-series momentum binding: the wave-5
     * divergence rule builds one indicator per evaluated series via the supplied
     * factory, so a single runner instance can study several series without
     * cross-series indicator reads.
     *
     * @param momentumFactory factory creating the momentum indicator for a series
     * @return immutable list of all four classical rules
     */
    public static List<RelationshipRule> classicalRelationships(
            final Function<BarSeries, Indicator<Num>> momentumFactory) {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule(),
                new Wave5MomentumDivergenceRule(momentumFactory));
    }
}
