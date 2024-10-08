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

import java.time.LocalTime;
import java.util.List;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;

/**
 * Satisfied when the "local time" value of the {@link DateTimeIndicator} is
 * within the specified set of {@link TimeRange}.
 */
public class TimeRangeRule extends AbstractRule {

    public record TimeRange(LocalTime from, LocalTime to) {
    };

    private final List<TimeRange> timeRanges;
    private final DateTimeIndicator timeIndicator;

    /**
     * Constructor.
     *
     * @param timeRanges         the list of time ranges
     * @param beginTimeIndicator the beginTime indicator
     */
    public TimeRangeRule(List<TimeRange> timeRanges, DateTimeIndicator beginTimeIndicator) {
        this.timeRanges = timeRanges;
        this.timeIndicator = beginTimeIndicator;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        var dateTime = timeIndicator.getValue(index);
        var localTime = dateTime.toLocalTime();
        final var satisfied = timeRanges.stream()
                .anyMatch(timeRange -> !localTime.isBefore(timeRange.from()) && !localTime.isAfter(timeRange.to()));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

}
