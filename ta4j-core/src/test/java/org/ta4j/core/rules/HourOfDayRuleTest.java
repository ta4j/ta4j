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

public class HourOfDayRuleTest extends AbstractIndicatorTest<Object, Object> {

    public HourOfDayRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfied() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T00:00:00Z")).add(); // Index=0, hour 0
        series.barBuilder().endTime(Instant.parse("2019-09-16T09:00:00Z")).add(); // Index=1, hour 9
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add(); // Index=2, hour 12
        series.barBuilder().endTime(Instant.parse("2019-09-16T15:00:00Z")).add(); // Index=3, hour 15
        series.barBuilder().endTime(Instant.parse("2019-09-16T18:00:00Z")).add(); // Index=4, hour 18
        series.barBuilder().endTime(Instant.parse("2019-09-16T21:00:00Z")).add(); // Index=5, hour 21
        series.barBuilder().endTime(Instant.parse("2019-09-16T23:00:00Z")).add(); // Index=6, hour 23
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        HourOfDayRule rule = new HourOfDayRule(dateTime, 0, 9, 12, 15, 18);

        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertTrue(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(5, null));
        assertFalse(rule.isSatisfied(6, null));
    }

    @Test
    public void isSatisfiedWithSingleHour() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add(); // Index=0, hour 12
        series.barBuilder().endTime(Instant.parse("2019-09-16T13:00:00Z")).add(); // Index=1, hour 13
        series.barBuilder().endTime(Instant.parse("2019-09-16T14:00:00Z")).add(); // Index=2, hour 14
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        HourOfDayRule rule = new HourOfDayRule(dateTime, 12);

        assertTrue(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
        assertFalse(rule.isSatisfied(2, null));
    }

    @Test
    public void isSatisfiedWithAllHours() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int hour = 0; hour < 24; hour++) {
            series.barBuilder().endTime(Instant.parse("2019-09-16T" + String.format("%02d:00:00Z", hour))).add();
        }
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        int[] allHours = new int[24];
        for (int i = 0; i < 24; i++) {
            allHours[i] = i;
        }
        HourOfDayRule rule = new HourOfDayRule(dateTime, allHours);

        for (int i = 0; i < 24; i++) {
            assertTrue("Hour " + i + " should be satisfied", rule.isSatisfied(i, null));
        }
    }

    @Test
    public void constructorWithInvalidHourNegative() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new HourOfDayRule(dateTime, -1);
            fail("Expected IllegalArgumentException for negative hour");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hour of day must be in range 0-23"));
        }
    }

    @Test
    public void constructorWithInvalidHourTooLarge() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new HourOfDayRule(dateTime, 24);
            fail("Expected IllegalArgumentException for hour > 23");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hour of day must be in range 0-23"));
        }
    }

    @Test
    public void constructorWithInvalidHourInArray() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        try {
            new HourOfDayRule(dateTime, 10, 15, 25);
            fail("Expected IllegalArgumentException for hour > 23 in array");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Hour of day must be in range 0-23"));
        }
    }

    @Test
    public void constructorWithBoundaryHours() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T00:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T23:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);

        HourOfDayRule rule = new HourOfDayRule(dateTime, 0, 23);
        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
    }

    /**
     * Tests serialization/deserialization round-trip for HourOfDayRule.
     * <p>
     * <b>Note:</b> This test may be skipped if serialization is not yet supported
     * for HourOfDayRule. The test uses {@code Assume.assumeNoException()} to
     * gracefully skip when serialization fails, rather than failing the build. This
     * is intentional - the test serves as a placeholder until serialization support
     * is implemented.
     * <p>
     * When serialization support is added to HourOfDayRule, this test should pass
     * automatically. See the TODO comment in HourOfDayRule class.
     */
    @Test
    public void serializeAndDeserialize() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T15:00:00Z")).add();
        series.barBuilder().endTime(Instant.parse("2019-09-16T18:00:00Z")).add();
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        HourOfDayRule rule = new HourOfDayRule(dateTime, 12, 15);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void isSatisfiedWithDuplicateHours() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder().endTime(Instant.parse("2019-09-16T12:00:00Z")).add(); // Index=0, hour 12
        series.barBuilder().endTime(Instant.parse("2019-09-16T13:00:00Z")).add(); // Index=1, hour 13
        var dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        // Duplicate hours should be handled gracefully (stored in Set)
        HourOfDayRule rule = new HourOfDayRule(dateTime, 12, 12, 12);

        assertTrue(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, null));
    }
}
