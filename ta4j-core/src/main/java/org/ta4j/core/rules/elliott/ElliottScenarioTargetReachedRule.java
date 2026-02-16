/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.OverOrEqualIndicatorRule;
import org.ta4j.core.rules.UnderOrEqualIndicatorRule;

/**
 * Checks whether the price has reached the scenario target.
 *
 * @since 0.22.2
 */
public class ElliottScenarioTargetReachedRule extends AbstractRule {

    private final Rule targetRule;

    /**
     * Creates a target-reached rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @param bullish           {@code true} for bullish evaluation, {@code false}
     *                          for bearish
     * @since 0.22.2
     */
    public ElliottScenarioTargetReachedRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator, final boolean bullish) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        Objects.requireNonNull(priceIndicator, "priceIndicator");
        Indicator<Num> targetIndicator = ElliottScenarioRuleSupport.targetIndicator(scenarioIndicator, bullish);
        this.targetRule = bullish ? new OverOrEqualIndicatorRule(priceIndicator, targetIndicator)
                : new UnderOrEqualIndicatorRule(priceIndicator, targetIndicator);
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        boolean satisfied = targetRule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
