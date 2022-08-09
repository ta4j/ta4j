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
package org.ta4j.core.mocks

import org.ta4j.core.Bar
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

/**
 * A bar series with sample data. TODO: add Builder
 */
class MockBarSeries : BaseBarSeries {
    constructor(nf: Function<Number?, Num>, vararg data: Double) : super(doublesToBars(nf, *data))
    constructor(nf: Function<Number?, Num>, data: List<Double>) : super(doublesToBars(nf, data))
    constructor(bars: MutableList<Bar>) : super(bars)
    constructor(nf: Function<Number?, Num>, data: DoubleArray, times: Array<ZonedDateTime>) : super(
        doublesAndTimesToBars(nf, data, times)
    )

    constructor(nf: Function<Number?, Num>, vararg dates: ZonedDateTime) : super(timesToBars(nf, *dates))
    constructor(nf: Function<Number?, Num>) : super(arbitraryBars(nf))
    companion object {
        private const val serialVersionUID = -1216549934945189371L
        private fun doublesToBars(nf: Function<Number?, Num>, data: List<Double>): MutableList<Bar> {
            val bars = ArrayList<Bar>()
            for (i in data.indices) {
                bars.add(MockBar(ZonedDateTime.now().minusSeconds((data.size + 1 - i).toLong()), data[i], nf))
            }
            return bars
        }

        private fun doublesToBars(nf: Function<Number?, Num>, vararg data: Double): MutableList<Bar> {
            val bars = ArrayList<Bar>()
            for (i in data.indices) {
                bars.add(MockBar(ZonedDateTime.now().minusSeconds((data.size + 1 - i).toLong()), data[i], nf))
            }
            return bars
        }

        private fun doublesAndTimesToBars(
            nf: Function<Number?, Num>,
            data: DoubleArray,
            times: Array<ZonedDateTime>
        ): MutableList<Bar> {
            require(data.size == times.size)
            val bars = ArrayList<Bar>()
            for (i in data.indices) {
                bars.add(MockBar(times[i], data[i], nf))
            }
            return bars
        }

        private fun timesToBars(nf: Function<Number?, Num>, vararg dates: ZonedDateTime): MutableList<Bar> {
            val bars = ArrayList<Bar>()
            var i = 1
            for (date in dates) {
                bars.add(MockBar(date, i++.toDouble(), nf))
            }
            return bars
        }

        private fun arbitraryBars(nf: Function<Number?, Num>): MutableList<Bar> {
            val bars = ArrayList<Bar>()
            for (ii in 0..4999) {
                val i=ii.toDouble()
                bars.add(
                    MockBar(
                        ZonedDateTime.now().minusMinutes((5001 - i).toLong()), i, i + 1, i + 2, i + 3, i + 4,
                        i + 5, (i + 6).toInt().toLong(), nf
                    )
                )
            }
            return bars
        }
    }
}