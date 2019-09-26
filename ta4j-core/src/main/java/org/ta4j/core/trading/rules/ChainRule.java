package org.ta4j.core.trading.rules;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.trading.rules.helper.ChainLink;

import java.util.Arrays;
import java.util.LinkedList;

public class ChainRule extends AbstractRule {
    LinkedList<ChainLink> rulesInChain = new LinkedList<>();

    public ChainRule(ChainLink... chainLinks) {
        rulesInChain.addAll(Arrays.asList(chainLinks));
    }

    @Override
    public boolean isSatisfied(int index) {
        int lastRuleWasSatisfiedAfterBars = 0;
        int startIndex = index;

        for (ChainLink link: rulesInChain) {
            boolean satisfiedWithinThreshold = false;
            startIndex = startIndex - lastRuleWasSatisfiedAfterBars;
            lastRuleWasSatisfiedAfterBars = 0;

            for (int i = 0; i < link.getThreshold(); i++) {
                lastRuleWasSatisfiedAfterBars++;
                int resultingIndex = startIndex - i;
                if (resultingIndex < 0) {
                    break;
                }

                satisfiedWithinThreshold = link.getRule().isSatisfied(resultingIndex);

                if (satisfiedWithinThreshold == true) {
                    break;
                }
            }

            if (!satisfiedWithinThreshold) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isSatisfied(int i, TradingRecord tradingRecord) {
        return this.isSatisfied(i);
    }
}
