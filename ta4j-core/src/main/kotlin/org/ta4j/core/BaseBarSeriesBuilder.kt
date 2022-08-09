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
import java.util.function.Function

class BaseBarSeriesBuilder : BarSeriesBuilder {
    private var bars: MutableList<Bar> = ArrayList()
    private var name: String = "unnamed_series"
    private var numFunction: Function<Number?, Num> = defaultFunction
    private var constrained = false
    private var maxBarCount = Int.MAX_VALUE


    private fun initValues() {
        bars = ArrayList()
        name = "unnamed_series"
        numFunction = defaultFunction
        constrained = false
        maxBarCount = Int.MAX_VALUE
    }

    override fun build(): BaseBarSeries {
        var beginIndex = -1
        var endIndex = -1
        if (bars.isNotEmpty()) {
            beginIndex = 0
            endIndex = bars.size - 1
        }
        val series = BaseBarSeries(name, bars, beginIndex, endIndex, constrained, numFunction)
        series.setMaximumBarCount( maxBarCount)
        initValues() // reinitialize values for next series
        return series
    }

    fun setConstrained(constrained: Boolean): BaseBarSeriesBuilder {
        this.constrained = constrained
        return this
    }

    fun withName(name: String): BaseBarSeriesBuilder {
        this.name = name
        return this
    }

    fun withBars(bars: MutableList<Bar>): BaseBarSeriesBuilder {
        this.bars = bars
        return this
    }

    fun withMaxBarCount(maxBarCount: Int): BaseBarSeriesBuilder {
        this.maxBarCount = maxBarCount
        return this
    }

    fun withNumTypeOf(type: Num): BaseBarSeriesBuilder {
        numFunction = type.function()
        return this
    }

    fun withNumTypeOf(function: Function<Number?, Num>): BaseBarSeriesBuilder {
        numFunction = function
        return this
    }

    fun withNumTypeOf(abstractNumClass: Class<out Num?>): BaseBarSeriesBuilder {
        if (abstractNumClass == DecimalNum::class.java) {
            numFunction = Function { `val`: Number? -> DecimalNum.valueOf(`val`) }
            return this
        } else if (abstractNumClass == DoubleNum::class.java) {
            numFunction = Function { i: Number? -> DoubleNum.valueOf(i) }
            return this
        }
        numFunction = Function { `val`: Number? -> DecimalNum.valueOf(`val`) }
        return this
    }

    companion object {
        /**
         * Default Num type function
         */
        private var defaultFunction = Function<Number?, Num> { `val`: Number? -> DecimalNum.valueOf(`val`) }

        @JvmStatic
        fun setDefaultFunction(defaultFunction: Function<Number?, Num>) {
            Companion.defaultFunction = defaultFunction
        }
    }
}