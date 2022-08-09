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
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Function

/**
 * Base implementation of a [Bar].
 */

open class BaseBar : Bar {
    /** Time period (e.g. 1 day, 15 min, etc.) of the bar  */
    final override var timePeriod: Duration?
    /**
     * @return the end timestamp of the bar period
     */
    /** End time of the bar  */
    final override var endTime: ZonedDateTime?
    /**
     * @return the begin timestamp of the bar period
     */
    /** Begin time of the bar  */
    final override var beginTime: ZonedDateTime
    /** Open price of the period  */
    final override var openPrice: Num? = null
    /** Close price of the period  */
    final override var closePrice: Num? = null
    /** High price of the period  */
    final override var highPrice: Num? = null
    /** Low price of the period  */
    final override var lowPrice: Num? = null
    /** Traded amount during the period  */
    final override var amount: Num?
    /** Volume of the period  */
    final override var volume: Num?
    /** Trade count  */
    override var trades: Long = 0

    /**
     * Constructor.
     *
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param numFunction the numbers precision
     */
    constructor(timePeriod: Duration?, endTime: ZonedDateTime, numFunction: Function<Number?, Num>) {
        checkTimeArguments(timePeriod, endTime)
        this.timePeriod = timePeriod
        this.endTime = endTime
        beginTime = endTime.minus(timePeriod)
        volume = numFunction.apply(0)
        amount = numFunction.apply(0)
    }
    /**
     * Constructor.
     *
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param openPrice   the open price of the bar period
     * @param highPrice   the highest price of the bar period
     * @param lowPrice    the lowest price of the bar period
     * @param closePrice  the close price of the bar period
     * @param volume      the volume of the bar period
     * @param amount      the amount of the bar period
     * @param trades      the trades count of the bar period
     * @param numFunction the numbers precision
     */
    @JvmOverloads
    constructor(
        timePeriod: Duration?,
        endTime: ZonedDateTime?,
        openPrice: Double,
        highPrice: Double,
        lowPrice: Double,
        closePrice: Double,
        volume: Double,
        amount: Double = 0.0,
        trades: Long = 0,
        numFunction: Function<Number?, Num> = Function { i: Number? -> DecimalNum.valueOf(i) }
    ) : this(
        timePeriod, endTime, numFunction.apply(openPrice), numFunction.apply(highPrice),
        numFunction.apply(lowPrice), numFunction.apply(closePrice), numFunction.apply(volume),
        numFunction.apply(amount), trades
    )
    /**
     * Constructor.
     *
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param openPrice   the open price of the bar period
     * @param highPrice   the highest price of the bar period
     * @param lowPrice    the lowest price of the bar period
     * @param closePrice  the close price of the bar period
     * @param volume      the volume of the bar period
     * @param amount      the amount of the bar period
     * @param trades      the trades count of the bar period
     * @param numFunction the numbers precision
     */
    @JvmOverloads
    constructor(
        timePeriod: Duration?,
        endTime: ZonedDateTime?,
        openPrice: BigDecimal?,
        highPrice: BigDecimal?,
        lowPrice: BigDecimal?,
        closePrice: BigDecimal?,
        volume: BigDecimal?,
        amount: BigDecimal? = BigDecimal.ZERO,
        trades: Long = 0,
        numFunction: Function<Number?, Num> = Function { v: Number? -> DecimalNum.valueOf(v) }
    ) : this(
        timePeriod, endTime, numFunction.apply(openPrice), numFunction.apply(highPrice),
        numFunction.apply(lowPrice), numFunction.apply(closePrice), numFunction.apply(volume),
        numFunction.apply(amount), trades
    )
    /**
     * Constructor.
     *
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     */
    @JvmOverloads
    constructor(
        timePeriod: Duration?,
        endTime: ZonedDateTime?,
        openPrice: String?,
        highPrice: String?,
        lowPrice: String?,
        closePrice: String?,
        volume: String?,
        amount: String? = "0",
        trades: String? = "0",
        numFunction: Function<Number?, Num> = Function { `val`: Number? -> DecimalNum.valueOf(`val`) }
    ) : this(
        timePeriod, endTime, numFunction.apply(BigDecimal(openPrice)),
        numFunction.apply(BigDecimal(highPrice)), numFunction.apply(BigDecimal(lowPrice)),
        numFunction.apply(BigDecimal(closePrice)), numFunction.apply(BigDecimal(volume)),
        numFunction.apply(BigDecimal(amount)), Integer.valueOf(trades).toLong()
    )
    /**
     * Constructor.
     *
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     */
    @JvmOverloads
    constructor(
        timePeriod: Duration?, endTime: ZonedDateTime?, openPrice: Num?, highPrice: Num?, lowPrice: Num?,
        closePrice: Num?, volume: Num?, amount: Num?, trades: Long = 0
    ) {
        checkTimeArguments(timePeriod, endTime)
        this.timePeriod = timePeriod
        this.endTime = endTime
        beginTime = endTime!!.minus(timePeriod)
        this.openPrice = openPrice
        this.highPrice = highPrice
        this.lowPrice = lowPrice
        this.closePrice = closePrice
        this.volume = volume
        this.amount = amount
        this.trades = trades
    }

    /**
     * Adds a trade at the end of bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
     */
    override fun addTrade(tradeVolume: Num?, tradePrice: Num?) {
        addPrice(tradePrice)
        volume = volume!!.plus(tradeVolume!!)
        amount = amount!!.plus(tradeVolume.times(tradePrice!!))
        trades++
    }

    override fun addPrice(price: Num?) {
        if (openPrice == null) {
            openPrice = price
        }
        closePrice = price
        if (highPrice == null || highPrice!!.isLessThan(price!!)) {
            highPrice = price
        }
        if (lowPrice == null || lowPrice!!.isGreaterThan(price!!)) {
            lowPrice = price
        }
    }

    override fun toString(): String {
        return String.format(
            "{end time: %1s, close price: %2\$f, open price: %3\$f, low price: %4\$f, high price: %5\$f, volume: %6\$f}",
            endTime!!.withZoneSameInstant(ZoneId.systemDefault()),
            closePrice!!.doubleValue(),
            openPrice!!.doubleValue(),
            lowPrice!!.doubleValue(),
            highPrice!!.doubleValue(),
            volume!!.doubleValue()
        )
    }

    override fun hashCode(): Int {
        return Objects.hash(
            beginTime, endTime, timePeriod, openPrice, highPrice, lowPrice, closePrice, volume, amount,
            trades
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseBar) return false
        return beginTime == other.beginTime && endTime == other.endTime && timePeriod == other.timePeriod && openPrice == other.openPrice && highPrice == other.highPrice && lowPrice == other.lowPrice && closePrice == other.closePrice && volume == other.volume && amount == other.amount && trades == other.trades
    }

    companion object {
        private const val serialVersionUID = 8038383777467488147L

        /**
         * Returns BaseBarBuilder
         *
         * @return builder of class BaseBarBuilder
         */
        @JvmStatic
        fun builder(): BaseBarBuilder {
            return BaseBarBuilder()
        }

        /**
         * Returns BaseBarBuilder
         *
         * @return builder of class BaseBarBuilder
         */
        @JvmStatic
        fun <T> builder(conversionFunction: Function<T, Num?>, clazz: Class<T>?): ConvertibleBaseBarBuilder<T> {
            return ConvertibleBaseBarBuilder(conversionFunction)
        }

        /**
         * @param timePeriod the time period
         * @param endTime    the end time of the bar
         * @throws IllegalArgumentException if one of the arguments is null
         */
        private fun checkTimeArguments(timePeriod: Duration?, endTime: ZonedDateTime?) {
            requireNotNull(timePeriod) { "Time period cannot be null" }
            requireNotNull(endTime) { "End time cannot be null" }
        }
    }
}

