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

import junit.framework.TestCase
import org.junit.Test
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.num.DecimalNum
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import java.time.ZonedDateTime
import java.util.function.Function

class SeriesBuilderTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    private val seriesBuilder: BaseBarSeriesBuilder = BaseBarSeriesBuilder().withNumTypeOf(numFunction)
    @Test
    fun testBuilder() {
        val defaultSeries: BarSeries = seriesBuilder.build() // build a new empty unnamed bar series
        val defaultSeriesName: BarSeries =
            seriesBuilder.withName("default").build() // build a new empty bar series using
        // BigDecimal as delegate
        val doubleSeries: BarSeries = seriesBuilder.withMaxBarCount(100)
            .withNumTypeOf(DoubleNum::class.java)
            .withName("useDoubleNum")
            .build()
        val precisionSeries: BarSeries = seriesBuilder.withMaxBarCount(100)
            .withNumTypeOf(DecimalNum::class.java)
            .withName("usePrecisionNum")
            .build()
        for (i in 1000 downTo 0) {
            defaultSeries.addBar(ZonedDateTime.now().minusSeconds(i.toLong()), i, i, i, i, i)
            defaultSeriesName.addBar(ZonedDateTime.now().minusSeconds(i.toLong()), i, i, i, i, i)
            doubleSeries.addBar(ZonedDateTime.now().minusSeconds(i.toLong()), i, i, i, i, i)
            precisionSeries.addBar(ZonedDateTime.now().minusSeconds(i.toLong()), i, i, i, i, i)
        }
        TestUtils.assertNumEquals(0, defaultSeries.getBar(1000).closePrice)
        TestUtils.assertNumEquals(1000, defaultSeries.getBar(0).closePrice)
        TestCase.assertEquals(defaultSeriesName.name, "default")
        TestUtils.assertNumEquals(99, doubleSeries.getBar(0).closePrice)
        TestUtils.assertNumEquals(99, precisionSeries.getBar(0).closePrice)
    }

    @Test
    fun testNumFunctions() {
        val series: BarSeries = seriesBuilder.withNumTypeOf(DoubleNum::class.java).build()
        TestUtils.assertNumEquals(series.numOf(12), DoubleNum.valueOf(12))
        val seriesB: BarSeries = seriesBuilder.withNumTypeOf(DecimalNum::class.java).build()
        TestUtils.assertNumEquals(seriesB.numOf(12), DecimalNum.valueOf(12))
    }

    @Test
    fun testWrongNumType() {
        val series: BarSeries = seriesBuilder.withNumTypeOf(DecimalNum::class.java).build()
        TestUtils.assertNumNotEquals(series.numOf(12), DoubleNum.valueOf(12))
    }
}