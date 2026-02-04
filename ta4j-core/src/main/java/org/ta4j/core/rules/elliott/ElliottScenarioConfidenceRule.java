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
 * Checks that the base scenario meets a minimum confidence threshold.
 *
 * @since 0.22.2
 */
public class ElliottScenarioConfidenceRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final double minConfidence;

    /**
     * Creates a confidence threshold rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param minConfidence     minimum confidence threshold
     * @since 0.22.2
     */
    public ElliottScenarioConfidenceRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final double minConfidence) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.minConfidence = minConfidence;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        boolean satisfied = base != null && base.confidence().isAboveThreshold(minConfidence);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
