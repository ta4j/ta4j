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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * A trailing stop-loss rule
 * <p></p>
 * Satisfied when the close price reaches the trailing loss threshold.
 */
public class TrailingStopLossRule extends AbstractRule {

	/** The close price indicator */
	private final ClosePriceIndicator closePrice;
	/** the loss-distance as percentage */
	private final Num lossPercentage;
	/** the current price extremum */
	private Num currentExtremum = null;
	/** the current threshold */
	private Num threshold = null;
	/** the current trade */
	private Trade supervisedTrade;

	/**
     * Constructor.
     * @param closePrice the close price indicator
	 * @param lossPercentage the loss percentage
	 */
	public TrailingStopLossRule(ClosePriceIndicator closePrice, Num lossPercentage) {
		this.closePrice = closePrice;
		this.lossPercentage = lossPercentage;
	}
	
	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no loss
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if ( currentTrade.isOpened() ) {
            	if ( ! currentTrade.equals(supervisedTrade) ) {
            		supervisedTrade = currentTrade;
                	currentExtremum = null;
                	threshold = null;
            	}
            	Num currentPrice = closePrice.getValue(index);
                if ( currentTrade.getEntry().isBuy() ) {
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
		boolean satisfied = false;
		if ( currentExtremum == null ) {
			currentExtremum = currentPrice.numOf(Float.MIN_VALUE);
		}
		if ( currentPrice.isGreaterThan(currentExtremum) ) {
			currentExtremum = currentPrice;
			Num lossRatioThreshold = currentPrice.numOf(100).minus(lossPercentage).dividedBy(currentPrice.numOf(100));
			threshold = currentExtremum.multipliedBy(lossRatioThreshold);
		}
		if ( threshold != null ) {
			satisfied = currentPrice.isLessThanOrEqual(threshold);
		}
		return satisfied;
	}

	private boolean isSellSatisfied(Num currentPrice) {
		boolean satisfied = false;
		if ( currentExtremum == null ) {
			currentExtremum = currentPrice.numOf(Float.MAX_VALUE);
		}
		if ( currentPrice.isLessThan(currentExtremum) ) {
			currentExtremum = currentPrice;
			Num lossRatioThreshold = currentPrice.numOf(100).plus(lossPercentage).dividedBy(currentPrice.numOf(100));
		    threshold = currentExtremum.multipliedBy(lossRatioThreshold);
		}
		if ( threshold != null ) {
			satisfied = currentPrice.isGreaterThanOrEqual(threshold);
		}
		return satisfied;
	}
}
