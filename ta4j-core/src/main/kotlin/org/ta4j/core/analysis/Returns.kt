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
package org.ta4j.core.analysis

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.Position
import org.ta4j.core.TradingRecord
import org.ta4j.core.num.*
import java.util.*
import java.util.function.Consumer

/**
 * The return rates.
 *
 * This class allows to compute the return rate of a price time-series
 */
class Returns : Indicator<Num> {
    enum class ReturnType {
        LOG {
            override fun calculate(xNew: Num, xOld: Num): Num {
                // r_i = ln(P_i/P_(i-1))
                return xNew.div(xOld).log()
            }
        },
        ARITHMETIC {
            override fun calculate(xNew: Num, xOld: Num): Num {
                // r_i = P_i/P_(i-1) - 1
                return xNew.div(xOld).minus(one!!)
            }
        };

        /**
         * @return calculate a single return rate
         */
        abstract fun calculate(xNew: Num, xOld: Num): Num
    }
    private val type: ReturnType

    /**
     * The bar series
     */
    override val barSeries: BarSeries?

    /**
     * The return rates
     */
    internal var values: MutableList<Num>

    /**
     * Constructor.
     *
     * @param barSeries the bar series
     * @param position  a single position
     */
    constructor(barSeries: BarSeries?, position: Position?, type: ReturnType) {
        one = barSeries!!.numOf(1)
        this.barSeries = barSeries
        this.type = type
        // at index 0, there is no return
        values = ArrayList(listOf<Num>(NaN.NaN))
        calculate(position)
        fillToTheEnd()
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    constructor(barSeries: BarSeries?, tradingRecord: TradingRecord, type: ReturnType) {
        one = barSeries!!.numOf(1)
        this.barSeries = barSeries
        this.type = type
        // at index 0, there is no return
        values = ArrayList(listOf<Num>(NaN.NaN))
        calculate(tradingRecord)
        fillToTheEnd()
    }

    fun getValues(): List<Num> {
        return values
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position
     */
    override fun getValue(index: Int): Num {
        return values[index]
    }

    override fun numOf(number: Number): Num {
        return barSeries!!.numOf(number)
    }

    /**
     * @return the size of the return series.
     */
    val size: Int
        get() = barSeries!!.barCount - 1

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
     */
    @JvmOverloads
    fun calculate(position: Position?, finalIndex: Int = barSeries!!.endIndex) {
        val isLongTrade = position!!.entry!!.isBuy
        val minusOne = barSeries!!.numOf(-1)
        val endIndex: Int = CashFlow.determineEndIndex(position, finalIndex, barSeries.endIndex)
        val entryIndex = position.entry!!.index
        val begin = entryIndex + 1
        if (begin > values.size) {
            values.addAll(Collections.nCopies(begin - values.size, barSeries.numOf(0)))
        }
        val startingIndex = Math.max(begin, 1)
        val nPeriods = endIndex - entryIndex
        val holdingCost = position.getHoldingCost(endIndex)
        val avgCost = holdingCost.div(holdingCost.numOf(nPeriods))

        // returns are per period (iterative). Base price needs to be updated
        // accordingly
        var lastPrice = position.entry!!.netPrice!!
        for (i in startingIndex until endIndex) {
            val intermediateNetPrice: Num =
                CashFlow.Companion.addCost(barSeries.getBar(i).closePrice!!, avgCost, isLongTrade)
            val assetReturn = type.calculate(intermediateNetPrice, lastPrice)
            val strategyReturn: Num = if (position.entry!!.isBuy) {
                assetReturn
            } else {
                assetReturn.times(minusOne)
            }
            values.add(strategyReturn)
            // update base price
            lastPrice = barSeries.getBar(i).closePrice!!
        }

        // add net return at exit position
        val exitPrice: Num?
        exitPrice = if (position.exit != null) {
            position.exit!!.netPrice
        } else {
            barSeries.getBar(endIndex).closePrice
        }!!
        val strategyReturn: Num
        val assetReturn = type.calculate(CashFlow.Companion.addCost(exitPrice, avgCost, isLongTrade), lastPrice)
        strategyReturn = if (position.entry!!.isBuy) {
            assetReturn
        } else {
            assetReturn.times(minusOne)
        }
        values.add(strategyReturn)
    }

    /**
     * Calculates the returns for a trading record.
     *
     * @param tradingRecord the trading record
     */
    private fun calculate(tradingRecord: TradingRecord) {
        // For each position...
        tradingRecord.positions.forEach(Consumer { position: Position? -> this.calculate(position) })
    }

    /**
     * Fills with zeroes until the end of the series.
     */
    private fun fillToTheEnd() {
        if (barSeries!!.endIndex >= values.size) {
            values.addAll(Collections.nCopies(barSeries.endIndex - values.size + 1, barSeries.numOf(0)))
        }
    }

    companion object {
        /**
         * Unit element for efficient arithmetic return computation
         */
        private var one: Num?=null
    }
}