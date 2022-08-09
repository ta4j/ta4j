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
import org.ta4j.core.analysis.CashFlow
import org.ta4j.core.num.*

/**
 * Maximum drawdown criterion.
 *
 * @see [https://en.wikipedia.org/wiki/Drawdown_
](http://en.wikipedia.org/wiki/Drawdown_%28economics%29) */
class MaximumDrawdownCriterion : AbstractAnalysisCriterion() {
    override fun calculate(series: BarSeries, position: Position): Num {
        if (position.entry != null && position.exit != null) {
            val cashFlow = CashFlow(series, position)
            return calculateMaximumDrawdown(series, cashFlow)
        }
        return series.numOf(0)
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val cashFlow = CashFlow(series, tradingRecord)
        return calculateMaximumDrawdown(series, cashFlow)
    }

    /** The lower the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isLessThan(criterionValue2)
    }

    /**
     * Calculates the maximum drawdown from a cash flow over a series.
     *
     * @param series   the bar series
     * @param cashFlow the cash flow
     * @return the maximum drawdown from a cash flow over a series
     */
    private fun calculateMaximumDrawdown(series: BarSeries, cashFlow: CashFlow): Num {
        var maximumDrawdown = series.numOf(0)
        var maxPeak = series.numOf(0)
        if (!series.isEmpty) {
            // The series is not empty
            for (i in series.beginIndex..series.endIndex) {
                val value = cashFlow[i]
                if (value.isGreaterThan(maxPeak)) {
                    maxPeak = value
                }
                val drawdown = (maxPeak - value)/maxPeak
                if (drawdown.isGreaterThan(maximumDrawdown)) {
                    maximumDrawdown = drawdown
                }
            }
        }
        return maximumDrawdown
    }
}