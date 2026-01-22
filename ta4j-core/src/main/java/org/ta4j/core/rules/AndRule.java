/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An AND combination of two {@link Rule rules}.
 *
 * <p>
 * Satisfied when both rules are satisfied.
 *
 * <p>
 * <b>Warning:</b> The second rule is not tested if the first rule is not
 * satisfied.
 */
public class AndRule extends AbstractRule {

    private final Rule rule1;
    private final Rule rule2;

    /**
     * Constructor.
     *
     * @param rule1 a trading rule
     * @param rule2 another trading rule
     */
    public AndRule(Rule rule1, Rule rule2) {
        this.rule1 = Objects.requireNonNull(rule1, "rule1 cannot be null");
        this.rule2 = Objects.requireNonNull(rule2, "rule2 cannot be null");
        setName(createCompositeName(getClass().getSimpleName(), rule1, rule2));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = rule1.isSatisfied(index, tradingRecord) && rule2.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /** @return the first rule */
    public Rule getRule1() {
        return rule1;
    }

    /** @return the second rule */
    public Rule getRule2() {
        return rule2;
    }
}
