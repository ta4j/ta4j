/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

/**
 * With this cost model, the trading costs for opening or closing a position
 * accrue linearly.
 */
public class LinearTransactionCostModel implements CostModel {

    /** The slope of the linear model (fee per position). */
    private final double feePerPosition;

    /**
     * Constructor with {@code feePerPosition * x}.
     *
     * @param feePerPosition the feePerPosition coefficient (e.g. 0.005 for 0.5% per
     *                       {@link Trade trade})
     */
    public LinearTransactionCostModel(double feePerPosition) {
        this.feePerPosition = feePerPosition;
    }

    /**
     * @param position     the position
     * @param currentIndex current bar index (irrelevant for the
     *                     LinearTransactionCostModel)
     * @return the trading cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        return this.calculate(position);
    }

    @Override
    public Num calculate(Position position) {
        Num totalPositionCost = null;
        Trade entryTrade = position.getEntry();
        if (entryTrade != null) {
            // transaction costs of the entry trade
            totalPositionCost = entryTrade.getCost();
            if (position.getExit() != null) {
                totalPositionCost = totalPositionCost.plus(position.getExit().getCost());
            }
        }
        return totalPositionCost;
    }

    @Override
    public Num calculate(Num price, Num amount) {
        return amount.numOf(feePerPosition).multipliedBy(price).multipliedBy(amount);
    }

    @Override
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearTransactionCostModel) otherModel).feePerPosition == this.feePerPosition;
        }
        return equality;
    }
}
