/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks that the base scenario direction matches the desired direction.
 *
 * @since 0.22.2
 */
public class ElliottScenarioDirectionRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final boolean bullish;

    /**
     * Creates a direction-matching rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param bullish           {@code true} for bullish scenarios, {@code false}
     *                          for bearish
     * @since 0.22.2
     */
    public ElliottScenarioDirectionRule(final Indicator<ElliottScenarioSet> scenarioIndicator, final boolean bullish) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.bullish = bullish;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        boolean satisfied = base != null && base.hasKnownDirection() && (bullish ? base.isBullish() : base.isBearish());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
