/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.strategy;

import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.strategy.named.NamedStrategy;

/**
 * Test fixture that shares the simple name of
 * {@code NamedStrategyTest.TestUnregisterStrategy} while living in a different
 * package. It is never instantiated and carries no static initializer, so it
 * never registers itself. {@link NamedStrategy#unregisterImplementation(Class)}
 * must not remove a registered strategy merely because this class shares its
 * simple name.
 */
public class TestUnregisterStrategy extends NamedStrategy {

    TestUnregisterStrategy() {
        super("TestUnregisterStrategy", new FixedRule(1), new FixedRule(2), 0);
    }
}
