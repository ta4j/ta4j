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
import org.ta4j.core.analysis.Returns
import org.ta4j.core.num.*

//import java.util.*

/**
 * Expected Shortfall criterion.
 *
 * Measures the expected shortfall of the strategy log-return time-series.
 *
 * @see [https://en.wikipedia.org/wiki/Expected_shortfall](https://en.wikipedia.org/wiki/Expected_shortfall)
 */
class ExpectedShortfallCriterion
/**
 * Constructor
 *
 * @param confidence the confidence level
 */(
    /**
     * Confidence level as absolute value (e.g. 0.95)
     */
    private val confidence: Double
) : AbstractAnalysisCriterion() {
    override fun calculate(series: BarSeries, position: Position): Num {
        if (position.entry != null && position.exit != null) {
            val returns = Returns(series, position, Returns.ReturnType.LOG)
            return calculateES(returns, confidence)
        }
        return series.numOf(0)
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val returns = Returns(series, tradingRecord, Returns.ReturnType.LOG)
        return calculateES(returns, confidence)
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }

    companion object {
        /**
         * Calculates the Expected Shortfall on the return series
         *
         * @param returns    the corresponding returns
         * @param confidence the confidence level
         * @return the relative Expected Shortfall
         */
        private fun calculateES(returns: Returns, confidence: Double): Num {
            // select non-NaN returns
            val returnRates = returns.values.subList(1, returns.size + 1)
            val zero = returns.numOf(0)
            var expectedShortfall = zero
            if (returnRates.isNotEmpty()) {
                // F(x_var) >= alpha (=1-confidence)
                val nInBody = (returns.size * confidence).toInt()
                val nInTail = returns.size - nInBody

                // calculate average tail loss
                returnRates.sort()
                val tailEvents = returnRates.subList(0, nInTail)
                var sum = zero
                for (i in 0 until nInTail) {
                    sum = sum.plus(tailEvents[i])
                }
                expectedShortfall = sum.div(returns.numOf(nInTail))

                // ES is non-positive
                if (expectedShortfall.isGreaterThan(zero)) {
                    expectedShortfall = zero
                }
            }
            return expectedShortfall
        }
    }
}