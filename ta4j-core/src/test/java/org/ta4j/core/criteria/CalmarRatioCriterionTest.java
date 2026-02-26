/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

public class CalmarRatioCriterionTest extends AbstractCriterionTest {

    public CalmarRatioCriterionTest(NumFactory numFactory) {
        super(params -> new CalmarRatioCriterion(), numFactory);
    }

    @Test
    public void calculatesExpectedValueForMixedReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_mixed", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForPositiveReturnsTradingRecordWithNoDrawdown() {
        double[] closes = new double[] { 100d, 110d, 121d };
        BarSeries series = buildYearlySeries("calmar_positive", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForNegativeReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 70d, 80d };
        BarSeries series = buildYearlySeries("calmar_negative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsZeroWhenThereAreNoReturnObservations() {
        BarSeries series = buildYearlySeries("calmar_one_bar", new double[] { 100d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordIsNull() {
        BarSeries series = buildYearlySeries("calmar_null_record", new double[] { 100d, 120d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, (TradingRecord) null);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void betterThanUsesHigherValuesAsBetter() {
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.zero()));
        assertFalse(criterion.betterThan(numFactory.zero(), numFactory.one()));
    }

    private BarSeries buildYearlySeries(String name, double[] closes) {
        BarSeries series = getBarSeries(name);
        Instant start = Instant.parse("2020-01-01T00:00:00Z");

        for (int i = 0; i < closes.length; i++) {
            Instant endTime = start.plus(Duration.ofDays(365L * i));
            double close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(365))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }
        return series;
    }

    private double referenceCalmar(BarSeries series, double[] closes) {
        double annualizedReturn = referenceAnnualizedReturn(series, closes);
        double maximumDrawdown = referenceMaximumDrawdown(closes);
        if (maximumDrawdown == 0d) {
            return annualizedReturn;
        }
        return annualizedReturn / maximumDrawdown;
    }

    private double referenceAnnualizedReturn(BarSeries series, double[] closes) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        if (endIndex <= beginIndex) {
            return 0d;
        }

        double elapsedSeconds = Duration
                .between(series.getBar(beginIndex).getEndTime(), series.getBar(endIndex).getEndTime())
                .getSeconds();
        if (elapsedSeconds <= 0d) {
            return 0d;
        }

        double years = elapsedSeconds / TimeConstants.SECONDS_PER_YEAR;
        double totalReturn = closes[closes.length - 1] / closes[0];
        return Math.pow(totalReturn, 1d / years) - 1d;
    }

    private double referenceMaximumDrawdown(double[] closes) {
        double peak = closes[0];
        double maximumDrawdown = 0d;
        for (double close : closes) {
            if (close > peak) {
                peak = close;
            }
            double drawdown = (peak - close) / peak;
            maximumDrawdown = Math.max(maximumDrawdown, drawdown);
        }
        return maximumDrawdown;
    }
}
