/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ParameterDomainTest {

    @Test
    void integerRangeBuildsInclusiveValues() {
        ParameterDomain<Integer> domain = ParameterDomain.integerRange("period", 5, 20, 5);

        assertThat(domain.name()).isEqualTo("period");
        assertThat(domain.values()).containsExactly(5, 10, 15, 20);
        assertThat(domain.decode(2)).isEqualTo(15);
    }

    @Test
    void constrainedBooleanKeepsExplicitOrder() {
        ParameterDomain<Boolean> domain = ParameterDomain.constrainedBoolean("enabled", true, false, true);

        assertThat(domain.values()).containsExactly(true, false);
    }

    @Test
    void invalidRangeIsRejected() {
        assertThatThrownBy(() -> ParameterDomain.integerRange("period", 10, 5, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endInclusive");
        assertThatThrownBy(() -> ParameterDomain.integerRange("period", 1, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
    }

    @Test
    void explicitValuesRejectNullAndEmptyLists() {
        assertThatThrownBy(() -> ParameterDomain.ofValues("mode", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("values");
        assertThatThrownBy(() -> ParameterDomain.ofValues("mode", Arrays.asList("fast", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
