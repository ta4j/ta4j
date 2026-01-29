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
import java.util.stream.IntStream;

import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.*;
import org.junit.Test;

import static org.ta4j.core.criteria.SharpeRatioCriterion.Annualization;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharpeRatioCriterionTest extends AbstractCriterionTest {

    public SharpeRatioCriterionTest(NumFactory numFactory) {
        super(params -> new SharpeRatioCriterion((double) params[0], (SamplingFrequency) params[1],
                (Annualization) params[2], (ZoneId) params[3]), numFactory);
    }

    @Test
    public void returnsKnownSharpePerPeriod_whenAlwaysInvested() {
        var series = getBarSeries("sr_test");

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

        var tradingRecord = alwaysInvested(series);
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, tradingRecord).doubleValue();
        var expected = Math.sqrt(3.0) / 2.0;

        assertEquals(expected, actual, 1e-12);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var period = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var annualized = criterion(SamplingFrequency.BAR, Annualization.ANNUALIZED).calculate(series, tradingRecord)
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

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var daily = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, daily, 1e-12);
    }

    @Test
    public void samplingDailyOnIntradayMatchesPerBarOnCompressedDailySeries() {
        var dayEndCloses = new double[] { 100d, 150d, 150d, 225d, 225d };

        var intradaySeries = buildIntradaySeriesWithDailyEnds(dayEndCloses, Instant.parse("2024-01-01T00:00:00Z"));
        var intradayTradingRecord = alwaysInvested(intradaySeries);

        var dailySeries = buildDailySeries(dayEndCloses, Instant.parse("2024-01-01T00:00:00Z"));
        var dailyTradingRecord = alwaysInvested(dailySeries);

        var intradayDailySampling = criterion(SamplingFrequency.DAY, Annualization.PERIOD)
                .calculate(intradaySeries, intradayTradingRecord)
                .doubleValue();

        var compressedDailyPerBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD)
                .calculate(dailySeries, dailyTradingRecord)
                .doubleValue();

        assertEquals(compressedDailyPerBar, intradayDailySampling, 1e-12);
    }

    @Test
    public void groupingZoneIdMakesSharpeZeroInOneZoneButNotTheOther() {
        var series = getBarSeries("zone_test_deterministic");

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

        var sharpeNoRf = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var sharpeWithRf = criterion().calculate(series, tradingRecord).doubleValue();

        assertTrue(sharpeWithRf < sharpeNoRf);
    }

    @Test
    public void returnsSharpe_whenNoClosedPositionsInTradingRecord() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsSharpe_whenOpenPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var openPosition = tradingRecord.getCurrentPosition();
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, openPosition);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsZero_whenStdevIsZero() {
        var series = buildDailySeries(new double[] { 100d, 100d, 100d, 100d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenLessThanTwoReturnsAreAvailable() {
        var series = buildDailySeries(new double[] { 100d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void samplingWeeklyOnDailyMatchesPerBarOnCompressedWeeklySeries() {
        var closes = IntStream.range(0, 15).mapToDouble(i -> 100d + i + ((i % 4) == 0 ? 7d : -3d)).toArray();

        var series = buildDailySeries(closes, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var weekly = criterion(SamplingFrequency.WEEK, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        var weeklyCompressedSeries = compressSeries(series, weeklyEndIndicesUtc(series), "weekly_compressed");
        var weeklyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD)
                .calculate(weeklyCompressedSeries, alwaysInvested(weeklyCompressedSeries))
                .doubleValue();

        assertEquals(weeklyCompressed, weekly, 1e-12);
    }

    @Test
    public void samplingMonthlyOnDailyMatchesPerBarOnCompressedMonthlySeries() {
        var closes = IntStream.range(0, 45).mapToDouble(i -> 120d + i + ((i % 7) == 0 ? 11d : -2d)).toArray();

        var series = buildDailySeries(closes, Instant.parse("2024-01-24T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var monthly = criterion(SamplingFrequency.MONTH, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        var monthlyCompressedSeries = compressSeries(series, monthlyEndIndicesUtc(series), "monthly_compressed");
        var monthlyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD)
                .calculate(monthlyCompressedSeries, alwaysInvested(monthlyCompressedSeries))
                .doubleValue();

        assertEquals(monthlyCompressed, monthly, 1e-12);
    }

    @Test
    public void returnsSharpe_whenClosedPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var position = tradingRecord.getPositions().getFirst();
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, position);
        var expected = Math.sqrt(3.0) / 2.0;

        assertEquals(expected, actual.doubleValue(), 1e-12);
    }

    @Test
    public void samplingHourlyEqualsPerBar_whenBarsAreHourly() {
        var series = buildHourlySeries(new double[] { 100d, 105d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var hourly = criterion(SamplingFrequency.HOUR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, hourly, 1e-12);
    }

    @Test
    public void samplingMinutelyEqualsPerBar_whenBarsAreMinutely() {
        var series = buildMinuteSeries(new double[] { 100d, 101d, 102d, 103d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var minutely = criterion(SamplingFrequency.MINUTE, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, minutely, 1e-12);
    }

    @Test
    public void samplingPerSecondEqualsPerBar_whenBarsArePerSecond() {
        var series = buildSecondSeries(new double[] { 100d, 101d, 103d, 106d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var perSecond = criterion(SamplingFrequency.SECOND, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(perBar, perSecond, 1e-12);
    }

    @Test
    public void returnsSharpe_whenOnlyOneClosedPositionIsAvailable() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        var return1 = 110d / 100d - 1d;
        var return2 = 120d / 110d - 1d;
        var mean = (return1 + return2) / 2d;
        var variance = (Math.pow(return1 - mean, 2) + Math.pow(return2 - mean, 2));
        var stdev = Math.sqrt(variance);
        var expected = mean / stdev;

        assertEquals(expected, actual.doubleValue(), 1e-12);
    }

    @Test
    public void returnsSharpe_whenOneClosedPositionAndOneOpenPositionAreAvailable() {
        var series = buildDailySeries(new double[] { 100d, 110d, 90d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getBeginIndex() + 1, series.getBar(series.getBeginIndex() + 1).getClosePrice(),
                amount);
        tradingRecord.enter(series.getBeginIndex() + 2, series.getBar(series.getBeginIndex() + 2).getClosePrice(),
                amount);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsSharpe_whenOpenPositionIsExcludedFromReturnSeries() {
        var series = buildDailySeries(new double[] { 100d, 110d, 90d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getBeginIndex() + 1, series.getBar(series.getBeginIndex() + 1).getClosePrice(),
                amount);
        tradingRecord.enter(series.getBeginIndex() + 2, series.getBar(series.getBeginIndex() + 2).getClosePrice(),
                amount);

        var criterion = new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.IGNORE);
        var actual = criterion.calculate(series, tradingRecord);

        var return1 = 110d / 100d - 1d;
        var mean = return1 / 3d;
        var variance = (Math.pow(return1 - mean, 2) + Math.pow(-mean, 2) + Math.pow(-mean, 2));
        var stdev = Math.sqrt(variance / 2d);
        var expected = mean / stdev;

        assertEquals(expected, actual.doubleValue(), 1e-12);
    }

    @Test
    public void realizedSharpeIgnoresOpenPositionEvenWhenMarkedToMarket() {
        var series = buildDailySeries(new double[] { 100d, 110d, 90d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getBeginIndex() + 1, series.getBar(series.getBeginIndex() + 1).getClosePrice(),
                amount);
        tradingRecord.enter(series.getBeginIndex() + 2, series.getBar(series.getBeginIndex() + 2).getClosePrice(),
                amount);

        var markToMarket = new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var realized = new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);

        var sharpeMarkToMarket = markToMarket.calculate(series, tradingRecord);
        var sharpeRealized = realized.calculate(series, tradingRecord);

        assertTrue(sharpeMarkToMarket.isGreaterThan(sharpeRealized));
    }

    private BarSeries buildDailySeries(double[] closes, Instant start) {
        var series = getBarSeries("daily_series");

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
        var series = getBarSeries("hourly_series");

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
        var series = getBarSeries("minute_series");

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
        var series = getBarSeries("second_series");

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
        var series = getBarSeries("intraday_series");

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

    private BarSeries compressSeries(BarSeries source, int[] indices, String name) {
        var series = getBarSeries(name);

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
        return (SharpeRatioCriterion) getCriterion(0d, SamplingFrequency.DAY, Annualization.PERIOD, zoneId);
    }

    private SharpeRatioCriterion criterion(SamplingFrequency samplingFrequency, Annualization annualization) {
        return (SharpeRatioCriterion) getCriterion(0d, samplingFrequency, annualization, ZoneOffset.UTC);
    }

    private SharpeRatioCriterion criterion() {
        return (SharpeRatioCriterion) getCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC);
    }

}
