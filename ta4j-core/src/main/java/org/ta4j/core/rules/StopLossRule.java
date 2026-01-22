/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * A stop-loss rule.
 *
 * <p>
 * Satisfied when the close price reaches the loss threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class StopLossRule extends AbstractRule {

    /** The constant value for 100. */
    private final Num HUNDRED;

    /** The reference price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The loss percentage. */
    private final Num lossPercentage;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param lossPercentage the loss percentage
     */
    public StopLossRule(Indicator<Num> priceIndicator, Number lossPercentage) {
        this(priceIndicator, priceIndicator.getBarSeries().numFactory().numOf(lossPercentage));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param lossPercentage the loss percentage
     */
    public StopLossRule(Indicator<Num> priceIndicator, Num lossPercentage) {
        this.priceIndicator = priceIndicator;
        this.lossPercentage = lossPercentage;
        HUNDRED = priceIndicator.getBarSeries().numFactory().hundred();
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            var currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {

                var entryPrice = currentPosition.getEntry().getNetPrice();
                var currentPrice = priceIndicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyStopSatisfied(entryPrice, currentPrice);
                } else {
                    satisfied = isSellStopSatisfied(entryPrice, currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuyStopSatisfied(Num entryPrice, Num currentPrice) {
        var lossRatioThreshold = HUNDRED.minus(lossPercentage).dividedBy(HUNDRED);
        var threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isLessThanOrEqual(threshold);
    }

    private boolean isSellStopSatisfied(Num entryPrice, Num currentPrice) {
        var lossRatioThreshold = HUNDRED.plus(lossPercentage).dividedBy(HUNDRED);
        var threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }
}
