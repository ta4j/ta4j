/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class ClassicalRelationshipRulesTest {

    @Test
    void containsOnlyTheThreeHardClassicalRules() {
        final List<RelationshipRule> rules = ClassicalRelationshipRules.classicalRelationships();

        assertThat(rules).extracting(RelationshipRule::id)
                .containsExactly("wave2-origin", "wave3-not-shortest", "wave4-nonoverlap");
    }

    @Test
    void createsFreshRuleInstancesForIndependentSelection() {
        final List<RelationshipRule> first = ClassicalRelationshipRules.classicalRelationships();
        final List<RelationshipRule> second = ClassicalRelationshipRules.classicalRelationships();

        assertThat(first).hasSize(3);
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    @Test
    void momentumFactoryYieldsTheFullClassicalAllPreset() {
        final BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7).build();
        final Indicator<Num> momentum = new SMAIndicator(new ClosePriceIndicator(series), 3);
        final List<RelationshipRule> rules = ClassicalRelationshipRules.classicalRelationships(momentum);

        assertThat(rules).extracting(RelationshipRule::id)
                .containsExactly("wave2-origin", "wave3-not-shortest", "wave4-nonoverlap", "wave5-divergence");
    }

    @Test
    void frozenAblationLadderIncludesEveryClassicalRule() {
        final BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7).build();
        final Indicator<Num> momentum = new SMAIndicator(new ClosePriceIndicator(series), 3);

        assertThat(RuleAblation.frozenModes(ClassicalRelationshipRules.classicalRelationships(momentum)))
                .extracting(RuleAblation.Mode::name)
                .containsExactly("topology-only", "+wave2-origin", "+wave3-not-shortest", "+wave4-nonoverlap",
                        "+wave5-divergence", "classical-all");
    }

}
