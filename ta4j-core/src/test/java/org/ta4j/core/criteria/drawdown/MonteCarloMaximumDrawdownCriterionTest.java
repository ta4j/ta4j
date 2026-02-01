/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.LiveTradingRecord;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import static org.ta4j.core.criteria.drawdown.MonteCarloMaximumDrawdownCriterion.*;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;
import java.time.Instant;

public class MonteCarloMaximumDrawdownCriterionTest extends AbstractCriterionTest {

    public MonteCarloMaximumDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new MonteCarloMaximumDrawdownCriterion(), numFactory);
    }

    @Test
    public void calculateWithOnlyGains() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        var criterion = new MonteCarloMaximumDrawdownCriterion(200, null, 123L, Statistic.P95);
        assertNumEquals(0d, criterion.calculate(series, record));
    }

    @Test
    public void reproducibility() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 2, 4, 5, 6, 5)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series));
        var criterion1 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistic.P95);
        var value1 = criterion1.calculate(series, record);
        var criterion2 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistic.P95);
        var value2 = criterion2.calculate(series, record);
        assertNumEquals(value1, value2);
    }

    @Test
    public void differentMetrics() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 2, 4, 5, 6, 5)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series));
        var medianCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistic.MEDIAN);
        var maxCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistic.MAX);
        var median = medianCriterion.calculate(series, record);
        var max = maxCriterion.calculate(series, record);
        // max drawdown should be at least as large as median drawdown
        Assert.assertTrue(max.isGreaterThanOrEqual(median));
    }

    @Test
    public void honorsEquityCurveModeInSimulation() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 0.5, 2, 1, 0.5, 2, 1, 0.5, 2)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(5, series), Trade.buyAt(6, series), Trade.sellAt(8, series));
        class FixedRandom implements RandomGenerator {
            @Override
            public int nextInt() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public boolean nextBoolean() {
                return false;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public double nextDouble() {
                return 0d;
            }
        }
        var markToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistic.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        var realized = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistic.MAX,
                EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0.5d, markToMarket.calculate(series, record));
        assertNumEquals(0d, realized.calculate(series, record));
    }

    @Test
    public void honorsOpenPositionHandlingInSimulation() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 110, 80, 120, 70, 130)
                .build();
        var record = buildLiveRecordWithOpenLot(series, true);
        var closedOnly = buildLiveRecordWithOpenLot(series, false);

        class FixedRandom implements RandomGenerator {
            @Override
            public int nextInt() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public boolean nextBoolean() {
                return false;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public double nextDouble() {
                return 0d;
            }
        }

        var markToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistic.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        var ignoreOpen = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistic.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, record);
        var ignoreValue = ignoreOpen.calculate(series, record);
        var closedOnlyValue = ignoreOpen.calculate(series, closedOnly);

        assertNumEquals(closedOnlyValue, ignoreValue);
        Assert.assertFalse(markToMarketValue.isEqual(ignoreValue));
    }

    @Test
    public void convenienceConstructorsDelegateToExplicitSettings() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 0.5, 1).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var equityModeConvenience = new MonteCarloMaximumDrawdownCriterion(EquityCurveMode.REALIZED);
        var equityModeExplicit = new MonteCarloMaximumDrawdownCriterion(10_000, null, 42L, Statistic.P95,
                EquityCurveMode.REALIZED);
        assertNumEquals(equityModeExplicit.calculate(series, record), equityModeConvenience.calculate(series, record));

        var handlingConvenience = new MonteCarloMaximumDrawdownCriterion(OpenPositionHandling.IGNORE);
        var handlingExplicit = new MonteCarloMaximumDrawdownCriterion(10_000, null, 42L, Statistic.P95,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        assertNumEquals(handlingExplicit.calculate(series, record), handlingConvenience.calculate(series, record));
    }

    @Test
    public void seedConstructorsRespectModeCombinations() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 110, 80, 120, 70, 130)
                .build();
        var record = buildLiveRecordWithOpenLot(series, true);

        var seedMarkToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, 7L, Statistic.MAX,
                EquityCurveMode.MARK_TO_MARKET);
        var seedIgnore = new MonteCarloMaximumDrawdownCriterion(1, 1, 7L, Statistic.MAX, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        var markToMarketValue = seedMarkToMarket.calculate(series, record);
        var ignoreValue = seedIgnore.calculate(series, record);

        Assert.assertTrue(markToMarketValue.isGreaterThan(ignoreValue));
    }

    @Test
    public void fallbackToHistoricalMaximumDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 2, 1).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));
        var monteCarlo = new MonteCarloMaximumDrawdownCriterion(100, null, 7L, Statistic.P95);
        var result = monteCarlo.calculate(series, record);
        var expected = new MaximumDrawdownCriterion().calculate(series, record);
        assertNumEquals(expected, result);
    }

    @Test
    public void usesInjectedRandomGenerator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        var counter = new AtomicInteger();
        class CountingRandom implements RandomGenerator {
            @Override
            public int nextInt() {
                counter.incrementAndGet();
                return 0;
            }

            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public boolean nextBoolean() {
                return false;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public double nextDouble() {
                return 0d;
            }
        }
        var criterion = new MonteCarloMaximumDrawdownCriterion(1, 2, CountingRandom::new, Statistic.P95);
        criterion.calculate(series, record);
        assertEquals(2, counter.get());
    }

    private LiveTradingRecord buildLiveRecordWithOpenLot(org.ta4j.core.BarSeries series, boolean includeOpenLot) {
        var record = new LiveTradingRecord(Trade.TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        var numFactory = series.numFactory();

        if (includeOpenLot) {
            record.recordFill(0, new LiveTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.numOf(10),
                    null, ExecutionSide.BUY, "order-open", "open"));
        }
        record.recordFill(1, new LiveTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-1", "c1"));
        record.recordFill(2, new LiveTrade(2, Instant.EPOCH, series.getBar(2).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-1", "c1"));
        record.recordFill(3, new LiveTrade(3, Instant.EPOCH, series.getBar(3).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-2", "c2"));
        record.recordFill(4, new LiveTrade(4, Instant.EPOCH, series.getBar(4).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-2", "c2"));
        record.recordFill(5, new LiveTrade(5, Instant.EPOCH, series.getBar(5).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-3", "c3"));
        record.recordFill(6, new LiveTrade(6, Instant.EPOCH, series.getBar(6).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-3", "c3"));

        return record;
    }
}
