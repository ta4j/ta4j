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

import org.ta4j.core.BarSeries
import org.ta4j.core.Position
import org.ta4j.core.TradingRecord
import org.ta4j.core.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.num.*

/**
 * Calculates the average return per bar criterion.
 *
 *
 *
 * The [gross return][GrossReturnCriterion] raised to the power of 1
 * divided by [number of bars][NumberOfBarsCriterion].
 */
class AverageReturnPerBarCriterion : AbstractAnalysisCriterion() {
    private val grossReturn = GrossReturnCriterion()
    private val numberOfBars = NumberOfBarsCriterion()

    override fun calculate(series: BarSeries, position: Position): Num {
        val bars = numberOfBars.calculate(series, position)
        return if (bars.isEqual(series.numOf(0))) {
            series.numOf(1)
        } else grossReturn.calculate(series, position).pow(series.numOf(1).div(bars))
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val bars = numberOfBars.calculate(series, tradingRecord)
        return if (bars.isEqual(series.numOf(0))) {
            series.numOf(1)
        } else grossReturn.calculate(series, tradingRecord).pow(series.numOf(1).div(bars))
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }
}