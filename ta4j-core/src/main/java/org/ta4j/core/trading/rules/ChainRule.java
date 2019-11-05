package org.ta4j.core.trading.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.trading.rules.helper.ChainLink;

import java.util.Arrays;
import java.util.LinkedList;

public class ChainRule extends AbstractRule {
    Rule initialRule = null;
    LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    /**
     * A chainrule has an initial rule that has to be satisfied before chain links are evaluated.
     * If the initial rule is satisfied every rule of chain link has to be satisfied within a specified amount of bars (threshold).
     *
     * @param initialRule
     * @param chainLinks
     */
    public ChainRule(Rule initialRule, ChainLink... chainLinks) {
        this.initialRule = initialRule;
        rulesInChain.addAll(Arrays.asList(chainLinks));
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        if (initialRule == null || !initialRule.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }
        traceIsSatisfied(index, true);

        for (ChainLink link: rulesInChain) {
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
