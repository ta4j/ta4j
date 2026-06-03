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
            if (isTraceEnabled()) {
                traceIsSatisfied(index, false, traceContext("threshold", threshold, "windowStart", 0, "windowEnd",
                        index, "reason", "insufficientBars"));
            }
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
        if (isTraceEnabled()) {
            traceIsSatisfied(index, satisfied,
                    traceContext("threshold", threshold, "windowStart", index - this.threshold + 1, "windowEnd", index,
                            "rule1", isFirstSatisfied, "rule2", isSecondSatisfied, "reason",
                            satisfied ? null : isFirstSatisfied ? "rule2False" : "rule1False"));
        }
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
