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

import org.slf4j.LoggerFactory
import org.ta4j.core.num.*
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

/**
 * Constructor.
 * Base implementation of a [BarSeries].
 *
 * @param name             the name of the series
 * @param barData          the list of bars of the series
 * @param seriesBeginIndex the begin index (inclusive) of the bar series
 * @param seriesEndIndex   the end index (inclusive) of the bar series
 * @param constrained      true to constrain the bar series (i.e. indexes cannot change), false otherwise
 * @param numFunction      a [Function] to convert a [Number] to a [Num implementation][Num]
 */
open class BaseBarSeries internal constructor(
    /**
     * Name of the series
     */
    override val name: String,
    /**
     * List of bars
     */
    final override val barData: MutableList<Bar>, seriesBeginIndex: Int, seriesEndIndex: Int, constrained: Boolean,
    numFunction: Function<Number?, Num>
) : BarSeries {
    /**
     * Num type function
     */
    @Transient
    protected val numFunction: Function<Number?, Num>

    /**
     * The logger
     */
    @Transient
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Begin index of the bar series
     */
    final override var beginIndex: Int=0

    /**
     * End index of the bar series
     */
    final override var endIndex: Int=0

    /**
     * Maximum number of bars for the bar series
     */
    private  var _maximumBarCount = Int.MAX_VALUE

    /**
     * Number of removed bars
     */
    override var removedBarsCount = 0

    /**
     * True if the current series is constrained (i.e. its indexes cannot change),
     * false otherwise
     */
    private var constrained: Boolean

    /**
     * Constructor of an unnamed series.
     *
     * @param bars the list of bars of the series
     */
    constructor(bars: MutableList<Bar>) : this(UNNAMED_SERIES_NAME, bars)
    /**
     * Constructor.
     *
     * @param name the name of the series
     * @param barData the list of bars of the series
     */
    /**
     * Constructor.
     *
     * @param name the name of the series
     */
    /**
     * Constructor of an unnamed series.
     */
    @JvmOverloads
    constructor(name: String = UNNAMED_SERIES_NAME, bars: MutableList<Bar> = ArrayList()) : this(
        name,
        bars,
        0,
        bars.size - 1,
        false
    )

    /**
     * Constructor.
     *
     * @param name        the name of the series
     * @param numFunction a [Function] to convert a [Number] to a
     * [Num implementation][Num]
     */
    constructor(name: String, numFunction: Function<Number?, Num>) : this(name, ArrayList<Bar>(), numFunction)

    /**
     * Constructor.
     *
     * @param name the name of the series
     * @param bars the list of bars of the series
     */
    constructor(name: String, bars: MutableList<Bar>, numFunction: Function<Number?, Num>) : this(
        name,
        bars,
        0,
        bars.size - 1,
        false,
        numFunction
    )
    /**
     * Constructor.
     *
     *
     * Creates a BaseBarSeries with default [DecimalNum] as type for the data
     * and all operations on it
     *
     * @param name             the name of the series
     * @param bars             the list of bars of the series
     * @param seriesBeginIndex the begin index (inclusive) of the bar series
     * @param seriesEndIndex   the end index (inclusive) of the bar series
     * @param constrained      true to constrain the bar series (i.e. indexes cannot
     * change), false otherwise
     */
    private constructor(
        name: String,
        bars: MutableList<Bar>,
        seriesBeginIndex: Int,
        seriesEndIndex: Int,
        constrained: Boolean
    ) : this(
        name,
        bars,
        seriesBeginIndex,
        seriesEndIndex,
        constrained,
        Function<Number?, Num> { v: Number? -> DecimalNum.valueOf(v) })


    init {
        if (barData.isEmpty()) {
            // Bar list empty
            beginIndex = -1
            endIndex = -1
            this.constrained = false
            this.numFunction = numFunction
        } else {
            // Bar list not empty: take Function of first bar
            this.numFunction = barData[0].closePrice!!.function()
            // Bar list not empty: checking num types
            require(checkBars(barData)) {
                String.format(
                    "Num implementation of bars: %s" + " does not match to Num implementation of bar series: %s",
                    barData[0].closePrice?.javaClass, numFunction
                )
            }
            // Bar list not empty: checking indexes
            require(seriesEndIndex >= seriesBeginIndex - 1) { "End index must be >= to begin index - 1" }
            require(seriesEndIndex < barData.size) { "End index must be < to the bar list size" }
            beginIndex = seriesBeginIndex
            endIndex = seriesEndIndex
            this.constrained = constrained
        }
    }

    /**
     * Returns a new BaseBarSeries that is a subset of this BaseBarSeries. The new
     * series holds a copy of all [bars][Bar] between <tt>startIndex</tt>
     * (inclusive) and <tt>endIndex</tt> (exclusive) of this BaseBarSeries. The
     * indices of this BaseBarSeries and the new subset BaseBarSeries can be
     * different. I. e. index 0 of the new BaseBarSeries will be index
     * <tt>startIndex</tt> of this BaseBarSeries. If <tt>startIndex</tt> <
     * this.seriesBeginIndex the new BaseBarSeries will start with the first
     * available Bar of this BaseBarSeries. If <tt>endIndex</tt> >
     * this.seriesEndIndex+1 the new BaseBarSeries will end at the last available
     * Bar of this BaseBarSeries
     *
     * @param startIndex the startIndex (inclusive)
     * @param endIndex   the endIndex (exclusive)
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
     */
    override fun getSubSeries(startIndex: Int, endIndex: Int): BaseBarSeries {
        require(startIndex >= 0) { String.format("the startIndex: %s must not be negative", startIndex) }
        require(startIndex < endIndex) {
            String.format(
                "the endIndex: %s must be greater than startIndex: %s",
                endIndex,
                startIndex
            )
        }
        if (barData.isNotEmpty()) {
            val start = max(startIndex - removedBarsCount, beginIndex)
            val end = min(endIndex - removedBarsCount, this.endIndex + 1)
            return BaseBarSeries(name, cut(barData, start, end), numFunction)
        }
        return BaseBarSeries(name, numFunction)
    }

    override fun numOf(number: Number?): Num {
        return numFunction.apply(number)
    }

    override fun function(): Function<Number?, Num> {
        return numFunction
    }

    /**
     * Checks if all [bars][Bar] of a list fits to the [NumFunction][Num]
     * used by this bar series.
     *
     * @param bars a List of Bar objects.
     * @return false if a Num implementation of at least one Bar does not fit.
     */
    private fun checkBars(bars: List<Bar>): Boolean {
        for (bar in bars) {
            if (!checkBar(bar)) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if the [Num] implementation of a [Bar] fits to the
     * NumFunction used by bar series.
     *
     * @param bar a Bar object.
     * @return false if another Num implementation is used than by this bar series.
     * @see Num
     *
     * @see Bar
     *
     * @see .addBar
     */
    private fun checkBar(bar: Bar): Boolean {
        if (bar.closePrice == null) {
            return true // bar has not been initialized with data (uses deprecated constructor)
        }
        // all other constructors initialize at least the close price, check if Num
        // implementation fits to numFunction
        val f: Class<out Num> = numOf(1).javaClass
        return f == bar.closePrice?.javaClass || bar.closePrice == NaN.NaN
    }

    override fun getBar(i: Int): Bar {
        var innerIndex = i - removedBarsCount
        if (innerIndex < 0) {
            if (i < 0) {
                // Cannot return the i-th bar if i < 0
                throw IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i))
            }
            if (log.isTraceEnabled) {
                log.trace(
                    "Bar series `{}` ({} bars): bar {} already removed, use {}-th instead", name, barData.size, i,
                    removedBarsCount
                )
            }
            if (barData.isEmpty()) {
                throw IndexOutOfBoundsException(buildOutOfBoundsMessage(this, removedBarsCount))
            }
            innerIndex = 0
        } else if (innerIndex >= barData.size) {
            // Cannot return the n-th bar if n >= bars.size()
            throw IndexOutOfBoundsException(buildOutOfBoundsMessage(this, i))
        }
        return barData[innerIndex]
    }

    override val barCount: Int
        get() {
            if (endIndex < 0) {
                return 0
            }
            val startIndex = max(removedBarsCount, beginIndex)
            return endIndex - startIndex + 1
        }

    override fun getMaximumBarCount(): Int {
        return _maximumBarCount
    }

    override fun setMaximumBarCount(maximumBarCount: Int) {
        check(!constrained) { "Cannot set a maximum bar count on a constrained bar series" }
        require(maximumBarCount > 0) { "Maximum bar count must be strictly positive" }
        this._maximumBarCount = maximumBarCount
        removeExceedingBars()
    }

    /**
     * @param bar the `Bar` to be added
     * @apiNote to add bar data directly use #addBar(Duration, ZonedDateTime, Num,
     * Num, Num, Num, Num)
     */
    override fun addBar(bar: Bar, replace: Boolean) {
        require(checkBar(bar)) {
            String.format(
                "Cannot add Bar with data type: %s to series with data" + "type: %s",
                bar.closePrice?.javaClass, numOf(1).javaClass
            )
        }
        if (barData.isNotEmpty()) {
            if (replace) {
                barData[barData.size - 1] = bar
                return
            }
            val lastBarIndex = barData.size - 1
            val seriesEndTime = barData[lastBarIndex].endTime
            require(bar.endTime!!.isAfter(seriesEndTime)) {
                String.format(
                    "Cannot add a bar with end time:%s that is <= to series end time: %s",
                    bar.endTime, seriesEndTime
                )
            }
        }
        barData.add(bar)
        if (beginIndex == -1) {
            // Begin index set to 0 only if it wasn't initialized
            beginIndex = 0
        }
        endIndex++
        removeExceedingBars()
    }

    override fun addBar(timePeriod: Duration?, endTime: ZonedDateTime) {
        this.addBar(BaseBar(timePeriod, endTime, function()))
    }

    override fun addBar(
        endTime: ZonedDateTime?,
        openPrice: Num?,
        highPrice: Num?,
        lowPrice: Num?,
        closePrice: Num?,
        volume: Num?
    ) {
        this.addBar(
            BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0))
        )
    }

    override fun addBar(
        endTime: ZonedDateTime?, openPrice: Num?, highPrice: Num?, lowPrice: Num?, closePrice: Num?, volume: Num?,
        amount: Num?
    ) {
        this.addBar(
            BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount)
        )
    }

    override fun addBar(
        timePeriod: Duration?, endTime: ZonedDateTime?, openPrice: Num?, highPrice: Num?, lowPrice: Num?,
        closePrice: Num?, volume: Num?
    ) {
        this.addBar(BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, numOf(0)))
    }

    override fun addBar(
        timePeriod: Duration?, endTime: ZonedDateTime?, openPrice: Num?, highPrice: Num?, lowPrice: Num?,
        closePrice: Num?, volume: Num?, amount: Num?
    ) {
        this.addBar(BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount))
    }

    override fun addTrade(price: Number?, amount: Number?) {
        addTrade(numOf(price), numOf(amount))
    }

    override fun addTrade(price: String?, amount: String?) {
        addTrade(numOf(BigDecimal(price)), numOf(BigDecimal(amount)))
    }

    override fun addTrade(tradeVolume: Num?, tradePrice: Num?) {
        lastBar.addTrade(tradeVolume, tradePrice)
    }

    override fun addPrice(price: Num?) {
        lastBar.addPrice(price)
    }

    /**
     * Removes the N first bars which exceed the maximum bar count.
     */
    private fun removeExceedingBars() {
        val barCount = barData.size
        if (barCount > _maximumBarCount) {
            // Removing old bars
            val nbBarsToRemove = barCount - _maximumBarCount
            if (nbBarsToRemove == 1) {
                barData.removeAt(0)
            } else {
                barData.subList(0, nbBarsToRemove).clear()
            }
            // Updating removed bars count
            removedBarsCount += nbBarsToRemove
        }
    }

    companion object {
        private const val serialVersionUID = -1878027009398790126L

        /**
         * Name for unnamed series
         */
        private const val UNNAMED_SERIES_NAME = "unnamed_series"

        /**
         * Cuts a list of bars into a new list of bars that is a subset of it
         *
         * @param bars       the list of [bars][Bar]
         * @param startIndex start index of the subset
         * @param endIndex   end index of the subset
         * @return a new list of bars with tick from startIndex (inclusive) to endIndex
         * (exclusive)
         */
        private fun cut(bars: List<Bar>, startIndex: Int, endIndex: Int): MutableList<Bar> {
            return ArrayList(bars.subList(startIndex, endIndex))
        }

        /**
         * @param series a bar series
         * @param index  an out of bounds bar index
         * @return a message for an OutOfBoundsException
         */
        private fun buildOutOfBoundsMessage(series: BaseBarSeries, index: Int): String {
            return String.format(
                "Size of series: %s bars, %s bars removed, index = %s", series.barData.size,
                series.removedBarsCount, index
            )
        }
    }
}