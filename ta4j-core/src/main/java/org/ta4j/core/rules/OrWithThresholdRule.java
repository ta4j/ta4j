/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * An OR combination of two {@link Rule rules}.
 *
 * <p>
 * Satisfied when at least one rule is satisfied.
 *
 * <p>
 * <b>Warning:</b> The second rule is not tested if the first rule is satisfied.
 */
public class OrWithThresholdRule extends AbstractRule {

    private final Rule rule1;
    private final Rule rule2;

    /**
     * The number of bars in which the rule must be satisfied. The current index is
     * included.
     */
    private int threshold = 0;

    /**
     * Constructor.
     *
     * @param rule1     a trading rule
     * @param rule2     another trading rule
     * @param threshold the number of bars in which at least one rule must be
     *                  satisfied. The current index is included.
     */
    public OrWithThresholdRule(Rule rule1, Rule rule2, int threshold) {
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
            return false;
        }

        boolean isFirstSatisfied = false;
        boolean isSecondSatisfied = false;
        for (int i = index - this.threshold + 1; i <= index; i++) {
            if (!isFirstSatisfied) {
                isFirstSatisfied = rule1.isSatisfied(i, tradingRecord);
            }
            if (!isSecondSatisfied) {
                isSecondSatisfied = rule2.isSatisfied(i, tradingRecord);
            }

            if (isFirstSatisfied || isSecondSatisfied) {
                break;
            }
        }
        final boolean satisfied = isFirstSatisfied || isSecondSatisfied;
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
