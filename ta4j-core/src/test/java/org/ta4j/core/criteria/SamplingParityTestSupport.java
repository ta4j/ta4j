/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.stream.IntStream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

final class SamplingParityTestSupport {

    private SamplingParityTestSupport() {
        throw new AssertionError("No instances");
    }

    static int[] weeklyEndIndicesUtc(BarSeries series) {
        var begin = series.getBeginIndex();
        var end = series.getEndIndex();

        return IntStream.rangeClosed(begin, end).filter(i -> {
            if (i == begin || i == end) {
                return true;
            }
            var now = series.getBar(i).getEndTime().atZone(ZoneOffset.UTC);
            var next = series.getBar(i + 1).getEndTime().atZone(ZoneOffset.UTC);
            return !sameIsoWeek(now, next);
        }).toArray();
    }

    private static boolean sameIsoWeek(ZonedDateTime a, ZonedDateTime b) {
        var weekFields = WeekFields.ISO;
        var weekA = a.get(weekFields.weekOfWeekBasedYear());
        var weekB = b.get(weekFields.weekOfWeekBasedYear());
        var yearA = a.get(weekFields.weekBasedYear());
        var yearB = b.get(weekFields.weekBasedYear());
        return weekA == weekB && yearA == yearB;
    }

    static int[] monthlyEndIndicesUtc(BarSeries series) {
        var begin = series.getBeginIndex();
        var end = series.getEndIndex();

        return IntStream.rangeClosed(begin, end).filter(i -> {
            if (i == begin || i == end) {
                return true;
            }
            var now = series.getBar(i).getEndTime().atZone(ZoneOffset.UTC);
            var next = series.getBar(i + 1).getEndTime().atZone(ZoneOffset.UTC);
            return !YearMonth.from(now).equals(YearMonth.from(next));
        }).toArray();
    }

    static BarSeries compressSeries(BarSeries source, int[] indices, String name) {
        var series = new BaseBarSeriesBuilder().withName(name).withNumFactory(source.numFactory()).build();

        IntStream.range(0, indices.length).forEach(i -> {
            var sourceBar = source.getBar(indices[i]);
            var close = sourceBar.getClosePrice().doubleValue();

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(sourceBar.getEndTime())
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });

        return series;
    }
}
