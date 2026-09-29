/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

public class ExcessReturnsTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public ExcessReturnsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test(timeout = 5000)
    public void compoundsIntervalEndingAtMaximumIntegerIndex() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 50d).build();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE - 1)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE - 1, series),
                Trade.sellAt(Integer.MAX_VALUE, series));

        Num value = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, record)
                .excessReturn(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);

        assertEquals(numFactory.numOf(-0.5), value);
    }

    @Test
    public void ignoresTradesOutsideAnEmptyLogicalWindow() {
        BarSeries series = ConstrainedSeriesSupport.emptyLogicalSeries("empty-window", numFactory, 100d, 100d);
        Num one = numFactory.one();
        BaseTradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), one),
                Trade.sellAt(1, numFactory.numOf(100d), one));

        Num actual = new ExcessReturns(series, numFactory.numOf(0.05d), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                tradingRecord).excessReturn(0, 1);

        // Both trades lie outside the (empty) logical window, so no invested
        // interval exists to price against the risk-free rate.
        assertNumEquals(0, actual);
    }

    @Test
    public void cashReturnPolicyControlsFlatIntervalExcessGrowth() {
        BarSeries series = getBarSeries("excess_returns_series");
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        var closes = new double[] { 100d, 110d, 110d, 121d };

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

        var tradingRecord = new BaseTradingRecord();
        Num one = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), one);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), one);
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), one);
        tradingRecord.exit(3, series.getBar(3).getClosePrice(), one);

        Num annualRate = numFactory.numOf(0.05d);
        double perBarRiskFree = Math.pow(1.0 + annualRate.doubleValue(),
                Duration.ofDays(1).getSeconds() / TimeConstants.SECONDS_PER_YEAR);

        double earnsRiskFree = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_RISK_FREE,
                tradingRecord).excessReturn(0, 3).doubleValue();
        double earnsZero = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 3)
                .doubleValue();

        double expectedEarnsRiskFree = (1.21d / (perBarRiskFree * perBarRiskFree)) - 1.0d;
        double expectedEarnsZero = (1.21d / (perBarRiskFree * perBarRiskFree * perBarRiskFree)) - 1.0d;

        assertEquals(expectedEarnsRiskFree, earnsRiskFree, 1e-12);
        assertEquals(expectedEarnsZero, earnsZero, 1e-12);
        assertTrue(earnsZero < earnsRiskFree);
    }

    @Test
    public void defaultPolicyKeepsFlatCashNeutralWhenRiskFreeIsZero() {
        BarSeries series = buildDailySeries(new double[] { 100d, 100d, 100d });
        var tradingRecord = new BaseTradingRecord();

        Num zero = numFactory.zero();
        Num actual = new ExcessReturns(series, zero, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord).excessReturn(0,
                2);

        assertEquals(zero, actual);
    }

    @Test
    public void cashEarnsZeroPenalizesFlatCashAgainstPositiveRiskFree() {
        BarSeries series = buildDailySeries(new double[] { 100d, 100d });
        var tradingRecord = new BaseTradingRecord();
        Num annualRate = numFactory.numOf(0.1d);
        double perBarRiskFree = Math.pow(1.0 + annualRate.doubleValue(),
                Duration.ofDays(1).getSeconds() / TimeConstants.SECONDS_PER_YEAR);

        double actual = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 1)
                .doubleValue();
        double expected = (1.0d / perBarRiskFree) - 1.0d;

        assertEquals(expected, actual, 1e-12);
        assertTrue(actual < 0.0d);
    }

    @Test
    public void riskFreeGrowthUsesBarTimesCapturedAtConstruction() {
        var series = buildDailySeries(new double[] { 100d, 100d, 100d });
        var annualRate = numFactory.numOf(0.1d);
        var excessReturns = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_ZERO,
                new BaseTradingRecord());
        var perBarRiskFree = Math.pow(1.0 + annualRate.doubleValue(),
                Duration.ofDays(1).getSeconds() / TimeConstants.SECONDS_PER_YEAR);
        var expected = (1.0d / (perBarRiskFree * perBarRiskFree)) - 1.0d;
        assertEquals(expected, excessReturns.excessReturn(0, 2).doubleValue(), 1e-12);

        // A live feed replaces the last bar with one ending a year later; the
        // captured cash flow still describes the original bar, so the risk-free
        // growth must too.
        var lastBar = series.getLastBar();
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(lastBar.getEndTime().plus(Duration.ofDays(365)))
                .openPrice(100d)
                .highPrice(100d)
                .lowPrice(100d)
                .closePrice(100d)
                .volume(1)
                .build(), true);

        assertEquals(expected, excessReturns.excessReturn(0, 2).doubleValue(), 1e-12);
    }

    @Test
    public void riskFreeGrowthAndEquityDescribeTheSameBarWhenABarIsReplacedDuringCapture() {
        var daily = buildDailySeries(new double[] { 100d, 110d, 121d });
        var lastBar = daily.getLastBar();
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger outermostLeases = new AtomicInteger();
        AtomicReference<Runnable> writer = new AtomicReference<>();
        // Lets a feed writer replace the last bar before the fifth outermost read
        // lease of the armed construction: after the invested interval and cash
        // flow were built from the original bar, before anything else is read.
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock() {
            private final ReadLock replacingReadLock = new ReadLock(this) {
                @Override
                public void lock() {
                    if (armed.get() && getReadHoldCount() == 0 && outermostLeases.incrementAndGet() == 5) {
                        armed.set(false);
                        writer.get().run();
                    }
                    super.lock();
                }
            };

            @Override
            public ReadLock readLock() {
                return replacingReadLock;
            }
        };
        ConcurrentBarSeries series = ConstrainedSeriesSupport.seriesWithReadWriteLock(daily, lock);
        // The replacement moves both the close and the end time, so equity and
        // risk-free growth disagree unless both come from the same bar.
        Bar replacement = series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(lastBar.getEndTime().plus(Duration.ofDays(365)))
                .openPrice(150d)
                .highPrice(150d)
                .lowPrice(150d)
                .closePrice(150d)
                .volume(1)
                .build();
        writer.set(() -> series.addBar(replacement, true));
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        var annualRate = numFactory.numOf(0.1d);

        armed.set(true);
        var raced = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord,
                OpenPositionHandling.MARK_TO_MARKET);
        armed.set(false);
        var settled = new ExcessReturns(series, annualRate, CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord,
                OpenPositionHandling.MARK_TO_MARKET);

        assertNumEquals(150d, series.getBar(2).getClosePrice());
        assertEquals(settled.excessReturn(0, 2), raced.excessReturn(0, 2));
    }

    @Test
    public void openPositionHandlingControlsExcessReturnForOpenPositions() {
        BarSeries series = buildDailySeries(new double[] { 100d, 120d, 180d });
        var tradingRecord = new BaseTradingRecord();
        Num amount = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), amount);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), amount);
        tradingRecord.enter(1, series.getBar(1).getClosePrice(), amount);

        double markToMarket = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO,
                tradingRecord, OpenPositionHandling.MARK_TO_MARKET).excessReturn(0, 2).doubleValue();
        double ignore = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord,
                OpenPositionHandling.IGNORE).excessReturn(0, 2).doubleValue();

        assertEquals(0.8d, markToMarket, 1e-12);
        assertEquals(0.2d, ignore, 1e-12);
        assertTrue(markToMarket > ignore);
    }

    @Test
    public void zeroPreviousEquityDoesNotBreakExcessReturn() {
        BarSeries series = buildDailySeries(new double[] { 1d, 0d, 0d });
        var tradingRecord = new BaseTradingRecord();
        Num one = numFactory.one();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), one);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), one);

        Num actual = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, tradingRecord)
                .excessReturn(0, 2);

        assertEquals(one.negate(), actual);
    }

    private BarSeries buildDailySeries(double[] closes) {
        BarSeries series = getBarSeries("excess_returns_series");
        Instant start = Instant.parse("2024-01-01T00:00:00Z");

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

        return series;
    }

}
