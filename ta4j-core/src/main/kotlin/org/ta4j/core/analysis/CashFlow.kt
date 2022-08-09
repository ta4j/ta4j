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
import kotlin.math.max
import kotlin.math.min

/**
 * The cash flow.
 *
 * This class allows to follow the money cash flow involved by a list of
 * positions over a bar series.
 */
class CashFlow : Indicator<Num> {
    /**
     * The bar series
     */
    override val barSeries: BarSeries

    /**
     * The cash flow values
     */
    private var values: MutableList<Num>

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries the bar series
     * @param position  a single position
     */
    constructor(barSeries: BarSeries, position: Position) {
        this.barSeries = barSeries
        values = ArrayList(listOf(numOf(1)))
        calculate(position)
        fillToTheEnd()
    }

    /**
     * Constructor for cash flows of closed positions of a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    constructor(barSeries: BarSeries, tradingRecord: TradingRecord) {
        this.barSeries = barSeries
        values = ArrayList(listOf(numOf(1)))
        calculate(tradingRecord)
        fillToTheEnd()
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open positions are
     * considered
     */
    constructor(barSeries: BarSeries, tradingRecord: TradingRecord, finalIndex: Int) {
        this.barSeries = barSeries
        values = ArrayList(listOf(numOf(1)))
        calculate(tradingRecord, finalIndex)
        fillToTheEnd()
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    override fun getValue(index: Int): Num {
        return values[index]
    }


    override fun numOf(number: Number): Num {
        return barSeries.numOf(number)
    }

    /**
     * @return the size of the bar series
     */
    val size: Int
        get() = barSeries.barCount

    /**
     * Calculates the cash flow for a single closed position.
     *
     * @param position a single position
     */
    private fun calculate(position: Position?) {
        require(!position!!.isOpened) { "Position is not closed. Final index of observation needs to be provided." }
        calculate(position, position.exit!!.index)
    }

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
     */
    private fun calculate(position: Position, finalIndex: Int) {
        val isLongTrade = position.entry!!.isBuy
        val endIndex = determineEndIndex(position, finalIndex, barSeries.endIndex)
        val entryIndex = position.entry!!.index
        val begin = entryIndex + 1
        if (begin > values.size) {
            val lastValue = values[values.size - 1]
            values.addAll(Collections.nCopies(begin - values.size, lastValue))
        }
        // Trade is not valid if net balance at the entryIndex is negative
        if (values[values.size - 1].isGreaterThan(values[0].numOf(0))) {
            val startingIndex = max(begin, 1)
            val nPeriods = endIndex - entryIndex
            val holdingCost = position.getHoldingCost(endIndex)
            val avgCost = holdingCost.div(holdingCost.numOf(nPeriods))

            // Add intermediate cash flows during position
            val netEntryPrice = position.entry!!.netPrice!!
            for (i in startingIndex until endIndex) {
                val intermediateNetPrice = addCost(barSeries.getBar(i).closePrice!!, avgCost, isLongTrade)
                val ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice)
                values.add(values[entryIndex].times(ratio))
            }

            // add net cash flow at exit position
            val exitPrice: Num = if (position.exit != null) {
                position.exit!!.netPrice
            } else {
                barSeries.getBar(endIndex).closePrice
            }!!
            val ratio = getIntermediateRatio(isLongTrade, netEntryPrice, addCost(exitPrice, avgCost, isLongTrade))
            values.add(values[entryIndex].times(ratio))
        }
    }

    /**
     * Calculates the cash flow for the closed positions of a trading record.
     *
     * @param tradingRecord the trading record
     */
    private fun calculate(tradingRecord: TradingRecord) {
        // For each position...
        tradingRecord.positions.forEach(Consumer { position: Position? -> this.calculate(position) })
    }

    /**
     * Calculates the cash flow for all positions of a trading record, including
     * accrued cash flow of an open position.
     *
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open positions are
     * considered
     */
    private fun calculate(tradingRecord: TradingRecord, finalIndex: Int) {
        calculate(tradingRecord)

        // Add accrued cash flow of open position
        if (tradingRecord.getCurrentPosition().isOpened) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex)
        }
    }

    /**
     * Fills with last value till the end of the series.
     */
    private fun fillToTheEnd() {
        if (barSeries.endIndex >= values.size) {
            val lastValue = values[values.size - 1]
            values.addAll(Collections.nCopies(barSeries.endIndex - values.size + 1, lastValue))
        }
    }

    companion object {
        /**
         * Calculates the ratio of intermediate prices.
         *
         * @param isLongTrade true, if the entry trade type is BUY
         * @param entryPrice  price ratio denominator
         * @param exitPrice   price ratio numerator
         */
        private fun getIntermediateRatio(isLongTrade: Boolean, entryPrice: Num, exitPrice: Num): Num {
            return if (isLongTrade) {
                exitPrice.div(entryPrice)
            } else {
                entryPrice.numOf(2).minus(exitPrice.div(entryPrice))
            }
        }

        /**
         * Adjusts (intermediate) price to incorporate trading costs.
         *
         * @param rawPrice    the gross asset price
         * @param holdingCost share of the holding cost per period
         * @param isLongTrade true, if the entry trade type is BUY
         */
        fun addCost(rawPrice: Num, holdingCost: Num, isLongTrade: Boolean): Num {
            return if (isLongTrade) {
                rawPrice.minus(holdingCost)
            } else {
                rawPrice.plus(holdingCost)
            }
        }

        /**
         * Determines the the valid final index to be considered.
         *
         * @param position   the position
         * @param finalIndex index up until cash flows of open positions are considered
         * @param maxIndex   maximal valid index
         */
        fun determineEndIndex(position: Position, finalIndex: Int, maxIndex: Int): Int {
            var idx = finalIndex
            // After closing of position, no further accrual necessary
            if (position.exit != null) {
                idx = min(position.exit!!.index, finalIndex)
            }
            // Accrual at most until maximal index of asset data
            if (idx > maxIndex) {
                idx = maxIndex
            }
            return idx
        }
    }
}