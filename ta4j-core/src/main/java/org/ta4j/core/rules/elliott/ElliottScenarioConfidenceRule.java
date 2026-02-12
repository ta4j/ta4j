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

/**
 * Checks that the base scenario meets a minimum confidence threshold.
 *
 * @since 0.22.2
 */
public class ElliottScenarioConfidenceRule extends AbstractRule {

    private final Rule confidenceRule;

    /**
     * Creates a confidence threshold rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param minConfidence     minimum confidence threshold
     * @since 0.22.2
     */
    public ElliottScenarioConfidenceRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final double minConfidence) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        Indicator<Num> confidenceIndicator = ElliottScenarioRuleSupport.confidenceIndicator(scenarioIndicator);
        this.confidenceRule = new OverOrEqualIndicatorRule(confidenceIndicator, minConfidence);
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        boolean satisfied = confidenceRule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
