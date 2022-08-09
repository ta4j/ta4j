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

/**
 * A trading strategy.
 *
 * A strategy is a pair of complementary [rules][Rule]. It may recommend to
 * enter or to exit. Recommendations are based respectively on the entry rule or
 * on the exit rule.
 */
interface Strategy {
    /**
     * @return the name of the strategy
     */
    val name: String?

    /**
     * @return the entry rule
     */
    val entryRule: Rule

    /**
     * @return the exit rule
     */
    val exitRule: Rule

    /**
     * @param strategy the other strategy
     * @return the AND combination of two [strategies][Strategy]
     */
    fun and(strategy: Strategy): Strategy

    /**
     * @param strategy the other strategy
     * @return the OR combination of two [strategies][Strategy]
     */
    fun or(strategy: Strategy): Strategy

    /**
     * @param name           the name of the strategy
     * @param strategy       the other strategy
     * @param unstablePeriod number of bars that will be strip off for this strategy
     * @return the AND combination of two [strategies][Strategy]
     */
    fun and(name: String?, strategy: Strategy, unstablePeriod: Int): Strategy

    /**
     * @param name           the name of the strategy
     * @param strategy       the other strategy
     * @param unstablePeriod number of bars that will be strip off for this strategy
     * @return the OR combination of two [strategies][Strategy]
     */
    fun or(name: String?, strategy: Strategy, unstablePeriod: Int): Strategy

    /**
     * @return the opposite of the [strategy][Strategy]
     */
    fun opposite(): Strategy
    /**
     * @return unstablePeriod number of bars that will be strip off for this
     * strategy
     */
    /**
     * @param unstablePeriod number of bars that will be strip off for this strategy
     */
    var unstablePeriod: Int

    /**
     * @param index a bar index
     * @return true if this strategy is unstable at the provided index, false
     * otherwise (stable)
     */
    fun isUnstableAt(index: Int): Boolean

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend a trade, false otherwise (no recommendation)
     */
    fun shouldOperate(index: Int, tradingRecord: TradingRecord): Boolean {
        val position = tradingRecord.getCurrentPosition()
        if (position.isNew) {
            return shouldEnter(index, tradingRecord)
        } else if (position.isOpened) {
            return shouldExit(index, tradingRecord)
        }
        return false
    }

    /**
     * @param index the bar index
     * @return true to recommend to enter, false otherwise
     */
    fun shouldEnter(index: Int): Boolean {
        return shouldEnter(index, null)
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to enter, false otherwise
     */
    fun shouldEnter(index: Int, tradingRecord: TradingRecord?): Boolean {
        return !isUnstableAt(index) && entryRule.isSatisfied(index, tradingRecord)
    }

    /**
     * @param index the bar index
     * @return true to recommend to exit, false otherwise
     */
    fun shouldExit(index: Int): Boolean {
        return shouldExit(index, null)
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to exit, false otherwise
     */
    fun shouldExit(index: Int, tradingRecord: TradingRecord?): Boolean {
        return !isUnstableAt(index) && exitRule.isSatisfied(index, tradingRecord)
    }
}