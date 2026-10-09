/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.BarSeries;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.BaseTrade;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.Statistics;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import java.time.Instant;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;

public class MonteCarloMaximumDrawdownCriterionTest extends AbstractCriterionTest {

    public MonteCarloMaximumDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new MonteCarloMaximumDrawdownCriterion(), numFactory);
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void pricesClosedBlockEndingAtMaximumIntegerIndex() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 50d).build();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE - 1)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE - 1, series),
                Trade.sellAt(Integer.MAX_VALUE, series));

        assertNumEquals(0.5,
                new MonteCarloMaximumDrawdownCriterion(1, null, 42L, Statistics.MAX).calculate(series, record));
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void blocksStartAtTheCapturedWindowForPreWindowEntries() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 80d).build();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE - 1)
                .build();
        // The entry predates the retained window by ~2^31 bars; only the two
        // retained bars may become block returns.
        BaseTradingRecord record = new BaseTradingRecord(Trade.buyAt(0, numFactory.numOf(100d), numFactory.one()),
                Trade.sellAt(Integer.MAX_VALUE, series));

        Num drawdown = new MonteCarloMaximumDrawdownCriterion(1, null, 42L, Statistics.MAX).calculate(series, record);

        assertNumEquals(0.2, drawdown);
    }

    @Test
    public void calculateWithOnlyGains() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        var criterion = new MonteCarloMaximumDrawdownCriterion(200, null, 123L, Statistics.P95);
        assertNumEquals(0d, criterion.calculate(series, record));
    }

    @Test
    public void carriedPreWindowLossDoesNotBecomeRetainedBlockReturn() {
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 1, 100, 110, 100, 80, 100, 105)
                .build();
        BaseTradingRecord prunedRecord = new BaseTradingRecord(Trade.buyAt(0, pruned), Trade.sellAt(1, pruned),
                Trade.buyAt(2, pruned), Trade.sellAt(3, pruned), Trade.buyAt(4, pruned), Trade.sellAt(5, pruned),
                Trade.buyAt(6, pruned), Trade.sellAt(7, pruned));
        pruned.setMaximumBarCount(6);
        assertNumEquals(0.01d, new CashFlow(pruned, prunedRecord).getValue(pruned.getBeginIndex()));

        BarSeries fresh = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 100, 80, 100, 105)
                .build();
        BaseTradingRecord freshRecord = new BaseTradingRecord(Trade.buyAt(0, fresh), Trade.sellAt(1, fresh),
                Trade.buyAt(2, fresh), Trade.sellAt(3, fresh), Trade.buyAt(4, fresh), Trade.sellAt(5, fresh));
        MonteCarloMaximumDrawdownCriterion criterion = new MonteCarloMaximumDrawdownCriterion(1000, null, 123L,
                Statistics.P95);

        assertEquals(criterion.calculate(fresh, freshRecord).doubleValue(),
                criterion.calculate(pruned, prunedRecord).doubleValue(), 1e-12);
    }

    @Test
    public void clipsBlocksToRetainedMovingSeriesWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 102, 98, 103, 97, 104, 96, 105, 95, 106)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series), Trade.buyAt(10, series), Trade.sellAt(11, series));
        series.setMaximumBarCount(5);
        var criterion = new MonteCarloMaximumDrawdownCriterion(1, null, 123L, Statistics.P95);

        // Positions entered before the retained window must not read pruned bars.
        Assertions.assertFalse(criterion.calculate(series, record).isNaN());
    }

    @Test
    public void reproducibility() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 2, 4, 5, 6, 5)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series));
        var criterion1 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.P95);
        var value1 = criterion1.calculate(series, record);
        var criterion2 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.P95);
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
        var medianCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.MEDIAN);
        var maxCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.MAX);
        var median = medianCriterion.calculate(series, record);
        var max = maxCriterion.calculate(series, record);
        // max drawdown should be at least as large as median drawdown
        Assertions.assertTrue(max.isGreaterThanOrEqual(median));
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
        var markToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        var realized = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistics.MAX,
                EquityCurveMode.REALIZED, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(0.5d, markToMarket.calculate(series, record));
        assertNumEquals(0d, realized.calculate(series, record));
    }

    @Test
    public void honorsOpenPositionHandlingInSimulation() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 110, 80, 120, 70, 130)
                .build();
        var record = buildRecordWithOpenLot(series, true);
        var closedOnly = buildRecordWithOpenLot(series, false);

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

        var markToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        var ignoreOpen = new MonteCarloMaximumDrawdownCriterion(1, 1, FixedRandom::new, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, record);
        var ignoreValue = ignoreOpen.calculate(series, record);
        var closedOnlyValue = ignoreOpen.calculate(series, closedOnly);

        assertNumEquals(closedOnlyValue, ignoreValue);
        Assertions.assertFalse(markToMarketValue.isEqual(ignoreValue));
    }

    @Test
    public void convenienceConstructorsDelegateToExplicitSettings() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 0.5, 1).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var equityModeConvenience = new MonteCarloMaximumDrawdownCriterion(EquityCurveMode.REALIZED);
        var equityModeExplicit = new MonteCarloMaximumDrawdownCriterion(10_000, null, 42L, Statistics.P95,
                EquityCurveMode.REALIZED);
        assertNumEquals(equityModeExplicit.calculate(series, record), equityModeConvenience.calculate(series, record));

        var handlingConvenience = new MonteCarloMaximumDrawdownCriterion(OpenPositionHandling.IGNORE);
        var handlingExplicit = new MonteCarloMaximumDrawdownCriterion(10_000, null, 42L, Statistics.P95,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);
        assertNumEquals(handlingExplicit.calculate(series, record), handlingConvenience.calculate(series, record));
    }

    @Test
    public void seedConstructorsRespectModeCombinations() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 110, 80, 120, 70, 130)
                .build();
        var record = buildRecordWithOpenLot(series, true);

        var seedMarkToMarket = new MonteCarloMaximumDrawdownCriterion(1, 1, 7L, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET);
        var seedIgnore = new MonteCarloMaximumDrawdownCriterion(1, 1, 7L, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);

        var markToMarketValue = seedMarkToMarket.calculate(series, record);
        var ignoreValue = seedIgnore.calculate(series, record);

        Assertions.assertTrue(markToMarketValue.isGreaterThan(ignoreValue));
    }

    @Test
    public void fallbackToHistoricalMaximumDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 2, 1).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));
        var monteCarlo = new MonteCarloMaximumDrawdownCriterion(100, null, 7L, Statistics.P95);
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
        var criterion = new MonteCarloMaximumDrawdownCriterion(1, 2, CountingRandom::new, Statistics.P95);
        criterion.calculate(series, record);
        assertEquals(2, counter.get());
    }

    private BaseTradingRecord buildRecordWithOpenLot(org.ta4j.core.BarSeries series, boolean includeOpenLot) {
        var record = new BaseTradingRecord(Trade.TradeType.BUY, ExecutionMatchPolicy.SPECIFIC_ID, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        var numFactory = series.numFactory();

        if (includeOpenLot) {
            record.operate(new BaseTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(), numFactory.numOf(10), null,
                    ExecutionSide.BUY, "order-open", "open"));
        }
        record.operate(new BaseTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-1", "c1"));
        record.operate(new BaseTrade(2, Instant.EPOCH, series.getBar(2).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-1", "c1"));
        record.operate(new BaseTrade(3, Instant.EPOCH, series.getBar(3).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-2", "c2"));
        record.operate(new BaseTrade(4, Instant.EPOCH, series.getBar(4).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-2", "c2"));
        record.operate(new BaseTrade(5, Instant.EPOCH, series.getBar(5).getClosePrice(), numFactory.one(), null,
                ExecutionSide.BUY, "order-3", "c3"));
        record.operate(new BaseTrade(6, Instant.EPOCH, series.getBar(6).getClosePrice(), numFactory.one(), null,
                ExecutionSide.SELL, "order-3", "c3"));

        return record;
    }

    @Test
    public void zeroDurationPositionAtFirstBarStillFormsABlock() {
        NumFactory decimalFactory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(decimalFactory)
                .withData(100, 120, 90, 110, 80)
                .build();
        BaseTradingRecord record = new BaseTradingRecord();
        for (int[] leg : new int[][] { { 0, 0 }, { 1, 2 }, { 3, 4 } }) {
            record.enter(leg[0], series.getBar(leg[0]).getClosePrice(), decimalFactory.one());
            record.exit(leg[1], series.getBar(leg[1]).getClosePrice(), decimalFactory.one());
        }
        MonteCarloMaximumDrawdownCriterion criterion = new MonteCarloMaximumDrawdownCriterion(1000, null, 42L,
                Statistics.P95, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        // Pinned from the pre-refactor implementation: three blocks run the
        // simulation instead of falling back to the deterministic drawdown
        // (0.4545...).
        assertEquals(0.6033057851239669, criterion.calculate(series, record).doubleValue(), 1e-12);
    }

    @Test
    public void zeroDurationPositionAtPrunedBeginStillFormsABlock() {
        NumFactory decimalFactory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        BarSeries pruned = new MockBarSeriesBuilder().withNumFactory(decimalFactory)
                .withData(50, 60, 70, 80, 90, 100, 120, 90, 110, 80)
                .build();
        pruned.setMaximumBarCount(5);
        BarSeries unpruned = new MockBarSeriesBuilder().withNumFactory(decimalFactory)
                .withData(100, 120, 90, 110, 80)
                .build();
        int offset = pruned.getBeginIndex();
        BaseTradingRecord prunedRecord = new BaseTradingRecord();
        BaseTradingRecord unprunedRecord = new BaseTradingRecord();
        for (int[] leg : new int[][] { { 0, 0 }, { 1, 2 }, { 3, 4 } }) {
            prunedRecord.enter(leg[0] + offset, pruned.getBar(leg[0] + offset).getClosePrice(), decimalFactory.one());
            prunedRecord.exit(leg[1] + offset, pruned.getBar(leg[1] + offset).getClosePrice(), decimalFactory.one());
            unprunedRecord.enter(leg[0], unpruned.getBar(leg[0]).getClosePrice(), decimalFactory.one());
            unprunedRecord.exit(leg[1], unpruned.getBar(leg[1]).getClosePrice(), decimalFactory.one());
        }
        MonteCarloMaximumDrawdownCriterion criterion = new MonteCarloMaximumDrawdownCriterion(1000, null, 42L,
                Statistics.P95, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        // The same three legs on the retained window simulate exactly like the
        // unpruned series (0.6033...), instead of falling back to 0.4545...
        assertEquals(criterion.calculate(unpruned, unprunedRecord).doubleValue(),
                criterion.calculate(pruned, prunedRecord).doubleValue(), 1e-12);
        assertEquals(0.6033057851239669, criterion.calculate(pruned, prunedRecord).doubleValue(), 1e-12);
    }

    @Test
    public void lossRealizedAtConstrainedBeginIsTheFirstBlockReturn() {
        var series = ConstrainedSeriesSupport.offsetSeries("mc-seeded-begin", numFactory, 1, 7, 0, 100d, 100d, 100d,
                110d, 120d, 130d, 140d, 150d);
        var record = new BaseTradingRecord();
        record.enter(0, numFactory.hundred(), numFactory.one());
        record.exit(1, numFactory.numOf(95), numFactory.one());
        record.enter(2, numFactory.hundred(), numFactory.one());
        record.exit(3, numFactory.numOf(110), numFactory.one());
        record.enter(4, numFactory.numOf(120), numFactory.one());
        record.exit(5, numFactory.numOf(130), numFactory.one());
        class FirstBlockRandom implements RandomGenerator {
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
        }
        var criterion = new MonteCarloMaximumDrawdownCriterion(1, 1, FirstBlockRandom::new, Statistics.MAX,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        // The first block's return is the 0.95 realized in the first slot, measured
        // from the
        // neutral 1 entering the window; nothing was pruned, so nothing is carried.
        assertNumEquals(0.05, criterion.calculate(series, record));
    }

    @Test
    public void matchesFreshSeriesAcrossWindowShapesAndEquitySettings() {
        for (ConstrainedSeriesSupport.CriterionWindowFixture fixture : ConstrainedSeriesSupport
                .criterionWindowFixtures(numFactory)) {
            for (EquityCurveMode mode : EquityCurveMode.values()) {
                for (OpenPositionHandling handling : OpenPositionHandling.values()) {
                    var criterion = new MonteCarloMaximumDrawdownCriterion(1, null, 42L, Statistics.MAX, mode,
                            handling);
                    Num actual = criterion.calculate(fixture.series(), fixture.tradingRecord());
                    Num expected = criterion.calculate(fixture.equivalentSeries(), fixture.equivalentRecord(mode));
                    Assertions.assertEquals(expected.doubleValue(), actual.doubleValue(), 1e-10,
                            fixture.name() + ": " + mode + "/" + handling);
                }
            }
        }
    }

    @Test
    public void boundedRecordResamplesOnlySelectedClosedBlocks() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 90, 110, 100, 70, 100, 80, 50, 20)
                .build();
        BaseTradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, 2, 8, new ZeroCostModel(),
                new ZeroCostModel());
        for (int[] leg : new int[][] { { 0, 2 }, { 2, 3 }, { 4, 5 }, { 6, 7 }, { 7, 9 } }) {
            record.enter(leg[0], series.getBar(leg[0]).getClosePrice(), numFactory.one());
            record.exit(leg[1], series.getBar(leg[1]).getClosePrice(), numFactory.one());
        }
        MonteCarloMaximumDrawdownCriterion criterion = new MonteCarloMaximumDrawdownCriterion(1000, 4, 42L,
                Statistics.P95, EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(OpenPositionHandling.IGNORE);
        AnalysisWindow window = AnalysisWindow.barRange(2, 8);
        // Three contained blocks: 110/90, 70/100 and 80/100, so this exercises
        // resampling.
        Num expected = criterion.calculate(series, record, window, context);
        Num historical = new MaximumDrawdownCriterion(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE)
                .calculate(series, record, window, context);
        // Resampling four blocks can repeat losses beyond the realized 1 - 0.7 * 0.8 =
        // 0.44.
        Assertions.assertTrue(expected.isGreaterThan(historical));
        assertNumEquals(expected, criterion.calculate(series, record));
        assertNumEquals(expected, criterion.calculate(series, record, window, context));
    }

}
