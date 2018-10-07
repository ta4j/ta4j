package org.ta4j.core.trading.rules;

import org.ta4j.core.TradingRecord;

/**
 * A simple boolean rule.
 * </p>
 * Satisfied when it has been initialized with true.
 */
public class BooleanRule extends AbstractRule {

    /**
     * An always-true rule
     */
    public static final BooleanRule TRUE = new BooleanRule(true);

    /**
     * An always-false rule
     */
    public static final BooleanRule FALSE = new BooleanRule(false);

    private final boolean satisfied;

    /**
     * Constructor.
     *
     * @param satisfied true for the rule to be always satisfied, false to be never satisfied
     */
    public BooleanRule(boolean satisfied) {
        this.satisfied = satisfied;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
