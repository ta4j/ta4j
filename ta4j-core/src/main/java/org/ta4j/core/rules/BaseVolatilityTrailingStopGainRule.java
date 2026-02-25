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
 * Shared trailing stop-gain logic for volatility-based stop rules.
 *
 * <p>
 * This models a trailing take-profit: the stop price trails the most favorable
 * price by a volatility-scaled distance and triggers on retracement.
 *
 * @since 0.22.3
 */
abstract class BaseVolatilityTrailingStopGainRule extends AbstractRule implements StopGainPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopGainThreshold;
    private final int barCount;
    private final transient HighestValueIndicator highestReferencePriceWithMaxLookback;
    private final transient LowestValueIndicator lowestReferencePriceWithMaxLookback;

    /**
     * Constructor.
     *
     * @param referencePrice    reference price indicator
     * @param stopGainThreshold volatility-scaled stop-gain threshold indicator
     * @param barCount          maximum lookback for trailing reference calculation
     */
    protected BaseVolatilityTrailingStopGainRule(Indicator<Num> referencePrice, Indicator<Num> stopGainThreshold,
            int barCount) {
        if (referencePrice == null) {
            throw new IllegalArgumentException("referencePrice must not be null");
        }
        if (stopGainThreshold == null) {
            throw new IllegalArgumentException("stopGainThreshold must not be null");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.referencePrice = referencePrice;
        this.stopGainThreshold = stopGainThreshold;
        this.barCount = barCount;
        this.highestReferencePriceWithMaxLookback = new HighestValueIndicator(referencePrice, barCount);
        this.lowestReferencePriceWithMaxLookback = new LowestValueIndicator(referencePrice, barCount);
    }

    /**
     * Evaluates whether trailing stop-gain condition is satisfied for the current
     * open position.
     *
     * @param index         current bar index
     * @param tradingRecord trading record containing the open position
     * @return {@code true} when trailing stop-gain condition is satisfied
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord != null && !tradingRecord.isClosed()) {
            Position position = tradingRecord.getCurrentPosition();
            if (position.isOpened()) {
                Num entryPrice = position.getEntry().getNetPrice();
                Num currentPrice = referencePrice.getValue(index);
                Num threshold = stopGainThreshold.getValue(index);
                if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice) || Num.isNaNOrNull(threshold)) {
                    return false;
                }

                int barsSinceEntry = index - position.getEntry().getIndex() + 1;
                int lookback = Math.min(barsSinceEntry, barCount);

                if (position.getEntry().isBuy()) {
                    Num highestValue = highestReferencePrice(index, lookback);
                    Num gainActivationThreshold = StopGainRule.stopGainPriceFromDistance(entryPrice, threshold, true);
                    if (highestValue.isLessThan(gainActivationThreshold)) {
                        return false;
                    }
                    Num reference = entryPrice.max(highestValue);
                    Num thresholdPrice = StopGainRule.trailingStopGainPriceFromDistance(reference, threshold, true);
                    return currentPrice.isLessThanOrEqual(thresholdPrice);
                }
                Num lowestValue = lowestReferencePrice(index, lookback);
                Num gainActivationThreshold = StopGainRule.stopGainPriceFromDistance(entryPrice, threshold, false);
                if (lowestValue.isGreaterThan(gainActivationThreshold)) {
                    return false;
                }
                Num reference = entryPrice.min(lowestValue);
                Num thresholdPrice = StopGainRule.trailingStopGainPriceFromDistance(reference, threshold, false);
                return currentPrice.isGreaterThanOrEqual(thresholdPrice);
            }
        }
        return false;
    }

    /**
     * Returns the initial trailing stop-gain price at position entry.
     *
     * @param series   the bar series
     * @param position the position being evaluated
     * @return initial trailing stop-gain price, or {@code null} if unavailable
     */
    @Override
    public Num stopPrice(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return null;
        }
        int entryIndex = position.getEntry().getIndex();
        Num entryPrice = position.getEntry().getNetPrice();
        if (Num.isNaNOrNull(entryPrice)) {
            return null;
        }
        Num threshold = stopGainThreshold.getValue(entryIndex);
        if (Num.isNaNOrNull(threshold)) {
            return null;
        }

        // stopPrice models the initial trailing stop at entry time.
        int lookback = 1;
        if (position.getEntry().isBuy()) {
            Num reference = entryPrice.max(highestReferencePrice(entryIndex, lookback));
            return StopGainRule.trailingStopGainPriceFromDistance(reference, threshold, true);
        }
        Num reference = entryPrice.min(lowestReferencePrice(entryIndex, lookback));
        return StopGainRule.trailingStopGainPriceFromDistance(reference, threshold, false);
    }

    /**
     * Returns the highest reference price for the requested lookback.
     *
     * <p>
     * The max-lookback indicator is cached and reused; shorter warm-up lookbacks
     * are computed with a temporary indicator.
     *
     * @param index    current bar index
     * @param lookback lookback window size
     * @return highest reference price within the window
     */
    private Num highestReferencePrice(int index, int lookback) {
        if (lookback == barCount) {
            return highestReferencePriceWithMaxLookback.getValue(index);
        }
        return new HighestValueIndicator(referencePrice, lookback).getValue(index);
    }

    /**
     * Returns the lowest reference price for the requested lookback.
     *
     * <p>
     * The max-lookback indicator is cached and reused; shorter warm-up lookbacks
     * are computed with a temporary indicator.
     *
     * @param index    current bar index
     * @param lookback lookback window size
     * @return lowest reference price within the window
     */
    private Num lowestReferencePrice(int index, int lookback) {
        if (lookback == barCount) {
            return lowestReferencePriceWithMaxLookback.getValue(index);
        }
        return new LowestValueIndicator(referencePrice, lookback).getValue(index);
    }
}
