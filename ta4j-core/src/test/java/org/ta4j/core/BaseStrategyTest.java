/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void ruleAccessorsReturnCopies() {
        FixedRule entryRule = new FixedRule(0);
        FixedRule exitRule = new FixedRule(1);
        BaseStrategy strategy = new BaseStrategy(entryRule, exitRule);

        Rule firstEntryRule = strategy.getEntryRule();
        Rule secondEntryRule = strategy.getEntryRule();
        Rule firstExitRule = strategy.getExitRule();
        Rule secondExitRule = strategy.getExitRule();

        assertNotSame(entryRule, firstEntryRule);
        assertNotSame(firstEntryRule, secondEntryRule);
        assertNotSame(exitRule, firstExitRule);
        assertNotSame(firstExitRule, secondExitRule);
        assertTrue(firstEntryRule.isSatisfied(0));
        assertTrue(firstExitRule.isSatisfied(1));
    }

    @Test
    void shouldEnterAndExitUseOwnedRules() {
        BaseStrategy strategy = new GetterRejectingStrategy(new FixedRule(0), new FixedRule(1));

        assertTrue(strategy.shouldEnter(0));
        assertTrue(strategy.shouldExit(1));
    }

    private static final class GetterRejectingStrategy extends BaseStrategy {

        private GetterRejectingStrategy(Rule entryRule, Rule exitRule) {
            super(entryRule, exitRule);
        }

        @Override
        public Rule getEntryRule() {
            throw new AssertionError("shouldEnter must use the owned entry rule");
        }

        @Override
        public Rule getExitRule() {
            throw new AssertionError("shouldExit must use the owned exit rule");
        }
    }
}
