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
 * Shared trailing stop-loss logic for volatility-based stop rules.
 *
 * @since 0.22.3
 */
abstract class BaseVolatilityTrailingStopLossRule extends AbstractRule implements StopLossPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopLossThreshold;
    private final int barCount;
    private final transient HighestValueIndicator highestReferencePriceWithMaxLookback;
    private final transient LowestValueIndicator lowestReferencePriceWithMaxLookback;

    /**
     * Constructor.
     *
     * @param referencePrice    reference price indicator
     * @param stopLossThreshold volatility-scaled stop-loss threshold indicator
     * @param barCount          maximum lookback for trailing reference calculation
     */
    protected BaseVolatilityTrailingStopLossRule(Indicator<Num> referencePrice, Indicator<Num> stopLossThreshold,
            int barCount) {
        if (referencePrice == null) {
            throw new IllegalArgumentException("referencePrice must not be null");
        }
        if (stopLossThreshold == null) {
            throw new IllegalArgumentException("stopLossThreshold must not be null");
        }
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        this.referencePrice = referencePrice;
        this.stopLossThreshold = stopLossThreshold;
        this.barCount = barCount;
        this.highestReferencePriceWithMaxLookback = new HighestValueIndicator(referencePrice, barCount);
        this.lowestReferencePriceWithMaxLookback = new LowestValueIndicator(referencePrice, barCount);
    }

    /**
     * Evaluates whether trailing stop-loss condition is satisfied for the current
     * open position.
     *
     * @param index         current bar index
     * @param tradingRecord trading record containing the open position
     * @return {@code true} when trailing stop-loss condition is satisfied
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord != null && !tradingRecord.isClosed()) {
            Position position = tradingRecord.getCurrentPosition();
            if (position.isOpened()) {
                Num entryPrice = position.getEntry().getNetPrice();
                Num currentPrice = referencePrice.getValue(index);
                Num threshold = stopLossThreshold.getValue(index);
                if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(currentPrice) || Num.isNaNOrNull(threshold)) {
                    return false;
                }

                int barsSinceEntry = index - position.getEntry().getIndex() + 1;
                int lookback = Math.min(barsSinceEntry, barCount);

                if (position.getEntry().isBuy()) {
                    Num reference = entryPrice.max(highestReferencePrice(index, lookback));
                    Num thresholdPrice = StopLossRule.stopLossPriceFromDistance(reference, threshold, true);
                    return currentPrice.isLessThanOrEqual(thresholdPrice);
                }
                Num reference = entryPrice.min(lowestReferencePrice(index, lookback));
                Num thresholdPrice = StopLossRule.stopLossPriceFromDistance(reference, threshold, false);
                return currentPrice.isGreaterThanOrEqual(thresholdPrice);
            }
        }
        return false;
    }

    /**
     * Returns the initial trailing stop-loss price at position entry.
     *
     * @param series   the bar series
     * @param position the position being evaluated
     * @return initial trailing stop-loss price, or {@code null} if unavailable
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
        Num threshold = stopLossThreshold.getValue(entryIndex);
        if (Num.isNaNOrNull(threshold)) {
            return null;
        }

        // stopPrice models the initial trailing stop at entry time.
        int lookback = 1;
        if (position.getEntry().isBuy()) {
            Num reference = entryPrice.max(highestReferencePrice(entryIndex, lookback));
            return StopLossRule.stopLossPriceFromDistance(reference, threshold, true);
        }
        Num reference = entryPrice.min(lowestReferencePrice(entryIndex, lookback));
        return StopLossRule.stopLossPriceFromDistance(reference, threshold, false);
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
