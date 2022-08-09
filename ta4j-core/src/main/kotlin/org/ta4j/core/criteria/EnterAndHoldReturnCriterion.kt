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
package org.ta4j.core.criteria

import org.ta4j.core.*
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.*

/**
 * Enter and hold criterion.
 *
 * Calculates the return if a enter-and-hold strategy was used:
 *
 *
 *  * For [.tradeType] = [TradeType.BUY]: buying on the first bar
 * and selling on the last bar.
 *  * For [.tradeType] = [TradeType.SELL]: selling on the first bar
 * and buying on the last bar.
 *
 *
 * @see [http://en.wikipedia.org/wiki/Buy_and_hold](http://en.wikipedia.org/wiki/Buy_and_hold)
 */
class EnterAndHoldReturnCriterion
/**
 * Constructor
 *
 *
 *
 * For buy-and-hold strategy.
 */ @JvmOverloads constructor(private val tradeType: TradeType = TradeType.BUY) : AbstractAnalysisCriterion() {
    /**
     * Constructor.
     *
     * @param tradeType the [TradeType] used to open the position
     */
    override fun calculate(series: BarSeries, position: Position): Num {
        return createEnterAndHoldTrade(series, position.entry!!.index, position.exit!!.index)
            .getGrossReturn(series)
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        return createEnterAndHoldTrade(series).getGrossReturn(series)
    }

    /** The higher the criterion value the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }

    private fun createEnterAndHoldTrade(
        series: BarSeries,
        beginIndex: Int = series.beginIndex,
        endIndex: Int = series.endIndex
    ): Position {
        val position = Position(tradeType)
        position.operate(beginIndex, series.getBar(beginIndex).closePrice, series.numOf(1))
        position.operate(endIndex, series.getBar(endIndex).closePrice, series.numOf(1))
        return position
    }
}