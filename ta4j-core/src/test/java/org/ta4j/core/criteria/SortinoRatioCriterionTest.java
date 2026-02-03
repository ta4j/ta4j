/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildDailySeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildIntradaySeriesWithDailyEnds;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.compressSeries;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.monthlyEndIndicesUtc;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.tradingRecordWithGap;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.weeklyEndIndicesUtc;
import static org.ta4j.core.TestUtils.assertNumEquals;

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

public class SortinoRatioCriterionTest extends AbstractCriterionTest {

    public SortinoRatioCriterionTest(NumFactory numFactory) {
        super(params -> new SortinoRatioCriterion((double) params[0], (SamplingFrequency) params[1],
                (Annualization) params[2], (ZoneId) params[3]), numFactory);
    }

    @Test
    public void returnsKnownSortinoPerPeriod_whenAlwaysInvested() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 50d, 100d, 50d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        var expected = numFactory.numOf(Math.sqrt(0.5d));

        assertNumEquals(expected, actual, 1e-12);
    }

    @Test
    public void annualizationEqualsPeriodTimesSqrtPeriodsPerYear_whenDailyBars() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 50d, 100d, 50d, 100d },
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
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 50d, 100d, 50d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var perBar = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var daily = criterion(SamplingFrequency.DAY, Annualization.PERIOD).calculate(series, tradingRecord);

        assertNumEquals(perBar, daily, 1e-12);
    }

    @Test
    public void samplingDailyOnIntradayMatchesPerBarOnCompressedDailySeries() {
        var dayEndCloses = new double[] { 100d, 50d, 100d, 50d, 100d };

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
    public void riskFreeRateReducesSortino_whenPositive() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 50d, 100d, 50d, 100d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sortinoNoRf = criterion(SamplingFrequency.BAR, Annualization.PERIOD).calculate(series, tradingRecord);
        var sortinoWithRf = criterion().calculate(series, tradingRecord);

        assertTrue(sortinoWithRf.isLessThan(sortinoNoRf));
    }

    @Test
    public void cashEarnsZeroLowersSortino_whenRiskFreeRatePositiveAndFlatIntervals() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 90d, 95d, 90d, 100d },
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

        assertTrue(sortinoNewYork.isNaN());
        assertFalse(sortinoUtc.isNaN());
    }

    @Test
    public void returnsNaN_whenDownsideDeviationIsZero() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 110d, 121d, 133.1d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isNaN());
    }

    @Test
    public void returnsNaN_whenDownsideDeviationIsZero_forWeeklySampling() {
        var closes = IntStream.range(0, 15).mapToDouble(i -> 100d * Math.pow(1.05d, i)).toArray();
        var series = buildDailySeries(getBarSeries("daily_series"), closes, Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.WEEK, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isNaN());
    }

    @Test
    public void returnsZero_whenLessThanTwoReturnsAreAvailable() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);
        var actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(series.numFactory().zero(), actual, 0d);
    }

    @Test
    public void openPositionHandlingIgnoreReturnsNaN_whenPositionIsOpen() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 80d, 120d },
                Instant.parse("2024-01-01T00:00:00Z"));

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
        assertTrue(ignoreValue.isNaN());
    }

    @Test
    public void returnsSortino_whenOpenPositionIsEvaluatedDirectly() {
        var series = buildDailySeries(getBarSeries("daily_series"), new double[] { 100d, 90d, 110d },
                Instant.parse("2024-01-01T00:00:00Z"));

        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var openPosition = tradingRecord.getCurrentPosition();
        var criterion = criterion(SamplingFrequency.BAR, Annualization.PERIOD);

        var actual = criterion.calculate(series, openPosition);

        assertTrue(actual.isGreaterThan(series.numFactory().zero()));
    }

    private SortinoRatioCriterion criterion(SamplingFrequency samplingFrequency, Annualization annualization) {
        return (SortinoRatioCriterion) getCriterion(0d, samplingFrequency, annualization, ZoneOffset.UTC);
    }

    private SortinoRatioCriterion criterion() {
        return (SortinoRatioCriterion) getCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC);
    }

}
