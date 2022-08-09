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
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.*

class LinearBorrowingCostModel
/**
 * Constructor. (feePerPeriod * nPeriod)
 *
 * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
 */(
    /**
     * Slope of the linear model - fee per period
     */
    private val feePerPeriod: Double
) : CostModel {
    override fun calculate(price: Num?, amount: Num?): Num {
        // borrowing costs depend on borrowed period
        return price!!.numOf(0)
    }

    /**
     * Calculates the borrowing cost of a closed position.
     *
     * @param position the position
     * @return the absolute trade cost
     */
    override fun calculate(position: Position): Num {
        require(!position.isOpened) { "Position is not closed. Final index of observation needs to be provided." }
        return calculate(position, position.exit!!.index)
    }

    /**
     * Calculates the borrowing cost of a position.
     *
     * @param position     the position
     * @param finalIndex final bar index to be considered (for open positions)
     * @return the absolute trade cost
     */
    override fun calculate(position: Position, finalIndex: Int): Num {
        val entryTrade = position.entry
        val exitTrade = position.exit
        var borrowingCost = position.entry!!.netPrice!!.numOf(0)

        // borrowing costs apply for short positions only
        if (entryTrade != null && entryTrade.type == TradeType.SELL) {
            var tradingPeriods = 0
            if (position.isClosed) {
                tradingPeriods = exitTrade!!.index - entryTrade.index
            } else if (position.isOpened) {
                tradingPeriods = finalIndex - entryTrade.index
            }
            borrowingCost = getHoldingCostForPeriods(tradingPeriods, position.entry!!.value!!)
        }
        return borrowingCost
    }

    /**
     * @param tradingPeriods number of periods
     * @param tradedValue    value of the trade initial trade position
     * @return the absolute borrowing cost
     */
    private fun getHoldingCostForPeriods(tradingPeriods: Int, tradedValue: Num): Num {
        return tradedValue
            .times(tradedValue.numOf(tradingPeriods).times(tradedValue.numOf(feePerPeriod)))
    }

    /**
     * Evaluate if two models are equal
     *
     * @param other model to compare with
     */
    override fun equals(other: Any?): Boolean {
        var equality = false
        if (this.javaClass == other!!.javaClass) {
            equality = (other as LinearBorrowingCostModel?)!!.feePerPeriod == feePerPeriod
        }
        return equality
    }

    companion object {
        private const val serialVersionUID = -2839623394737567618L
    }
}