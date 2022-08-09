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
package org.ta4j.core.utils

import org.ta4j.core.Bar
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.ConvertibleBaseBarBuilder
import org.ta4j.core.aggregator.BarAggregator
import org.ta4j.core.aggregator.BarSeriesAggregator
import org.ta4j.core.aggregator.BaseBarSeriesAggregator
import org.ta4j.core.aggregator.DurationBarAggregator
import org.ta4j.core.num.*
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.function.Function

/**
 * Common utilities and helper methods for BarSeries.
 */
object BarSeriesUtils {
    /**
     * Sorts the Bars by [Bar.endTime] in ascending sequence (lower
     * values before higher values).
     */
    private val sortBarsByTime = Comparator { b1: Bar, b2: Bar -> if (b1.endTime!!.isAfter(b2.endTime)) 1 else -1 }

    /**
     * Aggregates a list of bars by `timePeriod`. The new
     * `timePeriod` must be a multiplication of the actual time period.
     *
     * @param barSeries            the barSeries
     * @param timePeriod           time period to aggregate
     * @param aggregatedSeriesName the name of the aggregated barSeries
     * @return the aggregated barSeries
     */
    fun aggregateBars(barSeries: BarSeries, timePeriod: Duration, aggregatedSeriesName: String): BarSeries? {
        val durationAggregator: BarAggregator = DurationBarAggregator(timePeriod, true)
        val seriesAggregator: BarSeriesAggregator = BaseBarSeriesAggregator(durationAggregator)
        return seriesAggregator.aggregate(barSeries, aggregatedSeriesName)
    }

    /**
     * We can assume that finalized bar data will be never changed afterwards by the
     * marketdata provider. It is rare, but depending on the exchange, they reserve
     * the right to make updates to finalized bars. This method finds and replaces
     * potential bar data that was changed afterwards by the marketdata provider. It
     * can also be uses to check bar data equality over different marketdata
     * providers. This method does **not** add missing bars but replaces an
     * existing bar with its new bar.
     *
     * @param barSeries the barSeries
     * @param newBar    the bar which has precedence over the same existing bar
     * @return the previous bar replaced by newBar, or null if there was no
     * replacement.
     */
    @JvmStatic
    fun replaceBarIfChanged(barSeries: BarSeries, newBar: Bar): Bar? {
        val bars = barSeries.barData
        if (bars.isEmpty()) return null
        for (i in bars.indices) {
            val bar = bars[i]
            val isSameBar = (bar.beginTime.isEqual(newBar.beginTime)
                    && bar.endTime!!.isEqual(newBar.endTime)) && bar.timePeriod == newBar.timePeriod
            if (isSameBar && bar != newBar) return bars.set(i, newBar)
        }
        return null
    }

    /**
     * Finds possibly missing bars. The returned list contains the
     * `endTime` of each missing bar. A bar is possibly missing if: (1)
     * the subsequent bar starts not with the end time of the previous bar or (2) if
     * any open, high, low price is missing.
     *
     * **Note:** Market closing times (e.g., weekends, holidays) will lead to
     * wrongly detected missing bars and should be ignored by the client.
     *
     * @param barSeries       the barSeries
     * @param findOnlyNaNBars find only bars with undefined prices
     * @return the list of possibly missing bars
     */
    @JvmStatic
    fun findMissingBars(barSeries: BarSeries, findOnlyNaNBars: Boolean): List<ZonedDateTime?> {
        val bars = barSeries.barData
        if (bars.isEmpty()) return ArrayList()
        val duration = bars.iterator().next().timePeriod
        val missingBars: MutableList<ZonedDateTime?> = ArrayList()
        for (i in bars.indices) {
            val bar = bars[i]
            if (!findOnlyNaNBars) {
                val nextBar = if (i + 1 < bars.size) bars[i + 1] else null
                var incDuration = Duration.ZERO
                if (nextBar != null) {
                    // market closing times are also treated as missing bars
                    while (nextBar.beginTime.minus(incDuration).isAfter(bar.endTime)) {
                        missingBars.add(bar.endTime!!.plus(incDuration).plus(duration))
                        incDuration = incDuration.plus(duration)
                    }
                }
            }
            val noFullData = bar.openPrice!!.isNaN || bar.highPrice!!.isNaN || bar.lowPrice!!.isNaN
            if (noFullData) {
                missingBars.add(bar.endTime)
            }
        }
        return missingBars
    }

    /**
     * Gets a new BarSeries cloned from the provided barSeries with bars converted
     * by conversionFunction. The returned barSeries inherits
     * `beginIndex`, `endIndex` and
     * `maximumBarCount` from the provided barSeries.
     *
     * @param barSeries          the BarSeries
     * @param conversionFunction the conversionFunction
     * @return new cloned BarSeries with bars converted by conversionFunction
     */
    @JvmStatic
    fun convertBarSeries(barSeries: BarSeries, conversionFunction: Function<Number?, Num>): BarSeries {
        val bars = barSeries.barData
        if (bars.isEmpty()) return barSeries
        val convertedBars: MutableList<Bar> = ArrayList()
        for (i in barSeries.beginIndex..barSeries.endIndex) {
            val bar = bars[i]
            val convertedBar: Bar = ConvertibleBaseBarBuilder { t: Number? -> conversionFunction.apply(t) }
                .timePeriod(bar.timePeriod)
                .endTime(bar.endTime)
                .openPrice(bar.openPrice?.delegate)
                .highPrice(bar.highPrice?.delegate)
                .lowPrice(bar.lowPrice?.delegate)
                .closePrice(bar.closePrice?.delegate)
                .volume(bar.volume?.delegate)
                .amount(bar.amount?.delegate)
                .trades(bar.trades)
                .build()
            convertedBars.add(convertedBar)
        }
        val convertedBarSeries: BarSeries = BaseBarSeries(barSeries.name, convertedBars, conversionFunction)
        if (barSeries.getMaximumBarCount() > 0) {
            convertedBarSeries.setMaximumBarCount(barSeries.getMaximumBarCount())
        }
        return convertedBarSeries
    }

    /**
     * Finds overlapping bars within barSeries.
     *
     * @param barSeries the bar series with bar data
     * @return overlapping bars
     */
    @JvmStatic
    fun findOverlappingBars(barSeries: BarSeries): List<Bar> {
        val bars = barSeries.barData
        if (bars.isEmpty()) return ArrayList()
        val period = bars.iterator().next().timePeriod
        val overlappingBars: MutableList<Bar> = ArrayList()
        for (i in bars.indices) {
            val bar = bars[i]
            val nextBar = if (i + 1 < bars.size) bars[i + 1] else null
            if (nextBar != null) {
                if (bar.endTime!!.isAfter(nextBar.beginTime)
                    || bar.beginTime.plus(period).isBefore(nextBar.beginTime)
                ) {
                    overlappingBars.add(nextBar)
                }
            }
        }
        return overlappingBars
    }

    /**
     * Adds `newBars` to `barSeries`.
     *
     * @param barSeries the BarSeries
     * @param newBars   the new bars to be added
     */
    @JvmStatic
    fun addBars(barSeries: BarSeries, newBars: List<Bar>) {
        if (newBars.isNotEmpty()) {
            sortBars(newBars)
            for (bar in newBars) {
                if (barSeries.isEmpty || bar.endTime!!.isAfter(barSeries.lastBar.endTime)) {
                    barSeries.addBar(bar)
                }
            }
        }
    }

    /**
     * Sorts the Bars by [Bar.endTime] in ascending sequence (lower times
     * before higher times).
     *
     * @param bars the bars
     * @return the sorted bars
     */
    @JvmStatic
    fun sortBars(bars: List<Bar>): List<Bar> {
        if (bars.isNotEmpty()) {
            Collections.sort(bars, sortBarsByTime)
        }
        return bars
    }
}