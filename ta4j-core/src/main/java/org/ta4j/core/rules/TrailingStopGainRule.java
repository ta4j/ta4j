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
 * A trailing stop-gain rule.
 *
 * <p>
 * This rule trails the most favorable price by a percentage and triggers when
 * price retraces by that amount (a trailing take-profit).
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.3
 */
public class TrailingStopGainRule extends AbstractRule implements StopGainPriceModel {

    /** The price indicator. */
    private final Indicator<Num> priceIndicator;

    /** The barCount. */
    private final int barCount;

    /** the gain-distance as percentage. */
    private final Num gainPercentage;

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param gainPercentage the gain percentage
     * @param barCount       the number of bars to look back for the calculation
     */
    public TrailingStopGainRule(Indicator<Num> indicator, Num gainPercentage, int barCount) {
        this.priceIndicator = Objects.requireNonNull(indicator, "priceIndicator");
        if (Num.isNaNOrNull(gainPercentage) || gainPercentage.isNegative()) {
            throw new IllegalArgumentException("gainPercentage must be >= 0");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.barCount = barCount;
        this.gainPercentage = gainPercentage;
    }

    /**
     * Constructor.
     *
     * @param indicator      the (close price) indicator
     * @param gainPercentage the gain percentage
     */
    public TrailingStopGainRule(Indicator<Num> indicator, Num gainPercentage) {
        this(indicator, gainPercentage, Integer.MAX_VALUE);
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord == null) {
            StopRuleTrace.traceUnavailable(this, index, "noTradingRecord");
            return false;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition == null || !currentPosition.isOpened() || currentPosition.getEntry() == null) {
            StopRuleTrace.traceUnavailable(this, index, "noOpenPosition");
            return false;
        }

        Num entryPrice = currentPosition.getEntry().getNetPrice();
        Num currentPrice = priceIndicator.getValue(index);
        int positionIndex = currentPosition.getEntry().getIndex();
        if (index < positionIndex) {
            StopRuleTrace.traceUnavailable(this, index, "indexBeforeEntry");
            return false;
        }
        boolean buy = currentPosition.getEntry().isBuy();
        int windowStartIndex = windowStartIndex(index, positionIndex);
        int lookback = index - windowStartIndex + 1;
        String extremeField = buy ? "highestPrice" : "lowestPrice";
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice)) {
            StopRuleTrace.traceTrailingGainDecision(this, index, false, buy, currentPrice, entryPrice, null,
                    extremeField, null, null, lookback, "gainPercentage", gainPercentage, "priceUnavailable");
            return false;
        }

        Num extremePrice;
        Num activationPrice;
        Num stopPrice;
        boolean activationReached;
        boolean satisfied;
        if (buy) {
            extremePrice = highestValue(windowStartIndex, index);
            if (Num.isNaNOrNull(extremePrice)) {
                StopRuleTrace.traceTrailingGainDecision(this, index, false, true, currentPrice, entryPrice, null,
                        extremeField, extremePrice, null, lookback, "gainPercentage", gainPercentage,
                        "extremePriceUnavailable");
                return false;
            }
            activationPrice = StopGainRule.stopGainPrice(entryPrice, gainPercentage, true);
            activationReached = !extremePrice.isLessThan(activationPrice);
            stopPrice = StopGainRule.trailingStopGainPrice(extremePrice, gainPercentage, true);
            satisfied = activationReached && currentPrice.isLessThanOrEqual(stopPrice);
        } else {
            extremePrice = lowestValue(windowStartIndex, index);
            if (Num.isNaNOrNull(extremePrice)) {
                StopRuleTrace.traceTrailingGainDecision(this, index, false, false, currentPrice, entryPrice, null,
                        extremeField, extremePrice, null, lookback, "gainPercentage", gainPercentage,
                        "extremePriceUnavailable");
                return false;
            }
            activationPrice = StopGainRule.stopGainPrice(entryPrice, gainPercentage, false);
            activationReached = !extremePrice.isGreaterThan(activationPrice);
            stopPrice = StopGainRule.trailingStopGainPrice(extremePrice, gainPercentage, false);
            satisfied = activationReached && currentPrice.isGreaterThanOrEqual(stopPrice);
        }
        String reason = traceReason(satisfied, activationReached, buy);
        StopRuleTrace.traceTrailingGainDecision(this, index, satisfied, buy, currentPrice, entryPrice, stopPrice,
                extremeField, extremePrice, activationPrice, lookback, "gainPercentage", gainPercentage, reason);
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
        // If the entry bar was evicted by max-bar-count retention, fall back to
        // the first retained bar as the anchor (an approximation of entry-time stop).
        int entryIndex = retainedStartIndex(position.getEntry().getIndex());
        if (position.getEntry().isBuy()) {
            Num highestCloseNum = priceIndicator.getValue(entryIndex);
            if (Num.isNaNOrNull(highestCloseNum)) {
                return null;
            }
            return StopGainRule.trailingStopGainPrice(highestCloseNum, gainPercentage, true);
        }
        Num lowestCloseNum = priceIndicator.getValue(entryIndex);
        if (Num.isNaNOrNull(lowestCloseNum)) {
            return null;
        }
        return StopGainRule.trailingStopGainPrice(lowestCloseNum, gainPercentage, false);
    }

    private int windowStartIndex(int index, int positionIndex) {
        int activeBarCount = Math.min(index - positionIndex + 1, barCount);
        return retainedStartIndex(index - activeBarCount + 1);
    }

    private int retainedStartIndex(int requestedStartIndex) {
        return Math.max(requestedStartIndex, priceIndicator.getBarSeries().getBeginIndex());
    }

    private Num highestValue(int startIndex, int endIndex) {
        Num highest = null;
        for (int index = startIndex; index <= endIndex; index++) {
            Num candidate = priceIndicator.getValue(index);
            if (!Num.isNaNOrNull(candidate) && (highest == null || candidate.isGreaterThan(highest))) {
                highest = candidate;
            }
        }
        return highest;
    }

    private Num lowestValue(int startIndex, int endIndex) {
        Num lowest = null;
        for (int index = startIndex; index <= endIndex; index++) {
            Num candidate = priceIndicator.getValue(index);
            if (!Num.isNaNOrNull(candidate) && (lowest == null || candidate.isLessThan(lowest))) {
                lowest = candidate;
            }
        }
        return lowest;
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
