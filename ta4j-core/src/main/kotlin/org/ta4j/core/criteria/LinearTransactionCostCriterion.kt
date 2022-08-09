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
 * FOR A PARTICULAR PURPOSE AND NONINFINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria

import org.ta4j.core.*
import org.ta4j.core.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.num.*

/**
 * A linear transaction cost criterion.
 *
 * Calculates the transaction cost according to an initial traded amount and a
 * linear function defined by a and b (a * x + b).
 */
/**
 * Constructor. (a * x + b)
 *
 * @param initialAmount the initially traded amount
 * @param a             the a coefficient (e.g. 0.005 for 0.5% per [                      trade][Trade])
 * @param b             the b constant (e.g. 0.2 for $0.2 per [                      trade][Trade])
 */


class LinearTransactionCostCriterion @JvmOverloads constructor(
    private val initialAmount: Double,
    private val a: Double,
    private val b: Double = 0.0
) : AbstractAnalysisCriterion() {
    private val grossReturn = GrossReturnCriterion()

    override fun calculate(series: BarSeries, position: Position): Num {
        return getTradeCost(series, position, series.numOf(initialAmount))
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        var totalCosts = series.numOf(0)
        var tradedAmount = series.numOf(initialAmount)
        for (position in tradingRecord.positions) {
            val tradeCost = getTradeCost(series, position, tradedAmount)
            totalCosts = totalCosts.plus(tradeCost)
            // To calculate the new traded amount:
            // - Remove the cost of the *first* trade
            // - Multiply by the profit ratio
            // - Remove the cost of the *second* trade
            tradedAmount = tradedAmount.minus(getTradeCost(position.entry, tradedAmount))
            tradedAmount = tradedAmount.times(grossReturn.calculate(series, position))
            tradedAmount = tradedAmount.minus(getTradeCost(position.exit, tradedAmount))
        }

        // Special case: if the current position is open
        val currentPosition = tradingRecord.getCurrentPosition()
        if (currentPosition.isOpened) {
            totalCosts = totalCosts.plus(getTradeCost(currentPosition.entry, tradedAmount))
        }
        return totalCosts
    }

    /** The lower the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isLessThan(criterionValue2)
    }

    /**
     * @param trade        the trade
     * @param tradedAmount the amount of the trade
     * @return the absolute trade cost
     */
    private fun getTradeCost(trade: Trade?, tradedAmount: Num?): Num {
        val tradeCost = tradedAmount!!.numOf(0)
        return if (trade != null) {
            tradedAmount.numOf(a).times(tradedAmount).plus(tradedAmount.numOf(b))
        } else tradeCost
    }

    /**
     * @param series        the bar series
     * @param position      the position
     * @param initialAmount the initially traded amount for the position
     * @return the absolute total cost of all trades in the position
     */
    private fun getTradeCost(series: BarSeries?, position: Position?, initialAmount: Num): Num {
        var totalTradeCost = series!!.numOf(0)
        if (position != null) {
            if (position.entry != null) {
                totalTradeCost = getTradeCost(position.entry, initialAmount)
                if (position.exit != null) {
                    // To calculate the new traded amount:
                    // - Remove the cost of the first trade
                    // - Multiply by the profit ratio
                    val newTradedAmount = initialAmount.minus(totalTradeCost)
                        .times(grossReturn.calculate(series, position))
                    totalTradeCost = totalTradeCost.plus(getTradeCost(position.exit, newTradedAmount))
                }
            }
        }
        return totalTradeCost
    }
}