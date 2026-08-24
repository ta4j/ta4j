/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

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
}
