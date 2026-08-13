/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.LinkedList;
import java.util.Objects;

import org.ta4j.core.BarSeries;
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
    private final LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    /** The first retained bar index of the backing series, or {@code 0}. */
    private final int beginIndex;

    /**
     * @param initialRule the first rule that has to be satisfied before
     *                    {@link ChainLink} are evaluated
     * @param chainLinks  {@link ChainLink} that has to be satisfied after the
     *                    initial rule within their thresholds
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this.initialRule = Objects.requireNonNull(initialRule, "initialRule cannot be null");
        Objects.requireNonNull(chainLinks, "chainLinks cannot be null");
        for (ChainLink chainLink : chainLinks) {
            this.rulesInChain.add(Objects.requireNonNull(chainLink, "chainLink cannot be null"));
        }
        this.beginIndex = findBeginIndex(initialRule, rulesInChain);
    }

    private static int findBeginIndex(Rule initialRule, LinkedList<ChainLink> rulesInChain) {
        int beginIndex = RuleCopies.findBarSeries(initialRule).map(BarSeries::getBeginIndex).orElse(0);
        if (beginIndex == 0) {
            for (ChainLink chainLink : rulesInChain) {
                int linkBeginIndex = RuleCopies.findBarSeries(chainLink.getRule())
                        .map(BarSeries::getBeginIndex)
                        .orElse(0);
                if (linkBeginIndex != 0) {
                    return linkBeginIndex;
                }
            }
        }
        return beginIndex;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        if (!evaluateChildRule(initialRule, "initialRule", index, tradingRecord)) {
            if (isTraceEnabled()) {
                traceIsSatisfied(index, false, traceContext("initialRule", false));
            }
            return false;
        }

        int linkIndex = 0;
        for (ChainLink link : rulesInChain) {
            boolean satisfiedWithinThreshold = false;
            startIndex = startIndex - lastRuleWasSatisfiedAfterBars;
            lastRuleWasSatisfiedAfterBars = 0;

            for (int i = 0; i <= link.getThreshold(); i++) {
                int resultingIndex = startIndex - i;
                if (resultingIndex < beginIndex) {
                    break;
                }

                satisfiedWithinThreshold = evaluateChildRule(link.getRule(), "chainRule" + linkIndex, resultingIndex,
                        tradingRecord);

                if (satisfiedWithinThreshold) {
                    break;
                }

                lastRuleWasSatisfiedAfterBars++;
            }

            if (!satisfiedWithinThreshold) {
                if (isTraceEnabled()) {
                    traceIsSatisfied(index, false, traceContext("initialRule", true, "failedChainRule", linkIndex,
                            "threshold", link.getThreshold()));
                }
                return false;
            }
            linkIndex++;
        }

        if (isTraceEnabled()) {
            traceIsSatisfied(index, true, traceContext("initialRule", true, "chainRules", rulesInChain.size()));
        }
        return true;
    }
}
