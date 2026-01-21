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
package org.ta4j.core.analysis;

import java.util.stream.IntStream;
import java.time.Duration;
import java.time.Instant;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.BarSeries;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExcessReturnsTest {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;

    @Test
    public void cashReturnPolicyControlsFlatIntervalExcessGrowth() {
        var series = new BaseBarSeriesBuilder().withName("excess_returns_series").build();
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

        var numFactory = series.numFactory();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), numFactory.one());
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), numFactory.one());
        tradingRecord.exit(3, series.getBar(3).getClosePrice(), numFactory.one());

        var cashFlow = new CashFlow(series, tradingRecord);
        var annualRate = 0.05d;
        var perBarRiskFree = Math.pow(1.0 + annualRate, Duration.ofDays(1).getSeconds()
                / SECONDS_PER_YEAR);

        var earnsRiskFree = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_RISK_FREE)
                .excessReturn(cashFlow, 0, 3)
                .doubleValue();
        var earnsZero = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO)
                .excessReturn(cashFlow, 0, 3)
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
        var cashFlow = new CashFlow(series, new BaseTradingRecord());

        var actual = new ExcessReturns(series).excessReturn(cashFlow, 0, 2);

        assertEquals(series.numFactory().zero(), actual);
    }

    @Test
    public void cashEarnsZeroPenalizesFlatCashAgainstPositiveRiskFree() {
        var series = buildDailySeries(new double[] { 100d, 100d });
        var cashFlow = new CashFlow(series, new BaseTradingRecord());
        var annualRate = 0.1d;
        var perBarRiskFree = Math.pow(1.0 + annualRate, Duration.ofDays(1).getSeconds()
                / SECONDS_PER_YEAR);

        var actual = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO)
                .excessReturn(cashFlow, 0, 1)
                .doubleValue();
        var expected = (1.0d / perBarRiskFree) - 1.0d;

        assertEquals(expected, actual, 1e-12);
        assertTrue(actual < 0.0d);
    }

    private static BarSeries buildDailySeries(double[] closes) {
        var series = new BaseBarSeriesBuilder().withName("excess_returns_series").build();
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
