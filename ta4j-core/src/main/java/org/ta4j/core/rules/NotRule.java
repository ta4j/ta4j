/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An opposite (logical operator: NOT) rule (i.e. a rule that is the negation of
 * another rule).
 *
 * <p>
 * Satisfied when the rule is not satisfied.<br>
 * Not satisfied when the rule is satisfied.
 */
public class NotRule extends AbstractRule {

    /** The trading rule to negate. */
    private final Rule ruleToNegate;

    /**
     * Constructor.
     *
     * @param ruleToNegate the trading rule to negate
     */
    public NotRule(Rule ruleToNegate) {
        this.ruleToNegate = Objects.requireNonNull(ruleToNegate, "ruleToNegate cannot be null");
        setName(createCompositeName(getClass().getSimpleName(), ruleToNegate));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean childSatisfied = evaluateChildRule(ruleToNegate, "ruleToNegate", index, tradingRecord);
        final boolean satisfied = !childSatisfied;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /** @return {@link #ruleToNegate} */
    public Rule getRuleToNegate() {
        return RuleCopies.copy(ruleToNegate);
    }
}
