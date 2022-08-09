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
import org.ta4j.core.num.*

/**
 * Versus "buy and hold" criterion.
 *
 * Compares the value of a provided [criterion][AnalysisCriterion] with the
 * value of a "buy and hold".
 */
class VersusBuyAndHoldCriterion
/**
 * Constructor.
 *
 * @param criterion an analysis criterion to be compared
 */(private val criterion: AnalysisCriterion) : AbstractAnalysisCriterion() {
    override fun calculate(series: BarSeries, position: Position): Num {
        val fakeRecord = createBuyAndHoldTradingRecord(series)
        return criterion.calculate(series, position).div(criterion.calculate(series, fakeRecord))
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val fakeRecord = createBuyAndHoldTradingRecord(series)
        return criterion.calculate(series, tradingRecord).div(criterion.calculate(series, fakeRecord))
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }

    private fun createBuyAndHoldTradingRecord(
        series: BarSeries,
        beginIndex: Int = series.beginIndex,
        endIndex: Int = series.endIndex
    ): TradingRecord {
        val fakeRecord: TradingRecord = BaseTradingRecord()
        fakeRecord.enter(beginIndex, series.getBar(beginIndex).closePrice, series.numOf(1))
        fakeRecord.exit(endIndex, series.getBar(endIndex).closePrice, series.numOf(1))
        return fakeRecord
    }
}