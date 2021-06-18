/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
import java.time.ZonedDateTime;
import java.util.List;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.DateTimeIndicator;

/**
 * Time range rule.
 *
 * Satisfied when the local time value of the DateTimeIndicator is within a time
 * range specified in the TimeRange list.
 */
public class TimeRangeRule extends AbstractRule {

    private final List<TimeRange> timeRanges;
    private final DateTimeIndicator timeIndicator;

    public TimeRangeRule(List<TimeRange> timeRanges, DateTimeIndicator beginTimeIndicator) {
        this.timeRanges = timeRanges;
        this.timeIndicator = beginTimeIndicator;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        ZonedDateTime dateTime = this.timeIndicator.getValue(index);
        LocalTime localTime = dateTime.toLocalTime();
        satisfied = this.timeRanges.stream()
                .anyMatch(
                        timeRange -> !localTime.isBefore(timeRange.getFrom()) && !localTime.isAfter(timeRange.getTo()));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    public static class TimeRange {

        private final LocalTime from;
        private final LocalTime to;

        public TimeRange(LocalTime from, LocalTime to) {
            this.from = from;
            this.to = to;
        }

        public LocalTime getFrom() {
            return from;
        }

        public LocalTime getTo() {
            return to;
        }
    }
}
