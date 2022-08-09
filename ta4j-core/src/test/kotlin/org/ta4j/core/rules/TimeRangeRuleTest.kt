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
package org.ta4j.core.rules

import org.junit.Assert
import org.junit.Test
import org.ta4j.core.Bar
import org.ta4j.core.indicators.AbstractIndicatorTest
import org.ta4j.core.indicators.DateTimeIndicator
import org.ta4j.core.mocks.MockBarSeries
import org.ta4j.core.num.Num
import org.ta4j.core.rules.TimeRangeRule.TimeRange
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Function

class TimeRangeRuleTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Any?, Any?>(numFunction) {
    // Index=0
    // 1
    // 2
    // 3
    // 4
    // 5
    // 6
    // 7
    // 8
    // 9
    // 10
    @Test
    fun isSatisfiedForBuy() {
            val dtf = DateTimeFormatter.ISO_ZONED_DATE_TIME
            val dateTimeIndicator = DateTimeIndicator(
                MockBarSeries(
                    numFunction,
                    doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0),
                    arrayOf(
                        ZonedDateTime.parse("2019-09-17T00:00:00-00:00", dtf),  // Index=0
                        ZonedDateTime.parse("2019-09-17T05:00:00-00:00", dtf),  // 1
                        ZonedDateTime.parse("2019-09-17T07:00:00-00:00", dtf),  // 2
                        ZonedDateTime.parse("2019-09-17T08:00:00-00:00", dtf),  // 3
                        ZonedDateTime.parse("2019-09-17T15:00:00-00:00", dtf),  // 4
                        ZonedDateTime.parse("2019-09-17T15:05:00-00:00", dtf),  // 5
                        ZonedDateTime.parse("2019-09-17T16:59:00-00:00", dtf),  // 6
                        ZonedDateTime.parse("2019-09-17T17:05:00-00:00", dtf),  // 7
                        ZonedDateTime.parse("2019-09-17T23:00:00-00:00", dtf),  // 8
                        ZonedDateTime.parse("2019-09-17T23:30:00-00:00", dtf),  // 9
                        ZonedDateTime.parse("2019-09-17T23:35:00-00:00", dtf) // 10
                    )
                )
            ) { b: Bar? -> b!!.endTime }
            val rule = TimeRangeRule(
                Arrays.asList(
                    TimeRange(LocalTime.of(0, 0), LocalTime.of(4, 0)),
                    TimeRange(LocalTime.of(6, 0), LocalTime.of(7, 0)),
                    TimeRange(LocalTime.of(12, 0), LocalTime.of(15, 0)),
                    TimeRange(LocalTime.of(17, 0), LocalTime.of(21, 0)),
                    TimeRange(LocalTime.of(22, 0), LocalTime.of(23, 30))
                ),
                dateTimeIndicator
            )
            Assert.assertTrue(rule.isSatisfied(0, null))
            Assert.assertFalse(rule.isSatisfied(1, null))
            Assert.assertTrue(rule.isSatisfied(2, null))
            Assert.assertFalse(rule.isSatisfied(3, null))
            Assert.assertTrue(rule.isSatisfied(4, null))
            Assert.assertFalse(rule.isSatisfied(5, null))
            Assert.assertFalse(rule.isSatisfied(6, null))
            Assert.assertTrue(rule.isSatisfied(7, null))
            Assert.assertTrue(rule.isSatisfied(8, null))
            Assert.assertTrue(rule.isSatisfied(9, null))
            Assert.assertFalse(rule.isSatisfied(10, null))
        }
}