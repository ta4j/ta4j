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

import org.ta4j.core.Bar
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord

/**
 * A [org.ta4j.core.Rule] which waits for a number of [Bar] after a
 * trade.
 *
 * Satisfied after a fixed number of bars since the last trade.
 */
class WaitForRule
/**
 * Constructor.
 *
 * @param tradeType    the type of the trade since we have to wait for
 * @param numberOfBars the number of bars to wait for
 */(
    /**
     * The type of the trade since we have to wait for
     */
    private val tradeType: TradeType,
    /**
     * The number of bars to wait for
     */
    private val numberOfBars: Int
) : AbstractRule() {
    /** This rule uses the `tradingRecord`.  */
    override fun isSatisfied(index: Int, tradingRecord: TradingRecord?): Boolean {
        var satisfied = false
        // No trading history, no need to wait
        if (tradingRecord != null) {
            val lastTrade = tradingRecord.getLastTrade(tradeType)
            if (lastTrade != null) {
                val currentNumberOfBars = index - lastTrade.index
                satisfied = currentNumberOfBars >= numberOfBars
            }
        }
        traceIsSatisfied(index, satisfied)
        return satisfied
    }
}