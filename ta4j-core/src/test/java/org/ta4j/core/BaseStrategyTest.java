/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.ta4j.core.rules.FixedRule;

class BaseStrategyTest {

    @Test
    void rejectsInvalidConstructorArguments() {
        FixedRule entryRule = new FixedRule(0);
        FixedRule exitRule = new FixedRule(1);

        assertThrows(IllegalArgumentException.class, () -> new BaseStrategy(null, exitRule));
        assertThrows(IllegalArgumentException.class, () -> new BaseStrategy(entryRule, null));
        assertThrows(IllegalArgumentException.class, () -> new BaseStrategy(entryRule, exitRule, -1));
        assertThrows(IllegalArgumentException.class, () -> new BaseStrategy("invalid", entryRule, exitRule, 0, null));
    }
}
