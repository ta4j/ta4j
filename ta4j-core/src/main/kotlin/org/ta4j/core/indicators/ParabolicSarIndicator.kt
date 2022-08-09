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
import org.ta4j.core.indicators.helpers.HighPriceIndicator
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.LowPriceIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.*
import kotlin.math.min

/**
 * Parabolic SAR indicator.
 *
 * @see [
 * https://www.investopedia.com/trading/introduction-to-parabolic-sar/](https://www.investopedia.com/trading/introduction-to-parabolic-sar/)
 *
 * @see [
 * https://www.investopedia.com/terms/p/parabolicindicator.asp](https://www.investopedia.com/terms/p/parabolicindicator.asp)
 */
/**
 * Constructor with custom parameters
 *
 * @param series    the bar series for this indicator
 * @param aF        acceleration factor
 * @param maxA      maximum acceleration
 * @param increment the increment step
 */
class ParabolicSarIndicator @JvmOverloads constructor(
    series: BarSeries,
    aF: Num? = series.numOf(0.02),
    maxA: Num? = series.numOf(0.2),
    increment: Num? = series.numOf(0.02)
) : RecursiveCachedIndicator<Num>(series) {
    private val maxAcceleration: Num?
    private val accelerationIncrement: Num?
    private val accelerationStart: Num?
    private var accelerationFactor: Num?=null
    private var currentTrend // true if uptrend, false otherwise
            = false
    private var startTrendIndex = 0 // index of start bar of the current trend
    private val lowPriceIndicator=LowPriceIndicator(series)
    private val highPriceIndicator=HighPriceIndicator(series)
    private var currentExtremePoint // the extreme point of the current calculation
            : Num? = null
    private var minMaxExtremePoint // depending on trend the maximum or minimum extreme point value of trend
            : Num? = null

    init {
        maxAcceleration = maxA
        accelerationIncrement = increment
        accelerationStart = aF
        accelerationFactor= aF
    }

    override fun calculate(index: Int): Num {
        var sar: Num = NaN.NaN
        if (index == barSeries!!.beginIndex) {
            return sar // no trend detection possible for the first value
        } else if (index == barSeries.beginIndex + 1) { // start trend detection
            currentTrend = barSeries.getBar(barSeries.beginIndex)
                .closePrice!!
                .isLessThan(barSeries.getBar(index).closePrice)
            if (!currentTrend) { // down trend
                sar = HighestValueIndicator(highPriceIndicator, 2)[index] // put the highest high value of
                // two first bars
                currentExtremePoint = sar
                minMaxExtremePoint = currentExtremePoint
            } else { // up trend
                sar = LowestValueIndicator(lowPriceIndicator, 2)[index] // put the lowest low value of two
                // first bars
                currentExtremePoint = sar
                minMaxExtremePoint = currentExtremePoint
            }
            return sar
        }
        val priorSar = getValue(index - 1)
        if (currentTrend) { // if up trend
            sar = priorSar.plus(accelerationFactor!!.times(currentExtremePoint!!.minus(priorSar)))
            currentTrend = lowPriceIndicator[index].isGreaterThan(sar)
            if (!currentTrend) { // check if sar touches the low price
                sar =
                    if (minMaxExtremePoint!!.isGreaterThan(highPriceIndicator[index])) minMaxExtremePoint!! // sar starts at the highest extreme point of previous up trend
                    else highPriceIndicator[index]
                currentTrend = false // switch to down trend and reset values
                startTrendIndex = index
                accelerationFactor = accelerationStart
                currentExtremePoint = barSeries.getBar(index).lowPrice // put point on max
                minMaxExtremePoint = currentExtremePoint
            } else { // up trend is going on
                val lowestPriceOfTwoPreviousBars = LowestValueIndicator(
                    lowPriceIndicator,
                    min(2, index - startTrendIndex)
                )[index-1]
                if (sar.isGreaterThan(lowestPriceOfTwoPreviousBars)) sar = lowestPriceOfTwoPreviousBars
                currentExtremePoint = HighestValueIndicator(highPriceIndicator, index - startTrendIndex + 1)[index]
                if (currentExtremePoint!!.isGreaterThan(minMaxExtremePoint)) {
                    incrementAcceleration()
                    minMaxExtremePoint = currentExtremePoint
                }
            }
        } else { // downtrend
            sar = priorSar.minus(accelerationFactor!!.times(priorSar.minus(currentExtremePoint!!)))
            currentTrend = highPriceIndicator[index].isGreaterThanOrEqual(sar)
            if (currentTrend) { // check if switch to up trend
                sar =
                    if (minMaxExtremePoint!!.isLessThan(lowPriceIndicator[index])) minMaxExtremePoint!! // sar starts at the lowest extreme point of previous down trend
                    else lowPriceIndicator[index]
                accelerationFactor = accelerationStart
                startTrendIndex = index
                currentExtremePoint = barSeries.getBar(index).highPrice
                minMaxExtremePoint = currentExtremePoint
            } else { // down trend io going on
                val highestPriceOfTwoPreviousBars = HighestValueIndicator(
                    highPriceIndicator,
                    min(2, index - startTrendIndex)
                )[index-1]
                if (sar.isLessThan(highestPriceOfTwoPreviousBars)) sar = highestPriceOfTwoPreviousBars
                currentExtremePoint = LowestValueIndicator(lowPriceIndicator, index - startTrendIndex + 1)                 [index]
                if (currentExtremePoint!!.isLessThan(minMaxExtremePoint)) {
                    incrementAcceleration()
                    minMaxExtremePoint = currentExtremePoint
                }
            }
        }
        return sar
    }

    /**
     * Increments the acceleration factor.
     */
    private fun incrementAcceleration() {
        accelerationFactor = if (accelerationFactor!!.isGreaterThanOrEqual(maxAcceleration)) {
            maxAcceleration
        } else {
            accelerationFactor!!.plus(accelerationIncrement!!)
        }
    }
}