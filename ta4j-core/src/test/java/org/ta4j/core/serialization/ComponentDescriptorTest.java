/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComponentDescriptorTest {
    @Test
    void resolveSubtypeTreatsMissingSuperclassAsUnresolved() throws Throwable {
        try (MissingDependencyFixture fixture = new MissingDependencyFixture()) {
            assertThat(fixture.invoke("resolveMissingSuperclass")).isNull();
        }
    }
}
