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

    @Test
    void projectIndexClampsExtremePositionsToTheNearestBoundary() {
        // Math.round(double) saturates at Long.MAX_VALUE; narrowing that long
        // to int before the bound checks truncated the low 32 bits to -1 and
        // mapped an outlier coordinate onto the lowest grid index instead of
        // the highest.
        DomainSpec spec = DomainSpec.of(ParameterDomain.decimal("a", 0, 10, 1));

        assertThat(spec.projectIndex(1e20)).isEqualTo(10);
        assertThat(spec.projectIndex(-1e20)).isEqualTo(0);
        assertThat(spec.projectIndex(Double.NaN)).isEqualTo(0);
        assertThat(spec.projectIndex(5.4)).isEqualTo(5);
        assertThat(spec.projectIndex(5.6)).isEqualTo(6);
    }
}
