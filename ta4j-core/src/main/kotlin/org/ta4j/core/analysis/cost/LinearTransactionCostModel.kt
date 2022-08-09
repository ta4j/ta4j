/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core.analysis.cost

import org.ta4j.core.Position
import org.ta4j.core.Trade
import org.ta4j.core.num.*

class LinearTransactionCostModel
/**
 * Constructor. (feePerPosition * x)
 *
 * @param feePerPosition the feePerPosition coefficient (e.g. 0.005 for 0.5% per
 * [trade][Trade])
 */(
    /**
     * Slope of the linear model - fee per position
     */
    private val feePerPosition: Double
) : CostModel {
    /**
     * Calculates the transaction cost of a position.
     *
     * @param position     the position
     * @param finalIndex current bar index (irrelevant for the
     * LinearTransactionCostModel)
     * @return the absolute trade cost
     */
    override fun calculate(position: Position, finalIndex: Int): Num? {
        return this.calculate(position)
    }

    /**
     * Calculates the transaction cost of a position.
     *
     * @param position the position
     * @return the absolute trade cost
     */
    override fun calculate(position: Position): Num? {
        var totalPositionCost: Num? = null
        val entryTrade = position.entry
        if (entryTrade != null) {
            // transaction costs of entry trade
            totalPositionCost = entryTrade.cost
            if (position.exit != null) {
                totalPositionCost = totalPositionCost!!.plus(position.exit!!.cost!!)
            }
        }
        return totalPositionCost
    }

    /**
     * @param price  execution price
     * @param amount trade amount
     * @return the absolute trade transaction cost
     */
    override fun calculate(price: Num?, amount: Num?): Num {
        return amount!!.numOf(feePerPosition).times(price!!).times(amount)
    }

    /**
     * Evaluate if two models are equal
     *
     * @param other model to compare with
     */
    override fun equals(other: Any?): Boolean {
        var equality = false
        if (this.javaClass == other!!.javaClass) {
            equality = (other as LinearTransactionCostModel?)!!.feePerPosition == feePerPosition
        }
        return equality
    }

    companion object {
        private const val serialVersionUID = -8808559507754156097L
    }
}