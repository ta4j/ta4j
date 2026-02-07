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
 * Checks whether the price has breached the corrective stop.
 *
 * @since 0.22.2
 */
public class ElliottScenarioStopViolationRule extends AbstractRule {

    private final Rule stopRule;

    /**
     * Creates a stop-violation rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @param bullish           {@code true} for bullish evaluation, {@code false}
     *                          for bearish
     * @since 0.22.2
     */
    public ElliottScenarioStopViolationRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator, final boolean bullish) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        Objects.requireNonNull(priceIndicator, "priceIndicator");
        Indicator<Num> stopIndicator = ElliottScenarioRuleSupport.stopIndicator(scenarioIndicator);
        this.stopRule = bullish ? new UnderOrEqualIndicatorRule(priceIndicator, stopIndicator)
                : new OverOrEqualIndicatorRule(priceIndicator, stopIndicator);
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        boolean satisfied = stopRule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
