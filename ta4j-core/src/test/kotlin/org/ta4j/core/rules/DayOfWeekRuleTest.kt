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
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Function

class DayOfWeekRuleTest(numFunction: Function<Number?, Num>) : AbstractIndicatorTest<Any?, Any?>(numFunction) {
    // Index=0, Mon
    // 1, Tue
    // 2, Wed
    // 3, Thu
    // 4, Fri
    // 5, Sat
    // 6, Sun
    @Test
    fun isSatisfied() {
            val dtf = DateTimeFormatter.ISO_ZONED_DATE_TIME
            val dateTime = DateTimeIndicator(
                MockBarSeries(
                    numFunction, doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0), arrayOf(
                        ZonedDateTime.parse("2019-09-16T12:00:00-00:00", dtf),  // Index=0, Mon
                        ZonedDateTime.parse("2019-09-17T12:00:00-00:00", dtf),  // 1, Tue
                        ZonedDateTime.parse("2019-09-18T12:00:00-00:00", dtf),  // 2, Wed
                        ZonedDateTime.parse("2019-09-19T12:00:00-00:00", dtf),  // 3, Thu
                        ZonedDateTime.parse("2019-09-20T12:00:00-00:00", dtf),  // 4, Fri
                        ZonedDateTime.parse("2019-09-21T12:00:00-00:00", dtf),  // 5, Sat
                        ZonedDateTime.parse("2019-09-22T12:00:00-00:00", dtf) // 6, Sun
                    )
                ), { b: Bar? -> b!!.endTime }
            )
            val rule = DayOfWeekRule(
                dateTime, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            )
            Assert.assertTrue(rule.isSatisfied(0, null))
            Assert.assertTrue(rule.isSatisfied(1, null))
            Assert.assertTrue(rule.isSatisfied(2, null))
            Assert.assertTrue(rule.isSatisfied(3, null))
            Assert.assertTrue(rule.isSatisfied(4, null))
            Assert.assertFalse(rule.isSatisfied(5, null))
            Assert.assertFalse(rule.isSatisfied(6, null))
        }
}