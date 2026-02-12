/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PatternSetTest {

    @Test
    void allEnablesAllScenarioTypes() {
        PatternSet patternSet = PatternSet.all();

        for (ScenarioType type : ScenarioType.values()) {
            assertThat(patternSet.allows(type)).isTrue();
        }
        assertThat(patternSet.enabledTypes()).containsExactlyInAnyOrder(ScenarioType.values());
    }

    @Test
    void ofEnablesOnlySelectedTypes() {
        PatternSet patternSet = PatternSet.of(ScenarioType.IMPULSE, null, ScenarioType.CORRECTIVE_FLAT);

        assertThat(patternSet.enabledTypes()).containsExactlyInAnyOrder(ScenarioType.IMPULSE,
                ScenarioType.CORRECTIVE_FLAT);
        assertThat(patternSet.allows(ScenarioType.CORRECTIVE_ZIGZAG)).isFalse();
        assertThat(patternSet.allows(null)).isFalse();
    }

    @Test
    void withoutDisablesSpecifiedTypes() {
        PatternSet patternSet = PatternSet.without(ScenarioType.IMPULSE, ScenarioType.CORRECTIVE_COMPLEX);

        assertThat(patternSet.allows(ScenarioType.IMPULSE)).isFalse();
        assertThat(patternSet.allows(ScenarioType.CORRECTIVE_COMPLEX)).isFalse();
        assertThat(patternSet.allows(ScenarioType.CORRECTIVE_ZIGZAG)).isTrue();
    }

    @Test
    void enabledTypesIsImmutable() {
        PatternSet patternSet = PatternSet.of(ScenarioType.IMPULSE);

        Set<ScenarioType> enabledTypes = patternSet.enabledTypes();
        assertThrows(UnsupportedOperationException.class, () -> enabledTypes.add(ScenarioType.CORRECTIVE_FLAT));
    }
}
