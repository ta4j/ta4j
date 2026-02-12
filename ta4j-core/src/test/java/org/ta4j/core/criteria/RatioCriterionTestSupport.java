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
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

final class RatioCriterionTestSupport {

    private RatioCriterionTestSupport() {
        throw new AssertionError("No instances");
    }

    static BarSeries buildDailySeries(BarSeries series, double[] closes, Instant start) {
        IntStream.range(0, closes.length).forEach(i -> {
            Instant endTime = start.plus(Duration.ofDays(i + 1L));
            double close = closes[i];
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
            Instant endTime = start.plus(Duration.ofHours(i + 1L));
            double close = closes[i];
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
            Instant endTime = start.plus(Duration.ofMinutes(i + 1L));
            double close = closes[i];
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
            Instant endTime = start.plus(Duration.ofSeconds(i + 1L));
            double close = closes[i];
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
        Instant day0EndTime = day0StartUtc.plus(Duration.ofHours(23));
        double day0EndClose = dailyEndCloses[0];
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
            Instant dayBase = day0StartUtc.plus(Duration.ofDays(dayIndex));

            Instant[] times = new Instant[] { dayBase.plus(Duration.ofHours(10)), dayBase.plus(Duration.ofHours(15)),
                    dayBase.plus(Duration.ofHours(23)) };

            double prevDayEnd = dailyEndCloses[dayIndex - 1];
            double dayEnd = dailyEndCloses[dayIndex];
            double mid = (prevDayEnd + dayEnd) / 2.0;

            double[] closes = new double[] { prevDayEnd, mid, dayEnd };

            IntStream.range(0, times.length).forEach(i -> {
                double close = closes[i];
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
        Num amount = series.numFactory().one();
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        int split = begin + (end - begin) / 2;

        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(split, series.getBar(split).getClosePrice(), amount);

        if (split < end) {
            tradingRecord.enter(split, series.getBar(split).getClosePrice(), amount);
            tradingRecord.exit(end, series.getBar(end).getClosePrice(), amount);
        }

        return tradingRecord;
    }

    static TradingRecord tradingRecordWithGap(BarSeries series) {
        Num amount = series.numFactory().one();
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        int begin = series.getBeginIndex();

        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(begin + 1, series.getBar(begin + 1).getClosePrice(), amount);

        tradingRecord.enter(begin + 3, series.getBar(begin + 3).getClosePrice(), amount);
        tradingRecord.exit(begin + 4, series.getBar(begin + 4).getClosePrice(), amount);

        return tradingRecord;
    }

    static int[] weeklyEndIndicesUtc(BarSeries series) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();

        return IntStream.rangeClosed(begin, end).filter(i -> {
            if (i == begin || i == end) {
                return true;
            }
            ZonedDateTime now = series.getBar(i).getEndTime().atZone(ZoneOffset.UTC);
            ZonedDateTime next = series.getBar(i + 1).getEndTime().atZone(ZoneOffset.UTC);
            return !sameIsoWeek(now, next);
        }).toArray();
    }

    private static boolean sameIsoWeek(ZonedDateTime a, ZonedDateTime b) {
        WeekFields weekFields = WeekFields.ISO;
        int weekA = a.get(weekFields.weekOfWeekBasedYear());
        int weekB = b.get(weekFields.weekOfWeekBasedYear());
        int yearA = a.get(weekFields.weekBasedYear());
        int yearB = b.get(weekFields.weekBasedYear());
        return weekA == weekB && yearA == yearB;
    }

    static int[] monthlyEndIndicesUtc(BarSeries series) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();

        return IntStream.rangeClosed(begin, end).filter(i -> {
            if (i == begin || i == end) {
                return true;
            }
            ZonedDateTime now = series.getBar(i).getEndTime().atZone(ZoneOffset.UTC);
            ZonedDateTime next = series.getBar(i + 1).getEndTime().atZone(ZoneOffset.UTC);
            return !YearMonth.from(now).equals(YearMonth.from(next));
        }).toArray();
    }

    static BarSeries compressSeries(BarSeries source, int[] indices, String name) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(source.numFactory()).build();

        IntStream.range(0, indices.length).forEach(i -> {
            Bar sourceBar = source.getBar(indices[i]);
            double close = sourceBar.getClosePrice().doubleValue();

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
