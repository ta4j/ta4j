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
package org.ta4j.core.indicators.bollinger

import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.numeric.NumericIndicator
import org.ta4j.core.num.*

/**
 * A facade to create the 3 Bollinger Band indicators. A simple moving average
 * of close price is used as the middle band. The BB bandwidth and %B indicators
 * can also be created on demand.
 *
 *
 *
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall there
 * is less caching and probably better performance.
 */
class BollingerBandFacade {
    private val price: NumericIndicator
    private val middle: NumericIndicator
    private val upper: NumericIndicator
    private val lower: NumericIndicator

    /**
     * Create the BollingerBands facade based on the close price of a bar series
     *
     * @param barSeries a bar series
     * @param barCount  the number of periods used for the indicators
     * @param k         the multiplier used to calculate the upper and lower bands
     */
    constructor(barSeries: BarSeries?, barCount: Int, k: Number) {
        price = NumericIndicator.of(ClosePriceIndicator(barSeries))
        middle = NumericIndicator.of(price.sma(barCount))
        val stdev = price.stddev(barCount)
        upper = middle + stdev * k
        lower = middle - stdev * k
    }

    /**
     * Create the BollingerBands facade based on the provided indicator
     *
     * @param indicator an indicator
     * @param barCount  the number of periods used for the indicators
     * @param k         the multiplier used to calculate the upper and lower bands
     */
    constructor(indicator: Indicator<Num>, barCount: Int, k: Number) {
        price = NumericIndicator.of(indicator)
        middle = NumericIndicator.of(price.sma(barCount))
        val stdev = price.stddev(barCount)
        upper = middle + stdev * k
        lower = middle - stdev * k
    }

    /**
     * A fluent BB middle band
     *
     * @return a NumericIndicator wrapped around a cached SMAIndicator of close
     * price.
     */
    fun middle(): NumericIndicator {
        return middle
    }

    /**
     * A fluent BB upper band
     *
     * @return an object that calculates the sum of BB middle and a multiple of
     * standard deviation.
     */
    fun upper(): NumericIndicator {
        return upper
    }

    /**
     * A fluent BB lower band
     *
     * @return an object that calculates the difference between BB middle and a
     * multiple of standard deviation.
     */
    fun lower(): NumericIndicator {
        return lower
    }

    /**
     * A fluent BB Bandwidth indicator
     *
     * @return an object that calculates BB bandwidth from BB upper, lower and
     * middle
     */
    fun bandwidth(): NumericIndicator {
        return (upper - lower) / middle * 100
    }

    /**
     * A fluent %B indicator
     *
     * @return an object that calculates %B from close price, BB upper and lower
     */
    fun percentB(): NumericIndicator {
        return (price - lower) / (upper - lower)
    }
}