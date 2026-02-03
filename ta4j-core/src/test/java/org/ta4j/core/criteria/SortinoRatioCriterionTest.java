/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.criteria.SortinoRatioCriterion.Annualization;

public class SortinoRatioCriterionTest extends AbstractCriterionTest {

    public SortinoRatioCriterionTest(NumFactory numFactory) {
        super(params -> new SortinoRatioCriterion((double) params[0], (SamplingFrequency) params[1],
                (Annualization) params[2], (ZoneId) params[3]), numFactory);
    }

    @Test
    public void returnsKnownSortinoPerPeriod_whenAlwaysInvested() {
        var series = buildDailySeries(new double[] { 100d, 50d, 100d, 50d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord).doubleValue();

        var expected = Math.sqrt(0.5d);

        assertEquals(expected, actual, 1e-12);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        var series = buildDailySeries(new double[] { 100d, 50d, 100d, 50d, 100d },
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
        var series = buildDailySeries(new double[] { 100d, 50d, 100d, 50d, 100d },
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
        var dayEndCloses = new double[] { 100d, 50d, 100d, 50d, 100d };

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
    public void riskFreeRateReducesSortino_whenPositive() {
        var series = buildDailySeries(new double[] { 100d, 50d, 100d, 50d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sortinoNoRf = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord)
                .doubleValue();
        var sortinoWithRf = criterion().calculate(series, tradingRecord).doubleValue();

        assertTrue(sortinoWithRf < sortinoNoRf);
    }

    @Test
    public void cashEarnsZeroLowersSortino_whenRiskFreeRatePositiveAndFlatIntervals() {
        var series = buildDailySeries(new double[] { 100d, 90d, 95d, 90d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = tradingRecordWithGap(series);

        var cashRiskFree = new SortinoRatioCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.MARK_TO_MARKET);
        var cashZero = new SortinoRatioCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_ZERO, OpenPositionHandling.MARK_TO_MARKET);

        var sortinoRiskFree = cashRiskFree.calculate(series, tradingRecord);
        var sortinoZero = cashZero.calculate(series, tradingRecord);

        assertTrue(sortinoZero.isLessThan(sortinoRiskFree));
    }

    @Test
    public void groupingZoneIdMakesSortinoNaNInOneZoneButNotTheOther() {
        var series = getBarSeries("zone_test_sortino");

        var endTimes = new Instant[] { Instant.parse("2024-01-01T23:30:00Z"), // NY: Jan 1
                Instant.parse("2024-01-02T00:30:00Z"), // NY: Jan 1 -> day boundary happens between this and next
                Instant.parse("2024-01-02T23:30:00Z"), // NY: Jan 2
                Instant.parse("2024-01-03T00:30:00Z"), // NY: Jan 2 -> day boundary happens between this and next
                Instant.parse("2024-01-03T23:30:00Z"), // NY: Jan 3
                Instant.parse("2024-01-04T00:30:00Z") // NY: Jan 3 (last)
        };

        // NY daily sampling uses indices (1, 3, 5) -> constant positive returns ->
        // downside deviation = 0 -> NaN
        // UTC daily sampling uses indices (2, 4, 5) -> includes a negative return ->
        // finite Sortino
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

        var sortinoNewYork = new SortinoRatioCriterion(0d, SamplingFrequency.DAY, Annualization.PERIOD,
                ZoneId.of("America/New_York"), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                OpenPositionHandling.MARK_TO_MARKET).calculate(series, tradingRecord);

        var sortinoUtc = new SortinoRatioCriterion(0d, SamplingFrequency.DAY, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.MARK_TO_MARKET)
                .calculate(series, tradingRecord);

        assertEquals(NaN.NaN, sortinoNewYork);
        assertFalse(sortinoUtc.isNaN());
    }

    @Test
    public void returnsNaN_whenDownsideDeviationIsZero() {
        var series = buildDailySeries(new double[] { 100d, 110d, 121d, 133.1d }, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertEquals(NaN.NaN, actual);
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
    public void openPositionHandlingIgnoreReturnsNaN_whenPositionIsOpen() {
        var series = buildDailySeries(new double[] { 100d, 80d, 120d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var markToMarket = new SortinoRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.MARK_TO_MARKET);
        var ignore = new SortinoRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, tradingRecord);
        var ignoreValue = ignore.calculate(series, tradingRecord);

        assertFalse(markToMarketValue.isNaN());
        assertEquals(NaN.NaN, ignoreValue);
    }

    @Test
    public void returnsSortino_whenOpenPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(new double[] { 100d, 90d, 110d }, Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var openPosition = tradingRecord.getCurrentPosition();
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, openPosition);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
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

    private static TradingRecord tradingRecordWithGap(BarSeries series) {
        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        var begin = series.getBeginIndex();

        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(begin + 1, series.getBar(begin + 1).getClosePrice(), amount);

        tradingRecord.enter(begin + 3, series.getBar(begin + 3).getClosePrice(), amount);
        tradingRecord.exit(begin + 4, series.getBar(begin + 4).getClosePrice(), amount);

        return tradingRecord;
    }

    private SortinoRatioCriterion criterion(SamplingFrequency samplingFrequency, Annualization annualization) {
        return (SortinoRatioCriterion) getCriterion(0d, samplingFrequency, annualization, ZoneOffset.UTC);
    }

    private SortinoRatioCriterion criterion() {
        return (SortinoRatioCriterion) getCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC);
    }

}
