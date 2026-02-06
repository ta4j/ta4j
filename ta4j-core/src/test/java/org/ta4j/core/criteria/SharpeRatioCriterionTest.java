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
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;

public class SharpeRatioCriterionTest extends AbstractCriterionTest {

    public SharpeRatioCriterionTest(NumFactory numFactory) {
        super(params -> new SharpeRatioCriterion((double) params[0], (SamplingFrequency) params[1],
                (Annualization) params[2], (ZoneId) params[3]), numFactory);
    }

    @Test
    public void returnsKnownSharpePerPeriod_whenAlwaysInvested() {
        BarSeries series = getBarSeries("sr_test");

        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        double[] closes = new double[] { 100d, 150d, 150d, 225d, 225d };

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

        TradingRecord tradingRecord = alwaysInvested(series);
        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        Num actual = criterion.calculate(series, tradingRecord);
        Num expected = numFactory.numOf(Math.sqrt(3.0) / 2.0);

        assertNumEquals(expected, actual);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num period = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num annualized = criterion(SamplingFrequency.BAR, Annualization.ANNUALIZED).calculate(series, tradingRecord);

        Num expectedFactor = numFactory.numOf(Math.sqrt(365.2425d));
        Num expected = period.multipliedBy(expectedFactor);
        assertNumEquals(expected, annualized, 1e-9);
    }

    @Test
    public void samplingDailyEqualsPerBar_whenBarsAreDaily() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num daily = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, daily, 1e-12);
    }

    @Test
    public void samplingDailyOnIntradayMatchesPerBarOnCompressedDailySeries() {
        double[] dayEndCloses = new double[] { 100d, 150d, 150d, 225d, 225d };

        BarSeries intradaySeries = buildIntradaySeriesWithDailyEnds(getBarSeries("intraday_series"), dayEndCloses,
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord intradayTradingRecord = alwaysInvested(intradaySeries);

        BarSeries dailySeries = buildDailySeries(getBarSeries("daily_series"), dayEndCloses,
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord dailyTradingRecord = alwaysInvested(dailySeries);

        Num intradayDailySampling = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(intradaySeries,
                intradayTradingRecord);

        Num compressedDailyPerBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(dailySeries,
                dailyTradingRecord);

        assertNumEquals(compressedDailyPerBar, intradayDailySampling, 1e-12);
    }

    @Test
    public void groupingZoneIdMakesSharpeZeroInOneZoneButNotTheOther() {
        BarSeries series = getBarSeries("zone_test_deterministic");

        Instant[] endTimes = new Instant[] { Instant.parse("2024-01-01T23:30:00Z"), // NY: Jan 1
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
        double[] closes = new double[] { 100d, 150d, 80d, 225d, 300d, 337.5d };

        IntStream.range(0, closes.length).forEach(i -> {
            double close = closes[i];
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

        TradingRecord tradingRecord = alwaysInvested(series);

        Num sharpeNewYork = criterion(ZoneId.of("America/New_York")).calculate(series, tradingRecord);

        Num sharpeUtc = criterion(ZoneOffset.UTC).calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), sharpeNewYork, 0d);
        assertTrue(sharpeUtc.abs().isGreaterThan(series.numFactory().numOf(1e-12)));
    }

    @Test
    public void riskFreeRateReducesSharpe_whenPositive() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num sharpeNoRf = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num sharpeWithRf = criterion().calculate(series, tradingRecord);

        assertTrue(sharpeWithRf.isLessThan(sharpeNoRf));
    }

    @Test
    public void returnsSharpe_whenNoClosedPositionsInTradingRecord() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        Num actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsSharpe_whenOpenPositionIsEvaluatedDirectly() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        Position openPosition = tradingRecord.getCurrentPosition();
        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        Num actual = criterion.calculate(series, openPosition);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsZero_whenStdevIsZero() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 100d, 100d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        Num actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void returnsZero_whenLessThanTwoReturnsAreAvailable() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        Num actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void samplingWeeklyOnDailyMatchesPerBarOnCompressedWeeklySeries() {
        double[] closes = IntStream.range(0, 15).mapToDouble(i -> 100d + i + ((i % 4) == 0 ? 7d : -3d)).toArray();

        BarSeries series = buildDailySeries(getBarSeries("daily_series"), closes,
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num weekly = criterion(SamplingFrequency.WEEK, Annualization.PERIOD).calculate(series, tradingRecord);

        BarSeries weeklyCompressedSeries = compressSeries(series, weeklyEndIndicesUtc(series), "weekly_compressed");
        Num weeklyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(weeklyCompressedSeries,
                alwaysInvested(weeklyCompressedSeries));

        assertNumEquals(weeklyCompressed, weekly, 1e-12);
    }

    @Test
    public void samplingMonthlyOnDailyMatchesPerBarOnCompressedMonthlySeries() {
        double[] closes = IntStream.range(0, 45).mapToDouble(i -> 120d + i + ((i % 7) == 0 ? 11d : -2d)).toArray();

        BarSeries series = buildDailySeries(getBarSeries("daily_series"), closes,
                Instant.parse("2024-01-24T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num monthly = criterion(SamplingFrequency.MONTH, Annualization.PERIOD).calculate(series, tradingRecord);

        BarSeries monthlyCompressedSeries = compressSeries(series, monthlyEndIndicesUtc(series), "monthly_compressed");
        Num monthlyCompressed = criterion(SamplingFrequency.BAR, Annualization.PERIOD)
                .calculate(monthlyCompressedSeries, alwaysInvested(monthlyCompressedSeries));

        assertNumEquals(monthlyCompressed, monthly, 1e-12);
    }

    @Test
    public void returnsSharpe_whenClosedPositionIsEvaluatedDirectly() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 150d, 150d, 225d, 225d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        Position position = tradingRecord.getPositions().getFirst();
        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        Num actual = criterion.calculate(series, position);
        Num expected = numFactory.numOf(Math.sqrt(3.0) / 2.0);

        assertNumEquals(expected, actual, 1e-12);
    }

    @Test
    public void samplingHourlyEqualsPerBar_whenBarsAreHourly() {
        BarSeries series = buildHourlySeries(getBarSeries("hourly_series"), new double[] { 100d, 105d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num hourly = criterion(SamplingFrequency.HOUR, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, hourly, 1e-12);
    }

    @Test
    public void samplingMinutelyEqualsPerBar_whenBarsAreMinutely() {
        BarSeries series = buildMinuteSeries(getBarSeries("minute_series"), new double[] { 100d, 101d, 102d, 103d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num minutely = criterion(SamplingFrequency.MINUTE, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, minutely, 1e-12);
    }

    @Test
    public void samplingPerSecondEqualsPerBar_whenBarsArePerSecond() {
        BarSeries series = buildSecondSeries(getBarSeries("second_series"), new double[] { 100d, 101d, 103d, 106d },
                Instant.parse("2024-01-01T00:00:00Z"));
        TradingRecord tradingRecord = alwaysInvested(series);

        Num perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        Num perSecond = criterion(SamplingFrequency.SECOND, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, perSecond, 1e-12);
    }

    @Test
    public void returnsSharpe_whenOnlyOneClosedPositionIsAvailable() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        Num actual = criterion.calculate(series, tradingRecord);

        double return1 = 110d / 100d - 1d;
        double return2 = 120d / 110d - 1d;
        double mean = (return1 + return2) / 2d;
        double variance = (Math.pow(return1 - mean, 2) + Math.pow(return2 - mean, 2));
        double stdev = Math.sqrt(variance);
        Num expected = numFactory.numOf(mean / stdev);

        assertNumEquals(expected, actual, 1e-12);
    }

    @Test
    public void returnsSharpe_whenOneClosedPositionAndOneOpenPositionAreAvailable() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 90d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getBeginIndex() + 1, series.getBar(series.getBeginIndex() + 1).getClosePrice(),
                amount);
        tradingRecord.enter(series.getBeginIndex() + 2, series.getBar(series.getBeginIndex() + 2).getClosePrice(),
                amount);

        SharpeRatioCriterion criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        Num actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    @Test
    public void returnsSharpe_whenOpenPositionIsExcludedFromReturnSeries() {
        BarSeries series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 90d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

        Num amount = series.numFactory().one();
        TradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getBeginIndex() + 1, series.getBar(series.getBeginIndex() + 1).getClosePrice(),
                amount);
        tradingRecord.enter(series.getBeginIndex() + 2, series.getBar(series.getBeginIndex() + 2).getClosePrice(),
                amount);

        SharpeRatioCriterion criterion = new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD,
                ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE, OpenPositionHandling.IGNORE);
        Num actual = criterion.calculate(series, tradingRecord);

        double return1 = 110d / 100d - 1d;
        double mean = return1 / 3d;
        double variance = (Math.pow(return1 - mean, 2) + Math.pow(-mean, 2) + Math.pow(-mean, 2));
        double stdev = Math.sqrt(variance / 2d);
        Num expected = numFactory.numOf(mean / stdev);

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
