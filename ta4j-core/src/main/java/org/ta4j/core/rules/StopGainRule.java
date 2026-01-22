/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * A stop-gain rule.
 *
 * <p>
 * Satisfied when the close price reaches the gain threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class StopGainRule extends AbstractRule {

    /** The constant value for 100. */
    private final Num HUNDRED;

    /** The reference price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The gain percentage. */
    private final Num gainPercentage;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule(Indicator<Num> priceIndicator, Number gainPercentage) {
        this(priceIndicator, priceIndicator.getBarSeries().numFactory().numOf(gainPercentage));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule(Indicator<Num> priceIndicator, Num gainPercentage) {
        this.priceIndicator = priceIndicator;
        this.gainPercentage = gainPercentage;
        HUNDRED = priceIndicator.getBarSeries().numFactory().hundred();
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        var satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {

                var entryPrice = currentPosition.getEntry().getNetPrice();
                var currentPrice = priceIndicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyGainSatisfied(entryPrice, currentPrice);
                } else {
                    satisfied = isSellGainSatisfied(entryPrice, currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuyGainSatisfied(Num entryPrice, Num currentPrice) {
        var lossRatioThreshold = HUNDRED.plus(gainPercentage).dividedBy(HUNDRED);
        var threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }

    private boolean isSellGainSatisfied(Num entryPrice, Num currentPrice) {
        var lossRatioThreshold = HUNDRED.minus(gainPercentage).dividedBy(HUNDRED);
        var threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isLessThanOrEqual(threshold);
    }
}
