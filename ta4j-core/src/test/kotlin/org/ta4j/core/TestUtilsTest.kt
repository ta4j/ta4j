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

import org.junit.Test
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.num.Num
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.function.Function

class TestUtilsTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<BarSeries?, Num>(numFunction) {
    init {
        numStringDouble = numOf(bigDecimalDouble)
        diffNumStringDouble = numOf(diffBigDecimalDouble)
        numInt = numOf(aInt)
        diffNumInt = numOf(diffInt)
        numDouble = numOf(aDouble)
        diffNumDouble = numOf(diffDouble)
        val series = randomSeries()
        val diffSeries = randomSeries()
        indicator = ClosePriceIndicator(series)
        diffIndicator = ClosePriceIndicator(diffSeries)
    }

    private fun randomSeries(): BarSeries {
        val builder = BaseBarSeriesBuilder()
        val series: BarSeries = builder.withNumTypeOf(numFunction).build()
        var time = ZonedDateTime.of(1970, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault())
        var random: Double
        for (i in 0..999) {
            random = Math.random()
            time = time.plusDays(i.toLong())
            series.addBar(
                BaseBar(
                    Duration.ofDays(1), time, random, random, random, random, random, random, 0,
                    numFunction
                )
            )
        }
        return series
    }

    @Test
    fun testStringNum() {
        TestUtils.assertNumEquals(stringDouble, numStringDouble)
        TestUtils.assertNumNotEquals(stringDouble, diffNumStringDouble)
        TestUtils.assertNumNotEquals(diffStringDouble, numStringDouble)
        TestUtils.assertNumEquals(diffStringDouble, diffNumStringDouble)
    }

    @Test
    fun testNumNum() {
        TestUtils.assertNumEquals(numStringDouble, numStringDouble)
        TestUtils.assertNumNotEquals(numStringDouble, diffNumStringDouble)
        TestUtils.assertNumNotEquals(diffNumStringDouble, numStringDouble)
        TestUtils.assertNumEquals(diffNumStringDouble, diffNumStringDouble)
    }

    @Test
    fun testIntNum() {
        TestUtils.assertNumEquals(aInt, numInt)
        TestUtils.assertNumNotEquals(aInt, diffNumInt)
        TestUtils.assertNumNotEquals(diffInt, numInt)
        TestUtils.assertNumEquals(diffInt, diffNumInt)
    }

    @Test
    fun testDoubleNum() {
        TestUtils.assertNumEquals(aDouble, numDouble)
        TestUtils.assertNumNotEquals(aDouble, diffNumDouble)
        TestUtils.assertNumNotEquals(diffDouble, numDouble)
        TestUtils.assertNumEquals(diffDouble, diffNumDouble)
    }

    @Test
    fun testIndicator() {
        TestUtils.assertIndicatorEquals(indicator, indicator)
        TestUtils.assertIndicatorNotEquals(indicator!!, diffIndicator!!)
        TestUtils.assertIndicatorNotEquals(diffIndicator!!, indicator!!)
        TestUtils.assertIndicatorEquals(diffIndicator, diffIndicator)
    }

    companion object {
        private const val stringDouble = "1234567890.12345"
        private const val diffStringDouble = "1234567890.12346"
        private val bigDecimalDouble = BigDecimal(stringDouble)
        private val diffBigDecimalDouble = BigDecimal(diffStringDouble)
        private const val aInt = 1234567890
        private const val diffInt = 1234567891
        private const val aDouble = 1234567890.1234
        private const val diffDouble = 1234567890.1235
        private var numStringDouble: Num?=null
        private var diffNumStringDouble: Num?=null
        private var numInt: Num?=null
        private var diffNumInt: Num?=null
        private var numDouble: Num?=null
        private var diffNumDouble: Num?=null
        private var indicator: Indicator<Num>?=null
        private var diffIndicator: Indicator<Num>?=null
    }
}