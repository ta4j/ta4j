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
 * A trailing stop-gain rule using a fixed absolute amount.
 *
 * <p>
 * The stop-gain distance is a fixed price amount (flat-dollar trailing
 * take-profit).
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class TrailingFixedAmountStopGainRule extends AbstractRule implements StopGainPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** The gain distance as an absolute amount. */
    private final Num gainAmount;

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param gainAmount the absolute gain amount
     * @param barCount   the number of bars to look back for the calculation
     */
    public TrailingFixedAmountStopGainRule(Indicator<Num> indicator, Num gainAmount, int barCount) {
        if (indicator == null) {
            throw new IllegalArgumentException("indicator must not be null");
        }
        if (Num.isNaNOrNull(gainAmount) || gainAmount.isZero() || gainAmount.isNegative()) {
            throw new IllegalArgumentException("gainAmount must be positive");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.priceIndicator = indicator;
        this.barCount = barCount;
        this.gainAmount = gainAmount;
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param gainAmount the absolute gain amount
     * @param barCount   the number of bars to look back for the calculation
     */
    public TrailingFixedAmountStopGainRule(Indicator<Num> indicator, Number gainAmount, int barCount) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(gainAmount), barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param gainAmount the absolute gain amount
     */
    public TrailingFixedAmountStopGainRule(Indicator<Num> indicator, Num gainAmount) {
        this(indicator, gainAmount, Integer.MAX_VALUE);
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param gainAmount the absolute gain amount
     */
    public TrailingFixedAmountStopGainRule(Indicator<Num> indicator, Number gainAmount) {
        this(indicator, gainAmount, Integer.MAX_VALUE);
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
        Num currentStopGainLimitActivation = StopLossRule.stopLossPriceFromDistance(highestCloseNum, gainAmount, true);
        return currentPrice.isLessThanOrEqual(currentStopGainLimitActivation);
    }

    private boolean isSellSatisfied(Num currentPrice, int index, int positionIndex) {
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, positionIndex));
        Num lowestCloseNum = lowest.getValue(index);
        Num currentStopGainLimitActivation = StopLossRule.stopLossPriceFromDistance(lowestCloseNum, gainAmount, false);
        return currentPrice.isGreaterThanOrEqual(currentStopGainLimitActivation);
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
        int entryIndex = position.getEntry().getIndex();
        int lookback = Math.min(1, barCount);
        if (position.getEntry().isBuy()) {
            HighestValueIndicator highest = new HighestValueIndicator(priceIndicator, lookback);
            Num highestCloseNum = highest.getValue(entryIndex);
            return StopLossRule.stopLossPriceFromDistance(highestCloseNum, gainAmount, true);
        }
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator, lookback);
        Num lowestCloseNum = lowest.getValue(entryIndex);
        return StopLossRule.stopLossPriceFromDistance(lowestCloseNum, gainAmount, false);
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
