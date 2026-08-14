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
    void gridSearchProposesBoundedBatches() {
        // A budget spanning a billion-point space used to pre-size one batch
        // with the entire remaining space, allocating a huge list before the
        // first evaluation. Batches must be capped and stay disjoint.
        List<DomainSpec> specs = List.of(DomainSpec.of(ParameterDomain.integer("a", 0, 1_000_000_000)));
        GridSearchEngine engine = new GridSearchEngine(specs);

        List<ParameterSet> first = engine.propose(Integer.MAX_VALUE);
        assertThat(first).hasSize(65536);
        List<ParameterSet> second = engine.propose(Integer.MAX_VALUE);
        assertThat(second).hasSize(65536);
        assertThat(first.stream().map(ParameterSet::stableId))
                .doesNotContainAnyElementsOf(second.stream().map(ParameterSet::stableId).toList());
    }

    @Test
    void decimalDomainAcceptsExactlyMaximumValueCount() {
        // 0..2147483646.5 step 1 declares exactly Integer.MAX_VALUE positions:
        // the raw ratio exceeds the per-domain limit while the floored position
        // count is legal and must not be rejected.
        DomainSpec spec = DomainSpec.of(ParameterDomain.decimal("a", 0, 2147483646.5, 1));

        assertThat(spec.cardinality()).isEqualTo(Integer.MAX_VALUE);
    }
}
