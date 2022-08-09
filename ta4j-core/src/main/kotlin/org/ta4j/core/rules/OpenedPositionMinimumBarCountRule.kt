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
package org.ta4j.core.rules

import org.ta4j.core.TradingRecord

/**
 * Rule to specify minimum bar count for opened position. Using this rule makes
 * sense only for exit rule (for entry rule
 * [OpenedPositionMinimumBarCountRule.isSatisfied]
 * always return false.
 */
class OpenedPositionMinimumBarCountRule(barCount: Int) : AbstractRule() {
    /** @return the [.barCount]
     */
    /**
     * Minimum bar count for opened trade.
     */
    val barCount: Int

    init {
        require(barCount >= 1) { "Bar count must be positive" }
        this.barCount = barCount
    }

    /**
     * Returns true if opened trade reached minimum bar count specified in
     * [OpenedPositionMinimumBarCountRule.barCount]
     *
     * @param index         the bar index
     * @param tradingRecord the required trading history
     * @return true if opened trade reached minimum bar count specified in
     * [OpenedPositionMinimumBarCountRule.barCount], otherwise false
     */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        if (tradingRecord!!.getCurrentPosition().isOpened) {
            val entryIndex = tradingRecord.getLastEntry()!!.index
            val currentBarCount = index - entryIndex
            return currentBarCount >= barCount
        }
        return false
    }
}