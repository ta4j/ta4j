/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.Num;

/**
 * A rule for a trailing stop loss using the Average True Range (ATR) as a basis.
 * <p>
 * The rule is satisfied when the price drops below the highest price since the position entry
 * (for a long position) or rises above the lowest price since the position entry (for a short position)
 * by a certain multiple of the ATR.
 */
public class TrailingATRStopLossRule extends AbstractRule {

    private final TransformIndicator atr;
    private final ClosePriceIndicator closePrice;
    private final HighPriceIndicator highPrice;
    private final LowPriceIndicator lowPrice;

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param barCount       the number of bars to consider for the ATR
     * @param atrCoefficient the coefficient for the ATR
     */
    public TrailingATRStopLossRule(BarSeries series, int barCount, double atrCoefficient) {
        this.atr = TransformIndicator.multiply(new ATRIndicator(series, barCount), atrCoefficient);
        this.closePrice = new ClosePriceIndicator(series);
        this.highPrice = new HighPriceIndicator(series);
        this.lowPrice = new LowPriceIndicator(series);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        // Rule is not satisfied if there is no active trade
        if (tradingRecord == null || tradingRecord.isClosed()) {
            return false;
        }

        int barsSinceEntry = index - tradingRecord.getCurrentPosition().getEntry().getIndex() + 1;
        Num currentPrice = this.closePrice.getValue(index);

        // Determine if stop loss should be triggered for a long position
        if (tradingRecord.getCurrentPosition().getEntry().isBuy()) {
            HighestValueIndicator highestPrice = new HighestValueIndicator(this.highPrice, barsSinceEntry);
            Num stopThresholdPrice = highestPrice.getValue(index).minus(this.atr.getValue(index));
            return currentPrice.isLessThanOrEqual(stopThresholdPrice);
        }

        // Determine if stop loss should be triggered for a short position
        if (tradingRecord.getCurrentPosition().getEntry().isSell()) {
            LowestValueIndicator lowestPrice = new LowestValueIndicator(this.lowPrice, barsSinceEntry);
            Num stopThresholdPrice = lowestPrice.getValue(index).plus(this.atr.getValue(index));
            return currentPrice.isGreaterThanOrEqual(stopThresholdPrice);
        }

        return false;
    }
}
