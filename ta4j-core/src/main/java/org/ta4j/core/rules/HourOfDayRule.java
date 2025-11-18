/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;

/**
 * Satisfied when the "hour of the day" value of the {@link DateTimeIndicator}
 * matches the specified set of hours (0-23).
 *
 * <p>
 * The {@link java.time.Instant UTC} represents a point in time on the
 * time-line, typically measured in milliseconds. It is independent of time
 * zones, days of the week, or months. However, this rule converts a UTC to a
 * ZonedDateTime with UTC to get the hour of the day in that time zone.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 *
 * @since 0.19
 */
public class HourOfDayRule extends AbstractRule {

    private final int[] hoursOfDay;
    private final Set<Integer> hoursOfDaySet;
    private final DateTimeIndicator timeIndicator;

    /**
     * Constructor.
     *
     * @param timeIndicator the {@link DateTimeIndicator}
     * @param hoursOfDay    the hours of the day (0-23)
     * @throws IllegalArgumentException if any hour is not in the range 0-23
     */
    public HourOfDayRule(DateTimeIndicator timeIndicator, int... hoursOfDay) {
        this.timeIndicator = Objects.requireNonNull(timeIndicator, "timeIndicator");
        Objects.requireNonNull(hoursOfDay, "hoursOfDay");
        this.hoursOfDay = Arrays.copyOf(hoursOfDay, hoursOfDay.length);
        this.hoursOfDaySet = new HashSet<>(this.hoursOfDay.length);
        for (int hour : this.hoursOfDay) {
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Hour of day must be in range 0-23, but got: " + hour);
            }
        }
        for (int hour : this.hoursOfDay) {
            this.hoursOfDaySet.add(hour);
        }
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Instant dateTime = timeIndicator.getValue(index);
        final boolean satisfied = hoursOfDaySet.contains(dateTime.atZone(ZoneOffset.UTC).getHour());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
