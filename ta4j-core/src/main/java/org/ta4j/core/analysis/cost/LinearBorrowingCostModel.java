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
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class LinearBorrowingCostModel implements CostModel {

    private static final long serialVersionUID = -2839623394737567618L;
    /**
     * Slope of the linear model - fee per period
     */
    private double feePerPeriod;

    /**
     * Constructor. (feePerPeriod * nPeriod)
     * 
     * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
     */
    public LinearBorrowingCostModel(double feePerPeriod) {
        this.feePerPeriod = feePerPeriod;
    }

    public Num calculate(Num price, Num amount) {
        // borrowing costs depend on borrowed period
        return price.numOf(0);
    }

    /**
     * Calculates the borrowing cost of a closed position.
     * 
     * @param position the position
     * @return the absolute trade cost
     */
    public Num calculate(Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided.");
        }
        return calculate(position, position.getExit().getIndex());
    }

    /**
     * Calculates the borrowing cost of a position.
     * 
     * @param position     the position
     * @param currentIndex final bar index to be considered (for open positions)
     * @return the absolute trade cost
     */
    public Num calculate(Position position, int currentIndex) {
        Trade entryTrade = position.getEntry();
        Trade exitTrade = position.getExit();
        Num borrowingCost = position.getEntry().getNetPrice().numOf(0);

        // borrowing costs apply for short positions only
        if (entryTrade != null && entryTrade.getType().equals(Trade.TradeType.SELL) && entryTrade.getAmount() != null) {
            int tradingPeriods = 0;
            if (position.isClosed()) {
                tradingPeriods = exitTrade.getIndex() - entryTrade.getIndex();
            } else if (position.isOpened()) {
                tradingPeriods = currentIndex - entryTrade.getIndex();
            }
            borrowingCost = getHoldingCostForPeriods(tradingPeriods, position.getEntry().getValue());
        }
        return borrowingCost;
    }

    /**
     * @param tradingPeriods number of periods
     * @param tradedValue    value of the trade initial trade position
     * @return the absolute borrowing cost
     */
    private Num getHoldingCostForPeriods(int tradingPeriods, Num tradedValue) {
        return tradedValue
                .multipliedBy(tradedValue.numOf(tradingPeriods).multipliedBy(tradedValue.numOf(feePerPeriod)));
    }

    /**
     * Evaluate if two models are equal
     * 
     * @param otherModel model to compare with
     */
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearBorrowingCostModel) otherModel).feePerPeriod == this.feePerPeriod;
        }
        return equality;
    }
}
