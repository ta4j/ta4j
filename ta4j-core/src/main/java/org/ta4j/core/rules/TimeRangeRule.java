/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;

/**
 * Satisfied when the "local time" value of the {@link DateTimeIndicator} is
 * within the specified set of {@link TimeRange}.
 *
 * <p>
 * An {@link java.time.Instant UTC} itself does not have a concept of a local
 * time, as UTC is a time standard that does not include details such as days of
 * the week. However, this rule converts a UTC to a ZonedDateTime in the
 * system's default time zone and then to a LocalTime to get the local time in
 * that time zone.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class TimeRangeRule extends AbstractRule {

    public record TimeRange(LocalTime from, LocalTime to) {
    }

    private final List<TimeRange> timeRanges;
    private final DateTimeIndicator timeIndicator;
    private final int[] fromSecondOfDay;
    private final int[] toSecondOfDay;

    /**
     * Constructor.
     *
     * @param timeRanges         the list of time ranges
     * @param beginTimeIndicator the beginTime indicator
     */
    public TimeRangeRule(List<TimeRange> timeRanges, DateTimeIndicator beginTimeIndicator) {
        this(validatedConfig(beginTimeIndicator, extractSeconds(timeRanges, true), extractSeconds(timeRanges, false)));
    }

    /**
     * Constructor for serialization support that accepts the ranges as total
     * seconds-of-day arrays.
     *
     * @param beginTimeIndicator the beginTime indicator
     * @param fromSecondOfDay    the inclusive range starting points measured in
     *                           seconds since midnight (0-86399)
     * @param toSecondOfDay      the inclusive range ending points measured in
     *                           seconds since midnight (0-86399)
     */
    public TimeRangeRule(DateTimeIndicator beginTimeIndicator, int[] fromSecondOfDay, int[] toSecondOfDay) {
        this(validatedConfig(beginTimeIndicator, fromSecondOfDay, toSecondOfDay));
    }

    private TimeRangeRule(Config config) {
        this.timeIndicator = config.timeIndicator();
        this.fromSecondOfDay = config.fromSecondOfDay();
        this.toSecondOfDay = config.toSecondOfDay();
        this.timeRanges = config.timeRanges();
    }

    private static Config validatedConfig(DateTimeIndicator beginTimeIndicator, int[] fromSecondOfDay,
            int[] toSecondOfDay) {
        DateTimeIndicator validatedTimeIndicator = Objects.requireNonNull(beginTimeIndicator, "timeIndicator");
        Objects.requireNonNull(fromSecondOfDay, "fromSecondOfDay");
        Objects.requireNonNull(toSecondOfDay, "toSecondOfDay");
        if (fromSecondOfDay.length != toSecondOfDay.length) {
            throw new IllegalArgumentException("fromSecondOfDay and toSecondOfDay must have the same length");
        }
        if (fromSecondOfDay.length == 0) {
            throw new IllegalArgumentException("At least one time range is required");
        }
        int[] copiedFromSecondOfDay = Arrays.copyOf(fromSecondOfDay, fromSecondOfDay.length);
        int[] copiedToSecondOfDay = Arrays.copyOf(toSecondOfDay, toSecondOfDay.length);
        List<TimeRange> normalizedRanges = new ArrayList<>(copiedFromSecondOfDay.length);
        for (int i = 0; i < copiedFromSecondOfDay.length; i++) {
            LocalTime from = LocalTime.ofSecondOfDay(validateSecond(copiedFromSecondOfDay[i]));
            LocalTime to = LocalTime.ofSecondOfDay(validateSecond(copiedToSecondOfDay[i]));
            normalizedRanges.add(new TimeRange(from, to));
        }
        return new Config(validatedTimeIndicator, copiedFromSecondOfDay, copiedToSecondOfDay,
                List.copyOf(normalizedRanges));
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        var localTime = LocalTime.ofInstant(timeIndicator.getValue(index), ZoneOffset.UTC);
        final boolean satisfied = timeRanges.stream().anyMatch(range -> {
            return !localTime.isBefore(range.from()) && !localTime.isAfter(range.to());
        });
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private static int validateSecond(int second) {
        if (second < 0 || second >= 24 * 60 * 60) {
            throw new IllegalArgumentException("Second of day must be between 0 and 86399 but was: " + second);
        }
        return second;
    }

    private static int[] extractSeconds(List<TimeRange> ranges, boolean useFrom) {
        Objects.requireNonNull(ranges, "timeRanges");
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("At least one time range is required");
        }
        int[] seconds = new int[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) {
            TimeRange range = Objects.requireNonNull(ranges.get(i), "timeRange");
            LocalTime time = useFrom ? range.from() : range.to();
            seconds[i] = Objects.requireNonNull(time, "time").toSecondOfDay();
        }
        return seconds;
    }

    private record Config(DateTimeIndicator timeIndicator, int[] fromSecondOfDay, int[] toSecondOfDay,
            List<TimeRange> timeRanges) {
    }
}
