/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedHashMap;
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
        final boolean firstSatisfied = evaluateChildRule(rule1, "rule1", index, tradingRecord);
        final boolean satisfied;
        final boolean secondEvaluated;

        if (!firstSatisfied) {
            satisfied = false;
            secondEvaluated = false;
        } else {
            satisfied = evaluateChildRule(rule2, "rule2", index, tradingRecord);
            secondEvaluated = true;
        }
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("rule1", Boolean.toString(firstSatisfied));
        context.put("rule2Evaluated", Boolean.toString(secondEvaluated));
        context.put("rule2", secondEvaluated ? Boolean.toString(satisfied) : "skipped");
        if (!secondEvaluated) {
            context.put("reason", "rule1False");
        }
        traceIsSatisfied(index, satisfied, context);
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
