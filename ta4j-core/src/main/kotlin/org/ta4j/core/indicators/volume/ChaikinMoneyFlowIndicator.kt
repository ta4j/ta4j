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
package org.ta4j.core.indicators.volume

import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.num.*
import kotlin.math.max

/**
 * Chaikin Money Flow (CMF) indicator.
 *
 * @see [
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf"](http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf)
 *
 * @see [
 * http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm](http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm)
 */
class ChaikinMoneyFlowIndicator(series: BarSeries?, private val barCount: Int) : CachedIndicator<Num>(series) {
    private val clvIndicator: CloseLocationValueIndicator
    private val volumeIndicator: VolumeIndicator

    init {
        clvIndicator = CloseLocationValueIndicator(series)
        volumeIndicator = VolumeIndicator(series, barCount)
    }

    override fun calculate(index: Int): Num {
        val startIndex = max(0, index - barCount + 1)
        var sumOfMoneyFlowVolume = numOf(0)
        for (i in startIndex..index) {
            sumOfMoneyFlowVolume += getMoneyFlowVolume(i)
        }
        val sumOfVolume = volumeIndicator[index]
        return sumOfMoneyFlowVolume / sumOfVolume
    }

    /**
     * @param index the bar index
     * @return the money flow volume for the i-th period/bar
     */
    private fun getMoneyFlowVolume(index: Int): Num {
        return clvIndicator[index] * barSeries!!.getBar(index).volume!!
    }

    override fun toString(): String {
        return javaClass.simpleName + " barCount: " + barCount
    }
}