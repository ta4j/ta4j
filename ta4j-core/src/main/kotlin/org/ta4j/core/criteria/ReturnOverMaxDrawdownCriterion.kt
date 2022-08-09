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
import org.ta4j.core.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Reward risk ratio criterion, defined as the [gross][GrossReturnCriterion] over the [maximum drawdown][MaximumDrawdownCriterion].
 */
class ReturnOverMaxDrawdownCriterion : AbstractAnalysisCriterion() {
    private val grossReturnCriterion: AnalysisCriterion = GrossReturnCriterion()
    private val maxDrawdownCriterion: AnalysisCriterion = MaximumDrawdownCriterion()
    override fun calculate(series: BarSeries, position: Position): Num {
        val maxDrawdown = maxDrawdownCriterion.calculate(series, position)
        return if (maxDrawdown.isZero) {
            NaN.NaN
        } else {
            val totalProfit = grossReturnCriterion.calculate(series, position)
            totalProfit.div(maxDrawdown)
        }
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val maxDrawdown = maxDrawdownCriterion.calculate(series, tradingRecord)
        return if (maxDrawdown.isZero) {
            NaN.NaN
        } else {
            val totalProfit = grossReturnCriterion.calculate(series, tradingRecord)
            totalProfit.div(maxDrawdown)
        }
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }
}