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
package org.ta4j.core.indicators

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.helpers.*
import org.ta4j.core.num.*
import kotlin.math.ln

/**
 * The Fisher Indicator.
 *
 * @apiNote Minimal deviations in last Num places possible. During the
 * calculations this indicator converts [Num] to
 * [double][Double]
 * @see [
 * http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf](http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf)
 *
 * @see [
 * https://www.investopedia.com/terms/f/fisher-transform.asp](https://www.investopedia.com/terms/f/fisher-transform.asp)
 */
/**
 * Constructor
 *
 * @param ref              the indicator
 * @param barCount         the time frame (usually 10)
 * @param alphaD           the alpha (usually 0.33 or 0.5)
 * @param betaD            the beta (usually 0.67 or 0.5)
 * @param gammaD           the gamma (usually 0.25 or 0.5)
 * @param deltaD           the delta (usually 0.5)
 * @param densityFactorD   the density factor (usually 1.0)
 * @param isPriceIndicator use true, if "ref" is a price indicator
 */

class FisherIndicator(
    private val ref: Indicator<Num>, barCount: Int, alphaD: Double, betaD: Double,
    gammaD: Double, deltaD: Double, densityFactorD: Double, isPriceIndicator: Boolean
) : RecursiveCachedIndicator<Num>(
    ref
) {
    private val intermediateValue: Indicator<Num>
    private val densityFactor: Num?
    private val gamma: Num?
    private val delta: Num?

    /**
     * Constructor.
     *
     * @param series the series
     */
    constructor(series: BarSeries?) : this(MedianPriceIndicator(series), 10) {}

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param price    the price indicator (usually [MedianPriceIndicator])
     * @param barCount the time frame (usually 10)
     */
    constructor(price: Indicator<Num>, barCount: Int) : this(
        price,
        barCount,
        0.33,
        0.67,
        ZERO_DOT_FIVE,
        ZERO_DOT_FIVE,
        1.0,
        true
    ) {
    }

    /**
     * Constructor (with gamma 0.5, delta 0.5).
     *
     * @param price    the price indicator (usually [MedianPriceIndicator])
     * @param barCount the time frame (usually 10)
     * @param alpha    the alpha (usually 0.33 or 0.5)
     * @param beta     the beta (usually 0.67 0.5 or)
     */
    constructor(price: Indicator<Num>, barCount: Int, alpha: Double, beta: Double) : this(
        price,
        barCount,
        alpha,
        beta,
        ZERO_DOT_FIVE,
        ZERO_DOT_FIVE,
        1.0,
        true
    ) {
    }

    /**
     * Constructor.
     *
     * @param price    the price indicator (usually [MedianPriceIndicator])
     * @param barCount the time frame (usually 10)
     * @param alpha    the alpha (usually 0.33 or 0.5)
     * @param beta     the beta (usually 0.67 or 0.5)
     * @param gamma    the gamma (usually 0.25 or 0.5)
     * @param delta    the delta (usually 0.5)
     */
    constructor(
        price: Indicator<Num>,
        barCount: Int,
        alpha: Double,
        beta: Double,
        gamma: Double,
        delta: Double
    ) : this(price, barCount, alpha, beta, gamma, delta, 1.0, true) {
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param ref              the indicator
     * @param barCount         the time frame (usually 10)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    constructor(ref: Indicator<Num>, barCount: Int, isPriceIndicator: Boolean) : this(
        ref,
        barCount,
        0.33,
        0.67,
        ZERO_DOT_FIVE,
        ZERO_DOT_FIVE,
        1.0,
        isPriceIndicator
    ) {
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param ref              the indicator
     * @param barCount         the time frame (usually 10)
     * @param densityFactor    the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    constructor(ref: Indicator<Num>, barCount: Int, densityFactor: Double, isPriceIndicator: Boolean) : this(
        ref,
        barCount,
        0.33,
        0.67,
        ZERO_DOT_FIVE,
        ZERO_DOT_FIVE,
        densityFactor,
        isPriceIndicator
    ) {
    }

    init {
        gamma = numOf(gammaD)
        delta = numOf(deltaD)
        densityFactor = numOf(densityFactorD)
        val alpha = numOf(alphaD)
        val beta = numOf(betaD)
        val periodHigh: Indicator<Num> = HighestValueIndicator(
            if (isPriceIndicator) HighPriceIndicator(ref.barSeries) else ref, barCount
        )
        val periodLow: Indicator<Num> = LowestValueIndicator(
            if (isPriceIndicator) LowPriceIndicator(ref.barSeries) else ref, barCount
        )
        intermediateValue = object : RecursiveCachedIndicator<Num>(ref) {
            override fun calculate(index: Int): Num {
                if (index <= 0) {
                    return numOf(0)
                }

                // Value = (alpha * 2 * ((ref - MinL) / (MaxH - MinL) - 0.5) + beta *
                // priorValue) / densityFactor
                val currentRef = ref[index]
                val minL = periodLow[index]
                val maxH = periodHigh[index]
                val term1 = currentRef.minus(minL).div(maxH.minus(minL)).minus(numOf(ZERO_DOT_FIVE))
                val term2 = alpha.times(numOf(2)).times(term1)
                val term3 = term2.plus(beta.times(getValue(index - 1)))
                return term3.div(densityFactor)
            }
        }
    }

    override fun calculate(index: Int): Num {
        if (index <= 0) {
            return numOf(0)
        }
        var value = intermediateValue[index]
        if (value.isGreaterThan(numOf(VALUE_MAX))) {
            value = numOf(VALUE_MAX)
        } else if (value.isLessThan(numOf(VALUE_MIN))) {
            value = numOf(VALUE_MIN)
        }

        // Fisher = gamma * Log((1 + Value) / (1 - Value)) + delta * priorFisher
        val term1 = numOf(ln(numOf(1).plus(value).div(numOf(1).minus(value)).doubleValue()))
        val term2 = getValue(index - 1)
        return gamma!!.times(term1).plus(delta!!.times(term2))
    }

    companion object {
        private const val ZERO_DOT_FIVE = 0.5
        private const val VALUE_MAX = 0.999
        private const val VALUE_MIN = -0.999
    }
}