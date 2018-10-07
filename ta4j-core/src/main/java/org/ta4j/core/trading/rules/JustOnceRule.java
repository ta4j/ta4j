package org.ta4j.core.trading.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * A one-shot rule.<p />
 * Satisfied the first time it's checked then never again.
 */
public class JustOnceRule extends AbstractRule {

    private final Rule rule;
    private boolean satisfied = false;

    /**
     * Constructor.<p />
     * Satisfied the first time the inner rule is satisfied then never again.
     *
     * @param rule the rule that should be satisfied only the first time
     */
    public JustOnceRule(Rule rule) {
        this.rule = rule;
    }

    /**
     * Constructor.<p />
     * Satisfied the first time it's checked then never again.
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
