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
package org.ta4j.core

import org.slf4j.LoggerFactory
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.analysis.cost.CostModel
import org.ta4j.core.analysis.cost.ZeroCostModel
import org.ta4j.core.num.*

/**
 * A manager for [BarSeries] objects.
 *
 * Used for backtesting. Allows to run a [trading strategy][Strategy] over
 * the managed bar series.
 */
class BarSeriesManager
/**
 * Constructor.
 *
 * @param barSeries the bar series to be managed
 */ @JvmOverloads constructor(
    /** The managed bar series  */
    val barSeries: BarSeries,
    /** The trading cost models  */
    private val transactionCostModel: CostModel? = ZeroCostModel(),
    private val holdingCostModel: CostModel = ZeroCostModel()
) {
    /** The logger  */
    private val log = LoggerFactory.getLogger(javaClass)
    /**
     * Constructor.
     *
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    /**
     * @return the managed bar series
     */
//    fun getBarSeries(): BarSeries {
//        return barSeries
//    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * Opens the position with a [TradeType] BUY trade.
     *
     * @param strategy    the trading strategy
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    fun run(strategy: Strategy, startIndex: Int, finishIndex: Int): TradingRecord {
        return run(strategy, TradeType.BUY, barSeries.numOf(1), startIndex, finishIndex)
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * Opens the position with a trade of [tradeType][TradeType].
     *
     * @param strategy    the trading strategy
     * @param tradeType   the [TradeType] used to open the position
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    fun run(strategy: Strategy, tradeType: TradeType?, startIndex: Int, finishIndex: Int): TradingRecord {
        return run(strategy, tradeType, barSeries.numOf(1), startIndex, finishIndex)
    }
    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * @param strategy    the trading strategy
     * @param tradeType   the [TradeType] used to open the trades
     * @param amount      the amount used to open/close the trades
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    /**
     * Runs the provided strategy over the managed series.
     *
     * @param strategy  the trading strategy
     * @param tradeType the [TradeType] used to open the position
     * @param amount    the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    /**
     * Runs the provided strategy over the managed series.
     *
     * Opens the position with a trade of [tradeType][TradeType].
     *
     * @param strategy  the trading strategy
     * @param tradeType the [TradeType] used to open the position
     * @return the trading record coming from the run
     */
    /**
     * Runs the provided strategy over the managed series.
     *
     * Opens the position with a [TradeType] BUY trade.
     *
     * @return the trading record coming from the run
     */
    @JvmOverloads
    fun run(
        strategy: Strategy,
        tradeType: TradeType? = TradeType.BUY,
        amount: Num? = barSeries.numOf(1),
        startIndex: Int = barSeries.beginIndex,
        finishIndex: Int = barSeries.endIndex
    ): TradingRecord {
        val runBeginIndex = Math.max(startIndex, barSeries.beginIndex)
        val runEndIndex = Math.min(finishIndex, barSeries.endIndex)
        if (log.isTraceEnabled) {
            log.trace(
                "Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex,
                strategy, tradeType
            )
        }
        val tradingRecord: TradingRecord = BaseTradingRecord(tradeType, transactionCostModel, holdingCostModel)
        for (i in runBeginIndex..runEndIndex) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, barSeries.getBar(i).closePrice, amount)
            }
        }
        if (!tradingRecord.isClosed) {
            // If the last position is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of
            // bars
            val seriesMaxSize = Math.max(barSeries.endIndex + 1, barSeries.barData.size)
            for (i in runEndIndex + 1 until seriesMaxSize) {
                // For each bar after the end index of this run...
                // --> Trying to close the last position
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradingRecord.operate(i, barSeries.getBar(i).closePrice, amount)
                    break
                }
            }
        }
        return tradingRecord
    }
}