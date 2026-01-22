/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * A one-shot rule.
 *
 * <p>
 * Satisfied when the rule is satisfied for the first time, then never again.
 */
public class JustOnceRule extends AbstractRule {

    private final Rule rule;
    private boolean satisfied = false;

    /**
     * Constructor.
     *
     * <p>
     * Satisfied when the given {@code rule} is satisfied the first time, then never
     * again.
     *
     * @param rule the rule that should be satisfied only the first time
     */
    public JustOnceRule(Rule rule) {
        this.rule = rule;
    }

    /**
     * Constructor.
     *
     * <p>
     * Satisfied at the first check, then never again.
     */
    public JustOnceRule() {
        this.rule = null;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (satisfied) {
            return false;
        } else if (rule == null) {
            satisfied = true;
            traceIsSatisfied(index, true);
            return true;
        }
        this.satisfied = this.rule.isSatisfied(index, tradingRecord);
        return this.satisfied;
    }
}
