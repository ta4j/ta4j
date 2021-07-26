/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.util.Arrays;
import java.util.LinkedList;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.helper.ChainLink;

/**
 * A chainrule has an initial rule that has to be satisfied before chain links
 * are evaluated. If the initial rule is satisfied every rule of chain link has
 * to be satisfied within a specified amount of bars (threshold).
 *
 */
public class ChainRule extends AbstractRule {
    private final Rule initialRule;
    LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param chainLinks  {@link ChainLink} that has to be satisfied after the
     *                    inital rule within their thresholds
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this.initialRule = initialRule;
        rulesInChain.addAll(Arrays.asList(chainLinks));
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        if (!initialRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }
        traceIsSatisfied(index, true);

        for (ChainLink link : rulesInChain) {
            boolean satisfiedWithinThreshold = false;
            startIndex = startIndex - lastRuleWasSatisfiedAfterBars;
            lastRuleWasSatisfiedAfterBars = 0;

            for (int i = 0; i <= link.getThreshold(); i++) {
                int resultingIndex = startIndex - i;
                if (resultingIndex < 0) {
                    break;
                }

                satisfiedWithinThreshold = link.getRule().isSatisfied(resultingIndex, tradingRecord);

                if (satisfiedWithinThreshold == true) {
                    break;
                }

                lastRuleWasSatisfiedAfterBars++;
            }

            if (!satisfiedWithinThreshold) {
                traceIsSatisfied(index, false);
                return false;
            }
        }

        traceIsSatisfied(index, true);
        return true;
    }
}
