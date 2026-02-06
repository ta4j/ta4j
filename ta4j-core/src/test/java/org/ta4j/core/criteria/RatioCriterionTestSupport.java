/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.stream.IntStream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;

final class RatioCriterionTestSupport {

    private RatioCriterionTestSupport() {
        throw new AssertionError("No instances");
    }

    static BarSeries buildDailySeries(BarSeries series, double[] closes, Instant start) {
        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofDays(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });
        return series;
    }

    static BarSeries buildHourlySeries(BarSeries series, double[] closes, Instant start) {
        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofHours(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });
        return series;
    }

    static BarSeries buildMinuteSeries(BarSeries series, double[] closes, Instant start) {
        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofMinutes(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });
        return series;
    }

    static BarSeries buildSecondSeries(BarSeries series, double[] closes, Instant start) {
        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofSeconds(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofSeconds(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });
        return series;
    }

    static BarSeries buildIntradaySeriesWithDailyEnds(BarSeries series, double[] dailyEndCloses, Instant day0StartUtc) {
        var day0EndTime = day0StartUtc.plus(Duration.ofHours(23));
        var day0EndClose = dailyEndCloses[0];
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofHours(1))
                .endTime(day0EndTime)
                .openPrice(day0EndClose)
                .highPrice(day0EndClose)
                .lowPrice(day0EndClose)
                .closePrice(day0EndClose)
                .volume(1)
                .build());

        IntStream.range(1, dailyEndCloses.length).forEach(dayIndex -> {
            var dayBase = day0StartUtc.plus(Duration.ofDays(dayIndex));

            var times = new Instant[] { dayBase.plus(Duration.ofHours(10)), dayBase.plus(Duration.ofHours(15)),
                    dayBase.plus(Duration.ofHours(23)) };

            var prevDayEnd = dailyEndCloses[dayIndex - 1];
            var dayEnd = dailyEndCloses[dayIndex];
            var mid = (prevDayEnd + dayEnd) / 2.0;

            var closes = new double[] { prevDayEnd, mid, dayEnd };

            IntStream.range(0, times.length).forEach(i -> {
                var close = closes[i];
                series.addBar(series.barBuilder()
                        .timePeriod(Duration.ofHours(1))
                        .endTime(times[i])
                        .openPrice(close)
                        .highPrice(close)
                        .lowPrice(close)
                        .closePrice(close)
                        .volume(1)
                        .build());
            });
        });

        return series;
    }

    static TradingRecord alwaysInvested(BarSeries series) {
        var amount = series.numFactory().one();
        var begin = series.getBeginIndex();
        var end = series.getEndIndex();
        var split = begin + (end - begin) / 2;

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(split, series.getBar(split).getClosePrice(), amount);

        if (split < end) {
            tradingRecord.enter(split, series.getBar(split).getClosePrice(), amount);
            tradingRecord.exit(end, series.getBar(end).getClosePrice(), amount);
        }

        return tradingRecord;
    }

    static TradingRecord tradingRecordWithGap(BarSeries series) {
        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        var begin = series.getBeginIndex();

        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(begin + 1, series.getBar(begin + 1).getClosePrice(), amount);

        tradingRecord.enter(begin + 3, series.getBar(begin + 3).getClosePrice(), amount);
        tradingRecord.exit(begin + 4, series.getBar(begin + 4).getClosePrice(), amount);

        return tradingRecord;
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
