/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MinuteOfHourRuleTest extends AbstractIndicatorTest<Object, Object> {

    public MinuteOfHourRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfied() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add(); // Index=0, minute 0
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:15:00Z")).add(); // Index=1, minute 15
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add(); // Index=2, minute 30
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:45:00Z")).add(); // Index=3, minute 45
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:50:00Z")).add(); // Index=4, minute 50
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:55:00Z")).add(); // Index=5, minute 55
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:59:00Z")).add(); // Index=6, minute 59
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, 0, 15, 30, 45, 50);

        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertTrue(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(5, null));
        assertFalse(rule.isSatisfied(6, null));
    }

    @Test
    public void isSatisfiedWithSingleMinute() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add(); // Index=0, minute 30
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:31:00Z")).add(); // Index=1, minute 31
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:32:00Z")).add(); // Index=2, minute 32
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, 30);

        assertTrue(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
    }

    @Test
    public void isSatisfiedWithAllMinutes() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int minute = 0; minute < 60; minute++) {
            series.barBuilder().endTime(Instant.parse("2019-09-16T12:" + String.format("%02d:00Z", minute))).add();
        }
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        int[] allMinutes = new int[60];
        for (int i = 0; i < 60; i++) {
            allMinutes[i] = i;
        }
        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, allMinutes);

        for (int i = 0; i < 60; i++) {
            assertTrue("Minute " + i + " should be satisfied", rule.isSatisfied(i, null));
        }
    }

    @Test
    public void constructorWithInvalidMinuteNegative() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new MinuteOfHourRule(dateTime, -1);
            fail("Expected IllegalArgumentException for negative minute");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minute of hour must be in range 0-59"));
        }
    }

    @Test
    public void constructorWithInvalidMinuteTooLarge() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new MinuteOfHourRule(dateTime, 60);
            fail("Expected IllegalArgumentException for minute > 59");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minute of hour must be in range 0-59"));
        }
    }

    @Test
    public void constructorWithInvalidMinuteInArray() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new MinuteOfHourRule(dateTime, 10, 30, 60);
            fail("Expected IllegalArgumentException for minute > 59 in array");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Minute of hour must be in range 0-59"));
        }
    }

    @Test
    public void constructorWithBoundaryMinutes() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:59:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, 0, 59);
        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
    }

    /**
     * Tests serialization/deserialization round-trip for MinuteOfHourRule.
     * <p>
     * <b>Note:</b> This test may be skipped if serialization is not yet supported
     * for MinuteOfHourRule. The test uses {@code Assume.assumeNoException()} to
     * gracefully skip when serialization fails, rather than failing the build. This
     * is intentional - the test serves as a placeholder until serialization support
     * is implemented.
     * <p>
     * When serialization support is added to MinuteOfHourRule, this test should
     * pass automatically. See the TODO comment in MinuteOfHourRule class.
     */
    @Test
    public void serializeAndDeserialize() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:15:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:45:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, 15, 30);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void isSatisfiedWithDuplicateMinutes() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:30:00Z")).add(); // Index=0, minute 30
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:31:00Z")).add(); // Index=1, minute 31
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        // Duplicate minutes should be handled gracefully (stored in Set)
        MinuteOfHourRule rule = new MinuteOfHourRule(dateTime, 30, 30, 30);

        assertTrue(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
    }
}
