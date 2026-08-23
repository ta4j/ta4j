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
 * @since 0.22.3
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
        this(validatedConfig(indicator, lossAmount, barCount));
    }

    private TrailingFixedAmountStopLossRule(Config config) {
        this.priceIndicator = config.priceIndicator();
        this.barCount = config.barCount();
        this.lossAmount = config.lossAmount();
    }

    private static Config validatedConfig(Indicator<Num> indicator, Num lossAmount, int barCount) {
        if (indicator == null) {
            throw new IllegalArgumentException("indicator must not be null");
        }
        if (Num.isNaNOrNull(lossAmount) || lossAmount.isZero() || lossAmount.isNegative()) {
            throw new IllegalArgumentException("lossAmount must be positive");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        return new Config(indicator, lossAmount, barCount);
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     * @param barCount   the number of bars to look back for the calculation
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Number lossAmount, int barCount) {
        this(validatedConfig(indicator, toNumLossAmount(indicator, lossAmount), barCount));
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Num lossAmount) {
        this(validatedConfig(indicator, lossAmount, Integer.MAX_VALUE));
    }

    /**
     * Constructor.
     *
     * @param indicator  the (close price) indicator
     * @param lossAmount the absolute loss amount
     */
    public TrailingFixedAmountStopLossRule(Indicator<Num> indicator, Number lossAmount) {
        this(validatedConfig(indicator, toNumLossAmount(indicator, lossAmount), Integer.MAX_VALUE));
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
        Num extremePrice = buy ? new HighestValueIndicator(priceIndicator, lookback).getValue(index)
                : new LowestValueIndicator(priceIndicator, lookback).getValue(index);
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice) || Num.isNaNOrNull(extremePrice)) {
            StopRuleTrace.traceDecision(this, index, false, buy, currentPrice, entryPrice, null, "lossAmount",
                    lossAmount, "priceUnavailable");
            return false;
        }
        Num stopPrice = StopLossRule.stopLossPriceFromDistance(extremePrice, lossAmount, buy);
        boolean satisfied = buy ? currentPrice.isLessThanOrEqual(stopPrice)
                : currentPrice.isGreaterThanOrEqual(stopPrice);
        String extremeField = buy ? "highestPrice" : "lowestPrice";
        String reason = satisfied ? "stopReached" : buy ? "priceAboveStop" : "priceBelowStop";
        StopRuleTrace.traceTrailingDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice,
                extremeField, extremePrice, lookback, "lossAmount", lossAmount, reason);
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
        // stopPrice models the initial trailing stop at entry time.
        int lookback = 1;
        Num extremePrice = position.getEntry().isBuy()
                ? new HighestValueIndicator(priceIndicator, lookback).getValue(entryIndex)
                : new LowestValueIndicator(priceIndicator, lookback).getValue(entryIndex);
        if (Num.isNaNOrNull(extremePrice)) {
            return null;
        }
        return StopLossRule.stopLossPriceFromDistance(extremePrice, lossAmount, position.getEntry().isBuy());
    }

    private int getValueIndicatorBarCount(int index, int positionIndex) {
        return Math.min(index - positionIndex + 1, this.barCount);
    }

    private static Num toNumLossAmount(Indicator<Num> indicator, Number lossAmount) {
        if (indicator == null) {
            throw new IllegalArgumentException("indicator must not be null");
        }
        if (lossAmount == null) {
            throw new IllegalArgumentException("lossAmount must be positive");
        }
        return indicator.getBarSeries().numFactory().numOf(lossAmount);
    }

    private record Config(Indicator<Num> priceIndicator, Num lossAmount, int barCount) {
    }
}
