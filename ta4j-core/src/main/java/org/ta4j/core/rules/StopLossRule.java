/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
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
public class StopLossRule extends AbstractRule implements StopLossPriceModel {

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
    }

    /**
     * Computes the stop-loss price from the entry price and loss percentage.
     *
     * @param entryPrice     the entry price
     * @param lossPercentage the loss percentage
     * @param isBuy          true for long positions, false for short positions
     * @return the stop-loss price
     * @since 0.22.2
     */
    public static Num stopLossPrice(Num entryPrice, Num lossPercentage, boolean isBuy) {
        if (entryPrice == null) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (lossPercentage == null) {
            throw new IllegalArgumentException("lossPercentage must not be null");
        }
        var hundred = entryPrice.getNumFactory().hundred();
        var lossRatioThreshold = isBuy ? hundred.minus(lossPercentage).dividedBy(hundred)
                : hundred.plus(lossPercentage).dividedBy(hundred);
        return entryPrice.multipliedBy(lossRatioThreshold);
    }

    /**
     * Computes the stop-loss price from the entry price and an absolute loss
     * distance.
     *
     * @param entryPrice   the entry price
     * @param lossDistance the absolute price distance to the stop
     * @param isBuy        true for long positions, false for short positions
     * @return the stop-loss price
     * @since 0.22.2
     */
    public static Num stopLossPriceFromDistance(Num entryPrice, Num lossDistance, boolean isBuy) {
        if (entryPrice == null) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (lossDistance == null) {
            throw new IllegalArgumentException("lossDistance must not be null");
        }
        return isBuy ? entryPrice.minus(lossDistance) : entryPrice.plus(lossDistance);
    }

    /**
     * Returns the stop-loss price for the supplied position entry.
     *
     * @param series   the price series (unused in this implementation; required by
     *                 {@link StopLossPriceModel})
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if unavailable
     * @since 0.22.2
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        Num entryPrice = position.getEntry().getNetPrice();
        if (Num.isNaNOrNull(entryPrice)) {
            return null;
        }
        return stopLossPrice(entryPrice, lossPercentage, position.getEntry().isBuy());
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

    /**
     * Checks stop-loss trigger condition for a long position.
     *
     * @param entryPrice   entry price
     * @param currentPrice current price
     * @return {@code true} when current price reaches long stop-loss threshold
     */
    private boolean isBuyStopSatisfied(Num entryPrice, Num currentPrice) {
        var threshold = stopLossPrice(entryPrice, lossPercentage, true);
        return currentPrice.isLessThanOrEqual(threshold);
    }

    /**
     * Checks stop-loss trigger condition for a short position.
     *
     * @param entryPrice   entry price
     * @param currentPrice current price
     * @return {@code true} when current price reaches short stop-loss threshold
     */
    private boolean isSellStopSatisfied(Num entryPrice, Num currentPrice) {
        var threshold = stopLossPrice(entryPrice, lossPercentage, false);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }
}
