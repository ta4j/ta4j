/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedHashMap;
import java.util.Map;
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
 *
 * @since 0.22.2
 */
public class AndWithThresholdRule extends AbstractRule {

    private final Rule rule1;
    private final Rule rule2;

    /**
     * The number of bars in which the rule must be satisfied. The current index is
     * included.
     */
    private final int threshold;

    /**
     * Constructor.
     *
     * @param rule1     a trading rule
     * @param rule2     another trading rule
     * @param threshold the number of bars in which the rule must be satisfied. The
     *                  current index is included.
     */
    public AndWithThresholdRule(Rule rule1, Rule rule2, int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Threshold must be at least 1");

        }
        this.rule1 = Objects.requireNonNull(rule1, "rule1 cannot be null");
        this.rule2 = Objects.requireNonNull(rule2, "rule2 cannot be null");
        this.threshold = threshold;

        setName(createCompositeName(getClass().getSimpleName(), rule1, rule2));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (index - this.threshold + 1 < 0) {
            traceIsSatisfied(index, false,
                    Map.of("threshold", Integer.toString(threshold), "reason", "insufficientBars"));
            return false;
        }

        boolean isFirstSatisfied = false;
        boolean isSecondSatisfied = false;
        for (int i = index - this.threshold + 1; i <= index; i++) {
            if (!isFirstSatisfied) {
                isFirstSatisfied = evaluateChildRule(rule1, "rule1", i, tradingRecord);
            }
            if (!isSecondSatisfied) {
                isSecondSatisfied = evaluateChildRule(rule2, "rule2", i, tradingRecord);
            }

            if (isFirstSatisfied && isSecondSatisfied) {
                break;
            }
        }
        final boolean satisfied = isFirstSatisfied && isSecondSatisfied;
        var context = new LinkedHashMap<String, String>();
        context.put("threshold", Integer.toString(threshold));
        context.put("rule1", Boolean.toString(isFirstSatisfied));
        context.put("rule2", Boolean.toString(isSecondSatisfied));
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
