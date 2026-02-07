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
 * Checks that a base Elliott scenario exists and the reference price is valid.
 *
 * @since 0.22.2
 */
public class ElliottScenarioValidRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Indicator<Num> priceIndicator;

    /**
     * Creates a validity rule for scenario and price data.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @since 0.22.2
     */
    public ElliottScenarioValidRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        Num price = priceIndicator.getValue(index);
        boolean satisfied = base != null && !Num.isNaNOrNull(price);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
