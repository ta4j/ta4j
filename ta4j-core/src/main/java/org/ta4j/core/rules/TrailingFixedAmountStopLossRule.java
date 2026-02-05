/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule using a fixed absolute amount.
 *
 * <p>
 * The stop-loss distance is a fixed price amount (flat-dollar trailing stop).
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class TrailingFixedAmountStopLossRule extends AbstractRule implements StopLossPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** The loss distance as an absolute amount. */
    private final Num lossAmount;

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     * @param barCount   the number of bars to look back for the calculation
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Num lossAmount, int barCount) {
        if (indicator == null) {
            throw new IllegalArgumentException("indicator must not be null");
        }
        if (Num.isNaNOrNull(lossAmount) || lossAmount.isZero() || lossAmount.isNegative()) {
            throw new IllegalArgumentException("lossAmount must be positive");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.priceIndicator = indicator;
        this.barCount = barCount;
        this.lossAmount = lossAmount;
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     * @param barCount   the number of bars to look back for the calculation
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Number lossAmount, int barCount) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(lossAmount), barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Num lossAmount) {
        this(indicator, lossAmount, Integer.MAX_VALUE);
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Number lossAmount) {
        this(indicator, lossAmount, Integer.MAX_VALUE);
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
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
        Num currentStopLossLimitActivation = StopLossRule.stopLossPriceFromDistance(highestCloseNum, lossAmount, true);
        return currentPrice.isLessThanOrEqual(currentStopLossLimitActivation);
    }

    private boolean isSellSatisfied(Num currentPrice, int index, int positionIndex) {
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, positionIndex));
        Num lowestCloseNum = lowest.getValue(index);
        Num currentStopLossLimitActivation = StopLossRule.stopLossPriceFromDistance(lowestCloseNum, lossAmount, false);
        return currentPrice.isGreaterThanOrEqual(currentStopLossLimitActivation);
    }

    /**
     * Returns the stop-loss price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if unavailable
     * @since 0.22.2
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        int entryIndex = position.getEntry().getIndex();
        int lookback = Math.min(1, barCount);
        if (position.getEntry().isBuy()) {
            HighestValueIndicator highest = new HighestValueIndicator(priceIndicator, lookback);
            Num highestCloseNum = highest.getValue(entryIndex);
            return StopLossRule.stopLossPriceFromDistance(highestCloseNum, lossAmount, true);
        }
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator, lookback);
        Num lowestCloseNum = lowest.getValue(entryIndex);
        return StopLossRule.stopLossPriceFromDistance(lowestCloseNum, lossAmount, false);
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
