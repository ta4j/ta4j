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
package org.ta4j.core.analysis.sampling;

import java.time.temporal.WeekFields;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.Instant;
import java.time.ZoneId;
import org.ta4j.core.BarSeries;

/**
 * Groups bar indices into sampling periods so return calculations can be aggregated.
 *
 * <p>
 * The grouping logic detects period boundaries from bar end times in the provided
 * {@link ZoneId}. Weekly grouping follows ISO week rules. Each sampling interval
 * is represented by a pair of indices
 * {@code (previousIndex, currentIndex)} that spans the period. The first pair
 * always uses the supplied anchor index as its starting point.
 *
 * @since 0.22.2
 */
public final class IndexPairGrouping {

    private static final WeekFields ISO_WEEK_FIELDS = WeekFields.ISO;

    /**
     * Supported sampling granularities.
     *
     * @since 0.22.2
     */
    public enum Sampling {
        PER_BAR, PER_SECOND, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY
    }

    private final Sampling sampling;
    private final ZoneId groupingZoneId;

    /**
     * Creates a grouping helper for the chosen aggregation mode and time zone.
     *
     * @param sampling the sampling granularity
     * @param groupingZoneId the time zone used to interpret bar end times
     * @since 0.22.2
     */
    public IndexPairGrouping(Sampling sampling, ZoneId groupingZoneId) {
        this.sampling = sampling;
        this.groupingZoneId = groupingZoneId;
    }

    /**
     * Returns index pairs spanning each sampling period from the provided range.
     *
     * @param series the bar series
     * @param anchorIndex the starting anchor index for the first sampled pair
     * @param start the first index eligible for sampling
     * @param end the last index eligible for sampling
     * @return a stream of index pairs describing each sampling interval
     * @since 0.22.2
     */
    public Stream<IndexPair> sample(BarSeries series, int anchorIndex, int start, int end) {
        if (sampling == Sampling.PER_BAR) {
            return IntStream.rangeClosed(start, end).mapToObj(i -> new IndexPair(i - 1, i));
        }

        var periodEndIndices = periodEndIndices(series, start, end).toArray();
        if (periodEndIndices.length == 0) {
            return Stream.empty();
        }

        var firstPair = Stream.of(new IndexPair(anchorIndex, periodEndIndices[0]));
        var consecutivePairs = IntStream.range(1, periodEndIndices.length)
                .mapToObj(k -> new IndexPair(periodEndIndices[k - 1], periodEndIndices[k]));

        return Stream.concat(firstPair, consecutivePairs);
    }

    private IntStream periodEndIndices(BarSeries series, int start, int end) {
        return IntStream.rangeClosed(start, end).filter(i -> isPeriodEnd(series, i, end));
    }

    private boolean isPeriodEnd(BarSeries series, int index, int endIndex) {
        if (index == endIndex) {
            return true;
        }

        var now = endTimeZoned(series, index);
        var next = endTimeZoned(series, index + 1);

        return switch (sampling) {
        case PER_SECOND -> !sameChronoUnit(now, next, ChronoUnit.SECONDS);
        case MINUTELY -> !sameChronoUnit(now, next, ChronoUnit.MINUTES);
        case HOURLY -> !sameChronoUnit(now, next, ChronoUnit.HOURS);
        case DAILY -> !now.toLocalDate().equals(next.toLocalDate());
        case WEEKLY -> !sameIsoWeek(now, next);
        case MONTHLY -> !YearMonth.from(now).equals(YearMonth.from(next));
        case PER_BAR -> true;
        };
    }

    private boolean sameChronoUnit(ZonedDateTime a, ZonedDateTime b, ChronoUnit chronoUnit) {
        return a.truncatedTo(chronoUnit).equals(b.truncatedTo(chronoUnit));
    }

    private boolean sameIsoWeek(ZonedDateTime a, ZonedDateTime b) {
        var weekA = a.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var weekB = b.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var yearA = a.get(ISO_WEEK_FIELDS.weekBasedYear());
        var yearB = b.get(ISO_WEEK_FIELDS.weekBasedYear());
        return weekA == weekB && yearA == yearB;
    }

    private ZonedDateTime endTimeZoned(BarSeries series, int index) {
        return endTimeInstant(series, index).atZone(groupingZoneId);
    }

    private Instant endTimeInstant(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

    /**
     * Pair of indices describing a sampled interval.
     *
     * @param previousIndex the interval start index
     * @param currentIndex the interval end index
     * @since 0.22.2
     */
    public record IndexPair(int previousIndex, int currentIndex) {
    }
}
