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
package org.ta4j.core.aggregator

import org.ta4j.core.Bar
import org.ta4j.core.BaseBar
import org.ta4j.core.num.*
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Bar aggregator basing on duration.
 */
class DurationBarAggregator
/**
 * Duration basing bar aggregator. Only bars with elapsed time (final bars) will
 * be created.
 *
 * @param timePeriod time period to aggregate
 */ @JvmOverloads constructor(
    /**
     * Target time period to aggregate
     */
    private val timePeriod: Duration, private val onlyFinalBars: Boolean = true
) : BarAggregator {
    /**
     * Duration basing bar aggregator
     *
     * @param timePeriod    time period to aggregate
     * @param onlyFinalBars if true only bars with elapsed time (final bars) will be
     * created, otherwise also pending bars
     */
    /**
     * Aggregates a list of bars by `timePeriod`.The new
     * `timePeriod` must be a multiplication of the actual time period.
     *
     * @param bars the actual bars
     * @return the aggregated bars with new `timePeriod`
     */
    override fun aggregate(bars: List<Bar>): MutableList<Bar> {
        val aggregated: MutableList<Bar> = ArrayList()
        if (bars.isEmpty()) {
            return aggregated
        }
        val firstBar = bars[0]
        // get the actual time period
        val actualDur = firstBar.timePeriod
        // check if new timePeriod is a multiplication of actual time period
        val isMultiplication = timePeriod.seconds % actualDur!!.seconds == 0L
        require(isMultiplication) { "Cannot aggregate bars: the new timePeriod must be a multiplication of the actual timePeriod." }
        var i = 0
        val zero = firstBar.openPrice!!.numOf(0)
        while (i < bars.size) {
            var bar = bars[i]
            val beginTime = bar.beginTime
            val open = bar.openPrice
            var high = bar.highPrice
            var low = bar.lowPrice
            var close: Num? = null
            var volume = zero
            var amount = zero
            var trades: Long = 0
            var sumDur = Duration.ZERO
            while (isInDuration(sumDur)) {
                if (i < bars.size) {
                    if (!beginTimesInDuration(beginTime, bars[i].beginTime)) {
                        break
                    }
                    bar = bars[i]
                    if (high == null || bar.highPrice!!.isGreaterThan(high)) {
                        high = bar.highPrice
                    }
                    if (low == null || bar.lowPrice!!.isLessThan(low)) {
                        low = bar.lowPrice
                    }
                    close = bar.closePrice
                    if (bar.volume != null) {
                        volume = volume.plus(bar.volume!!)
                    }
                    if (bar.amount != null) {
                        amount = amount.plus(bar.amount!!)
                    }
                    if (bar.trades != 0L) {
                        trades += bar.trades
                    }
                }
                sumDur = sumDur.plus(actualDur)
                i++
            }
            if (!onlyFinalBars || i <= bars.size) {
                val aggregatedBar: Bar = BaseBar(
                    timePeriod, beginTime.plus(timePeriod), open, high, low, close,
                    volume, amount, trades
                )
                aggregated.add(aggregatedBar)
            }
        }
        return aggregated
    }

    private fun beginTimesInDuration(startTime: ZonedDateTime?, endTime: ZonedDateTime?): Boolean {
        return Duration.between(startTime, endTime) < timePeriod
    }

    private fun isInDuration(duration: Duration): Boolean {
        return duration < timePeriod
    }
}