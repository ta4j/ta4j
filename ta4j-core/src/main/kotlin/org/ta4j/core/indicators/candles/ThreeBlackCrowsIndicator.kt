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
package org.ta4j.core.indicators.candles

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.num.*

/**
 * Three black crows indicator.
 *
 * @see [
 * http://www.investopedia.com/terms/t/three_black_crows.asp](http://www.investopedia.com/terms/t/three_black_crows.asp)
 */
/**
 * Constructor.
 *
 * @param series   the bar series
 * @param barCount the number of bars used to calculate the average lower shadow
 * @param factor   the factor used when checking if a candle has a very short
 * lower shadow
 */

class ThreeBlackCrowsIndicator(series: BarSeries?, barCount: Int, factor: Double) : CachedIndicator<Boolean>(series) {
    /**
     * Lower shadow
     */
    private val lowerShadowInd: LowerShadowIndicator

    /**
     * Average lower shadow
     */
    private val averageLowerShadowInd: SMAIndicator

    /**
     * Factor used when checking if a candle has a very short lower shadow
     */
    private val factor: Num
    private var whiteCandleIndex = -1

    init {
        lowerShadowInd = LowerShadowIndicator(series)
        averageLowerShadowInd = SMAIndicator(lowerShadowInd, barCount)
        this.factor = numOf(factor)
    }

    override fun calculate(index: Int): Boolean {
        if (index < 3) {
            // We need 4 candles: 1 white, 3 black
            return false
        }
        whiteCandleIndex = index - 3
        return (barSeries!!.getBar(whiteCandleIndex).isBullish && isBlackCrow(index - 2) && isBlackCrow(index - 1)
                && isBlackCrow(index))
    }

    /**
     * @param index the bar/candle index
     * @return true if the bar/candle has a very short lower shadow, false otherwise
     */
    private fun hasVeryShortLowerShadow(index: Int): Boolean {
        val currentLowerShadow = lowerShadowInd[index]
        // We use the white candle index to remove to bias of the previous crows
        val averageLowerShadow = averageLowerShadowInd.getValue(whiteCandleIndex)
        return currentLowerShadow.isLessThan(averageLowerShadow.times(factor))
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is declining, false otherwise
     */
    private fun isDeclining(index: Int): Boolean {
        val prevBar = barSeries!!.getBar(index - 1)
        val currBar = barSeries.getBar(index)
        val prevOpenPrice = prevBar.openPrice
        val prevClosePrice = prevBar.closePrice
        val currOpenPrice = currBar.openPrice
        val currClosePrice = currBar.closePrice

        // Opens within the body of the previous candle
        return (currOpenPrice!!.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice) // Closes below the previous close price
                && currClosePrice!!.isLessThan(prevClosePrice))
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is a black crow, false otherwise
     */
    private fun isBlackCrow(index: Int): Boolean {
        val prevBar = barSeries!!.getBar(index - 1)
        val currBar = barSeries.getBar(index)
        return if (currBar.isBearish) {
            if (prevBar.isBullish) {
                // First crow case
                hasVeryShortLowerShadow(index) && currBar.openPrice!!.isLessThan(prevBar.highPrice)
            } else {
                hasVeryShortLowerShadow(index) && isDeclining(index)
            }
        } else false
    }
}