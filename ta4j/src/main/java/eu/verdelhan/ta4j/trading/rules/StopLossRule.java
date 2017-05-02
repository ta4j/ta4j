/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.trading.rules;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;

/**
 * A stop-loss rule.
 * <p>
 * Satisfied when the close price reaches the loss threshold.
 */
public class StopLossRule extends AbstractRule {

    /** The close price indicator */
    private ClosePriceIndicator closePrice;
    
    /** The loss ratio threshold (e.g. 0.97 for 3%) */
    private Decimal lossRatioThreshold;

    /**
     * Constructor.
     * @param closePrice the close price indicator
     * @param lossPercentage the loss percentage
     */
    public StopLossRule(ClosePriceIndicator closePrice, Decimal lossPercentage) {
        this.closePrice = closePrice;
        this.lossRatioThreshold = Decimal.HUNDRED.minus(lossPercentage).dividedBy(Decimal.HUNDRED);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no loss
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                Decimal entryPrice = currentTrade.getEntry().getPrice();
                Decimal currentPrice = closePrice.getValue(index);
                satisfied = currentPrice.isLessThanOrEqual(entryPrice.multipliedBy(lossRatioThreshold));
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
