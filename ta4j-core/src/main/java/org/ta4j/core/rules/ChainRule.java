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

import java.util.Arrays;
import java.util.LinkedList;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.helper.ChainLink;

/**
 * A {@code ChainRule} consists of:
 *
 * <ul>
 * <li>The list of {@link ChainLink chain links}: Each rule in
 * {@link ChainRule#rulesInChain chain links} has to be satisfied within a
 * specified "number of bars (= {@code threshold})".
 * <li>The (optional) {@link #initialRule}: If defined, it must be satisfied
 * <b>before</b> {@link ChainLink chain links} are evaluated, i.e. the tested
 * index is the number of the maximum {@code threshold} from
 * {@link #rulesInChain}.
 * <li>The (optional) {@link #currentRule}: If defined, it must be satisfied
 * <b>at the current {@code index}</b> .
 */
public class ChainRule extends AbstractRule {

    private final Rule initialRule;
    private final Rule currentRule;
    private LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param chainLinks {@link ChainLink} that has to be satisfied within their
     *                   thresholds (= "number of bars")
     */
    public ChainRule(ChainLink... chainLinks) {
        this(null, null, chainLinks);
    }

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param chainLinks  {@link ChainLink} that has to be satisfied within their
     *                    thresholds (= "number of bars")
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this(initialRule, null, chainLinks);
    }

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param currentRule the current rule that has to be satisfied at the current
     *                    {@code index}
     * @param chainLinks  {@link ChainLink} that has to be satisfied within their
     *                    thresholds (= "number of bars")
     */
    public ChainRule(Rule initialRule, Rule currentRule, ChainLink... chainLinks) {
        this.initialRule = initialRule;
        this.currentRule = currentRule;
        this.rulesInChain.addAll(Arrays.asList(chainLinks));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        // 1. check the initialRule
        if (initialRule != null) {
            // We need at least a required minimum number of bars ( = initialRuleIndex) to
            // determine whether the initialRule is satisfied. As long as we do not have the
            // required minimum number of bars, the rule cannot be satisfied.
            int initialRuleIndex = index - rulesInChain.stream().mapToInt(ChainLink::getThreshold).max().orElse(0);
            if (initialRuleIndex < 0 || !initialRule.isSatisfied(initialRuleIndex, tradingRecord)) {
                traceIsSatisfied(initialRuleIndex, false);
                return false;
            }
        }

        // 2. check the currentRule
        if (currentRule != null && !currentRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }

        traceIsSatisfied(index, true);

        // 3. check the rulesInChain
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

                // Stop further checks if the rule was not satisfied at resultingIndex.
                if (!satisfiedWithinThreshold) {
                    // To speed up the execution of the ChainRule, we could "return false"
                    // instead of a "break." However, since we want to "trace" each rule, we need to
                    // check the next rule from "rulesInChain", even though it's already clear that
                    // the ChainRule rule isn't satisfied.
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
