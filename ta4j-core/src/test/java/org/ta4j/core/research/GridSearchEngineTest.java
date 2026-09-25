/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;
import org.ta4j.core.research.ParameterResearch.ParameterSet;

class GridSearchEngineTest {

    @Test
    void gridSearchPreservesCohortLimit() {
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 65536)));
        GridSearchEngine engine = new GridSearchEngine(specs);

        assertThat(engine.propose(SearchEngine.MAX_COHORT_SIZE + 1)).hasSize(SearchEngine.MAX_COHORT_SIZE);
    }

    @Test
    void gridSearchProposesSmallDisjointBatches() {
        // Exercise iteration order and batch independence without materializing
        // tens of thousands of parameter sets for a single assertion.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 7)));
        GridSearchEngine engine = new GridSearchEngine(specs);

        List<ParameterSet> first = engine.propose(4);
        List<ParameterSet> second = engine.propose(4);

        assertThat(first).hasSize(4);
        assertThat(second).hasSize(4);
        assertThat(first.stream().map(ParameterSet::stableId))
                .doesNotContainAnyElementsOf(second.stream().map(ParameterSet::stableId).toList());
    }
}
