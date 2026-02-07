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
 * Checks whether the price invalidates the base scenario.
 *
 * @since 0.22.2
 */
public class ElliottScenarioInvalidationRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Indicator<Num> priceIndicator;

    /**
     * Creates an invalidation rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @since 0.22.2
     */
    public ElliottScenarioInvalidationRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        Num closePrice = priceIndicator.getValue(index);
        boolean satisfied = base != null && !Num.isNaNOrNull(closePrice) && base.isInvalidatedBy(closePrice);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
