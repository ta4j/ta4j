/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the value of the boolean {@link Indicator indicator} is
 * {@code true}.
 */
public class BooleanIndicatorRule extends AbstractRule {

    private final Indicator<Boolean> indicator;

    /**
     * Constructor.
     *
     * @param indicator the boolean indicator
     */
    public BooleanIndicatorRule(Indicator<Boolean> indicator) {
        this.indicator = indicator;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = indicator.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
