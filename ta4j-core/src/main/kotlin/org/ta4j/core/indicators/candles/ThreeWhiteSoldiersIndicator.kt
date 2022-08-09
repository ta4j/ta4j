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
 * Three white soldiers indicator.
 *
 * @see [
 * http://www.investopedia.com/terms/t/three_white_soldiers.asp](http://www.investopedia.com/terms/t/three_white_soldiers.asp)
 */
/**
 * Constructor.
 *
 * @param series   the bar series
 * @param barCount the number of bars used to calculate the average upper shadow
 * @param factor   the factor used when checking if a candle has a very short
 * upper shadow
 */

class ThreeWhiteSoldiersIndicator(series: BarSeries?, barCount: Int, factor: Num) : CachedIndicator<Boolean>(series) {
    /**
     * Upper shadow
     */
    private val upperShadowInd: UpperShadowIndicator

    /**
     * Average upper shadow
     */
    private val averageUpperShadowInd: SMAIndicator

    /**
     * Factor used when checking if a candle has a very short upper shadow
     */
    private val factor: Num
    private var blackCandleIndex = -1

    init {
        upperShadowInd = UpperShadowIndicator(series)
        averageUpperShadowInd = SMAIndicator(upperShadowInd, barCount)
        this.factor = factor
    }

    override fun calculate(index: Int): Boolean {
        if (index < 3) {
            // We need 4 candles: 1 black, 3 white
            return false
        }
        blackCandleIndex = index - 3
        return (barSeries!!.getBar(blackCandleIndex).isBearish && isWhiteSoldier(index - 2)
                && isWhiteSoldier(index - 1) && isWhiteSoldier(index))
    }

    /**
     * @param index the bar/candle index
     * @return true if the bar/candle has a very short upper shadow, false otherwise
     */
    private fun hasVeryShortUpperShadow(index: Int): Boolean {
        val currentUpperShadow = upperShadowInd[index]
        // We use the black candle index to remove to bias of the previous soldiers
        val averageUpperShadow = averageUpperShadowInd.getValue(blackCandleIndex)
        return currentUpperShadow.isLessThan(averageUpperShadow * factor)
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is growing, false otherwise
     */
    private fun isGrowing(index: Int): Boolean {
        val prevBar = barSeries!!.getBar(index - 1)
        val currBar = barSeries.getBar(index)
        val prevOpenPrice = prevBar.openPrice
        val prevClosePrice = prevBar.closePrice
        val currOpenPrice = currBar.openPrice
        val currClosePrice = currBar.closePrice

        // Opens within the body of the previous candle
        return (currOpenPrice!!.isGreaterThan(prevOpenPrice) && currOpenPrice.isLessThan(prevClosePrice) // Closes above the previous close price
                && currClosePrice!!.isGreaterThan(prevClosePrice))
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is a white soldier, false otherwise
     */
    private fun isWhiteSoldier(index: Int): Boolean {
        val prevBar = barSeries!!.getBar(index - 1)
        val currBar = barSeries.getBar(index)
        return if (currBar.isBullish) {
            if (prevBar.isBearish) {
                // First soldier case
                hasVeryShortUpperShadow(index) && currBar.openPrice!!.isGreaterThan(prevBar.lowPrice)
            } else {
                hasVeryShortUpperShadow(index) && isGrowing(index)
            }
        } else false
    }
}