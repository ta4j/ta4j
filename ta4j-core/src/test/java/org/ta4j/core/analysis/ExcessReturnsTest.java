/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

public class ExcessReturnsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ExcessReturnsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void cashReturnPolicyControlsFlatIntervalExcessGrowth() {
        var series = getBarSeries("excess_returns_series");
        var start = Instant.parse("2024-01-01T00:00:00Z");
        var closes = new double[] { 100d, 110d, 110d, 121d };

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

        var tradingRecord = new BaseTradingRecord();
        var one = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), one);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), one);
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), one);
        tradingRecord.exit(3, series.getBar(3).getClosePrice(), one);

        var annualRate = numFactory.numOf(0.05d);
        var perBarRiskFree = Math.pow(1.0 + annualRate.doubleValue(),
                Duration.ofDays(1).getSeconds() / TimeConstants.SECONDS_PER_YEAR);

        var earnsRiskFree = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord)
                .excessReturn(0, 3)
                .doubleValue();
        var earnsZero = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 3)
                .doubleValue();

        var expectedEarnsRiskFree = (1.21d / (perBarRiskFree * perBarRiskFree)) - 1.0d;
        var expectedEarnsZero = (1.21d / (perBarRiskFree * perBarRiskFree * perBarRiskFree)) - 1.0d;

        assertEquals(expectedEarnsRiskFree, earnsRiskFree, 1e-12);
        assertEquals(expectedEarnsZero, earnsZero, 1e-12);
        assertTrue(earnsZero < earnsRiskFree);
    }

    @Test
    public void defaultPolicyKeepsFlatCashNeutralWhenRiskFreeIsZero() {
        var series = buildDailySeries(new double[] { 100d, 100d, 100d });
        var tradingRecord = new BaseTradingRecord();

        var zero = numFactory.zero();
        var actual = new ExcessReturns(series, zero, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord).excessReturn(0,
                2);

        assertEquals(zero, actual);
    }

    @Test
    public void cashEarnsZeroPenalizesFlatCashAgainstPositiveRiskFree() {
        var series = buildDailySeries(new double[] { 100d, 100d });
        var tradingRecord = new BaseTradingRecord();
        var annualRate = numFactory.numOf(0.1d);
        var perBarRiskFree = Math.pow(1.0 + annualRate.doubleValue(),
                Duration.ofDays(1).getSeconds() / TimeConstants.SECONDS_PER_YEAR);

        var actual = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 1)
                .doubleValue();
        var expected = (1.0d / perBarRiskFree) - 1.0d;

        assertEquals(expected, actual, 1e-12);
        assertTrue(actual < 0.0d);
    }

    @Test
    public void openPositionHandlingControlsExcessReturnForOpenPositions() {
        var series = buildDailySeries(new double[] { 100d, 120d, 180d });
        var tradingRecord = new BaseTradingRecord();
        var amount = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), amount);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), amount);
        tradingRecord.enter(1, series.getBar(1).getClosePrice(), amount);

        var markToMarket = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord,
                OpenPositionHandling.MARK_TO_MARKET).excessReturn(0, 2).doubleValue();
        var ignore = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord,
                OpenPositionHandling.IGNORE).excessReturn(0, 2).doubleValue();

        assertEquals(0.8d, markToMarket, 1e-12);
        assertEquals(0.2d, ignore, 1e-12);
        assertTrue(markToMarket > ignore);
    }

    @Test
    public void zeroPreviousEquityDoesNotBreakExcessReturn() {
        var series = buildDailySeries(new double[] { 1d, 0d, 0d });
        var tradingRecord = new BaseTradingRecord();
        var one = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), one);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), one);

        var actual = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 2);

        assertEquals(one.negate(), actual);
    }

    private BarSeries buildDailySeries(double[] closes) {
        var series = getBarSeries("excess_returns_series");
        var start = Instant.parse("2024-01-01T00:00:00Z");

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

}
