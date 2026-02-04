/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks whether the price has breached the corrective stop.
 *
 * @since 0.22.2
 */
public class ElliottScenarioStopViolationRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Indicator<Num> priceIndicator;
    private final boolean bullish;

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
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.bullish = bullish;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        Num closePrice = priceIndicator.getValue(index);
        if (base == null || Num.isNaNOrNull(closePrice)) {
            traceIsSatisfied(index, false);
            return false;
        }
        Num stop = ElliottScenarioRuleSupport.selectStop(base);
        if (Num.isNaNOrNull(stop)) {
            traceIsSatisfied(index, false);
            return false;
        }
        boolean satisfied = bullish ? closePrice.isLessThanOrEqual(stop) : closePrice.isGreaterThanOrEqual(stop);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
