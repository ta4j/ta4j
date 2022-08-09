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
 * Sequence of [bars][Bar] separated by a predefined period (e.g. 15
 * minutes, 1 day, etc.)
 *
 * Notably, a [bar series][BarSeries] can be:
 *
 *  * the base of [indicator][Indicator] calculations
 *  * constrained between begin and end indexes (e.g. for some backtesting
 * cases)
 *  * limited to a fixed number of bars (e.g. for actual trading)
 *
 */
interface BarSeries : Serializable {
    /**
     * @return the name of the series
     */
    val name: String

    /**
     * @param i an index
     * @return the bar at the i-th position
     */
    fun getBar(i: Int): Bar

    /**
     * @return the first bar of the series
     */
    val firstBar: Bar
        get() = getBar(beginIndex)

    /**
     * @return the last bar of the series
     */
    val lastBar: Bar
        get() = getBar(endIndex)

    /**
     * @return the number of bars in the series
     */
    val barCount: Int

    /**
     * @return true if the series is empty, false otherwise
     */
    val isEmpty: Boolean
        get() = barCount == 0

    /**
     * Warning: should be used carefully!
     *
     * Returns the raw bar data. It means that it returns the current List object
     * used internally to store the [bars][Bar]. It may be: - a shortened bar
     * list if a maximum bar count has been set - an extended bar list if it is a
     * constrained bar series
     *
     * @return the raw bar data
     */
    val barData: MutableList<Bar>

    /**
     * @return the begin index of the series
     */
    val beginIndex: Int

    /**
     * @return the end index of the series
     */
    val endIndex: Int

    /**
     * @return the description of the series period (e.g. "from 12:00 21/01/2014 to
     * 12:15 21/01/2014")
     */
    val seriesPeriodDescription: String
        get() {
            val sb = StringBuilder()
            if (barData.isNotEmpty()) {
                val firstBar = firstBar
                val lastBar = lastBar
                sb.append(firstBar.endTime?.format(DateTimeFormatter.ISO_DATE_TIME))
                    .append(" - ")
                    .append(lastBar.endTime?.format(DateTimeFormatter.ISO_DATE_TIME))
            }
            return sb.toString()
        }
    /**
     * @return the maximum number of bars
     */
    fun getMaximumBarCount(): Int
    /**
     * Sets the maximum number of bars that will be retained in the series.
     *
     * If a new bar is added to the series such that the number of bars will exceed
     * the maximum bar count, then the FIRST bar in the series is automatically
     * removed, ensuring that the maximum bar count is not exceeded.
     *
     * @param maximumBarCount the maximum bar count
     */

    fun setMaximumBarCount(maximumBarCount: Int)


        /**
     * @return the number of removed bars
     */
    val removedBarsCount: Int

    /**
     * Adds a bar at the end of the series.
     *
     * Begin index set to 0 if it wasn't initialized.<br></br>
     * End index set to 0 if it wasn't initialized, or incremented if it matches the
     * end of the series.<br></br>
     * Exceeding bars are removed.
     *
     * @param bar the bar to be added
     * @apiNote use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) to add
     * bar data directly
     * @see BarSeries.setMaximumBarCount
     */
    fun addBar(bar: Bar) {
        addBar(bar, false)
    }

    /**
     * Adds a bar at the end of the series.
     *
     * Begin index set to 0 if it wasn't initialized.<br></br>
     * End index set to 0 if it wasn't initialized, or incremented if it matches the
     * end of the series.<br></br>
     * Exceeding bars are removed.
     *
     * @param bar     the bar to be added
     * @param replace true to replace the latest bar. Some exchange provide
     * continuous new bar data in the time period. (eg. 1s in 1m
     * Duration)<br></br>
     * @apiNote use #addBar(Duration, ZonedDateTime, Num, Num, Num, Num, Num) to add
     * bar data directly
     * @see BarSeries.setMaximumBarCount
     */
    fun addBar(bar: Bar, replace: Boolean)

    /**
     * Adds a bar at the end of the series.
     *
     * @param timePeriod the [Duration] of this bar
     * @param endTime    the [end time][ZonedDateTime] of this bar
     */
    fun addBar(timePeriod: Duration?, endTime: ZonedDateTime)
    fun addBar(
        endTime: ZonedDateTime?,
        openPrice: Number?,
        highPrice: Number?,
        lowPrice: Number?,
        closePrice: Number?
    ) {
        this.addBar(
            endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(0),
            numOf(0)
        )
    }

    fun addBar(
        endTime: ZonedDateTime?, openPrice: Number?, highPrice: Number?, lowPrice: Number?, closePrice: Number?,
        volume: Number?
    ) {
        this.addBar(endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(volume))
    }

    fun addBar(
        endTime: ZonedDateTime?, openPrice: Number?, highPrice: Number?, lowPrice: Number?, closePrice: Number?,
        volume: Number?, amount: Number?
    ) {
        this.addBar(
            endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice), numOf(volume),
            numOf(amount)
        )
    }

    fun addBar(
        timePeriod: Duration?, endTime: ZonedDateTime?, openPrice: Number?, highPrice: Number?, lowPrice: Number?,
        closePrice: Number?, volume: Number?
    ) {
        this.addBar(
            timePeriod, endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice),
            numOf(volume), numOf(0)
        )
    }

    fun addBar(
        timePeriod: Duration?, endTime: ZonedDateTime?, openPrice: Number?, highPrice: Number?, lowPrice: Number?,
        closePrice: Number?, volume: Number?, amount: Number?
    ) {
        this.addBar(
            timePeriod, endTime, numOf(openPrice), numOf(highPrice), numOf(lowPrice), numOf(closePrice),
            numOf(volume), numOf(amount)
        )
    }

    fun addBar(
        endTime: ZonedDateTime?,
        openPrice: String?,
        highPrice: String?,
        lowPrice: String?,
        closePrice: String?
    ) {
        this.addBar(
            endTime, numOf(BigDecimal(openPrice)), numOf(BigDecimal(highPrice)),
            numOf(BigDecimal(lowPrice)), numOf(BigDecimal(closePrice)), numOf(0), numOf(0)
        )
    }

    fun addBar(
        endTime: ZonedDateTime?, openPrice: String?, highPrice: String?, lowPrice: String?, closePrice: String?,
        volume: String?
    ) {
        this.addBar(
            endTime, numOf(BigDecimal(openPrice)), numOf(BigDecimal(highPrice)),
            numOf(BigDecimal(lowPrice)), numOf(BigDecimal(closePrice)), numOf(BigDecimal(volume)),
            numOf(0)
        )
    }

    fun addBar(
        endTime: ZonedDateTime?, openPrice: String?, highPrice: String?, lowPrice: String?, closePrice: String?,
        volume: String?, amount: String?
    ) {
        this.addBar(
            endTime, numOf(BigDecimal(openPrice)), numOf(BigDecimal(highPrice)),
            numOf(BigDecimal(lowPrice)), numOf(BigDecimal(closePrice)), numOf(BigDecimal(volume)),
            numOf(BigDecimal(amount))
        )
    }

    fun addBar(
        endTime: ZonedDateTime?,
        openPrice: Num?,
        highPrice: Num?,
        lowPrice: Num?,
        closePrice: Num?,
        volume: Num?
    ) {
        this.addBar(endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0))
    }

    /**
     * Adds a new `Bar` to the bar series.
     *
     * @param endTime    end time of the bar
     * @param openPrice  the open price
     * @param highPrice  the high/max price
     * @param lowPrice   the low/min price
     * @param closePrice the last/close price
     * @param volume     the volume (default zero)
     * @param amount     the amount (default zero)
     */
    fun addBar(
        endTime: ZonedDateTime?, openPrice: Num?, highPrice: Num?, lowPrice: Num?, closePrice: Num?, volume: Num?,
        amount: Num?
    )

    /**
     * Adds a new `Bar` to the bar series.
     *
     * @param endTime    end time of the bar
     * @param openPrice  the open price
     * @param highPrice  the high/max price
     * @param lowPrice   the low/min price
     * @param closePrice the last/close price
     * @param volume     the volume (default zero)
     */
    fun addBar(
        timePeriod: Duration?,
        endTime: ZonedDateTime?,
        openPrice: Num?,
        highPrice: Num?,
        lowPrice: Num?,
        closePrice: Num?,
        volume: Num?
    )

    /**
     * Adds a new `Bar` to the bar series.
     *
     * @param timePeriod the time period of the bar
     * @param endTime    end time of the bar
     * @param openPrice  the open price
     * @param highPrice  the high/max price
     * @param lowPrice   the low/min price
     * @param closePrice the last/close price
     * @param volume     the volume (default zero)
     * @param amount     the amount (default zero)
     */
    fun addBar(
        timePeriod: Duration?,
        endTime: ZonedDateTime?,
        openPrice: Num?,
        highPrice: Num?,
        lowPrice: Num?,
        closePrice: Num?,
        volume: Num?,
        amount: Num?
    )

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    fun addTrade(tradeVolume: Number?, tradePrice: Number?) {
        addTrade(numOf(tradeVolume), numOf(tradePrice))
    }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    fun addTrade(tradeVolume: String?, tradePrice: String?) {
        addTrade(numOf(BigDecimal(tradeVolume)), numOf(BigDecimal(tradePrice)))
    }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    fun addTrade(tradeVolume: Num?, tradePrice: Num?)

    /**
     * Adds a price to the last bar
     *
     * @param price the price for the bar
     */
    fun addPrice(price: Num?)

    fun addPrice(price: String?) {
        addPrice(BigDecimal(price))
    }

    fun addPrice(price: Number?) {
        addPrice(numOf(price))
    }

    /**
     * Returns a new [BarSeries] instance that is a subset of this BarSeries
     * instance. It holds a copy of all [bars][Bar] between <tt>startIndex</tt>
     * (inclusive) and <tt>endIndex</tt> (exclusive) of this BarSeries. The indices
     * of this BarSeries and the new subset BarSeries can be different. I. e. index
     * 0 of the new BarSeries will be index <tt>startIndex</tt> of this BarSeries.
     * If <tt>startIndex</tt> < this.seriesBeginIndex the new BarSeries will start
     * with the first available Bar of this BarSeries. If <tt>endIndex</tt> >
     * this.seriesEndIndex the new BarSeries will end at the last available Bar of
     * this BarSeries
     *
     * @param startIndex the startIndex (inclusive)
     * @param endIndex   the endIndex (exclusive)
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
     */
    fun getSubSeries(startIndex: Int, endIndex: Int): BarSeries

    /**
     * Transforms a [Number] into the [implementation][Num] used by this
     * bar series
     *
     * @param number a [Number] implementing object.
     * @return the corresponding value as a Num implementing object
     */
    fun numOf(number: Number?): Num

    /**
     * Returns the underlying function to transform a Number into the Num
     * implementation used by this bar series
     *
     * @return a function Number -> Num
     */
    fun function(): Function<Number?, Num>
}