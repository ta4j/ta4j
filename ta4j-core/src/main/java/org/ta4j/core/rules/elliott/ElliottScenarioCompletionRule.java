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
 * Checks whether the base scenario is expected to complete.
 *
 * @since 0.22.2
 */
public class ElliottScenarioCompletionRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;

    /**
     * Creates a completion rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @since 0.22.2
     */
    public ElliottScenarioCompletionRule(final Indicator<ElliottScenarioSet> scenarioIndicator) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        boolean satisfied = base != null && base.expectsCompletion();
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
