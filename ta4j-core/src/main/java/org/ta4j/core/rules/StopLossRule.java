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

import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * A stop-loss rule.
 *
 * Satisfied when the close price reaches the loss threshold.
 */
public class StopLossRule extends AbstractRule {

    /**
     * Constant value for 100
     */
    private final Num HUNDRED;

    /**
     * The close price indicator
     */
    private final ClosePriceIndicator closePrice;

    /**
     * The loss percentage
     */
    private Num lossPercentage;

    /**
     * Constructor.
     *
     * @param closePrice     the close price indicator
     * @param lossPercentage the loss percentage
     */
    public StopLossRule(ClosePriceIndicator closePrice, Number lossPercentage) {
        this(closePrice, closePrice.numOf(lossPercentage));
    }

    /**
     * Constructor.
     *
     * @param closePrice     the close price indicator
     * @param lossPercentage the loss percentage
     */
    public StopLossRule(ClosePriceIndicator closePrice, Num lossPercentage) {
        this.closePrice = closePrice;
        this.lossPercentage = lossPercentage;
        this.HUNDRED = closePrice.numOf(100);
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {

                Num entryPrice = currentPosition.getEntry().getNetPrice();
                Num currentPrice = closePrice.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyStopSatisfied(entryPrice, currentPrice);
                } else {
                    satisfied = isSellStopSatisfied(entryPrice, currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isBuyStopSatisfied(Num entryPrice, Num currentPrice) {
        Num lossRatioThreshold = HUNDRED.minus(lossPercentage).dividedBy(HUNDRED);
        Num threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isLessThanOrEqual(threshold);
    }

    private boolean isSellStopSatisfied(Num entryPrice, Num currentPrice) {
        Num lossRatioThreshold = HUNDRED.plus(lossPercentage).dividedBy(HUNDRED);
        Num threshold = entryPrice.multipliedBy(lossRatioThreshold);
        return currentPrice.isGreaterThanOrEqual(threshold);
    }
}
