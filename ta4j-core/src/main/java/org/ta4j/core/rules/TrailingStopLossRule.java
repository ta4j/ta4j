/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule.
 *
 * <p>
 * Satisfied when the price reaches the trailing loss threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class TrailingStopLossRule extends AbstractRule {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** the loss-distance as percentage. */
    private final Num lossPercentage;

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param lossPercentage the loss percentage
     * @param barCount       the number of bars to look back for the calculation
     */
    public TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage, int barCount) {
        this.priceIndicator = indicator;
        this.barCount = barCount;
        this.lossPercentage = lossPercentage;
    }

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param lossPercentage the loss percentage
     */
    public TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage) {
        this(indicator, lossPercentage, Integer.MAX_VALUE);
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {
                Num currentPrice = priceIndicator.getValue(index);
                int positionIndex = currentPosition.getEntry().getIndex();

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuySatisfied(currentPrice, index, positionIndex);
                } else {
                    satisfied = isSellSatisfied(currentPrice, index, positionIndex);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuySatisfied(Num currentPrice, int index, int positionIndex) {
        HighestValueIndicator highest = new HighestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, positionIndex));
        Num highestCloseNum = highest.getValue(index);
        final var hundred = highestCloseNum.getNumFactory().hundred();
        Num lossRatioThreshold = hundred.minus(lossPercentage).dividedBy(hundred);
        Num currentStopLossLimitActivation = highestCloseNum.multipliedBy(lossRatioThreshold);
        return currentPrice.isLessThanOrEqual(currentStopLossLimitActivation);
    }

    private boolean isSellSatisfied(Num currentPrice, int index, int positionIndex) {
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, positionIndex));
        Num lowestCloseNum = lowest.getValue(index);
        final var hundred = lowestCloseNum.getNumFactory().hundred();
        Num lossRatioThreshold = hundred.plus(lossPercentage).dividedBy(hundred);
        Num currentStopLossLimitActivation = lowestCloseNum.multipliedBy(lossRatioThreshold);
        return currentPrice.isGreaterThanOrEqual(currentStopLossLimitActivation);
    }

    private int getValueIndicatorBarCount(int index, int positionIndex) {
        return Math.min(index - positionIndex + 1, this.barCount);
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}", getTraceDisplayName(), index, isSatisfied,
                    priceIndicator.getValue(index));
        }
    }
}
