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
 * Satisfied when the "minute of the hour" value of the
 * {@link DateTimeIndicator} matches the specified set of minutes (0-59).
 *
 * <p>
 * The {@link java.time.Instant UTC} represents a point in time on the
 * time-line, typically measured in milliseconds. It is independent of time
 * zones, days of the week, or months. However, this rule converts a UTC to a
 * ZonedDateTime with UTC to get the minute of the hour in that time zone.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 *
 * @since 0.19
 */
public class MinuteOfHourRule extends AbstractRule {

    private final int[] minutesOfHour;
    private final Set<Integer> minutesOfHourSet;
    private final DateTimeIndicator timeIndicator;

    /**
     * Constructor.
     *
     * @param timeIndicator the {@link DateTimeIndicator}
     * @param minutesOfHour the minutes of the hour (0-59)
     * @throws IllegalArgumentException if any minute is not in the range 0-59
     */
    public MinuteOfHourRule(DateTimeIndicator timeIndicator, int... minutesOfHour) {
        this.timeIndicator = Objects.requireNonNull(timeIndicator, "timeIndicator");
        Objects.requireNonNull(minutesOfHour, "minutesOfHour");
        this.minutesOfHour = Arrays.copyOf(minutesOfHour, minutesOfHour.length);
        this.minutesOfHourSet = new HashSet<>(this.minutesOfHour.length);
        for (int minute : this.minutesOfHour) {
            if (minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Minute of hour must be in range 0-59, but got: " + minute);
            }
        }
        for (int minute : this.minutesOfHour) {
            this.minutesOfHourSet.add(minute);
        }
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Instant dateTime = timeIndicator.getValue(index);
        final boolean satisfied = minutesOfHourSet.contains(dateTime.atZone(ZoneOffset.UTC).getMinute());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
