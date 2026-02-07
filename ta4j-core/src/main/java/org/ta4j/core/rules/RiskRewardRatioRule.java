/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * A rule that checks if a price/stop/target configuration meets a minimum
 * risk/reward ratio.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class RiskRewardRatioRule extends AbstractRule {

    private final Indicator<Num> priceIndicator;
    private final Indicator<Num> stopIndicator;
    private final Indicator<Num> targetIndicator;
    private final boolean bullish;
    private final Num minRiskReward;

    /**
     * Constructor.
     *
     * @param priceIndicator  reference price indicator
     * @param stopIndicator   stop price indicator
     * @param targetIndicator target price indicator
     * @param bullish         {@code true} for bullish evaluation, {@code false} for
     *                        bearish
     * @param minRiskReward   minimum acceptable risk/reward ratio
     */
    public RiskRewardRatioRule(final Indicator<Num> priceIndicator, final Indicator<Num> stopIndicator,
            final Indicator<Num> targetIndicator, final boolean bullish, final Number minRiskReward) {
        this(priceIndicator, stopIndicator, targetIndicator, bullish,
                priceIndicator.getBarSeries().numFactory().numOf(minRiskReward));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator  reference price indicator
     * @param stopIndicator   stop price indicator
     * @param targetIndicator target price indicator
     * @param bullish         {@code true} for bullish evaluation, {@code false} for
     *                        bearish
     * @param minRiskReward   minimum acceptable risk/reward ratio
     */
    public RiskRewardRatioRule(final Indicator<Num> priceIndicator, final Indicator<Num> stopIndicator,
            final Indicator<Num> targetIndicator, final boolean bullish, final Num minRiskReward) {
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        this.stopIndicator = Objects.requireNonNull(stopIndicator, "stopIndicator");
        this.targetIndicator = Objects.requireNonNull(targetIndicator, "targetIndicator");
        this.minRiskReward = Objects.requireNonNull(minRiskReward, "minRiskReward");
        this.bullish = bullish;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        Num price = priceIndicator.getValue(index);
        Num stop = stopIndicator.getValue(index);
        Num target = targetIndicator.getValue(index);
        if (Num.isNaNOrNull(price) || Num.isNaNOrNull(stop) || Num.isNaNOrNull(target)) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num risk;
        Num reward;
        if (bullish) {
            if (!price.isGreaterThan(stop) || !target.isGreaterThan(price)) {
                traceIsSatisfied(index, false);
                return false;
            }
            risk = price.minus(stop);
            reward = target.minus(price);
        } else {
            if (!stop.isGreaterThan(price) || !price.isGreaterThan(target)) {
                traceIsSatisfied(index, false);
                return false;
            }
            risk = stop.minus(price);
            reward = price.minus(target);
        }

        if (risk.isLessThanOrEqual(price.getNumFactory().zero())) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num rr = reward.dividedBy(risk);
        boolean satisfied = rr.isGreaterThanOrEqual(minRiskReward);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
