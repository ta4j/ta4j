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
import java.util.function.IntFunction;
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
    private static final String TRADE_REQUIRES_TRADING_RECORD = "SamplingFrequency.TRADE requires a trading record; "
            + "use a TradingRecord-aware sampling API";

    private final SamplingFrequency samplingFrequency;
    private final ZoneId groupingZoneId;

    /**
     * Creates a grouping helper for the chosen aggregation mode and time zone.
     *
     * @param samplingFrequency the sampling granularity
     * @param groupingZoneId    the time zone used to interpret bar end times
     * @throws IllegalArgumentException if {@code samplingFrequency} is
     *                                  {@link SamplingFrequency#TRADE}, because
     *                                  trade sampling requires a trading record
     * @since 0.22.2
     */
    public SamplingFrequencyIndexes(SamplingFrequency samplingFrequency, ZoneId groupingZoneId) {
        this.samplingFrequency = Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        if (this.samplingFrequency == SamplingFrequency.TRADE) {
            throw new IllegalArgumentException(TRADE_REQUIRES_TRADING_RECORD);
        }
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
        return sample(index -> series.getBar(index).getEndTime(), anchorIndex, start, end);
    }

    /**
     * Returns index pairs using end times supplied by an immutable analysis
     * snapshot.
     *
     * @param endTimeAtIndex returns the captured end time for an absolute bar index
     * @param anchorIndex    the starting anchor index for the first sampled pair
     * @param start          the first index eligible for sampling
     * @param end            the last index eligible for sampling
     * @return a stream of index pairs describing each sampling interval
     * @since 0.26
     */
    public Stream<IndexPair> sample(IntFunction<Instant> endTimeAtIndex, int anchorIndex, int start, int end) {
        Objects.requireNonNull(endTimeAtIndex, "endTimeAtIndex must not be null");
        if (start > end || end - start < 1) {
            return Stream.empty();
        }
        if (samplingFrequency == SamplingFrequency.BAR) {
            return IntStream.rangeClosed(start, end).mapToObj(i -> new IndexPair(i - 1, i));
        }

        int[] periodEndIndices = periodEndIndices(endTimeAtIndex, start, end).toArray();
        if (periodEndIndices.length == 0) {
            return Stream.empty();
        }

        Stream<IndexPair> firstPair = Stream.of(new IndexPair(anchorIndex, periodEndIndices[0]));
        Stream<IndexPair> consecutivePairs = IntStream.range(1, periodEndIndices.length)
                .mapToObj(k -> new IndexPair(periodEndIndices[k - 1], periodEndIndices[k]));

        return Stream.concat(firstPair, consecutivePairs);
    }

    private IntStream periodEndIndices(IntFunction<Instant> endTimeAtIndex, int start, int end) {
        return IntStream.rangeClosed(start, end).filter(i -> isPeriodEnd(endTimeAtIndex, i, end));
    }

    private boolean isPeriodEnd(IntFunction<Instant> endTimeAtIndex, int index, int endIndex) {
        if (index == endIndex) {
            return true;
        }

        ZonedDateTime now = endTimeZoned(endTimeAtIndex, index);
        ZonedDateTime next = endTimeZoned(endTimeAtIndex, index + 1);

        return switch (samplingFrequency) {
        case SECOND -> crossesChronoUnitBoundary(now, next, ChronoUnit.SECONDS);
        case MINUTE -> crossesChronoUnitBoundary(now, next, ChronoUnit.MINUTES);
        case HOUR -> crossesChronoUnitBoundary(now, next, ChronoUnit.HOURS);
        case DAY -> !now.toLocalDate().equals(next.toLocalDate());
        case WEEK -> !sameIsoWeek(now, next);
        case MONTH -> !YearMonth.from(now).equals(YearMonth.from(next));
        case BAR -> true;
        case TRADE -> throw new IllegalStateException(TRADE_REQUIRES_TRADING_RECORD);
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

    private ZonedDateTime endTimeZoned(IntFunction<Instant> endTimeAtIndex, int index) {
        return endTimeAtIndex.apply(index).atZone(groupingZoneId);
    }

}
