/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * An ATR-based stop-gain rule.
 *
 * <p>
 * Satisfied when a reference price (by default the close price) reaches the
 * gain threshold defined by a multiple of the Average True Range (ATR).
 */
public class AverageTrueRangeStopGainRule extends AbstractRule {

    /**
     * The ATR indicator pre-multiplied with the multiple to give the gain threshold
     */
    private final Indicator<Num> stopGainThreshold;
    private final Indicator<Num> referencePrice;

    /**
     * Constructor defaulting the reference price to the close price
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(BarSeries series, int atrBarCount, Number atrCoefficient) {
        this(series, new ClosePriceIndicator(series), atrBarCount, atrCoefficient);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param atrBarCount    the number of bars used for ATR calculation
     * @param atrCoefficient the multiple of ATR to set the gain threshold
     */
    public AverageTrueRangeStopGainRule(BarSeries series, Indicator<Num> referencePrice, int atrBarCount,
            Number atrCoefficient) {
        this.stopGainThreshold = TransformIndicator.multiply(new ATRIndicator(series, atrBarCount), atrCoefficient);
        this.referencePrice = referencePrice;
    }

    /**
     * This rule uses the {@code tradingRecord}.
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no gain
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {

                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = referencePrice.getValue(index);
                Num gainThreshold = stopGainThreshold.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = currentPrice.isGreaterThanOrEqual(entryPrice.plus(gainThreshold));
                } else {
                    satisfied = currentPrice.isLessThanOrEqual(entryPrice.minus(gainThreshold));
                }
            }
        }

        return satisfied;
    }
}
