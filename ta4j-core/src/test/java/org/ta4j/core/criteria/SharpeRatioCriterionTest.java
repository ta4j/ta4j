/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildDailySeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildHourlySeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildIntradaySeriesWithDailyEnds;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildMinuteSeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildSecondSeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.compressSeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.monthlyEndIndicesUtc;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.weeklyEndIndicesUtc;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.NumFactory;

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

        var actual = criterion.calculate(series, tradingRecord);
        var expected = numFactory.numOf(Math.sqrt(3.0) / 2.0);

        assertNumEquals(expected, actual);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var period = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var annualized = criterion(SamplingFrequency.BAR, Annualization.ANNUALIZED).calculate(series, tradingRecord);

        var expectedFactor = numFactory.numOf(Math.sqrt(365.2425d));
        var expected = period.multipliedBy(expectedFactor);
        assertNumEquals(expected, annualized, 1e-9);
    }

    @Test
    public void samplingDailyEqualsPerBar_whenBarsAreDaily() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var daily = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, daily, 1e-12);
    }

    @Test
    public void samplingDailyOnIntradayMatchesPerBarOnCompressedDailySeries() {
        var dayEndCloses = new double[] { 100d, 150d, 150d, 225d, 225d };

        var intradaySeries = buildIntradaySeriesWithDailyEnds(getBarSeries("intraday_series"), dayEndCloses,
                Instant.parse("2024-01-01T00:00:00Z"));
        var intradayTradingRecord = alwaysInvested(intradaySeries);

        var dailySeries = buildDailySeries(getBarSeries("daily_series"), dayEndCloses,
                Instant.parse("2024-01-01T00:00:00Z"));
        var dailyTradingRecord = alwaysInvested(dailySeries);

        var intradayDailySampling = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(intradaySeries,
                intradayTradingRecord);

        var compressedDailyPerBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(dailySeries,
                dailyTradingRecord);

        assertNumEquals(compressedDailyPerBar, intradayDailySampling, 1e-12);
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

        var sharpeNewYork = criterion(ZoneId.of("America/New_York")).calculate(series, tradingRecord);

        var sharpeUtc = criterion(ZoneOffset.UTC).calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), sharpeNewYork, 0d);
        assertTrue(sharpeUtc.abs().isGreaterThan(series.numFactory().numOf(1e-12)));
    }

    @Test
    public void riskFreeRateReducesSharpe_whenPositive() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sharpeNoRf = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var sharpeWithRf = criterion().calculate(series, tradingRecord);

        assertTrue(sharpeWithRf.isLessThan(sharpeNoRf));
    }

    @Test
    public void returnsSharpe_whenNoClosedPositionsInTradingRecord() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsSharpe_whenOpenPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

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
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 100d, 100d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenLessThanTwoReturnsAreAvailable() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void samplingWeeklyOnDailyMatchesPerBarOnCompressedWeeklySeries() {
        var closes = IntStream.range(0, 15).mapToDouble(i -> 100d + i + ((i % 4) == 0 ? 7d : -3d)).toArray();

        var series = buildDailySeries(getBarSeries("daily_series"), closes, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var weekly = criterion(SamplingFrequency.WEEK, Annualization.PERIOD).calculate(series, tradingRecord);

        var weeklyCompressedSeries = compressSeries(series, weeklyEndIndicesUtc(series), "weekly_compressed");
        var weeklyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(weeklyCompressedSeries,
                alwaysInvested(weeklyCompressedSeries));

        assertNumEquals(weeklyCompressed, weekly, 1e-12);
    }

    @Test
    public void samplingMonthlyOnDailyMatchesPerBarOnCompressedMonthlySeries() {
        var closes = IntStream.range(0, 45).mapToDouble(i -> 120d + i + ((i % 7) == 0 ? 11d : -2d)).toArray();

        var series = buildDailySeries(getBarSeries("daily_series"), closes, Instant.parse("2024-01-24T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var monthly = criterion(SamplingFrequency.MONTH, Annualization.PERIOD).calculate(series, tradingRecord);

        var monthlyCompressedSeries = compressSeries(series, monthlyEndIndicesUtc(series), "monthly_compressed");
        var monthlyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD)
                .calculate(monthlyCompressedSeries, alwaysInvested(monthlyCompressedSeries));

        assertNumEquals(monthlyCompressed, monthly, 1e-12);
    }

    @Test
    public void returnsSharpe_whenClosedPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var position = tradingRecord.getPositions().getFirst();
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, position);
        var expected = numFactory.numOf(Math.sqrt(3.0) / 2.0);

        assertNumEquals(expected, actual, 1e-12);
    }

    @Test
    public void samplingHourlyEqualsPerBar_whenBarsAreHourly() {
        var series = buildHourlySeries(getBarSeries("hourly_series"), new double[] { 100d, 105d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var hourly = criterion(SamplingFrequency.HOUR, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, hourly, 1e-12);
    }

    @Test
    public void samplingMinutelyEqualsPerBar_whenBarsAreMinutely() {
        var series = buildMinuteSeries(getBarSeries("minute_series"), new double[] { 100d, 101d, 102d, 103d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var minutely = criterion(SamplingFrequency.MINUTE, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, minutely, 1e-12);
    }

    @Test
    public void samplingPerSecondEqualsPerBar_whenBarsArePerSecond() {
        var series = buildSecondSeries(getBarSeries("second_series"), new double[] { 100d, 101d, 103d, 106d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var perSecond = criterion(SamplingFrequency.SECOND, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, perSecond, 1e-12);
    }

    @Test
    public void returnsSharpe_whenOnlyOneClosedPositionIsAvailable() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

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
        var expected = numFactory.numOf(mean / stdev);

        assertNumEquals(expected, actual, 1e-12);
    }

    @Test
    public void returnsSharpe_whenOneClosedPositionAndOneOpenPositionAreAvailable() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 90d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

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
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 90d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

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
        var expected = numFactory.numOf(mean / stdev);

        assertNumEquals(expected, actual, 1e-12);
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
