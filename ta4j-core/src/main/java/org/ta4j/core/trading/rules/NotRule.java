package org.ta4j.core.trading.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An opposite (logical operator: NOT) rule.
 * </p>
 * Satisfied when provided rule is not satisfied.<br>
 * Not satisfied when provided rule is satisfied.
 */
public class NotRule extends AbstractRule {

    private final Rule rule;

    /**
     * Constructor.
     *
     * @param rule a trading rule
     */
    public NotRule(Rule rule) {
        this.rule = rule;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = !rule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
