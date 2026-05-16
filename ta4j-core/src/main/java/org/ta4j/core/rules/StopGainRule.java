/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * A stop-gain rule.
 *
 * <p>
 * Satisfied when the configured price indicator touches or moves beyond the
 * gain threshold. Long positions trigger at or above the target price; short
 * positions trigger at or below it.
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
        this(priceIndicator, numOf(priceIndicator, gainPercentage, "gainPercentage"));
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule(Indicator<Num> priceIndicator, Num gainPercentage) {
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        if (Num.isNaNOrNull(gainPercentage) || gainPercentage.isNegative()) {
            throw new IllegalArgumentException("gainPercentage must be >= 0");
        }
        this.gainPercentage = gainPercentage;
    }

    /**
     * Computes the stop-gain price from the entry price and gain percentage.
     *
     * @param entryPrice     the entry price
     * @param gainPercentage the gain percentage
     * @param isBuy          true for long positions, false for short positions
     * @return the stop-gain price
     * @since 0.22.3
     */
    public static Num stopGainPrice(Num entryPrice, Num gainPercentage, boolean isBuy) {
        if (Num.isNaNOrNull(entryPrice)) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (Num.isNaNOrNull(gainPercentage) || gainPercentage.isNegative()) {
            throw new IllegalArgumentException("gainPercentage must be >= 0");
        }
        Num hundred = entryPrice.getNumFactory().hundred();
        Num gainRatioThreshold = isBuy ? hundred.plus(gainPercentage).dividedBy(hundred)
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
     * @since 0.22.3
     */
    public static Num stopGainPriceFromDistance(Num entryPrice, Num gainDistance, boolean isBuy) {
        if (Num.isNaNOrNull(entryPrice)) {
            throw new IllegalArgumentException("entryPrice must not be null");
        }
        if (Num.isNaNOrNull(gainDistance) || gainDistance.isNegative()) {
            throw new IllegalArgumentException("gainDistance must be >= 0");
        }
        return isBuy ? entryPrice.plus(gainDistance) : entryPrice.minus(gainDistance);
    }

    /**
     * Computes the trailing stop-gain retracement price from a favorable price and
     * retracement percentage.
     *
     * @param favorablePrice        the most favorable price reached so far
     * @param retracementPercentage the retracement percentage
     * @param isBuy                 true for long positions, false for short
     *                              positions
     * @return the trailing stop-gain retracement price
     * @since 0.22.3
     */
    public static Num trailingStopGainPrice(Num favorablePrice, Num retracementPercentage, boolean isBuy) {
        if (Num.isNaNOrNull(favorablePrice)) {
            throw new IllegalArgumentException("favorablePrice must not be null");
        }
        if (Num.isNaNOrNull(retracementPercentage) || retracementPercentage.isNegative()) {
            throw new IllegalArgumentException("retracementPercentage must be >= 0");
        }
        Num hundred = favorablePrice.getNumFactory().hundred();
        Num retracementRatioThreshold = isBuy ? hundred.minus(retracementPercentage).dividedBy(hundred)
                : hundred.plus(retracementPercentage).dividedBy(hundred);
        return favorablePrice.multipliedBy(retracementRatioThreshold);
    }

    /**
     * Computes the trailing stop-gain retracement price from a favorable price and
     * retracement distance.
     *
     * @param favorablePrice      the most favorable price reached so far
     * @param retracementDistance the retracement distance
     * @param isBuy               true for long positions, false for short positions
     * @return the trailing stop-gain retracement price
     * @since 0.22.3
     */
    public static Num trailingStopGainPriceFromDistance(Num favorablePrice, Num retracementDistance, boolean isBuy) {
        if (Num.isNaNOrNull(favorablePrice)) {
            throw new IllegalArgumentException("favorablePrice must not be null");
        }
        if (Num.isNaNOrNull(retracementDistance) || retracementDistance.isNegative()) {
            throw new IllegalArgumentException("retracementDistance must be >= 0");
        }
        return isBuy ? favorablePrice.minus(retracementDistance) : favorablePrice.plus(retracementDistance);
    }

    /**
     * Returns the stop-gain price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-gain price, or {@code null} if unavailable
     * @since 0.22.3
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
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition != null && currentPosition.isOpened() && currentPosition.getEntry() != null) {

                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = priceIndicator.getValue(index);

                if (!Num.isNaNOrNull(entryPrice) && !Num.isNaNOrNull(currentPrice)
                        && currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyGainSatisfied(entryPrice, currentPrice);
                } else if (!Num.isNaNOrNull(entryPrice) && !Num.isNaNOrNull(currentPrice)) {
                    satisfied = isSellGainSatisfied(entryPrice, currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuyGainSatisfied(Num entryPrice, Num currentPrice) {
        Num threshold = stopGainPrice(entryPrice, gainPercentage, true);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }

    private boolean isSellGainSatisfied(Num entryPrice, Num currentPrice) {
        Num threshold = stopGainPrice(entryPrice, gainPercentage, false);
        return currentPrice.isLessThanOrEqual(threshold);
    }

    private static Num numOf(Indicator<Num> priceIndicator, Number value, String parameterName) {
        Objects.requireNonNull(priceIndicator, "priceIndicator");
        Objects.requireNonNull(value, parameterName);
        return priceIndicator.getBarSeries().numFactory().numOf(value);
    }
}
