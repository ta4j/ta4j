/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
/*
  The MIT License (MIT)

  Copyright (c) 2014-2018 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.PriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule
 *
 * Satisfied when the price reaches the trailing loss threshold.
 */
public class TrailingStopLossRule extends AbstractRule {

    /**
     * The price indicator
     */
    private final PriceIndicator priceIndicator;
    /**
     * the current price extremum
     */
    private Num currentExtremum = null;
    /**
     * the current stop loss price activation
     */
    private Num currentStopLossLimitActivation = null;
    /** The barCount */
    private int barCount;
    /** the loss-distance as percentage */
    private final Num lossPercentage;

    /**
     * Constructor.
     * 
     * @param closePrice     the close price indicator
     * @param lossPercentage the loss percentage
     * @param barCount       number of bars to look back for the calculation
     */
    public TrailingStopLossRule(PriceIndicator priceIndicator, Num lossPercentage, int barCount) {
        this.priceIndicator = priceIndicator;
        this.barCount = barCount;
        this.lossPercentage = lossPercentage;
    }

    /**
     * Constructor.
     * 
     * @param closePrice     the close price indicator
     * @param lossPercentage the loss percentage
     */
    public TrailingStopLossRule(PriceIndicator priceIndicator, Num lossPercentage) {
        this(priceIndicator, lossPercentage, Integer.MAX_VALUE);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no loss
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                Num currentPrice = priceIndicator.getValue(index);
                int tradeIndex = currentTrade.getEntry().getIndex();

                if (currentTrade.getEntry().isBuy()) {
                    satisfied = isBuySatisfied(currentPrice, index, tradeIndex);
                } else {
                    satisfied = isSellSatisfied(currentPrice, index, tradeIndex);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuySatisfied(Num currentPrice, int index, int tradeIndex) {
        HighestValueIndicator highest = new HighestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, tradeIndex));
        Num highestCloseNum = highest.getValue(index);
        Num lossRatioThreshold = highestCloseNum.numOf(100).minus(lossPercentage).dividedBy(highestCloseNum.numOf(100));
        currentStopLossLimitActivation = highestCloseNum.multipliedBy(lossRatioThreshold);
        return currentPrice.isLessThanOrEqual(currentStopLossLimitActivation);
    }

    public Num getCurrentStopLossLimitActivation() {
        return currentStopLossLimitActivation;
    }

    private boolean isSellSatisfied(Num currentPrice, int index, int tradeIndex) {
        LowestValueIndicator lowest = new LowestValueIndicator(priceIndicator,
                getValueIndicatorBarCount(index, tradeIndex));
        Num lowestCloseNum = lowest.getValue(index);
        Num lossRatioThreshold = lowestCloseNum.numOf(100).plus(lossPercentage).dividedBy(lowestCloseNum.numOf(100));
        currentStopLossLimitActivation = lowestCloseNum.multipliedBy(lossRatioThreshold);
        return currentPrice.isGreaterThanOrEqual(currentStopLossLimitActivation);
    }

    private int getValueIndicatorBarCount(int index, int tradeIndex) {
        return Math.min(index - tradeIndex + 1, this.barCount);
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}, Current stop loss activation: {}",
                    getClass().getSimpleName(), index, isSatisfied, priceIndicator.getValue(index),
                    currentStopLossLimitActivation);
        }
    }
}
