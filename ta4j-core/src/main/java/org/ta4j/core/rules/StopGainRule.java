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
 * A stop-gain rule.
 *
 * <p>
 * Satisfied when the close price reaches the gain threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class StopGainRule extends AbstractRule implements StopGainPriceModel {

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
    }

    /**
     * Computes the stop-gain price from the entry price and gain percentage.
     *
     * @param entryPrice     the entry price
     * @param gainPercentage the gain percentage
     * @param isBuy          true for long positions, false for short positions
     * @return the stop-gain price
     * @since 0.22.2
     */
    public static Num stopGainPrice(Num entryPrice, Num gainPercentage, boolean isBuy) {
        if (entryPrice == null) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (gainPercentage == null) {
            throw new IllegalArgumentException("gainPercentage must not be null");
        }
        var hundred = entryPrice.getNumFactory().hundred();
        var gainRatioThreshold = isBuy ? hundred.plus(gainPercentage).dividedBy(hundred)
                : hundred.minus(gainPercentage).dividedBy(hundred);
        return entryPrice.multipliedBy(gainRatioThreshold);
    }

    /**
     * Computes the stop-gain price from the entry price and an absolute gain
     * distance.
     *
     * @param entryPrice   the entry price
     * @param gainDistance the absolute price distance to the gain target
     * @param isBuy        true for long positions, false for short positions
     * @return the stop-gain price
     * @since 0.22.2
     */
    public static Num stopGainPriceFromDistance(Num entryPrice, Num gainDistance, boolean isBuy) {
        if (entryPrice == null) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (gainDistance == null) {
            throw new IllegalArgumentException("gainDistance must not be null");
        }
        return isBuy ? entryPrice.plus(gainDistance) : entryPrice.minus(gainDistance);
    }

    /**
     * Returns the stop-gain price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-gain price, or {@code null} if unavailable
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
        return stopGainPrice(entryPrice, gainPercentage, position.getEntry().isBuy());
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
        var threshold = stopGainPrice(entryPrice, gainPercentage, true);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }

    private boolean isSellGainSatisfied(Num entryPrice, Num currentPrice) {
        var threshold = stopGainPrice(entryPrice, gainPercentage, false);
        return currentPrice.isLessThanOrEqual(threshold);
    }
}
