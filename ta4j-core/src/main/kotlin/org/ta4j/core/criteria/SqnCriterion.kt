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
import org.ta4j.core.criteria.helpers.StandardDeviationCriterion
import org.ta4j.core.criteria.pnl.ProfitLossCriterion
import org.ta4j.core.num.*

/**
 * The SQN ("System Quality Number") Criterion.
 *
 * @see [https://indextrader.com.au/van-tharps-sqn/](https://indextrader.com.au/van-tharps-sqn/)
 */
/**
 * Constructor.
 *
 * @param criterion  the Criterion (e.g. ProfitLossCriterion or
 * ExpectancyCriterion)
 * @param nPositions the [.nPositions] (optional)
 */

class SqnCriterion @JvmOverloads constructor(
    private val criterion: AnalysisCriterion = ProfitLossCriterion(),
    /**
     * The number to be used for the part of `√(numberOfPositions)`
     * within the SQN-Formula when there are more than 100 trades. If this value is
     * `null`, then the number of positions calculated by
     * [.numberOfPositionsCriterion] is used instead.
     */
    private val nPositions: Int? = null
) : AbstractAnalysisCriterion() {
    private val standardDeviationCriterion = StandardDeviationCriterion(criterion)
    private val numberOfPositionsCriterion = NumberOfPositionsCriterion()

    override fun calculate(series: BarSeries, position: Position): Num {
        val numberOfPositions = numberOfPositionsCriterion.calculate(series, position)
        val pnl = criterion.calculate(series, position)
        val avgPnl = pnl.div(numberOfPositions)
        val stdDevPnl = standardDeviationCriterion.calculate(series, position)
        return if (stdDevPnl.isZero) {
            series.numOf(0)
        } else avgPnl.div(stdDevPnl).times(numberOfPositions.sqrt())
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
    }

    override fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num {
        if (tradingRecord.positions.isEmpty()) return series.numOf(0)
        var numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord)
        val pnl = criterion.calculate(series, tradingRecord)
        val avgPnl = pnl.div(numberOfPositions)
        val stdDevPnl = standardDeviationCriterion.calculate(series, tradingRecord)
        if (stdDevPnl.isZero) {
            return series.numOf(0)
        }
        if (nPositions != null && numberOfPositions.isGreaterThan(series.numOf(100))) {
            numberOfPositions = series.numOf(nPositions)
        }
        // SQN = (Average (PnL) / StdDev(PnL)) * SquareRoot(NumberOfTrades)
        return avgPnl.div(stdDevPnl).times(numberOfPositions.sqrt())
    }

    /** The higher the criterion value, the better.  */
    override fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean {
        return criterionValue1.isGreaterThan(criterionValue2)
    }
}