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
package org.ta4j.core

import org.ta4j.core.num.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Function

/**
 * End bar of a time period.
 *
 * Bar object is aggregated open/high/low/close/volume/etc. data over a time
 * period.
 */
interface Bar : Serializable {
    /**
     * @return the open price of the period
     */
    val openPrice: Num?

    /**
     * @return the low price of the period
     */
    val lowPrice: Num?

    /**
     * @return the high price of the period
     */
    val highPrice: Num?

    /**
     * @return the close price of the period
     */
    val closePrice: Num?

    /**
     * @return the whole tradeNum volume in the period
     */
    val volume: Num?

    /**
     * @return the number of trades in the period
     */
    val trades: Long

    /**
     * @return the whole traded amount of the period
     */
    val amount: Num?

    /**
     * @return the time period of the bar
     */
    val timePeriod: Duration?

    /**
     * @return the begin timestamp of the bar period
     */
    val beginTime: ZonedDateTime

    /**
     * @return the end timestamp of the bar period
     */
    val endTime: ZonedDateTime?

    /**
     * @param timestamp a timestamp
     * @return true if the provided timestamp is between the begin time and the end
     * time of the current period, false otherwise
     */
    fun inPeriod(timestamp: ZonedDateTime?): Boolean {
        return timestamp != null && !timestamp.isBefore(beginTime) && timestamp.isBefore(endTime)
    }

    /**
     * @return a human-friendly string of the end timestamp
     */
    val dateName: String?
        get() = endTime!!.format(DateTimeFormatter.ISO_DATE_TIME)

    /**
     * @return a even more human-friendly string of the end timestamp
     */
    val simpleDateName: String?
        get() = endTime!!.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * @return true if this is a bearish bar, false otherwise
     */
    val isBearish: Boolean
        get() {
            val openPrice = openPrice
            val closePrice = closePrice
            return openPrice != null && closePrice != null && closePrice.isLessThan(openPrice)
        }

    /**
     * @return true if this is a bullish bar, false otherwise
     */
    val isBullish: Boolean
        get() {
            val openPrice = openPrice
          //  val closePrice = closePrice
            return openPrice != null && closePrice != null && openPrice.isLessThan(closePrice!!)
        }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    @Deprecated("use corresponding function of {@link BarSeries}")
    fun addTrade(tradeVolume: Double, tradePrice: Double, numFunction: Function<Number?, Num>) {
        addTrade(numFunction.apply(tradeVolume), numFunction.apply(tradePrice))
    }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    @Deprecated("use corresponding function of {@link BarSeries}")
    fun addTrade(tradeVolume: String?, tradePrice: String?, numFunction: Function<Number?, Num>) {
        addTrade(numFunction.apply(BigDecimal(tradeVolume)), numFunction.apply(BigDecimal(tradePrice)))
    }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    fun addTrade(tradeVolume: Num?, tradePrice: Num?)
    fun addPrice(price: String?, numFunction: Function<Number?, Num>) {
        addPrice(numFunction.apply(BigDecimal(price)))
    }

    fun addPrice(price: Number?, numFunction: Function<Number?, Num>) {
        addPrice(numFunction.apply(price))
    }

    fun addPrice(price: Num?)
}