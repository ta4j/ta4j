/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;

/**
 * Satisfied when the "day of the week" value of the {@link DateTimeIndicator}
 * matches the specified set of {@link DayOfWeek}.
 *
 * <p>
 * The {@link java.time.Instant UTC} represents a point in time on the
 * time-line, typically measured in milliseconds. It is independent of time
 * zones, days of the week, or months. However, this rule converts a UTC to a
 * ZonedDateTime with UTC to get the day, week and month in that time zone.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class DayOfWeekRule extends AbstractRule {

    private final Set<DayOfWeek> daysOfWeekSet;
    private final DateTimeIndicator timeIndicator;

    /**
     * Constructor.
     *
     * @param timeIndicator the {@link DateTimeIndicator}
     * @param daysOfWeek    the days of the week
     */
    public DayOfWeekRule(DateTimeIndicator timeIndicator, DayOfWeek... daysOfWeek) {
        this.timeIndicator = timeIndicator;
        this.daysOfWeekSet = new HashSet<>(Arrays.asList(daysOfWeek));
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Instant dateTime = timeIndicator.getValue(index);
        final boolean satisfied = daysOfWeekSet.contains(dateTime.atZone(ZoneOffset.UTC).getDayOfWeek());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}