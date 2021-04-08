/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.RuleWithCtx;
import org.ta4j.core.StrategyContext;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * A stop-gain rule that uses the context to promote a more precise exit price to the strategy.
 *
 * Satisfied when the price indicator reaches the gain threshold or absolute gain.
 * As price indicator for stop gain within a candle (e.g. take profit market order and TP is between close and high) you should use the {@link HighPriceIndicator}.
 * 
 * The {@link Rule} implements the {@link RuleWithCtx} interface which sets the satified price for exit the trade.
 * If the price shall be based on the close price use {@link StopGainRule}.
 */
public class StopGainRule2 extends AbstractRule implements RuleWithCtx {

    /**
     * Constant value for 100
     */
    private final Num HUNDRED;

    /**
     * The price indicator
     */
    private final Indicator<Num> priceIndicator;

    /**
     * The gain percentage
     */
    private final Num gainValue;

    /**
     * true for percentage usage of {@code gainValue}, false for absolute usage of {@code gainValue}
     */
	private boolean percentageOrAbs;

    /**
     * Constructor.
     *
     * @param priceIndicator     the price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule2(Indicator<Num> priceIndicator, Number gainPercentage) {
        this(priceIndicator, priceIndicator.numOf(gainPercentage), true);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator     the price indicator
     * @param gainValue the gain value (percentage or absolute)
     * @param percentageOrAbs true for percentage value, false for absolute value
     */
    public StopGainRule2(Indicator<Num> priceIndicator, Num gainValue, boolean percentageOrAbs) {
        this.priceIndicator = priceIndicator;
        this.gainValue = gainValue;
		this.percentageOrAbs = percentageOrAbs;
        HUNDRED = priceIndicator.numOf(100);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        return isSatisfied(index, tradingRecord, null);
    }

    private boolean isSellGainSatisfied(Num entryPrice, Num currentPrice, StrategyContext ctx) {
    	Num threshold;
    	if (percentageOrAbs) {
    		Num lossRatioThreshold = HUNDRED.minus(gainValue).dividedBy(HUNDRED);
    		threshold = entryPrice.multipliedBy(lossRatioThreshold);
    	} else {
    		threshold = entryPrice.minus(gainValue);
    	}
        boolean ltOrEq = currentPrice.isLessThanOrEqual(threshold);
        if (ltOrEq && ctx != null) {
        	ctx.setTradeExitPrice(threshold);
        }
		return ltOrEq;
    }

    private boolean isBuyGainSatisfied(Num entryPrice, Num currentPrice, StrategyContext ctx) {
    	Num threshold;
    	if (percentageOrAbs) {
    		Num lossRatioThreshold = HUNDRED.plus(gainValue).dividedBy(HUNDRED);
    		threshold = entryPrice.multipliedBy(lossRatioThreshold);
    	} else {
    		threshold = entryPrice.plus(gainValue);
    	}
    	boolean gtOrEq = currentPrice.isGreaterThanOrEqual(threshold);
        if (gtOrEq && ctx != null) {
        	ctx.setTradeExitPrice(threshold);
        }
		return gtOrEq;
    }

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord, StrategyContext ctx) {
		boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {

                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = priceIndicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyGainSatisfied(entryPrice, currentPrice, ctx);
                } else {
                    satisfied = isSellGainSatisfied(entryPrice, currentPrice, ctx);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
	}
}
