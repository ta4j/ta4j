/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.frequency;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;

/**
 * Groups bar indices into sampling periods so return calculations can be
 * aggregated.
 *
 * <p>
 * The grouping logic detects period boundaries from bar end times in the
 * provided {@link ZoneId}. Weekly grouping follows ISO week rules. Each
 * sampling interval is represented by a pair of indices
 * {@code (previousIndex, currentIndex)} that spans the period. The first pair
 * always uses the supplied anchor index as its starting point.
 *
 * @since 0.22.2
 */
public final class SamplingFrequencyIndexes {

    private static final WeekFields ISO_WEEK_FIELDS = WeekFields.ISO;

    private final SamplingFrequency samplingFrequency;
    private final ZoneId groupingZoneId;

    /**
     * Creates a grouping helper for the chosen aggregation mode and time zone.
     *
     * @param samplingFrequency the sampling granularity
     * @param groupingZoneId    the time zone used to interpret bar end times
     * @since 0.22.2
     */
    public SamplingFrequencyIndexes(SamplingFrequency samplingFrequency, ZoneId groupingZoneId) {
        this.samplingFrequency = Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        this.groupingZoneId = Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
    }

    /**
     * Returns index pairs spanning each sampling period from the provided range.
     *
     * @param series      the bar series
     * @param anchorIndex the starting anchor index for the first sampled pair
     * @param start       the first index eligible for sampling
     * @param end         the last index eligible for sampling
     * @return a stream of index pairs describing each sampling interval
     * @since 0.22.2
     */
    public Stream<IndexPair> sample(BarSeries series, int anchorIndex, int start, int end) {
        if (start > end || end - start < 1) {
            return Stream.empty();
        }
        if (samplingFrequency == SamplingFrequency.BAR) {
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

        return switch (samplingFrequency) {
        case SECOND -> crossesChronoUnitBoundary(now, next, ChronoUnit.SECONDS);
        case MINUTE -> crossesChronoUnitBoundary(now, next, ChronoUnit.MINUTES);
        case HOUR -> crossesChronoUnitBoundary(now, next, ChronoUnit.HOURS);
        case DAY -> !now.toLocalDate().equals(next.toLocalDate());
        case WEEK -> !sameIsoWeek(now, next);
        case MONTH -> !YearMonth.from(now).equals(YearMonth.from(next));
        case BAR -> true;
        };
    }

    private boolean crossesChronoUnitBoundary(ZonedDateTime a, ZonedDateTime b, ChronoUnit chronoUnit) {
        return !a.truncatedTo(chronoUnit).equals(b.truncatedTo(chronoUnit));
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

}
