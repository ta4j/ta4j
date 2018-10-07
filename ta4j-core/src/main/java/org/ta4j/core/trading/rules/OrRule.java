package org.ta4j.core.trading.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An OR combination of two {@link Rule rules}.
 * </p>
 * Satisfied when one of the two provided rules is satisfied.<br>
 * Warning: the second rule is not tested if the first rule is satisfied.
 */
public class OrRule extends AbstractRule {

    private final Rule rule1;
    private final Rule rule2;

    /**
     * Constructor.
     * @param rule1 a trading rule
     * @param rule2 another trading rule
     */
    public OrRule(Rule rule1, Rule rule2) {
        this.rule1 = rule1;
        this.rule2 = rule2;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = rule1.isSatisfied(index, tradingRecord) || rule2.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
