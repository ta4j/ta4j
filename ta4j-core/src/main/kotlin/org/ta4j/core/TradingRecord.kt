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

import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import java.io.Serializable

/**
 * A history/record of a trading session.
 *
 * Holds the full trading record when running a [strategy][Strategy]. It is
 * used to:
 *
 *  * check to satisfaction of some trading rules (when running a strategy)
 *  * analyze the performance of a trading strategy
 *
 */
interface TradingRecord : Serializable {
    /**
     * @return the entry type (BUY or SELL) of the first trade in the trading
     * session
     */
    val startingType: TradeType

    /**
     * @return the name of the TradingRecord
     */
    val name: String?

    /**
     * Places a trade in the trading record.
     *
     * @param index the index to place the trade
     */
    fun operate(index: Int) {
        operate(index, NaN.Companion.NaN, NaN.Companion.NaN)
    }

    /**
     * Places a trade in the trading record.
     *
     * @param index  the index to place the trade
     * @param price  the trade price
     * @param amount the trade amount
     */
    fun operate(index: Int, price: Num?, amount: Num?)

    /**
     * Places an entry trade in the trading record.
     *
     * @param index the index to place the entry
     * @return true if the entry has been placed, false otherwise
     */
    fun enter(index: Int): Boolean {
        return enter(index, NaN.Companion.NaN, NaN.Companion.NaN)
    }

    /**
     * Places an entry trade in the trading record.
     *
     * @param index  the index to place the entry
     * @param price  the trade price
     * @param amount the trade amount
     * @return true if the entry has been placed, false otherwise
     */
    fun enter(index: Int, price: Num?, amount: Num?): Boolean

    /**
     * Places an exit trade in the trading record.
     *
     * @param index the index to place the exit
     * @return true if the exit has been placed, false otherwise
     */
    fun exit(index: Int): Boolean {
        return exit(index, NaN.Companion.NaN, NaN.Companion.NaN)
    }

    /**
     * Places an exit trade in the trading record.
     *
     * @param index  the index to place the exit
     * @param price  the trade price
     * @param amount the trade amount
     * @return true if the exit has been placed, false otherwise
     */
    fun exit(index: Int, price: Num?, amount: Num?): Boolean

    /**
     * @return true if no position is open, false otherwise
     */
    val isClosed: Boolean
        get() = !getCurrentPosition().isOpened

    /**
     * @return the recorded closed positions
     */
    val positions: List<Position>

    /**
     * @return the number of recorded closed positions
     */
    fun getPositionCount(): Int {
        return positions.size
    }

    /**
     * @return the current (open) position
     */
    fun getCurrentPosition(): Position

    /**
     * @return the last closed position recorded
     */
    fun getLastPosition(): Position? {
        val positions = positions
        return if (!positions.isEmpty()) {
            positions[positions.size - 1]
        } else null
    }

    /**
     * @return the last trade recorded
     */
    fun getLastTrade(): Trade?

    /**
     * @param tradeType the type of the trade to get the last of
     * @return the last trade (of the provided type) recorded
     */
    fun getLastTrade(tradeType: TradeType): Trade?

    /**
     * @return the last entry trade recorded
     */
    fun getLastEntry(): Trade?

    /**
     * @return the last exit trade recorded
     */
    fun getLastExit(): Trade?
}