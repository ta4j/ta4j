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
import org.ta4j.core.rules.OpenPositionDurationRule;

/**
 * Checks whether a trade has exceeded the maximum wave duration window.
 *
 * @since 0.22.2
 */
public class ElliottScenarioTimeStopRule extends AbstractRule {

    private final Rule timeStopRule;

    /**
     * Creates a time-stop rule.
     *
     * @param scenarioIndicator         indicator supplying scenario sets
     * @param maxWaveDurationMultiplier multiplier applied to wave 3 duration
     * @since 0.22.2
     */
    public ElliottScenarioTimeStopRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final double maxWaveDurationMultiplier) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (maxWaveDurationMultiplier <= 0.0) {
            throw new IllegalArgumentException("maxWaveDurationMultiplier must be positive");
        }
        Num multiplier = scenarioIndicator.getBarSeries().numFactory().numOf(maxWaveDurationMultiplier);
        Indicator<Num> maxBarsIndicator = ElliottScenarioRuleSupport.wave3DurationIndicator(scenarioIndicator,
                multiplier);
        this.timeStopRule = new OpenPositionDurationRule(maxBarsIndicator);
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        boolean satisfied = timeStopRule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
