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
 * @since 0.22.2
 */
abstract class BaseVolatilityTrailingStopLossRule extends AbstractRule implements StopLossPriceModel {

    private final Indicator<Num> referencePrice;
    private final Indicator<Num> stopLossThreshold;
    private final int barCount;

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
    }

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
                    HighestValueIndicator highestPrice = new HighestValueIndicator(referencePrice, lookback);
                    Num reference = entryPrice.max(highestPrice.getValue(index));
                    Num thresholdPrice = StopLossRule.stopLossPriceFromDistance(reference, threshold, true);
                    return currentPrice.isLessThanOrEqual(thresholdPrice);
                }
                LowestValueIndicator lowestPrice = new LowestValueIndicator(referencePrice, lookback);
                Num reference = entryPrice.min(lowestPrice.getValue(index));
                Num thresholdPrice = StopLossRule.stopLossPriceFromDistance(reference, threshold, false);
                return currentPrice.isGreaterThanOrEqual(thresholdPrice);
            }
        }
        return false;
    }

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
            HighestValueIndicator highestPrice = new HighestValueIndicator(referencePrice, lookback);
            Num reference = entryPrice.max(highestPrice.getValue(entryIndex));
            return StopLossRule.stopLossPriceFromDistance(reference, threshold, true);
        }
        LowestValueIndicator lowestPrice = new LowestValueIndicator(referencePrice, lookback);
        Num reference = entryPrice.min(lowestPrice.getValue(entryIndex));
        return StopLossRule.stopLossPriceFromDistance(reference, threshold, false);
    }
}
