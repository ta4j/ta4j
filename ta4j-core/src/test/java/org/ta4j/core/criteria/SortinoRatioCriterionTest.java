/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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

    private SortinoRatioCriterion criterion(SamplingFrequency samplingFrequency, Annualization annualization) {
        return (SortinoRatioCriterion) getCriterion(0d, samplingFrequency, annualization, ZoneOffset.UTC);
    }

    private SortinoRatioCriterion criterion() {
        return (SortinoRatioCriterion) getCriterion(0.05d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC);
    }

}
