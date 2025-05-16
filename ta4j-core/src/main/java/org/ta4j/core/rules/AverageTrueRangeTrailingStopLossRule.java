/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule based on Average True Range (ATR).
 *
 * <p>
 * This rule is satisfied when the reference price reaches the loss threshold as
 * determined by a given multiple of the prevailing average true range. It can
 * be used for both long and short positions.
 */
public class AverageTrueRangeTrailingStopLossRule extends AbstractRule {

    /**
     * The ATR-based stop loss threshold.
     */
    private final Indicator<Num> stopLossThreshold;

    /**
     * The reference price indicator.
     */
    private final Indicator<Num> referencePrice;

    /**
     * Constructor with default close price as reference.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeTrailingStopLossRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient);
    }

    /**
     * Constructor with custom reference price.
     *
     * @param series         the bar series
     * @param referencePrice the reference price indicator
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the coefficient to multiply ATR
     */
    public AverageTrueRangeTrailingStopLossRule(BarSeries series, Indicator<Num> referencePrice, int atrBarCount,
            Number atrCoefficient) {
        this.stopLossThreshold = TransformIndicator.multiply(new ATRIndicator(series, atrBarCount), atrCoefficient);
        this.referencePrice = referencePrice;
    }

    /**
     * Checks if the stop loss condition is satisfied.
     *
     * <p>
     * For long positions: satisfied when the reference price is less than the
     * current trade's entry price (net of fees) OR the highest reference price
     * since entry minus the ATR-based stop loss threshold. For short positions:
     * satisfied when the reference price is greater than the current trade's entry
     * price (net of fees) OR the lowest reference price since entry plus the
     * ATR-based stop loss threshold.
     *
     * @param index         the current bar index
     * @param tradingRecord the trading record
     * @return true if the stop loss condition is satisfied, false otherwise
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord != null && !tradingRecord.isClosed()) {
            Num entryPrice = tradingRecord.getCurrentPosition().getEntry().getNetPrice();
            Num currentPrice = this.referencePrice.getValue(index);
            Num threshold = this.stopLossThreshold.getValue(index);

            int barsSinceEntry = index - tradingRecord.getCurrentPosition().getEntry().getIndex() + 1;

            if (tradingRecord.getCurrentPosition().getEntry().isBuy()) {
                HighestValueIndicator highestPrice = new HighestValueIndicator(this.referencePrice, barsSinceEntry);
                Num thresholdPrice = entryPrice.max(highestPrice.getValue(index)).minus(threshold);
                return currentPrice.isLessThan(thresholdPrice);
            } else {
                LowestValueIndicator lowestPrice = new LowestValueIndicator(this.referencePrice, barsSinceEntry);
                Num thresholdPrice = entryPrice.min(lowestPrice.getValue(index)).plus(threshold);
                return currentPrice.isGreaterThan(thresholdPrice);
            }
        }
        return false;
    }
}