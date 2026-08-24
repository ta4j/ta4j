/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Factory for the hard classical relationship rules.
 */
final class ClassicalRelationshipRules {

    private ClassicalRelationshipRules() {
    }

    static List<RelationshipRule> classicalRelationships() {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule());
    }

    /**
     * The full "classical-all" preset: the three hard rules plus the momentum-soft
     * wave-5 divergence rule built on the supplied indicator.
     *
     * @param momentum momentum indicator over the studied series
     * @return immutable list of all four classical rules
     */
    static List<RelationshipRule> classicalRelationships(final Indicator<Num> momentum) {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule(),
                new Wave5MomentumDivergenceRule(momentum));
    }
}
