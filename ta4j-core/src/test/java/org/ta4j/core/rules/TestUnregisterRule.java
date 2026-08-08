/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.rules.named.NamedRule;

/**
 * Test-only named rule that must stay OUTSIDE the default scan package
 * ({@code org.ta4j.core.rules.named}) so registry lookups asserting its absence
 * are independent of scan order.
 */
public class TestUnregisterRule extends NamedRule {

    private final ClosePredicate closePredicate;

    public TestUnregisterRule(ClosePredicate closePredicate) {
        super(NamedRule.buildLabel(TestUnregisterRule.class, closePredicate.name()));
        this.closePredicate = closePredicate;
    }

    public TestUnregisterRule(String... parameters) {
        this(ClosePredicate.valueOf(parameters[0]));
    }

    @Override
    public boolean isSatisfied(int index, org.ta4j.core.TradingRecord tradingRecord) {
        return closePredicate == ClosePredicate.ALWAYS;
    }

    public enum ClosePredicate {
        ALWAYS
    }
}
