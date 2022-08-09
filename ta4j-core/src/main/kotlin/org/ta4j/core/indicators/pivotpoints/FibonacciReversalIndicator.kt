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
package org.ta4j.core.indicators.pivotpoints

import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*

/**
 * Fibonacci Reversal Indicator.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points)
 */
/**
 * Constructor.
 *
 * Calculates a (fibonacci) reversal
 *
 * @param pivotPointIndicator the [PivotPointIndicator] for this reversal
 * @param fibonacciFactor     the fibonacci factor for this reversal
 * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of
 * the reversal (SUPPORT, RESISTANCE)
 */

class FibonacciReversalIndicator(
    private val pivotPointIndicator: PivotPointIndicator, fibonacciFactor: Double,
    fibReversalTyp: FibReversalTyp
) : RecursiveCachedIndicator<Num>(pivotPointIndicator) {
    private val fibReversalTyp: FibReversalTyp
    private val fibonacciFactor: Num

    enum class FibReversalTyp {
        SUPPORT, RESISTANCE
    }

    /**
     * Standard Fibonacci factors
     */
    enum class FibonacciFactor(val factor: Double) {
        FACTOR_1(0.382), FACTOR_2(0.618), FACTOR_3(1.0);

    }

    init {
        this.fibonacciFactor = numOf(fibonacciFactor)
        this.fibReversalTyp = fibReversalTyp
    }

    /**
     * Constructor.
     *
     * Calculates a (fibonacci) reversal
     *
     * @param pivotPointIndicator the [PivotPointIndicator] for this reversal
     * @param fibonacciFactor     the [FibonacciFactor] factor for this
     * reversal
     * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of
     * the reversal (SUPPORT, RESISTANCE)
     */
    constructor(
        pivotPointIndicator: PivotPointIndicator, fibonacciFactor: FibonacciFactor,
        fibReversalTyp: FibReversalTyp
    ) : this(pivotPointIndicator, fibonacciFactor.factor, fibReversalTyp)

    override fun calculate(index: Int): Num {
        val barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index)
        if (barsOfPreviousPeriod.isEmpty()) return NaN.NaN
        val bar = barSeries!!.getBar(barsOfPreviousPeriod[0])
        var high = bar.highPrice!!
        var low = bar.lowPrice!!
        for (i in barsOfPreviousPeriod) {
            high = barSeries.getBar(i).highPrice!!.max(high)
            low = barSeries.getBar(i).lowPrice!!.min(low)
        }
        return if (fibReversalTyp == FibReversalTyp.RESISTANCE) {
            pivotPointIndicator[index].plus(fibonacciFactor.times(high.minus(low)))
        } else pivotPointIndicator[index].minus(fibonacciFactor.times(high.minus(low)))
    }
}