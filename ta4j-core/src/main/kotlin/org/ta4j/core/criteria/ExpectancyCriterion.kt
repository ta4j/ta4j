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
import org.ta4j.core.criteria.pnl.ProfitLossRatioCriterion
import org.ta4j.core.num.*

/**
 * Expectancy criterion (Kelly Criterion).
 *
 * Measures the positive or negative expectancy. The higher the positive number,
 * the better a winning expectation. A negative number means there is only
 * losing expectations.
 *
 * @see [https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/](https://www.straightforex.com/advanced-forex-course/money-management/two-important-things-to-be-considered/)
 */
class ExpectancyCriterion : AbstractAnalysisCriterion() {
    private val profitLossRatioCriterion = ProfitLossRatioCriterion()
    private val numberOfPositionsCriterion = NumberOfPositionsCriterion()
    private val numberOfWinningPositionsCriterion = NumberOfWinningPositionsCriterion()
    override fun calculate(series: BarSeries, position: Position): Num {
        val profitLossRatio = profitLossRatioCriterion.calculate(series, position)
        val numberOfPositions = numberOfPositionsCriterion.calculate(series, position)
        val numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, position)
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions)
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        val profitLossRatio = profitLossRatioCriterion.calculate(series, tradingRecord)
        val numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord)
        val numberOfWinningPositions = numberOfWinningPositionsCriterion.calculate(series, tradingRecord)
        return calculate(series, profitLossRatio, numberOfWinningPositions, numberOfPositions)
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }

    private fun calculate(
        series: BarSeries, profitLossRatio: Num?, numberOfWinningPositions: Num?,
        numberOfAllPositions: Num?
    ): Num {
        val one = series.numOf(1)
        if (numberOfAllPositions!!.isZero || profitLossRatio!!.isZero) {
            return series.numOf(0)
        }
        // Expectancy = (1 + AW/AL) * (ProbabilityToWinOnePosition - 1)
        val probabiltyToWinOnePosition = numberOfWinningPositions!!.div(numberOfAllPositions)
        return one.plus(profitLossRatio).times(probabiltyToWinOnePosition.minus(one))
    }
}