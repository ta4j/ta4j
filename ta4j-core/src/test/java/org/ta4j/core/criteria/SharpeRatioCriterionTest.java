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
package org.ta4j.core.criteria;

import java.time.*;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.stream.IntStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import static org.ta4j.core.criteria.SharpeRatioCriterion.Annualization;
import static org.ta4j.core.analysis.sampling.IndexPairGrouping.Sampling;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SharpeRatioCriterionTest extends AbstractCriterionTest {

    public SharpeRatioCriterionTest(NumFactory numFactory) {
        super(params -> new SharpeRatioCriterion((Num) params[0], (Sampling) params[1], (Annualization) params[2],
                (ZoneId) params[3]), numFactory);
    }

    @Test
    public void returnsKnownSharpePerPeriod_whenAlwaysInvested() {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("sr_test").build();

        var start = Instant.parse("2024-01-01T00:00:00Z");
        var closes = new double[] { 100d, 150d, 150d, 225d, 225d };

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

        var amount = series.numFactory().one();

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, tradingRecord).doubleValue();
        var expected = Math.sqrt(3.0) / 2.0;

        assertEquals(expected, actual, 1e-12);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var period = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();
        var annualized = criterion(Sampling.PER_BAR, Annualization.ANNUALIZED).calculate(series, tradingRecord)
                .doubleValue();

        var expectedFactor = Math.sqrt(365.2425d);
        var expected = period * expectedFactor;
        assertEquals(expected, annualized, 1e-9);
    }

    @Test
    public void samplingDailyEqualsPerBar_whenBarsAreDaily() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();
        var daily = criterion(Sampling.DAILY, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();

        assertEquals(perBar, daily, 1e-12);
    }

    @Test
    public void samplingDailyOnIntradayMatchesPerBarOnCompressedDailySeries() {
        var dayEndCloses = new double[] { 100d, 150d, 150d, 225d, 225d };

        var intradaySeries = buildIntradaySeriesWithDailyEnds(dayEndCloses, Instant.parse("2024-01-01T00:00:00Z"));
        var intradayTradingRecord = alwaysInvested(intradaySeries);

        var dailySeries = buildDailySeries(dayEndCloses, Instant.parse("2024-01-01T00:00:00Z"));
        var dailyTradingRecord = alwaysInvested(dailySeries);

        var intradayDailySampling = criterion(Sampling.DAILY, Annualization.PERIOD)
                .calculate(intradaySeries, intradayTradingRecord)
                .doubleValue();

        var compressedDailyPerBar = criterion(Sampling.PER_BAR, Annualization.PERIOD)
                .calculate(dailySeries, dailyTradingRecord)
                .doubleValue();

        assertEquals(compressedDailyPerBar, intradayDailySampling, 1e-12);
    }

    @Test
    public void groupingZoneIdMakesSharpeZeroInOneZoneButNotTheOther() {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("zone_test_deterministic").build();

        var endTimes = new Instant[] { Instant.parse("2024-01-01T23:30:00Z"), // NY: Jan 1
                Instant.parse("2024-01-02T00:30:00Z"), // NY: Jan 1 -> day boundary happens between this and next
                Instant.parse("2024-01-02T23:30:00Z"), // NY: Jan 2
                Instant.parse("2024-01-03T00:30:00Z"), // NY: Jan 2 -> day boundary happens between this and next
                Instant.parse("2024-01-03T23:30:00Z"), // NY: Jan 3
                Instant.parse("2024-01-04T00:30:00Z") // NY: Jan 3 (last)
        };

        // Constructed so that NY daily sampling uses indices (1, 3, 5) with constant
        // returns:
        // 150/100 = 1.5, 225/150 = 1.5, 337.5/225 = 1.5 -> stdev=0 -> Sharpe=0
        // UTC daily sampling uses indices (2, 4, 5) -> non-constant returns -> Sharpe
        // != 0
        var closes = new double[] { 100d, 150d, 80d, 225d, 300d, 337.5d };

        IntStream.range(0, closes.length).forEach(i -> {
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(endTimes[i])
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });

        var tradingRecord = alwaysInvested(series);

        var sharpeNewYork = criterion(ZoneId.of("America/New_York")).calculate(series, tradingRecord).doubleValue();

        var sharpeUtc = criterion(ZoneOffset.UTC).calculate(series, tradingRecord).doubleValue();

        assertEquals(0d, sharpeNewYork, 0d);
        assertTrue(Math.abs(sharpeUtc) > 1e-12);
    }

    @Test
    public void riskFreeRateReducesSharpe_whenPositive() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sharpeNoRf = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var sharpeWithRf = criterion().calculate(series, tradingRecord).doubleValue();

        assertTrue(sharpeWithRf < sharpeNoRf);
    }

    @Test
    public void returnsZero_whenNoClosedPositionsInTradingRecord() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenOpenPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var openPosition = tradingRecord.getCurrentPosition();
        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, openPosition);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenStdevIsZero() {
        var series = buildDailySeries(new double[] { 100d, 100d, 100d, 100d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenLessThanTwoReturnsAreAvailable() {
        var series = buildDailySeries(new double[] { 100d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void samplingWeeklyOnDailyMatchesPerBarOnCompressedWeeklySeries() {
        var closes = IntStream.range(0, 15).mapToDouble(i -> 100d + i + ((i % 4) == 0 ? 7d : -3d)).toArray();

        var series = buildDailySeries(closes, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var weekly = criterion(Sampling.WEEKLY, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();

        var weeklyCompressedSeries = compressSeries(series, weeklyEndIndicesUtc(series), "weekly_compressed");
        var weeklyCompressed = criterion(Sampling.PER_BAR, Annualization.PERIOD)
                .calculate(weeklyCompressedSeries, alwaysInvested(weeklyCompressedSeries))
                .doubleValue();

        assertEquals(weeklyCompressed, weekly, 1e-12);
    }

    @Test
    public void samplingMonthlyOnDailyMatchesPerBarOnCompressedMonthlySeries() {
        var closes = IntStream.range(0, 45).mapToDouble(i -> 120d + i + ((i % 7) == 0 ? 11d : -2d)).toArray();

        var series = buildDailySeries(closes, Instant.parse("2024-01-24T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var monthly = criterion(Sampling.MONTHLY, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();

        var monthlyCompressedSeries = compressSeries(series, monthlyEndIndicesUtc(series), "monthly_compressed");
        var monthlyCompressed = criterion(Sampling.PER_BAR, Annualization.PERIOD)
                .calculate(monthlyCompressedSeries, alwaysInvested(monthlyCompressedSeries))
                .doubleValue();

        assertEquals(monthlyCompressed, monthly, 1e-12);
    }

    @Test
    public void positionCalculateEqualsTradingRecordCalculate_forSingleClosedTrade() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var position = tradingRecord.getPositions().getFirst();
        var criterion = criterion(Sampling.PER_BAR, Annualization.PERIOD);

        var byTradingRecord = criterion.calculate(series, tradingRecord).doubleValue();
        var byPosition = criterion.calculate(series, position).doubleValue();

        assertEquals(byTradingRecord, byPosition, 1e-12);
    }

    @Test
    public void samplingHourlyEqualsPerBar_whenBarsAreHourly() {
        var series = buildHourlySeries(new double[] { 100d, 105d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();
        var hourly = criterion(Sampling.HOURLY, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();

        assertEquals(perBar, hourly, 1e-12);
    }

    @Test
    public void samplingMinutelyEqualsPerBar_whenBarsAreMinutely() {
        var series = buildMinuteSeries(new double[] { 100d, 101d, 102d, 103d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();
        var minutely = criterion(Sampling.MINUTELY, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, minutely, 1e-12);
    }

    @Test
    public void samplingPerSecondEqualsPerBar_whenBarsArePerSecond() {
        var series = buildSecondSeries(new double[] { 100d, 101d, 103d, 106d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(Sampling.PER_BAR, Annualization.PERIOD).calculate(series, tradingRecord).doubleValue();
        var perSecond = criterion(Sampling.PER_SECOND, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, perSecond, 1e-12);
    }

    private BarSeries buildDailySeries(double[] closes, Instant start) {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("daily_series").build();

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

    private BarSeries buildHourlySeries(double[] closes, Instant start) {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("hourly_series").build();

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

    private BarSeries buildMinuteSeries(double[] closes, Instant start) {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("minute_series").build();

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

    private BarSeries buildSecondSeries(double[] closes, Instant start) {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("second_series").build();

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

    private BarSeries buildIntradaySeriesWithDailyEnds(double[] dailyEndCloses, Instant day0StartUtc) {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).withName("intraday_series").build();

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

    private static TradingRecord alwaysInvested(BarSeries series) {
        var amount = series.numFactory().one();

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        return tradingRecord;
    }

    private static int[] weeklyEndIndicesUtc(BarSeries series) {
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

    private static int[] monthlyEndIndicesUtc(BarSeries series) {
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

    private static BarSeries compressSeries(BarSeries source, int[] indices, String name) {
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

    private SharpeRatioCriterion criterion(ZoneId zoneId) {
        return (SharpeRatioCriterion) getCriterion(numOf(0), Sampling.DAILY, Annualization.PERIOD, zoneId);
    }

    private SharpeRatioCriterion criterion(Sampling sampling, Annualization annualization) {
        return (SharpeRatioCriterion) getCriterion(numOf(0), sampling, annualization, ZoneOffset.UTC);
    }

    private SharpeRatioCriterion criterion() {
        return (SharpeRatioCriterion) getCriterion(numOf(0.05), Sampling.PER_BAR, Annualization.PERIOD, ZoneOffset.UTC);
    }

}
