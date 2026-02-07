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
import org.ta4j.core.rules.RiskRewardRatioRule;

/**
 * Checks that the base scenario offers a minimum risk/reward ratio.
 *
 * @since 0.22.2
 */
public class ElliottScenarioRiskRewardRule extends AbstractRule {

    private final Rule riskRewardRule;

    /**
     * Creates a risk/reward rule using a numeric threshold.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @param bullish           {@code true} for bullish evaluation, {@code false}
     *                          for bearish
     * @param minRiskReward     minimum acceptable risk/reward ratio
     * @since 0.22.2
     */
    public ElliottScenarioRiskRewardRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator, final boolean bullish, final Number minRiskReward) {
        this(scenarioIndicator, priceIndicator, bullish,
                priceIndicator.getBarSeries().numFactory().numOf(minRiskReward));
    }

    /**
     * Creates a risk/reward rule using a Num threshold.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param priceIndicator    reference price indicator
     * @param bullish           {@code true} for bullish evaluation, {@code false}
     *                          for bearish
     * @param minRiskReward     minimum acceptable risk/reward ratio
     * @since 0.22.2
     */
    public ElliottScenarioRiskRewardRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Indicator<Num> priceIndicator, final boolean bullish, final Num minRiskReward) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        Objects.requireNonNull(priceIndicator, "priceIndicator");
        Objects.requireNonNull(minRiskReward, "minRiskReward");
        Indicator<Num> stopIndicator = ElliottScenarioRuleSupport.stopIndicator(scenarioIndicator);
        Indicator<Num> targetIndicator = ElliottScenarioRuleSupport.targetIndicator(scenarioIndicator, bullish);
        this.riskRewardRule = new RiskRewardRatioRule(priceIndicator, stopIndicator, targetIndicator, bullish,
                minRiskReward);
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        boolean satisfied = riskRewardRule.isSatisfied(index, tradingRecord);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
