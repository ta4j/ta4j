/*
 * SPDX-License-Identifier: MIT
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

    @Test
    public void serializeAndDeserialize() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T12:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-18T12:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        DayOfWeekRule rule = new DayOfWeekRule(dateTime, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
