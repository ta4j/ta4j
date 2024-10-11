/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class DayOfWeekRuleTest extends AbstractIndicatorTest<Object, Object> {

    public DayOfWeekRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfied() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add(); // Index=0, Mon
        series.barBuilder().endTime(Instant.parse("2019-09-17T12:00:00Z")).add(); // 1, Tue
        series.barBuilder().endTime(Instant.parse("2019-09-18T12:00:00Z")).add(); // 2, Wed
        series.barBuilder().endTime(Instant.parse("2019-09-19T12:00:00Z")).add(); // 3, Thu
        series.barBuilder().endTime(Instant.parse("2019-09-20T12:00:00Z")).add(); // 4, Fri
        series.barBuilder().endTime(Instant.parse("2019-09-21T12:00:00Z")).add(); // 5, Sat
        series.barBuilder().endTime(Instant.parse("2019-09-22T12:00:00Z")).add(); // 6, Sun
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        DayOfWeekRule rule = new DayOfWeekRule(dateTime, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertTrue(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(5, null));
        assertFalse(rule.isSatisfied(6, null));
    }
}
