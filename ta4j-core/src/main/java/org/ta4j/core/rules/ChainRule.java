/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Arrays;
import java.util.LinkedList;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.helper.ChainLink;

/**
 * A {@code ChainRule} has an initial rule that has to be satisfied before a
 * list of {@link ChainLink chain links} are evaluated. If the initial rule is
 * satisfied, each rule in {@link ChainRule#rulesInChain chain links} has to be
 * satisfied within a specified "number of bars (= threshold)".
 */
public class ChainRule extends AbstractRule {

    private final Rule initialRule;
    private LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param chainLinks  {@link ChainLink} that has to be satisfied after the
     *                    initial rule within their thresholds
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this.initialRule = initialRule;
        this.rulesInChain.addAll(Arrays.asList(chainLinks));
    }

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

                if (satisfiedWithinThreshold) {
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
