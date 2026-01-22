/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class TimeRangeRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TimeRangeRuleTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Test
    public void isSatisfiedForBuy() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-17T00:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T05:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T07:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T08:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T15:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T15:05:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T16:59:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T17:05:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T23:00:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T23:30:00Z")).closePrice(100).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T23:35:00Z")).closePrice(100).add();

        var dateTimeIndicator = new DateTimeIndicator(series, Bar::getBeginTime);
        var _00_04 = new TimeRangeRule.TimeRange(LocalTime.of(0, 0), LocalTime.of(4, 0));
        var _06_07 = new TimeRangeRule.TimeRange(LocalTime.of(6, 0), LocalTime.of(7, 0));
        var _12_15 = new TimeRangeRule.TimeRange(LocalTime.of(12, 0), LocalTime.of(15, 0));
        var _17_21 = new TimeRangeRule.TimeRange(LocalTime.of(17, 0), LocalTime.of(21, 0));
        var _22_2330 = new TimeRangeRule.TimeRange(LocalTime.of(22, 0), LocalTime.of(23, 30));
        var allRanges = List.of(_00_04, _06_07, _12_15, _17_21, _22_2330);
        TimeRangeRule rule = new TimeRangeRule(allRanges, dateTimeIndicator);

        assertTrue(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertFalse(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(5, null));
        assertFalse(rule.isSatisfied(6, null));
        assertTrue(rule.isSatisfied(7, null));
        assertTrue(rule.isSatisfied(8, null));
        assertTrue(rule.isSatisfied(9, null));
        assertFalse(rule.isSatisfied(10, null));
    }

    @Test
    public void serializeAndDeserialize() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-17T02:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T18:00:00Z")).add();
        var dateTimeIndicator = new DateTimeIndicator(series, Bar::getBeginTime);
        var range = new TimeRangeRule.TimeRange(LocalTime.of(1, 0), LocalTime.of(3, 0));
        TimeRangeRule rule = new TimeRangeRule(List.of(range), dateTimeIndicator);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void constructorWithSecondArraysEvaluatesRanges() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-17T02:30:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-17T18:15:00Z")).add();
        var dateTimeIndicator = new DateTimeIndicator(series, Bar::getBeginTime);
        int[] from = { LocalTime.of(2, 0).toSecondOfDay(), LocalTime.of(18, 0).toSecondOfDay() };
        int[] to = { LocalTime.of(3, 0).toSecondOfDay(), LocalTime.of(19, 0).toSecondOfDay() };

        TimeRangeRule rule = new TimeRangeRule(dateTimeIndicator, from, to);

        assertTrue("02:30 should be inside first range", rule.isSatisfied(0, null));
        assertTrue("18:15 should be inside second range", rule.isSatisfied(1, null));
    }

    @Test
    public void constructorWithSecondArraysValidatesLengths() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var dateTimeIndicator = new DateTimeIndicator(series, Bar::getBeginTime);
        int[] from = { LocalTime.of(2, 0).toSecondOfDay() };
        int[] to = { LocalTime.of(3, 0).toSecondOfDay(), LocalTime.of(4, 0).toSecondOfDay() };

        assertThrows(IllegalArgumentException.class, () -> new TimeRangeRule(dateTimeIndicator, from, to));
    }

    @Test
    public void constructorWithSecondArraysValidatesBounds() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var dateTimeIndicator = new DateTimeIndicator(series, Bar::getBeginTime);
        int[] from = { -1 };
        int[] to = { LocalTime.of(1, 0).toSecondOfDay() };

        assertThrows(IllegalArgumentException.class, () -> new TimeRangeRule(dateTimeIndicator, from, to));
    }
}
