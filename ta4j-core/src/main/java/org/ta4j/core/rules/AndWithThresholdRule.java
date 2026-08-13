/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.BarSeries;
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

    /** The first retained bar index of the backing series, or {@code 0}. */
    private final int beginIndex;

    /**
     * Constructor.
     *
     * @param rule1     a trading rule
     * @param rule2     another trading rule
     * @param threshold the number of bars in which the rule must be satisfied. The
     *                  current index is included.
     */
    public AndWithThresholdRule(Rule rule1, Rule rule2, int threshold) {
        this(validatedConfig(rule1, rule2, threshold));
    }

    private AndWithThresholdRule(Config config) {
        this.rule1 = config.rule1();
        this.rule2 = config.rule2();
        this.threshold = config.threshold();
        this.beginIndex = findBeginIndex(rule1, rule2);
        setName(createCompositeName(getClass().getSimpleName(), rule1, rule2));
    }

    private static int findBeginIndex(Rule rule1, Rule rule2) {
        return RuleCopies.findBarSeries(rule1)
                .or(() -> RuleCopies.findBarSeries(rule2))
                .map(BarSeries::getBeginIndex)
                .orElse(0);
    }

    private static Config validatedConfig(Rule rule1, Rule rule2, int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Threshold must be at least 1");

        }
        return new Config(Objects.requireNonNull(rule1, "rule1 cannot be null"),
                Objects.requireNonNull(rule2, "rule2 cannot be null"), threshold);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int windowStart = index - this.threshold + 1;
        if (windowStart < beginIndex) {
            if (isTraceEnabled()) {
                traceIsSatisfied(index, false, traceContext("threshold", threshold, "windowStart",
                        Math.max(beginIndex, windowStart), "windowEnd", index, "reason", "insufficientBars"));
            }
            return false;
        }

        boolean isFirstSatisfied = false;
        boolean isSecondSatisfied = false;
        for (int i = windowStart; i <= index; i++) {
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
                    traceContext("threshold", threshold, "windowStart", windowStart, "windowEnd", index, "rule1",
                            isFirstSatisfied, "rule2", isSecondSatisfied, "reason",
                            satisfied ? null : isFirstSatisfied ? "rule2False" : "rule1False"));
        }
        return satisfied;
    }

    /** @return the first rule */
    public Rule getRule1() {
        return RuleCopies.copy(rule1);
    }

    /** @return the second rule */
    public Rule getRule2() {
        return RuleCopies.copy(rule2);
    }

    private record Config(Rule rule1, Rule rule2, int threshold) {
    }
}
