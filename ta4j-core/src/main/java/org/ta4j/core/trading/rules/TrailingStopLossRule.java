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

import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.PriceIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule
 * <p></p>
 * Satisfied when the price reaches the trailing loss threshold.
 */
public class TrailingStopLossRule extends AbstractRule {

    /**
     * The price indicator
     */
    private final PriceIndicator priceIndicator;
    /**
     * the loss ratio multiplier for buy trades eg. for lossPercentage 5% this ratio will be: 0.95
     */
    private final Num lossRatioBuyMultiplier;
    /**
     * the loss ratio multiplier for sell trades eg. for lossPercentage 5% this ratio will be: 1.05
     */
    private final Num lossRatioSellMultiplier;
    /**
     * the current price extremum
     */
    private Num currentExtremum = null;
    /**
     * the current stop loss price activation
     */
    private Num currentStopLossLimitActivation = null;
    /**
     * the current trade
     */
    private Trade supervisedTrade;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param lossPercentage the loss percentage
     */
    public TrailingStopLossRule(PriceIndicator priceIndicator, Num lossPercentage) {
        this.priceIndicator = priceIndicator;
        final Num hundred = lossPercentage.numOf(100);
        this.lossRatioBuyMultiplier = hundred.minus(lossPercentage).dividedBy(hundred);
        this.lossRatioSellMultiplier = hundred.plus(lossPercentage).dividedBy(hundred);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no loss
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                if (!currentTrade.equals(supervisedTrade)) {
                    supervisedTrade = currentTrade;
                    currentExtremum = null;
                    currentStopLossLimitActivation = null;
                }
                final Num currentPrice = priceIndicator.getValue(index);
                if (currentTrade.getEntry().isBuy()) {
                    satisfied = isBuySatisfied(currentPrice);
                } else {
                    satisfied = isSellSatisfied(currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuySatisfied(Num currentPrice) {
        if (currentExtremum == null || currentPrice.isGreaterThan(currentExtremum)) {
            currentExtremum = currentPrice;
            currentStopLossLimitActivation = currentExtremum.multipliedBy(lossRatioBuyMultiplier);
        }
        return currentPrice.isLessThanOrEqual(currentStopLossLimitActivation);
    }

    private boolean isSellSatisfied(Num currentPrice) {
        if (currentExtremum == null || currentPrice.isLessThan(currentExtremum)) {
            currentExtremum = currentPrice;
            currentStopLossLimitActivation = currentExtremum.multipliedBy(lossRatioSellMultiplier);
        }
        return currentPrice.isGreaterThanOrEqual(currentStopLossLimitActivation);
    }

    public Num getCurrentStopLossLimitActivation() {
        return currentStopLossLimitActivation;
    }

    @Override
    protected void traceIsSatisfied(int index, boolean isSatisfied) {
        if (log.isTraceEnabled()) {
            log.trace("{}#isSatisfied({}): {}. Current price: {}, Current stop loss activation: {}", getClass().getSimpleName(), index, isSatisfied,
                    priceIndicator.getValue(index), currentStopLossLimitActivation
            );
        }
    }
}
