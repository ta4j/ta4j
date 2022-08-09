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
import org.ta4j.core.num.*
import java.util.*

/**
 * Distance From Moving Average (close - MA)/MA
 *
 * @see [
 * https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma
](https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma) *
 */
/**
 * Constructor.
 *
 * @param series        the bar series [BarSeries].
 * @param movingAverage the moving average.
 */

class DistanceFromMAIndicator(series: BarSeries?, movingAverage: Indicator<Num>) : CachedIndicator<Num>(series) {
    private val movingAverage: Indicator<Num>

    init {
        require(supportedMovingAverages.contains(movingAverage.javaClass)) { "Passed indicator must be a moving average based indicator. $movingAverage" }
        this.movingAverage = movingAverage
    }

    override fun calculate(index: Int): Num {
        val currentBar = barSeries!!.getBar(index)
        val closePrice = currentBar.closePrice
        val maValue = movingAverage[index]
        return closePrice!!.minus(maValue).div(maValue)
    }

    companion object {
        private val supportedMovingAverages: Set<Class<*>> = HashSet<Class<*>>(
            listOf(
                EMAIndicator::class.java,
                DoubleEMAIndicator::class.java,
                TripleEMAIndicator::class.java,
                SMAIndicator::class.java,
                WMAIndicator::class.java,
                ZLEMAIndicator::class.java,
                HMAIndicator::class.java,
                KAMAIndicator::class.java,
                LWMAIndicator::class.java,
                AbstractEMAIndicator::class.java,
                MMAIndicator::class.java
            )
        )
    }
}