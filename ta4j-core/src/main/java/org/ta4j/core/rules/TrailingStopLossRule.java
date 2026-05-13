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
 * A trailing stop-loss rule.
 *
 * <p>
 * Satisfied when the price reaches the trailing loss threshold.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class TrailingStopLossRule extends AbstractRule implements StopLossPriceModel {

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
        if (tradingRecord == null) {
            StopRuleTrace.traceUnavailable(this, index, "noTradingRecord");
            return false;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        if (!currentPosition.isOpened()) {
            StopRuleTrace.traceUnavailable(this, index, "noOpenPosition");
            return false;
        }

        Num entryPrice = currentPosition.getEntry().getNetPrice();
        Num currentPrice = priceIndicator.getValue(index);
        int positionIndex = currentPosition.getEntry().getIndex();
        int lookback = getValueIndicatorBarCount(index, positionIndex);
        boolean buy = currentPosition.getEntry().isBuy();
        Num extremePrice;
        Num stopPrice;
        boolean satisfied;
        String extremeField;
        if (buy) {
            extremePrice = new HighestValueIndicator(priceIndicator, lookback).getValue(index);
            stopPrice = StopLossRule.stopLossPrice(extremePrice, lossPercentage, true);
            satisfied = currentPrice.isLessThanOrEqual(stopPrice);
            extremeField = "highestPrice";
        } else {
            extremePrice = new LowestValueIndicator(priceIndicator, lookback).getValue(index);
            stopPrice = StopLossRule.stopLossPrice(extremePrice, lossPercentage, false);
            satisfied = currentPrice.isGreaterThanOrEqual(stopPrice);
            extremeField = "lowestPrice";
        }
        String reason = satisfied ? "stopReached" : buy ? "priceAboveStop" : "priceBelowStop";
        StopRuleTrace.traceTrailingDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice,
                extremeField, extremePrice, lookback, "lossPercentage", lossPercentage, reason);
        return satisfied;
    }

    /**
     * Returns the stop-loss price for the supplied position entry.
     *
     * @param series   the price series
     * @param position the position being evaluated
     * @return the stop-loss price, or {@code null} if unavailable
     * @since 0.22.3
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        int entryIndex = position.getEntry().getIndex();
        int barCount = getValueIndicatorBarCount(entryIndex, entryIndex);
        if (position.getEntry().isBuy()) {
            HighestValueIndicator highest = new HighestValueIndicator(priceIndicator, barCount);
            Num highestCloseNum = highest.getValue(entryIndex);
            return StopLossRule.stopLossPrice(highestCloseNum, lossPercentage, true);
        }
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator, barCount);
        Num lowestCloseNum = lowest.getValue(entryIndex);
        return StopLossRule.stopLossPrice(lowestCloseNum, lossPercentage, false);
    }

    private int getValueIndicatorBarCount(int index, int positionIndex) {
        return Math.min(index - positionIndex + 1, this.barCount);
    }

}
