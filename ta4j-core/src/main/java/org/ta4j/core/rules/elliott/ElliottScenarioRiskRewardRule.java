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
 * Checks that the base scenario offers a minimum risk/reward ratio.
 *
 * @since 0.22.2
 */
public class ElliottScenarioRiskRewardRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Indicator<Num> priceIndicator;
    private final boolean bullish;
    private final Num minRiskReward;

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
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.minRiskReward = Objects.requireNonNull(minRiskReward, "minRiskReward");
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
        Num target = ElliottScenarioRuleSupport.selectTarget(base, bullish);
        if (Num.isNaNOrNull(stop) || Num.isNaNOrNull(target)) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num risk;
        Num reward;
        if (bullish) {
            if (!closePrice.isGreaterThan(stop) || !target.isGreaterThan(closePrice)) {
                traceIsSatisfied(index, false);
                return false;
            }
            risk = closePrice.minus(stop);
            reward = target.minus(closePrice);
        } else {
            if (!stop.isGreaterThan(closePrice) || !closePrice.isGreaterThan(target)) {
                traceIsSatisfied(index, false);
                return false;
            }
            risk = stop.minus(closePrice);
            reward = closePrice.minus(target);
        }

        if (risk.isLessThanOrEqual(closePrice.getNumFactory().zero())) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num rr = reward.dividedBy(risk);
        boolean satisfied = rr.isGreaterThanOrEqual(minRiskReward);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
