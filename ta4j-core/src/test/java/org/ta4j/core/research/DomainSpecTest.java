/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.research.ParameterResearch.ParameterDomain;

class DomainSpecTest {

    @Test
    void decimalDomainAcceptsExactlyMaximumValueCount() {
        // 0..2147483646.5 step 1 declares exactly Integer.MAX_VALUE positions:
        // the raw ratio exceeds the per-domain limit while the floored position
        // count is legal and must not be rejected.
        DomainSpec spec = DomainSpec.of(ParameterDomain.decimal("a", 0, 2147483646.5, 1));

        assertThat(spec.cardinality()).isEqualTo(Integer.MAX_VALUE);
    }
}
