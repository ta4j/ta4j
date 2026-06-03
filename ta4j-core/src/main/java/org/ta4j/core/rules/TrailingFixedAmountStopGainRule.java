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
 * @since 0.22.3
 */
public class TrailingFixedAmountStopGainRule extends AbstractRule implements StopGainPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** The gain distance as an absolute amount. */
    private final Num gainAmount;
    private final transient HighestValueIndicator highestPriceWithMaxLookback;
    private final transient LowestValueIndicator lowestPriceWithMaxLookback;

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
        this.highestPriceWithMaxLookback = new HighestValueIndicator(priceIndicator, barCount);
        this.lowestPriceWithMaxLookback = new LowestValueIndicator(priceIndicator, barCount);
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
        if (lookback <= 0) {
            StopRuleTrace.traceUnavailable(this, index, "indexBeforeEntry");
            return false;
        }
        boolean buy = currentPosition.getEntry().isBuy();
        Num extremePrice;
        Num activationPrice;
        Num stopPrice;
        boolean activationReached;
        boolean satisfied;
        String extremeField;
        if (buy) {
            extremePrice = highestClosePrice(index, lookback);
            activationPrice = StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, true);
            activationReached = !extremePrice.isLessThan(activationPrice);
            stopPrice = StopGainRule.trailingStopGainPriceFromDistance(extremePrice, gainAmount, true);
            satisfied = activationReached && currentPrice.isLessThanOrEqual(stopPrice);
            extremeField = "highestPrice";
        } else {
            extremePrice = lowestClosePrice(index, lookback);
            activationPrice = StopGainRule.stopGainPriceFromDistance(entryPrice, gainAmount, false);
            activationReached = !extremePrice.isGreaterThan(activationPrice);
            stopPrice = StopGainRule.trailingStopGainPriceFromDistance(extremePrice, gainAmount, false);
            satisfied = activationReached && currentPrice.isGreaterThanOrEqual(stopPrice);
            extremeField = "lowestPrice";
        }
        String reason = traceReason(satisfied, activationReached, buy);
        StopRuleTrace.traceTrailingGainDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice,
                extremeField, extremePrice, activationPrice, lookback, "gainAmount", gainAmount, reason);
        return satisfied;
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
        int entryIndex = position.getEntry().getIndex();
        // stopPrice models the initial trailing stop at entry time.
        int lookback = 1;
        if (position.getEntry().isBuy()) {
            Num highestCloseNum = highestClosePrice(entryIndex, lookback);
            return StopGainRule.trailingStopGainPriceFromDistance(highestCloseNum, gainAmount, true);
        }
        Num lowestCloseNum = lowestClosePrice(entryIndex, lookback);
        return StopGainRule.trailingStopGainPriceFromDistance(lowestCloseNum, gainAmount, false);
    }

    /**
     * Returns the highest observed close for the provided lookback window.
     *
     * <p>
     * The max-lookback indicator is cached and reused; shorter warm-up lookbacks
     * are computed with a temporary indicator.
     *
     * @param index    current bar index
     * @param lookback lookback window size
     * @return highest close value in the window
     */
    private Num highestClosePrice(int index, int lookback) {
        if (lookback == barCount) {
            return highestPriceWithMaxLookback.getValue(index);
        }
        return new HighestValueIndicator(priceIndicator, lookback).getValue(index);
    }

    /**
     * Returns the lowest observed close for the provided lookback window.
     *
     * <p>
     * The max-lookback indicator is cached and reused; shorter warm-up lookbacks
     * are computed with a temporary indicator.
     *
     * @param index    current bar index
     * @param lookback lookback window size
     * @return lowest close value in the window
     */
    private Num lowestClosePrice(int index, int lookback) {
        if (lookback == barCount) {
            return lowestPriceWithMaxLookback.getValue(index);
        }
        return new LowestValueIndicator(priceIndicator, lookback).getValue(index);
    }

    /**
     * Returns effective lookback length between entry bar and current bar.
     *
     * @param index         current bar index
     * @param positionIndex position entry index
     * @return lookback length constrained by {@link #barCount}
     */
    private int getValueIndicatorBarCount(int index, int positionIndex) {
        return Math.min(index - positionIndex + 1, this.barCount);
    }

    private static String traceReason(boolean satisfied, boolean activationReached, boolean buy) {
        if (satisfied) {
            return "stopReached";
        }
        if (!activationReached) {
            return "activationNotReached";
        }
        return buy ? "priceAboveStop" : "priceBelowStop";
    }
}
