/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks that the aggregated trend bias matches a direction and strength.
 *
 * @since 0.22.2
 */
public class ElliottTrendBiasRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final boolean bullish;
    private final double minStrength;

    /**
     * Creates a trend bias rule.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param bullish           {@code true} for bullish bias, {@code false} for
     *                          bearish
     * @param minStrength       minimum bias strength threshold (0.0-1.0)
     * @since 0.22.2
     */
    public ElliottTrendBiasRule(final Indicator<ElliottScenarioSet> scenarioIndicator, final boolean bullish,
            final double minStrength) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (minStrength < 0.0 || minStrength > 1.0) {
            throw new IllegalArgumentException("minStrength must be in [0.0, 1.0]");
        }
        this.bullish = bullish;
        this.minStrength = minStrength;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(index);
        if (scenarioSet == null) {
            traceIsSatisfied(index, false);
            return false;
        }
        ElliottTrendBias bias = scenarioSet.trendBias(minStrength);
        boolean satisfied = !bias.isUnknown() && !bias.isNeutral() && (bullish ? bias.isBullish() : bias.isBearish());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
