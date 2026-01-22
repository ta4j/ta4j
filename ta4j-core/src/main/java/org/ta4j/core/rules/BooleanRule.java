/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the rule has been initialized with {@code true}.
 */
public class BooleanRule extends AbstractRule {

    /** An always-true rule. */
    public static final BooleanRule TRUE = new BooleanRule(true);

    /** An always-false rule. */
    public static final BooleanRule FALSE = new BooleanRule(false);

    private final boolean satisfied;

    /**
     * Constructor.
     *
     * @param satisfied true for the rule to be always satisfied, false to be never
     *                  satisfied
     */
    public BooleanRule(boolean satisfied) {
        this.satisfied = satisfied;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
