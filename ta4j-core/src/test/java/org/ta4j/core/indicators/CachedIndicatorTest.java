/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.saturatedRetainedWindowSeries;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.averages.EDMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.VIDYAIndicator;
import org.ta4j.core.indicators.averages.WildersMAIndicator;
import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.helpers.AverageIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.statistics.PearsonCorrelationIndicator;
import org.ta4j.core.indicators.statistics.ZScoreIndicator;
import org.ta4j.core.indicators.volume.KlingerVolumeOscillatorIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.mocks.MockBarBuilder;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class CachedIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CachedIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void ifCacheWorks() {
        var sma = new SMAIndicator(new ClosePriceIndicator(series), 3);
        Num firstTime = sma.getValue(4);
        Num secondTime = sma.getValue(4);
        assertEquals(firstTime, secondTime);
    }

    @Test
    public void readOnlySeriesViewIsStableAndDelegatesChangeSnapshots() {
        TestIndicator indicator = new TestIndicator(series);
        BarSeries firstView = indicator.getBarSeries();
        long initialRevision = firstView.getBarHistoryRevision();

        assertSame(firstView, indicator.getBarSeries());
        ((BaseBarSeries) series).replaceBar(1, series.getBar(1));

        assertEquals(initialRevision + 1, firstView.getBarHistoryRevision());
        assertEquals(1, firstView.getBarSeriesChangeSnapshot(initialRevision).earliestChangedIndex());
    }

    @Test
    public void nonTerminalReplacementInvalidatesGenericDownstreamTail() {
        BaseBarSeries barSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 3);
        assertNumEquals(3, sma.getValue(3));

        Bar replaced = barSeries.getBar(1);
        Bar replacement = barSeries.barBuilder()
                .timePeriod(replaced.getTimePeriod())
                .endTime(replaced.getEndTime())
                .openPrice(20)
                .highPrice(20)
                .lowPrice(20)
                .closePrice(20)
                .volume(replaced.getVolume())
                .build();
        barSeries.replaceBar(1, replacement);

        assertNumEquals(9, sma.getValue(3));
    }

    @Test
    public void multipleSkippedRevisionsInvalidateFromEarliestChangedIndex() {
        BaseBarSeries barSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingInvalidatableIndicator indicator = new CountingInvalidatableIndicator(barSeries);
        indicator.getValue(0);
        indicator.getValue(1);
        indicator.getValue(2);
        indicator.getValue(3);

        barSeries.replaceBar(3, barSeries.getBar(3));
        barSeries.replaceBar(1, barSeries.getBar(1));

        indicator.getValue(0);
        indicator.getValue(1);
        indicator.getValue(2);
        indicator.getValue(3);
        assertEquals(7, indicator.getCalculationCount());
    }

    @Test
    public void existingIndicatorReconcilesMaximumBarCountChanges() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 1d, 2d, 3d, 4d, 5d)
                .build();
        TestIndicator indicator = new TestIndicator(barSeries);
        for (int i = 0; i <= barSeries.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        barSeries.setMaximumBarCount(2);
        assertNumEquals(4, indicator.getValue(4));
        assertNumEquals(5, indicator.getValue(5));

        barSeries.setMaximumBarCount(4);
        Bar lastBar = barSeries.getLastBar();
        barSeries.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime().plus(lastBar.getTimePeriod()))
                .closePrice(6)
                .add();
        assertNumEquals(6, indicator.getValue(6));
    }

    @Test
    public void concurrentSnapshotSynchronizationRetriesAfterLosingObservationRace() throws Exception {
        BarSeries source = new MockBarSeriesBuilder().withData(0d, 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d).build();
        RacingSnapshotSeries barSeries = new RacingSnapshotSeries(source.getBarData());
        TestIndicator indicator = new TestIndicator(barSeries);
        for (int i = 0; i < barSeries.getEndIndex(); i++) {
            indicator.getValue(i);
        }

        barSeries.prepareBlockedSnapshot(8);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Num> olderSnapshotRead = executor.submit(() -> indicator.getValue(barSeries.getEndIndex()));
            assertTrue("Older snapshot was not captured", barSeries.awaitBlockedSnapshot());

            barSeries.setSnapshotMaximumBarCount(2);
            Future<Num> newerSnapshotRead = executor.submit(() -> indicator.getValue(barSeries.getEndIndex()));
            assertNumEquals(9, newerSnapshotRead.get(30, TimeUnit.SECONDS));

            barSeries.releaseBlockedSnapshot();
            assertNumEquals(9, olderSnapshotRead.get(30, TimeUnit.SECONDS));
        } finally {
            barSeries.releaseBlockedSnapshot();
            executor.shutdownNow();
        }

        indicator.getValue(6);
        assertEquals(6, indicator.getCache().getFirstCachedIndex());
        assertEquals(7, indicator.getCache().getHighestResultIndex());
    }

    @Test
    public void recursiveIndicatorRewindsBeforeIterativePrefill() {
        double[] data = new double[200];
        Arrays.fill(data, 1d);
        BaseBarSeries barSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(data)
                .build();
        SelfReferencingIndicator indicator = new SelfReferencingIndicator(barSeries);
        assertNumEquals(200, indicator.getValue(199));
        assertEquals(200, indicator.getCalculationCount());

        barSeries.replaceBar(50, barSeries.getBar(50));

        assertNumEquals(200, indicator.getValue(199));
        assertEquals(350, indicator.getCalculationCount());
    }

    @Test
    public void mutatedLastBarIsRecomputedAfterItBecomesHistorical() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertNumEquals(3, closePrice.getValue(2));

        barSeries.addPrice(30);
        Bar lastBar = barSeries.getLastBar();
        barSeries.barBuilder()
                .timePeriod(lastBar.getTimePeriod())
                .endTime(lastBar.getEndTime().plus(lastBar.getTimePeriod()))
                .closePrice(4)
                .add();

        assertNumEquals(30, closePrice.getValue(2));
    }

    @Test
    public void firstBarValueRefreshesWhenFirstRetainedBarIsReplaced() {
        BaseBarSeries barSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        barSeries.setMaximumBarCount(3);
        // Removed bars: 0,1 -> first retained bar index is 2 with close price 3
        assertEquals(2, barSeries.getRemovedBarsCount());
        FirstBarReadingIndicator indicator = new FirstBarReadingIndicator(barSeries);
        assertNumEquals(3, indicator.getValue(0));

        // Replace the first retained bar (index 2) with a new close price of 30.
        Bar replaced = barSeries.getBar(2);
        Bar replacement = barSeries.barBuilder()
                .timePeriod(replaced.getTimePeriod())
                .endTime(replaced.getEndTime())
                .openPrice(30)
                .highPrice(30)
                .lowPrice(30)
                .closePrice(30)
                .volume(replaced.getVolume())
                .build();
        barSeries.replaceBar(2, replacement);

        // The pruned index 0 maps to the first retained bar, whose value changed.
        assertNumEquals(30, indicator.getValue(0));
        int countAfterFirstBarRefresh = indicator.getCalculationCount();

        // Negative control: replacing a bar above the first retained index must
        // keep the cached first-bar value valid (no recomputation needed), so the
        // first-bar cache must NOT be cleared for unrelated changes.
        Bar aboveReplaced = barSeries.getBar(3);
        Bar aboveReplacement = barSeries.barBuilder()
                .timePeriod(aboveReplaced.getTimePeriod())
                .endTime(aboveReplaced.getEndTime())
                .openPrice(40)
                .highPrice(40)
                .lowPrice(40)
                .closePrice(40)
                .volume(aboveReplaced.getVolume())
                .build();
        barSeries.replaceBar(3, aboveReplacement);

        assertNumEquals(30, indicator.getValue(0));
        assertEquals(countAfterFirstBarRefresh, indicator.getCalculationCount());
    }

    @Test // should be not null
    public void getValueWithNullBarSeries() {

        ConstantIndicator<Num> constant = new ConstantIndicator<>(
                new BaseBarSeriesBuilder().withNumFactory(numFactory).build(), numFactory.numOf(10));
        assertEquals(numFactory.numOf(10), constant.getValue(0));
        assertEquals(numFactory.numOf(10), constant.getValue(100));
        assertNotNull(constant.getBarSeries());

        SMAIndicator sma = new SMAIndicator(constant, 10);
        assertEquals(numFactory.numOf(10), sma.getValue(0));
        assertEquals(numFactory.numOf(10), sma.getValue(100));
        assertNotNull(sma.getBarSeries());
    }

    @Test
    public void getValueWithCacheLengthIncrease() {
        double[] data = new double[200];
        Arrays.fill(data, 10);
        SMAIndicator sma = new SMAIndicator(
                new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build()),
                100);
        assertNumEquals(10, sma.getValue(105));
    }

    @Test
    public void getValueWithOldResultsRemoval() {
        double[] data = new double[20];
        Arrays.fill(data, 1);
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 10);
        assertNumEquals(1, sma.getValue(5));
        assertNumEquals(1, sma.getValue(10));
        barSeries.setMaximumBarCount(12);
        assertNumEquals(1, sma.getValue(19));
    }

    @Test
    public void strategyExecutionOnCachedIndicatorAndLimitedBarSeries() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 1, 2, 3, 4, 5, 6, 7)
                .build();
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        // Theoretical values for SMA(2) cache: 0, 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5
        barSeries.setMaximumBarCount(6);
        // Theoretical values for SMA(2) cache: null, null, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        Strategy strategy = new BaseStrategy(new OverIndicatorRule(sma, numFactory.numOf(3)),
                new UnderIndicatorRule(sma, numFactory.numOf(3)));
        // Theoretical shouldEnter results: false, false, false, false, true, true,
        // true, true
        // Theoretical shouldExit results: false, false, true, true, false, false,
        // false, false

        // As we return the first bar/result found for the removed bars:
        // -> Approximated values for ClosePrice cache: 2, 2, 2, 3, 4, 5, 6, 7
        // -> Approximated values for SMA(2) cache: 2, 2, 2, 2.5, 3.5, 4.5, 5.5, 6.5

        // Then enters/exits are also approximated:
        // -> shouldEnter results: false, false, false, false, true, true, true, true
        // -> shouldExit results: true, true, true, true, false, false, false, false

        assertFalse(strategy.shouldEnter(0));
        assertTrue(strategy.shouldExit(0));
        assertFalse(strategy.shouldEnter(1));
        assertTrue(strategy.shouldExit(1));
        assertFalse(strategy.shouldEnter(2));
        assertTrue(strategy.shouldExit(2));
        assertFalse(strategy.shouldEnter(3));
        assertTrue(strategy.shouldExit(3));
        assertTrue(strategy.shouldEnter(4));
        assertFalse(strategy.shouldExit(4));
        assertTrue(strategy.shouldEnter(5));
        assertFalse(strategy.shouldExit(5));
        assertTrue(strategy.shouldEnter(6));
        assertFalse(strategy.shouldExit(6));
        assertTrue(strategy.shouldEnter(7));
        assertFalse(strategy.shouldExit(7));
    }

    @Test
    public void getValueOnResultsCalculatedFromRemovedBarsShouldReturnFirstRemainingResult() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1).build();
        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getRemovedBarsCount());

        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 2);
        for (int i = 0; i < 5; i++) {
            assertNumEquals(1, sma.getValue(i));
        }
    }

    @Test
    public void prunedIndexCacheInvalidatesWhenRemovedBarsCountChanges() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);

        assertNumEquals(1, indicator.getValue(0));
        assertEquals(1, indicator.getCalculationCount());

        // Force removal of the first bar and thus change the "first available bar" for
        // index 0.
        barSeries.setMaximumBarCount(2);
        assertEquals(1, barSeries.getRemovedBarsCount());

        assertNumEquals(2, indicator.getValue(0));
        assertEquals(2, indicator.getCalculationCount());

        // Subsequent hits should reuse the pruned-index cache for the new
        // removedBarsCount.
        assertNumEquals(2, indicator.getValue(0));
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void recursiveCachedIndicatorOnMovingBarSeriesShouldNotCauseStackOverflow() {
        // Added to check issue #120: https://github.com/mdeverdelhan/ta4j/issues/120
        // See also: CachedIndicator#getValue(int index)
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        series.setMaximumBarCount(5);
        assertEquals(5, series.getBarCount());

        ZLEMAIndicator zlema = new ZLEMAIndicator(new ClosePriceIndicator(series), 1);
        try {
            assertNumEquals(4996, zlema.getValue(8));
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    @Test
    public void recursiveIndicatorKeepsCachedValuesWhenSeriesHeadAdvances() {
        // A recursive indicator's values depend on all earlier history, so the
        // head-advance cache floor must not evict the declared unstable band:
        // recomputing only that band against the retained window would split
        // the cache between window-relative and original-series values.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d)
                .build();
        ZLEMAIndicator zlema = new ZLEMAIndicator(new ClosePriceIndicator(barSeries), 2);
        Num beforeAdvance = zlema.getValue(2);

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(beforeAdvance, zlema.getValue(2));
    }

    @Test
    public void selfRecursiveCachedIndicatorKeepsCachedValuesWhenSeriesHeadAdvances() {
        // A CachedIndicator subclass whose calculate() reads its own preceding
        // value is recursive by definition even when it does not extend
        // RecursiveCachedIndicator: the head-advance cache floor must not
        // evict the retained band and reseed it from a different history.
        // SMMA is the deliberate exception: it re-anchors its chain at the
        // retained head (see SMMAIndicatorTest), so this test uses a plain
        // self-recursive subclass that keeps the default policy.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d)
                .build();
        SelfRecursiveKeepingIndicator selfRecursive = new SelfRecursiveKeepingIndicator(barSeries);
        Num beforeAdvance = selfRecursive.getValue(4);

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(beforeAdvance, selfRecursive.getValue(4));
    }

    @Test
    public void discardingIndicatorDropsWholeCacheWhenSeriesHeadAdvances() {
        // A subclass whose values are always recomputable from the retained
        // window can opt into full-cache eviction on head advance. Before the
        // advance index 4 caches 4; after the advance to begin index 2 the
        // same read must return the retained-window value 2, and the begin
        // index itself must resolve to the base case 0.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d)
                .build();
        HeadAdvanceDiscardingIndicator indicator = new HeadAdvanceDiscardingIndicator(barSeries);
        assertNumEquals(4, indicator.getValue(4));

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(2, indicator.getValue(4));
        assertNumEquals(0, indicator.getValue(2));
    }

    @Test
    public void cachedDependentFollowsRebaseliningSourceWhenSeriesHeadAdvances() {
        // Stochastic rebaselines its whole cache on head advance; a dependent
        // caching the pre-advance source values would keep serving stale
        // results, so the source's full-tail invalidation must propagate.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        EMAIndicator dependent = new EMAIndicator(source, 1);
        assertNumEquals(100, dependent.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        // The retained [50, 50, 50] window has a zero range: the rebaselined
        // source returns 0, and the dependent must follow instead of serving
        // its stale cached 100.
        assertNumEquals(0, dependent.getValue(3));
        assertNumEquals(0, dependent.getValue(4));
    }

    @Test
    public void cachedDependentFollowsRebaseliningSourceBehindOperationWrapperWhenSeriesHeadAdvances() {
        // A stochastic wrapped in a non-cached BinaryOperationIndicator hides
        // the rebaselining source from the direct source walk; the propagation
        // must traverse the dependency graph through non-cached wrappers, or
        // the EMA keeps serving the stale pre-advance product after the head
        // advance.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        BinaryOperationIndicator product = BinaryOperationIndicator.product(source, 1);
        EMAIndicator dependent = new EMAIndicator(product, 1);
        assertNumEquals(100, dependent.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        // The retained [50, 50, 50] window has a zero range: the rebaselined
        // stochastic returns 0, so the wrapped product returns 0 and the
        // dependent must follow instead of serving its stale cached 100.
        assertNumEquals(0, dependent.getValue(3));
        assertNumEquals(0, dependent.getValue(4));
    }

    @Test
    public void multiSourceIndicatorRegistersEveryInputForRebaselinePropagation() {
        // AverageIndicator consumes every supplied input but only tracked the
        // first one; a rebaselining second input could keep serving its stale
        // cached average after the head advance. Registering every source must
        // make the cached value match the fresh computation.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        StochasticIndicator stochastic = new StochasticIndicator(closePrice, 3);
        AverageIndicator average = new AverageIndicator(closePrice, stochastic);
        // (50 + 100) / 2 = 75 on the unbounded series.
        assertNumEquals(75, average.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        // The retained [50, 50, 50] window has a zero range: the rebaselined
        // stochastic returns 0, and the average must follow to (50 + 0) / 2 = 25
        // instead of serving its stale cached 75.
        assertNumEquals(25, average.getValue(3));
        assertNumEquals(25, average.getValue(4));
    }

    @Test
    public void transitiveCachedDependentsFollowRebaseliningSourceWhenSeriesHeadAdvances() {
        // Propagation must travel through a chain of cached dependents, not
        // only one level below the rebaselining source.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        EMAIndicator inner = new EMAIndicator(source, 1);
        EMAIndicator outer = new EMAIndicator(inner, 1);
        assertNumEquals(100, outer.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(0, outer.getValue(3));
        assertNumEquals(0, outer.getValue(4));
    }

    @Test
    public void nonRecursiveCachedDependentFollowsRebaseliningSourceWhenSeriesHeadAdvances() {
        // Propagation must not be limited to recursive dependents: a
        // non-recursive dependent whose own unstable band does not cover the
        // rebaselined source band would otherwise keep serving stale entries
        // at or above its default cache floor.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        StochasticIndicator source = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        PassingIndicator dependent = new PassingIndicator(source);
        assertNumEquals(100, dependent.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(0, dependent.getValue(3));
        assertNumEquals(0, dependent.getValue(4));
    }

    @Test
    public void traversesDeepDependencyGraphsIterativelyWhenSeriesHeadAdvances() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        Indicator<Num> source = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        for (int index = 0; index < 32_768; index++) {
            source = new DependencyWrapper(barSeries, source);
        }
        PassingIndicator dependent = new PassingIndicator(source);

        assertNumEquals(100, dependent.getValue(3));

        barSeries.setMaximumBarCount(3);

        assertNumEquals(0, dependent.getValue(3));
    }

    /**
     * Non-recursive cached passthrough with no unstable band of its own, used to
     * verify that a source's full-tail invalidation propagates to dependents whose
     * default cache floor would otherwise keep stale entries.
     */
    private static final class PassingIndicator extends CachedIndicator<Num> {

        private static final long serialVersionUID = 1L;

        private final Indicator<Num> source;

        PassingIndicator(Indicator<Num> source) {
            super(source);
            this.source = source;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            return source.getValue(index);
        }
    }

    /**
     * Identity wrapper with iterative-safe series and value accessors.
     */
    private static final class DependencyWrapper implements Indicator<Num> {

        private final BarSeries barSeries;
        private final Indicator<Num> source;

        private DependencyWrapper(BarSeries barSeries, Indicator<Num> source) {
            this.barSeries = barSeries;
            this.source = source;
        }

        @Override
        public Num getValue(int index) {
            Indicator<Num> currentSource = source;
            while (currentSource instanceof DependencyWrapper wrapper) {
                currentSource = wrapper.source;
            }
            return currentSource.getValue(index);
        }

        @Override
        public List<Indicator<?>> getDependencies() {
            return List.of(source);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return barSeries;
        }
    }

    @Test
    public void unannotatedRecursiveSubclassKeepsCachedValuesWhenSeriesHeadAdvances() {
        // RecursiveCachedIndicator opts into the head-advance exemption by
        // default: a subclass that computes each value from its predecessor
        // keeps pre-advance values even without a hook override of its own,
        // because those values encode removed history that a recomputed
        // unstable band could not reproduce. Non-uniform closes keep the
        // pre-advance cumulative (1+2+1+2+3 = 9) distinct from a reseeded
        // chain restarted from the first retained close (1+1+2+3 = 7).
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 1d, 2d, 3d, 4d, 5d)
                .build();
        CumulativeRecursiveIndicator cumulative = new CumulativeRecursiveIndicator(new ClosePriceIndicator(barSeries));
        Num beforeAdvance = cumulative.getValue(4);

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        assertNumEquals(beforeAdvance, cumulative.getValue(4));
    }

    @Test
    public void windowedRecursiveIndicatorRecomputesStaleBandWhenSeriesHeadAdvances() {
        // VolumeIndicator recurses only to walk its rolling accumulation; its
        // values depend on a fixed trailing window, so after a head advance the
        // stale band must be dropped and recomputed against the retained window
        // like any windowed indicator. The blanket recursive exemption used to
        // keep the pre-advance sums cached here.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            barSeries.barBuilder().closePrice(i + 1).volume(i + 4).add();
        }
        VolumeIndicator volume = new VolumeIndicator(barSeries, 3);
        assertNumEquals(15, volume.getValue(2)); // mock volumes 4 + 5 + 6
        assertNumEquals(18, volume.getValue(3)); // mock volumes 5 + 6 + 7

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        // Fresh values cover only the retained window: bar 2 alone, then bars
        // 2 and 3 - not the stale pre-advance sums over bars 0..2 and 0..3.
        assertNumEquals(6, volume.getValue(2));
        assertNumEquals(13, volume.getValue(3)); // mock volumes 6 + 7
    }

    @Test
    public void windowedRecursiveCorrelationRecomputesAgainstRetainedWindowWhenSeriesHeadAdvances() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 7; i++) {
            barSeries.barBuilder().closePrice(i + 1).volume(i + 4).add();
        }
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(new ClosePriceIndicator(barSeries),
                new VolumeIndicator(barSeries, 1), 3);
        // Closes (1, 2, 3) and volumes (4, 5, 6) are perfectly correlated.
        assertNumEquals(1, correlation.getValue(2));

        barSeries.setMaximumBarCount(5);
        assertEquals(2, barSeries.getBeginIndex());

        // A single retained observation cannot define a correlation: the stale
        // pre-advance coefficient must not survive the head advance.
        assertTrue(correlation.getValue(2).isNaN());
    }

    @Test
    public void leaveLastBarUncached() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var smaIndicator = new SMAIndicator(new ClosePriceIndicator(barSeries), 5);
        assertNumEquals(4998.0, smaIndicator.getValue(barSeries.getEndIndex()));
        barSeries.getLastBar().addTrade(numOf(10), numOf(5));

        // (4996 + 4997 + 4998 + 4999 + 5) / 5
        assertNumEquals(3999, smaIndicator.getValue(barSeries.getEndIndex()));

    }

    @Test
    public void concurrentAccessCachesSingleComputationPerIndex() throws InterruptedException {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    indicator.getValue(4);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Concurrent tasks did not finish in time", done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("Only one calculation should be performed for the requested index despite concurrent access.", 1,
                indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheIsThreadSafeAcrossThreads() throws InterruptedException {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    indicator.getValue(endIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Concurrent tasks did not finish in time", done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("Only one calculation should be performed for the last bar despite concurrent access.", 1,
                indicator.getCalculationCount());

        // Mutate last bar to force invalidation and ensure a recomputation occurs
        barSeries.getLastBar().addTrade(numOf(1), numOf(7));
        indicator.getValue(endIndex);
        assertEquals("Mutation should trigger recomputation of last-bar cache.", 2, indicator.getCalculationCount());
    }

    @Test
    public void lastBarComputationDoesNotDeadlockWhenCacheWriteLockHeldAndAnotherLastBarComputationIsInFlight()
            throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();

        WriteLockedLastBarIndicator indicator = new WriteLockedLastBarIndicator(barSeries, endIndex);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Num> writeLockedFuture = executor.submit(() -> indicator.getValue(endIndex - 1));
            assertTrue("Write-locked calculation did not start in time",
                    indicator.writeLockedCalculationStarted.await(30, TimeUnit.SECONDS));

            Future<Num> lastBarFuture = executor.submit(() -> indicator.getValue(endIndex));
            assertTrue("Last-bar calculation did not start in time",
                    indicator.lastBarCalculationStarted.await(30, TimeUnit.SECONDS));

            assertNumEquals(endIndex - 1, writeLockedFuture.get(30, TimeUnit.SECONDS));
            assertNumEquals(endIndex, lastBarFuture.get(30, TimeUnit.SECONDS));
            assertTrue("Expected last-bar read to occur while holding cache write lock",
                    indicator.writeLockedDuringLastBarRead.get());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void lastBarCacheInvalidatesWhenLastBarIsReplacedDuringRead() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        CountDownLatch tradesReadStarted = new CountDownLatch(1);
        CountDownLatch allowTradesRead = new CountDownLatch(1);
        BlockingTradesBar blockingBar = new BlockingTradesBar(barSeries.barBuilder()
                .closePrice(1)
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .volume(0)
                .amount(0)
                .trades(0)
                .build(), tradesReadStarted, allowTradesRead);
        barSeries.addBar(blockingBar);

        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
        int endIndex = barSeries.getEndIndex();

        assertNumEquals(1, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        blockingBar.enableBlocking();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(endIndex));
            assertTrue("Expected last-bar cache read to start in time", tradesReadStarted.await(30, TimeUnit.SECONDS));

            barSeries.addBar(barSeries.barBuilder()
                    .closePrice(2)
                    .openPrice(2)
                    .highPrice(2)
                    .lowPrice(2)
                    .volume(0)
                    .amount(0)
                    .trades(0)
                    .build(), true);

            allowTradesRead.countDown();

            assertNumEquals(2, future.get(30, TimeUnit.SECONDS));
            assertEquals(2, indicator.getCalculationCount());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void highestResultIndexNotAdvancedWhenCalculationFails() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int failIndex = 1; // non-last index to avoid last-bar path
        FailingIndicator indicator = new FailingIndicator(barSeries, failIndex);

        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());

        try {
            indicator.getValue(failIndex);
            fail("Expected calculation to throw on first attempt");
        } catch (RuntimeException expected) {
            // expected path
        }

        // highestResultIndex should not advance when calculation fails
        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(-1, indicator.getCacheHighestResultIndex());
        assertEquals(1, indicator.getCalculationCount());

        // Next call should compute successfully and advance both trackers
        assertNumEquals(failIndex, indicator.getValue(failIndex));
        assertEquals(failIndex, indicator.getHighestResultIndex());
        assertEquals(failIndex, indicator.getCacheHighestResultIndex());
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheDoesNotGetStuckWhenCalculationFails() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();
        FailingIndicator indicator = new FailingIndicator(barSeries, endIndex);

        assertEquals(-1, indicator.getHighestResultIndex());

        try {
            indicator.getValue(endIndex);
            fail("Expected calculation to throw on first attempt");
        } catch (RuntimeException expected) {
            // expected path
        }

        assertEquals(-1, indicator.getHighestResultIndex());
        assertEquals(1, indicator.getCalculationCount());

        assertNumEquals(endIndex, indicator.getValue(endIndex));
        assertEquals(endIndex, indicator.getHighestResultIndex());
        assertEquals(2, indicator.getCalculationCount());

        indicator.getValue(endIndex);
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void invalidateFromCancelsInFlightLastBarComputation() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        int endIndex = barSeries.getEndIndex();

        BlockingLastBarIndicator indicator = new BlockingLastBarIndicator(barSeries, endIndex);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(endIndex));

            assertTrue("Last-bar calculation did not start in time",
                    indicator.lastBarCalculationStarted.await(30, TimeUnit.SECONDS));

            indicator.invalidateFrom(endIndex);
            indicator.allowLastBarCalculation.countDown();

            future.get(30, TimeUnit.SECONDS);
            assertEquals(1, indicator.getCalculationCount());

            // The in-flight computation must not repopulate the last-bar cache after
            // invalidation.
            assertEquals(-1, indicator.getHighestResultIndex());

            // Next read should recompute and then cache.
            indicator.getValue(endIndex);
            assertEquals(2, indicator.getCalculationCount());
            assertEquals(endIndex, indicator.getHighestResultIndex());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    public void highestResultIndexUpdatedWhenLastBarAccessedFirst() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();
        assertEquals(-1, indicator.getHighestResultIndex());

        // Access last bar first - should update highestResultIndex
        Num value = indicator.getValue(endIndex);
        assertNumEquals(endIndex, value);
        assertEquals("highestResultIndex should be updated when last bar is accessed first", endIndex,
                indicator.getHighestResultIndex());
    }

    @Test
    public void highestResultIndexNotDecreasedWhenEarlierIndexAccessedAfterLastBar() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Access last bar first - sets highestResultIndex to endIndex
        indicator.getValue(endIndex);
        assertEquals("highestResultIndex should be set to endIndex", endIndex, indicator.getHighestResultIndex());

        // Access an earlier index - should NOT decrease highestResultIndex
        // The cache's highestResultIndex might be smaller, but we should take the max
        int earlierIndex = 1;
        indicator.getValue(earlierIndex);

        // highestResultIndex should remain at endIndex (or higher), not decrease
        assertTrue("highestResultIndex should not decrease when accessing earlier index after last bar",
                indicator.getHighestResultIndex() >= endIndex);
    }

    @Test
    public void highestResultIndexNotDecreasedWhenInvalidateFromDoesNotAffectLastBarCache() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        TestIndicator indicator = new TestIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Access last bar first - sets highestResultIndex via last-bar cache
        indicator.getValue(endIndex);
        assertEquals(endIndex, indicator.getHighestResultIndex());

        // Invalidate from an index that does not affect the cached last-bar index.
        indicator.invalidateFrom(endIndex + 1);

        assertEquals(
                "highestResultIndex should remain at least at the last-bar cached index when last-bar cache remains valid",
                endIndex, indicator.getHighestResultIndex());
    }

    @Test
    public void invalidateFromDoesNotClearLastBarCacheWhenNotAffected() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Cache last bar
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());

        // Invalidate beyond endIndex; last-bar cache should remain valid
        indicator.invalidateFrom(endIndex + 1);

        // No recomputation expected
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());
    }

    @Test
    public void invalidateFromClearsLastBarCacheWhenAffected() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // Cache last bar
        indicator.getValue(endIndex);
        assertEquals(1, indicator.getCalculationCount());

        // Invalidate from endIndex; this must clear the last-bar cache
        indicator.invalidateFrom(endIndex);

        // Next read of the last bar must recompute
        indicator.getValue(endIndex);
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void invalidateCacheClearsAllValues() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        // Cached result should be reused for the same index.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        indicator.invalidateCache();

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(2));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromClearsTailOnly() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        indicator.getValue(2);
        assertThat(indicator.getCalculationCount()).isEqualTo(3);

        indicator.invalidateFrom(1);

        // Index 0 stays cached.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(3);

        // Indices 1 and 2 are recomputed.
        assertThat(indicator.getValue(1)).isEqualByComparingTo(numFactory.numOf(4));
        assertThat(indicator.getValue(2)).isEqualByComparingTo(numFactory.numOf(5));
        assertThat(indicator.getCalculationCount()).isEqualTo(5);
    }

    @Test
    public void invalidateFromNegativeClearsAll() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        assertThat(indicator.getCalculationCount()).isEqualTo(1);

        indicator.invalidateFrom(-1);

        // Cache should be fully cleared.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(2));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromBeyondHighestIsNoOp() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        assertThat(indicator.getCalculationCount()).isEqualTo(2);

        indicator.invalidateFrom(5);

        // Cached values remain intact.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(2);
    }

    @Test
    public void invalidateFromAtFirstCachedClearsAll() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.getValue(0);
        indicator.getValue(1);
        assertThat(indicator.getCalculationCount()).isEqualTo(2);

        indicator.invalidateFrom(0);

        // All cached values should be dropped.
        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(3));
        assertThat(indicator.getCalculationCount()).isEqualTo(3);
    }

    @Test
    public void invalidateFromOnEmptyCacheIsSafe() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        final var indicator = new CountingInvalidatableIndicator(series);

        indicator.invalidateFrom(1);

        assertThat(indicator.getValue(0)).isEqualByComparingTo(numFactory.numOf(1));
        assertThat(indicator.getCalculationCount()).isEqualTo(1);
    }

    @Test
    public void evictionWithSmallMaximumBarCountAndWrapAround() {
        // Test the O(1) eviction with a small maximumBarCount (3) and >10 bars
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .build();
        barSeries.setMaximumBarCount(3);

        CountingIndicator indicator = new CountingIndicator(barSeries);

        int startIndex = barSeries.getBeginIndex();
        int endIndex = barSeries.getEndIndex();
        for (int i = startIndex; i <= endIndex; i++) {
            Num value = indicator.getValue(i);
            assertNumEquals(i, value);
        }

        // Each cached index should be computed exactly once
        assertEquals(endIndex - startIndex + 1, indicator.getCalculationCount());

        // Reset counter to verify cache hits
        indicator.resetCalculationCount();

        // Access the remaining cached values (10, 11, 12) - should be cache hits
        for (int i = startIndex; i <= endIndex; i++) {
            assertNumEquals(i, indicator.getValue(i));
        }

        // No new calculations should have occurred for cached values
        assertEquals(0, indicator.getCalculationCount());
    }

    @Test
    public void lastBarCacheReusesValueWhenUnchanged() {
        // Create a series with mutable last bar
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingIndicator indicator = new CountingIndicator(barSeries);

        int endIndex = barSeries.getEndIndex();

        // First access to last bar should compute
        Num firstValue = indicator.getValue(endIndex);
        assertNumEquals(endIndex, firstValue);
        assertEquals(1, indicator.getCalculationCount());

        // Repeated access without bar mutation should reuse cached value
        Num secondValue = indicator.getValue(endIndex);
        assertNumEquals(endIndex, secondValue);
        assertEquals(1, indicator.getCalculationCount()); // No new computation
    }

    @Test
    public void lastBarCacheInvalidatesOnMutation() {
        // Create a series with mutable last bar
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();

        // Use an indicator that returns the close price to verify mutation detection
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 2);

        int endIndex = barSeries.getEndIndex();

        // First access: SMA of (2, 3) = 2.5
        Num firstValue = sma.getValue(endIndex);
        assertNumEquals(2.5, firstValue);

        // Mutate the last bar
        barSeries.getLastBar().addTrade(numOf(1), numOf(10)); // Close price changes to 10

        // Second access should detect mutation and recompute
        // SMA of (2, 10) = 6.0
        Num secondValue = sma.getValue(endIndex);
        assertNumEquals(6.0, secondValue);
    }

    @Test
    public void lastBarCacheInvalidatesOnReplace() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
        int endIndex = barSeries.getEndIndex();

        assertNumEquals(3, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        assertNumEquals(3, indicator.getValue(endIndex));
        assertEquals(1, indicator.getCalculationCount());

        barSeries.addBar(barSeries.barBuilder()
                .closePrice(10)
                .openPrice(10)
                .highPrice(10)
                .lowPrice(10)
                .volume(0)
                .amount(0)
                .trades(0)
                .build(), true);

        assertNumEquals(10, indicator.getValue(endIndex));
        assertEquals(2, indicator.getCalculationCount());
    }

    @Test
    public void recursiveCalculateDoesNotDeadlock() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        SelfReferencingIndicator indicator = new SelfReferencingIndicator(barSeries);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Num> future = executor.submit(() -> indicator.getValue(4));
            Num result = future.get(2, TimeUnit.SECONDS);
            assertNumEquals(5, result);
            assertEquals(5, indicator.getCalculationCount());
        } catch (TimeoutException e) {
            fail("getValue should not deadlock for recursive indicators");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void highestResultIndexNotAdvancedWhenLastBarAccessedRecursivelyWhileHoldingWriteLock() throws Exception {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();

        // Indicator that reads endIndex (last bar) from within calculate(endIndex-1)
        // while holding the cache write lock
        RecursiveLastBarAccessIndicator indicator = new RecursiveLastBarAccessIndicator(barSeries, endIndex);

        // Access a non-last bar to trigger the recursive last-bar access
        Num value = indicator.getValue(endIndex - 1);
        assertNumEquals(endIndex - 1, value);

        // The recursive last-bar access should NOT advance highestResultIndex because
        // snapshotInvalidationCount is -1 when the write lock is already held.
        // Only the outer calculation (endIndex - 1) should advance it.
        assertEquals("highestResultIndex should only reflect the outer calculation, not the recursive last-bar access",
                endIndex - 1, indicator.getHighestResultIndex());

        // Accessing the last bar normally should now update highestResultIndex
        indicator.getValue(endIndex);
        assertEquals("highestResultIndex should be updated after normal last-bar access", endIndex,
                indicator.getHighestResultIndex());
    }

    @Test
    public void lastBarWaitTimeoutDoesNotCauseIndefiniteBlock() throws Exception {
        // Test that a stuck last-bar computation doesn't block other threads
        // indefinitely. After the timeout, other threads should compute independently.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        int endIndex = barSeries.getEndIndex();
        long lastBarWaitTimeoutMs = 50;

        // Create an indicator that blocks forever in its first last-bar calculation
        CountDownLatch firstComputationStarted = new CountDownLatch(1);
        CountDownLatch blockForever = new CountDownLatch(1); // Never counted down

        NeverFinishingIndicator indicator = new NeverFinishingIndicator(barSeries, endIndex, firstComputationStarted,
                blockForever, lastBarWaitTimeoutMs);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Start first thread that will block forever
            executor.submit(() -> {
                try {
                    indicator.getValue(endIndex);
                } catch (Exception e) {
                    // Expected to eventually be interrupted
                }
            });

            // Wait for first computation to start
            assertTrue("First computation should start", firstComputationStarted.await(30, TimeUnit.SECONDS));

            // Start second thread that should timeout waiting and compute independently
            Future<Num> secondFuture = executor.submit(() -> indicator.getValue(endIndex));

            // The second thread should complete within a reasonable time (timeout +
            // computation)
            // even though the first thread is blocked forever
            try {
                Num result = secondFuture.get(1, TimeUnit.SECONDS);
                // Either gets a computed value or times out waiting - both are acceptable
                // The key is that it doesn't block forever
                assertNotNull("Second thread should get a result after timeout", result);
            } catch (TimeoutException e) {
                fail("Second thread should not block forever waiting for first computation");
            }

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void stressTestConcurrentLastBarAccess() throws InterruptedException {
        // Stress test: multiple threads concurrently accessing the last bar.
        // Moderate concurrency is sufficient to expose race conditions.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = new CountingIndicator(barSeries);
        int endIndex = barSeries.getEndIndex();

        int threads = 8;
        int iterationsPerThread = 100;
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        Num value = indicator.getValue(endIndex);
                        if (value != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Stress test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // All reads should succeed
        assertEquals("All reads should succeed", threads * iterationsPerThread, successCount.get());

        // Last bar should only be computed once (subsequent reads use cache)
        assertEquals("Last bar should be computed exactly once", 1, indicator.getCalculationCount());
    }

    @Test
    public void stressTestConcurrentLastBarWithMutations() throws InterruptedException {
        // Stress test: concurrent reads with periodic mutations.
        // Moderate iteration count catches race conditions without excessive runtime.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        CountingIndicator indicator = CountingIndicator.closePrice(barSeries);
        int endIndex = barSeries.getEndIndex();

        int readers = 8;
        int iterations = 50;
        int minimumReads = 100;
        AtomicInteger totalReads = new AtomicInteger(0);
        AtomicBoolean mutationsDone = new AtomicBoolean(false);
        CountDownLatch readsObserved = new CountDownLatch(minimumReads);

        ExecutorService executor = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch ready = new CountDownLatch(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers + 1);

        // Reader threads
        for (int r = 0; r < readers; r++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    while (!mutationsDone.get()) {
                        indicator.getValue(endIndex);
                        int readCount = totalReads.incrementAndGet();
                        if (readCount <= minimumReads) {
                            readsObserved.countDown();
                        }
                        Thread.yield();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Mutator thread
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    barSeries.getLastBar().addTrade(numOf(1), numOf(i + 10));
                    Thread.sleep(1);
                }
                readsObserved.await(5, TimeUnit.SECONDS);
                mutationsDone.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        ready.await();
        start.countDown();
        assertTrue("Stress test with mutations did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Should have performed many reads
        assertTrue("Should have performed many concurrent reads", totalReads.get() >= minimumReads);

        // Each mutation should trigger a recomputation
        assertTrue("Should have recomputed after mutations", indicator.getCalculationCount() > 1);
    }

    private final static class SelfRecursiveKeepingIndicator extends CachedIndicator<Num> {

        private final ClosePriceIndicator close;

        private SelfRecursiveKeepingIndicator(BarSeries series) {
            super(series);
            this.close = new ClosePriceIndicator(series);
        }

        @Override
        protected Num calculate(int index) {
            if (index == 0) {
                return close.getValue(0);
            }
            return getValue(index - 1).plus(close.getValue(index));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private final static class HeadAdvanceDiscardingIndicator extends CachedIndicator<Num> {

        private final int unstableBars;

        private HeadAdvanceDiscardingIndicator(BarSeries series) {
            this(series, 0);
        }

        private HeadAdvanceDiscardingIndicator(BarSeries series, int unstableBars) {
            super(series);
            this.unstableBars = unstableBars;
        }

        @Override
        protected Num calculate(int index) {
            // Values depend only on the retained window: zero at the begin
            // index, then one more per step.
            int beginIndex = getBarSeries().getBeginIndex();
            if (index <= beginIndex) {
                return getBarSeries().numFactory().zero();
            }
            return getValue(index - 1).plus(getBarSeries().numFactory().one());
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
            return true;
        }
    }

    private final static class FailingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();
        private final AtomicBoolean failFirst = new AtomicBoolean(true);
        private final int failIndex;

        private FailingIndicator(BarSeries series, int failIndex) {
            super(series);
            this.failIndex = failIndex;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            if (index == failIndex && failFirst.compareAndSet(true, false)) {
                throw new RuntimeException("boom");
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }

        private int getHighestResultIndex() {
            return highestResultIndex;
        }

        private int getCacheHighestResultIndex() {
            return getCache().getHighestResultIndex();
        }
    }

    private final static class BlockingLastBarIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();
        private final CountDownLatch lastBarCalculationStarted = new CountDownLatch(1);
        private final CountDownLatch allowLastBarCalculation = new CountDownLatch(1);
        private final int endIndex;

        private BlockingLastBarIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            int count = calculations.incrementAndGet();
            if (index == endIndex) {
                lastBarCalculationStarted.countDown();
                try {
                    assertTrue("Last-bar calculation was not allowed to proceed in time",
                            allowLastBarCalculation.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return getBarSeries().numFactory().numOf(count);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getCalculationCount() {
            return calculations.get();
        }

        private int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private final static class WriteLockedLastBarIndicator extends CachedIndicator<Num> {

        private final AtomicBoolean writeLockedDuringLastBarRead = new AtomicBoolean();
        private final CountDownLatch lastBarCalculationStarted = new CountDownLatch(1);
        private final CountDownLatch writeLockedCalculationStarted = new CountDownLatch(1);
        private final int endIndex;

        private WriteLockedLastBarIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == endIndex) {
                lastBarCalculationStarted.countDown();
                try {
                    assertTrue("Write-locked calculation did not start in time",
                            writeLockedCalculationStarted.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return getValue(0).plus(getBarSeries().numFactory().numOf(index));
            }

            if (index == endIndex - 1) {
                writeLockedCalculationStarted.countDown();
                try {
                    assertTrue("Last-bar calculation did not start in time",
                            lastBarCalculationStarted.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                writeLockedDuringLastBarRead.set(getCache().isWriteLockedByCurrentThread());
                assertNotNull(getValue(endIndex));
                return getBarSeries().numFactory().numOf(index);
            }

            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private final static class CountingInvalidatableIndicator extends CachedIndicator<Num> {

        private int calculationCount = 0;

        private CountingInvalidatableIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount++;
            return getBarSeries().numFactory().numOf(calculationCount);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getCalculationCount() {
            return calculationCount;
        }
    }

    private final static class SelfReferencingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculationCount = new AtomicInteger();

        private SelfReferencingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount.incrementAndGet();
            if (index == 0) {
                return getBarSeries().numFactory().one();
            }
            return getValue(index - 1).plus(getBarSeries().numFactory().one());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getCalculationCount() {
            return calculationCount.get();
        }
    }

    private static final class CumulativeRecursiveIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> source;

        private CumulativeRecursiveIndicator(Indicator<Num> source) {
            super(source);
            this.source = source;
        }

        @Override
        protected Num calculate(int index) {
            if (index == 0) {
                return source.getValue(0);
            }
            return getValue(index - 1).plus(source.getValue(index));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 3;
        }
    }

    private final static class TestIndicator extends CachedIndicator<Num> {

        private TestIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private static final class RacingSnapshotSeries extends BaseBarSeries {

        private final AtomicBoolean blockNextSnapshot = new AtomicBoolean();
        private final CountDownLatch snapshotBlocked = new CountDownLatch(1);
        private final CountDownLatch releaseSnapshot = new CountDownLatch(1);
        private volatile int snapshotMaximumBarCount = Integer.MAX_VALUE;
        private volatile int blockTriggerMaximumBarCount = -1;

        private RacingSnapshotSeries(List<Bar> bars) {
            super("racing-snapshot", bars);
        }

        private void prepareBlockedSnapshot(int maximumBarCount) {
            snapshotMaximumBarCount = maximumBarCount;
            blockTriggerMaximumBarCount = maximumBarCount;
            blockNextSnapshot.set(true);
        }

        private boolean awaitBlockedSnapshot() throws InterruptedException {
            return snapshotBlocked.await(30, TimeUnit.SECONDS);
        }

        private void releaseBlockedSnapshot() {
            releaseSnapshot.countDown();
        }

        private void setSnapshotMaximumBarCount(int maximumBarCount) {
            snapshotMaximumBarCount = maximumBarCount;
        }

        @Override
        public BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(long sinceRevision) {
            BarSeriesChangeSnapshot snapshot = super.getBarSeriesChangeSnapshot(sinceRevision);
            int maximumBarCount = snapshotMaximumBarCount;
            if (maximumBarCount == blockTriggerMaximumBarCount && blockNextSnapshot.compareAndSet(true, false)) {
                snapshotBlocked.countDown();
                try {
                    assertTrue("Timed out waiting to release older snapshot",
                            releaseSnapshot.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted while waiting to release older snapshot");
                }
            }
            return new BarSeriesChangeSnapshot(snapshot.revision(), snapshot.earliestChangedIndex(),
                    snapshot.removedThroughIndex(), maximumBarCount, snapshot.endIndex());
        }
    }

    private static final class FirstBarReadingIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculationCount = new AtomicInteger();

        private FirstBarReadingIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            calculationCount.incrementAndGet();
            return getBarSeries().getBar(index).getClosePrice();
        }

        int getCalculationCount() {
            return calculationCount.get();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private final static class RecursiveLastBarAccessIndicator extends CachedIndicator<Num> {

        private final int endIndex;

        private RecursiveLastBarAccessIndicator(BarSeries series, int endIndex) {
            super(series);
            this.endIndex = endIndex;
        }

        @Override
        protected Num calculate(int index) {
            if (index == endIndex - 1) {
                // While holding the cache write lock for (endIndex - 1), access the last bar.
                // This triggers the code path where isWriteLockedByCurrentThread() returns
                // true, causing snapshotInvalidationCount = -1.
                getValue(endIndex);
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        int getHighestResultIndex() {
            return highestResultIndex;
        }
    }

    private final static class BlockingTradesBar implements Bar {

        private final AtomicBoolean blockingEnabled = new AtomicBoolean();
        private final AtomicBoolean blocked = new AtomicBoolean();
        private final Bar delegate;
        private final CountDownLatch tradesReadStarted;
        private final CountDownLatch allowTradesRead;

        private BlockingTradesBar(Bar delegate, CountDownLatch tradesReadStarted, CountDownLatch allowTradesRead) {
            this.delegate = delegate;
            this.tradesReadStarted = tradesReadStarted;
            this.allowTradesRead = allowTradesRead;
        }

        private void enableBlocking() {
            blockingEnabled.set(true);
        }

        @Override
        public Duration getTimePeriod() {
            return delegate.getTimePeriod();
        }

        @Override
        public Instant getBeginTime() {
            return delegate.getBeginTime();
        }

        @Override
        public Instant getEndTime() {
            return delegate.getEndTime();
        }

        @Override
        public Num getOpenPrice() {
            return delegate.getOpenPrice();
        }

        @Override
        public Num getHighPrice() {
            return delegate.getHighPrice();
        }

        @Override
        public Num getLowPrice() {
            return delegate.getLowPrice();
        }

        @Override
        public Num getClosePrice() {
            return delegate.getClosePrice();
        }

        @Override
        public Num getVolume() {
            return delegate.getVolume();
        }

        @Override
        public Num getAmount() {
            return delegate.getAmount();
        }

        @Override
        public long getTrades() {
            if (blockingEnabled.get() && blocked.compareAndSet(false, true)) {
                tradesReadStarted.countDown();
                try {
                    assertTrue("Timed out waiting to allow getTrades to proceed",
                            allowTradesRead.await(30, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.getTrades();
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            delegate.addTrade(tradeVolume, tradePrice);
        }

        @Override
        public void addPrice(Num price) {
            delegate.addPrice(price);
        }
    }

    private static final class NeverFinishingIndicator extends CachedIndicator<Num> {

        private final CountDownLatch computationStarted;
        private final CountDownLatch blockLatch;
        private final int targetIndex;
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        private NeverFinishingIndicator(BarSeries series, int targetIndex, CountDownLatch computationStarted,
                CountDownLatch blockLatch, long lastBarWaitTimeoutMs) {
            super(series, lastBarWaitTimeoutMs);
            this.targetIndex = targetIndex;
            this.computationStarted = computationStarted;
            this.blockLatch = blockLatch;
        }

        @Override
        protected Num calculate(int index) {
            if (index == targetIndex && firstCall.compareAndSet(true, false)) {
                computationStarted.countDown();
                try {
                    // Block forever (or until interrupted)
                    blockLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return getBarSeries().numFactory().numOf(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class CountingDependencyIndicator extends AbstractIndicator<Num> {

        private final List<Indicator<?>> dependencies;
        private int dependenciesInvocations;

        private CountingDependencyIndicator(BarSeries series, List<Indicator<?>> dependencies) {
            super(series);
            this.dependencies = dependencies;
        }

        @Override
        public Num getValue(int index) {
            return NaN.NaN;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public List<Indicator<?>> getDependencies() {
            dependenciesInvocations++;
            return dependencies;
        }

        private int getDependenciesInvocations() {
            return dependenciesInvocations;
        }

        private void resetDependenciesInvocations() {
            dependenciesInvocations = 0;
        }
    }

    @Test(timeout = 5000)
    public void sharedDependencySubgraphReconciliationStaysBounded() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0d, 50d, 50d, 50d, 50d)
                .build();
        // A diamond of non-cached wrappers: every node feeds both inputs of the
        // next, so the reconciliation graph has 2^30 paths, all reaching the
        // same non-rebaselining close price. Reconciling the dependent after a
        // head advance must visit each node once instead of once per path.
        Indicator<Num> level = BinaryOperationIndicator.difference(new ClosePriceIndicator(barSeries),
                new ClosePriceIndicator(barSeries));
        for (int i = 1; i < 30; i++) {
            level = BinaryOperationIndicator.difference(level, level);
        }
        // The dependent registers the diamond as its source but never evaluates
        // it, so the reconciliation walk is the only cost of the read.
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(level) {
            @Override
            protected Num calculate(int index) {
                return NaN.NaN;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        assertTrue(dependent.getValue(3).isNaN());

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        // Reconciliation must visit every wrapper once instead of once per path.
        assertTrue(dependent.getValue(3).isNaN());
    }

    @Test
    public void reconciliationSharesOneVisitedSetAcrossAllSources() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingDependencyIndicator shared = new CountingDependencyIndicator(barSeries, List.of());
        CountingDependencyIndicator firstSource = new CountingDependencyIndicator(barSeries, List.of(shared));
        CountingDependencyIndicator secondSource = new CountingDependencyIndicator(barSeries, List.of(shared));
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(firstSource, secondSource) {
            @Override
            protected Num calculate(int index) {
                return NaN.NaN;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        // Construction-time dependency observation also traverses the graph; only
        // the reconciliation walk must share one visited set.
        shared.resetDependenciesInvocations();
        assertEquals(0, dependent.minimumCacheableIndexAfterHeadAdvance(0));
        // The shared node is inspected once across both sources instead of once
        // per source.
        assertEquals(1, shared.getDependenciesInvocations());
    }

    @Test
    public void cachedDependencyPathsShareOneVisitedSet() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        CountingDependencyIndicator shared = new CountingDependencyIndicator(barSeries, List.of());
        SMAIndicator sharedCached = new SMAIndicator(shared, 1);
        SMAIndicator firstSource = new SMAIndicator(sharedCached, 1);
        SMAIndicator secondSource = new SMAIndicator(sharedCached, 1);
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(firstSource, secondSource) {
            @Override
            protected Num calculate(int index) {
                return NaN.NaN;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        shared.resetDependenciesInvocations();

        assertEquals(0, dependent.minimumCacheableIndexAfterHeadAdvance(0));
        assertEquals(1, shared.getDependenciesInvocations());
    }

    @Test
    public void propagatesExplicitFullInvalidationWhenDefaultFloorSaturates() {
        HeadAdvanceDiscardingIndicator source = new HeadAdvanceDiscardingIndicator(series, Integer.MAX_VALUE);
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(source) {
            @Override
            protected Num calculate(int index) {
                return NaN.NaN;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };

        assertEquals(Integer.MAX_VALUE, dependent.minimumCacheableIndexAfterHeadAdvance(Integer.MAX_VALUE - 1));
    }

    @Test
    public void multipleSourceConstructorRejectsDifferentSeries() {
        BarSeries otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d).build();
        ClosePriceIndicator source = new ClosePriceIndicator(series);
        ClosePriceIndicator otherSource = new ClosePriceIndicator(otherSeries);

        assertThrows(IllegalArgumentException.class, () -> new CachedIndicator<Num>(source, otherSource) {
            @Override
            protected Num calculate(int index) {
                return NaN.NaN;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        });
    }

    @Test
    public void trueRangeIndicatorRegistersEveryInputForRebaselinePropagation() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(60).lowPrice(-1).add();
        for (int i = 0; i < 4; i++) {
            barSeries.barBuilder().openPrice(50).closePrice(50).highPrice(60).lowPrice(49).add();
        }
        HighPriceIndicator high = new HighPriceIndicator(barSeries);
        StochasticIndicator low = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        ClosePriceIndicator close = new ClosePriceIndicator(barSeries);
        TRIndicator trueRange = new TRIndicator(high, low, close);

        // Warm the cache: the stochastic low is 100 throughout the flat tail.
        assertNumEquals(50, trueRange.getValue(4));

        barSeries.setMaximumBarCount(3);
        assertEquals(2, barSeries.getBeginIndex());

        // The flat retained tail rebases the stochastic to zero. The cached
        // true range at the far end must follow instead of serving the stale
        // high-source band value.
        assertNumEquals(60, trueRange.getValue(4));
    }

    @Test
    public void dependentDiscardsWholeCacheWhenSourceRebaselinesBeyondDefaultBand() {
        // The true range evicts its stale first-retained entry after a head
        // advance, applying a floor above its own default unstable-range band.
        // A dependent cannot bound the taint propagation with a finite floor
        // (its read depth below each cached index is unknown), so it must
        // discard its whole cache and rebuild against the retained window.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        TRIndicator trueRange = new TRIndicator(barSeries);
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(trueRange) {
            @Override
            protected Num calculate(int index) {
                return trueRange.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };

        assertEquals(Integer.MAX_VALUE, dependent.minimumCacheableIndexAfterHeadAdvance(1));
    }

    @Test
    public void vidyaReadsFarRetainedTailIterativelyWhenSeriesHeadAdvances() {
        assertFarRetainedTailRecomputesWithoutRecursing(series -> {
            StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 14);
            return new VIDYAIndicator(stochastic, 20, 10);
        }, TestUtils::assertNumEquals);
    }

    @Test
    public void wildersMaReadsFarRetainedTailIterativelyWhenSeriesHeadAdvances() {
        assertFarRetainedTailRecomputesWithoutRecursing(series -> {
            StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(series), 14);
            return new WildersMAIndicator(stochastic, 14);
        }, TestUtils::assertNumEquals);
    }

    @Test
    public void zigZagStateReadsFarRetainedTailIterativelyWhenSeriesHeadAdvances() {
        assertFarRetainedTailRecomputesWithoutRecursing(series -> {
            Indicator<Num> price = new ClosePriceIndicator(series);
            StochasticIndicator reversalAmount = new StochasticIndicator(price, 14);
            return new ZigZagStateIndicator(price, price, price, reversalAmount);
        }, (fresh, cached) -> {
            assertEquals(fresh.getTrend(), cached.getTrend());
            assertEquals(fresh.getLastHighIndex(), cached.getLastHighIndex());
            assertEquals(fresh.getLastHighPrice(), cached.getLastHighPrice());
            assertEquals(fresh.getLastLowIndex(), cached.getLastLowIndex());
            assertEquals(fresh.getLastLowPrice(), cached.getLastLowPrice());
            assertEquals(fresh.getLastExtremeIndex(), cached.getLastExtremeIndex());
            assertEquals(fresh.getLastExtremePrice(), cached.getLastExtremePrice());
        });
    }

    @Test
    public void klingerRebuildsDailyMeasurementsAfterSeriesHeadAdvance() {
        final int totalBars = 5101;
        final int retainedBars = 3000;
        BarSeries barSeries = flatTailSeries(totalBars, 500);
        StochasticIndicator stochasticLow = new StochasticIndicator(new ClosePriceIndicator(barSeries), 14);
        KlingerVolumeOscillatorIndicator klinger = new KlingerVolumeOscillatorIndicator(
                new HighPriceIndicator(barSeries), stochasticLow, new ClosePriceIndicator(barSeries),
                new VolumeIndicator(barSeries), 2, 3, 1);

        // Warm the whole chain over the unbounded series. The flat plateau pins the
        // stochastic at 100 through its zero-range recursion, so every far-tail
        // daily measurement caches `high - 100`.
        klinger.getValue(barSeries.getEndIndex());

        // Head advance: the plateau extends below the retained head, so the
        // stochastic's zero-range recursion bottoms out at the retained head and
        // rebases the whole flat tail to zero. Cached daily measurements beyond
        // the unstable band were computed from the pre-advance stochastic and
        // must not survive.
        barSeries.setMaximumBarCount(retainedBars);
        assertEquals(totalBars - retainedBars, barSeries.getBeginIndex());
        barSeries.barBuilder()
                .openPrice(flatPlateauClose(500))
                .closePrice(flatPlateauClose(500))
                .highPrice(flatPlateauClose(500) + 1d)
                .lowPrice(flatPlateauClose(500) - 1d)
                .volume(1000d + totalBars - 1)
                .add();
        assertEquals(totalBars - retainedBars + 1, barSeries.getBeginIndex());

        StochasticIndicator freshStochastic = new StochasticIndicator(new ClosePriceIndicator(barSeries), 14);
        KlingerVolumeOscillatorIndicator freshKlinger = new KlingerVolumeOscillatorIndicator(
                new HighPriceIndicator(barSeries), freshStochastic, new ClosePriceIndicator(barSeries),
                new VolumeIndicator(barSeries), 2, 3, 1);
        Num freshValue = freshKlinger.getValue(barSeries.getEndIndex());
        assertTrue(Num.isFinite(freshValue));
        assertNumEquals(freshValue, klinger.getValue(barSeries.getEndIndex()));
    }

    @Test
    public void edmaReadsRebasedSourceLiveAfterSeriesHeadAdvance() {
        final int totalBars = 5101;
        final int retainedBars = 3000;
        BarSeries barSeries = flatTailSeries(totalBars, 500);
        StochasticIndicator stochastic = new StochasticIndicator(new ClosePriceIndicator(barSeries), 14);
        EDMAIndicator edma = new EDMAIndicator(stochastic, 9, 2);

        // Warm the cache over the unbounded series.
        edma.getValue(barSeries.getEndIndex());

        // Head advance: the stochastic rebases the whole flat tail to zero, so the
        // pre-advance displaced EMA snapshot is stale everywhere. The displaced
        // cache must be rebuilt live instead of serving the materialized results.
        barSeries.setMaximumBarCount(retainedBars);
        assertEquals(totalBars - retainedBars, barSeries.getBeginIndex());
        barSeries.barBuilder()
                .openPrice(flatPlateauClose(500))
                .closePrice(flatPlateauClose(500))
                .highPrice(flatPlateauClose(500) + 1d)
                .lowPrice(flatPlateauClose(500) - 1d)
                .add();
        assertEquals(totalBars - retainedBars + 1, barSeries.getBeginIndex());

        StochasticIndicator freshStochastic = new StochasticIndicator(new ClosePriceIndicator(barSeries), 14);
        EDMAIndicator freshEdma = new EDMAIndicator(freshStochastic, 9, 2);
        Num freshValue = freshEdma.getValue(barSeries.getEndIndex());
        assertTrue(Num.isFinite(freshValue));
        assertNumEquals(freshValue, edma.getValue(barSeries.getEndIndex()));
    }

    @Test
    public void vortexReadsRebasedSourceLiveAfterSeriesHeadAdvance() {
        // Nine bars: the close rises once and then plateaus, so the stochastic
        // low pins at 100 before the head advance and rebases the whole
        // retained tail to zero after it. The highs keep rising, so the stale
        // and fresh vortex windows differ: the cached vortex must follow the
        // rebased source instead of serving the pre-advance materialization.
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 9; i++) {
            barSeries.barBuilder()
                    .openPrice(i == 0 ? 0d : 50d)
                    .closePrice(i == 0 ? 0d : 50d)
                    .highPrice(51d + i)
                    .lowPrice(i == 0 ? 0d : 49d)
                    .add();
        }
        StochasticIndicator stochasticLow = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        VortexIndicator vortex = new VortexIndicator(new HighPriceIndicator(barSeries), stochasticLow,
                new ClosePriceIndicator(barSeries), 2);

        // Warm the cache over the unbounded series.
        vortex.getValue(8);

        barSeries.setMaximumBarCount(5);
        assertEquals(4, barSeries.getBeginIndex());

        StochasticIndicator freshStochastic = new StochasticIndicator(new ClosePriceIndicator(barSeries), 3);
        VortexIndicator freshVortex = new VortexIndicator(new HighPriceIndicator(barSeries), freshStochastic,
                new ClosePriceIndicator(barSeries), 2);
        Num freshValue = freshVortex.getValue(8);
        assertTrue(Num.isFinite(freshValue));
        assertNumEquals(freshValue, vortex.getValue(8));
    }

    @Test
    public void volumeIndicatorRecomputesRetainedWindowAfterSeriesHeadAdvance() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        barSeries.setMaximumBarCount(3);
        for (int i = 0; i < 3; i++) {
            barSeries.barBuilder().openPrice(1d).closePrice(1d).highPrice(2d).lowPrice(0.5d).volume(i + 1).add();
        }
        VolumeIndicator volume = new VolumeIndicator(barSeries, 3);
        assertNumEquals(3, volume.getValue(1));
        assertNumEquals(6, volume.getValue(2));

        // Appending a fourth bar advances the head past index 0. The cached
        // window sums include the evicted volume, so the retained window must
        // recompute them from the remaining bars.
        barSeries.barBuilder().openPrice(1d).closePrice(1d).highPrice(2d).lowPrice(0.5d).volume(4d).add();
        assertEquals(1, barSeries.getBeginIndex());
        assertNumEquals(2, volume.getValue(1));
        assertNumEquals(5, volume.getValue(2));
    }

    @Test
    public void pearsonCorrelationRecomputesRetainedWindowAfterSeriesHeadAdvance() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        barSeries.setMaximumBarCount(3);
        double[] closes = { 100d, 101d, 105d };
        double[] volumes = { 1d, 2d, 3d };
        for (int i = 0; i < 3; i++) {
            barSeries.barBuilder()
                    .openPrice(closes[i])
                    .closePrice(closes[i])
                    .highPrice(closes[i] + 1d)
                    .lowPrice(closes[i] - 1d)
                    .volume(volumes[i])
                    .add();
        }
        PearsonCorrelationIndicator correlation = new PearsonCorrelationIndicator(new ClosePriceIndicator(barSeries),
                new VolumeIndicator(barSeries, 3), 3);
        // The closes [100, 101, 105] against volumes [1, 2, 3] are not linear.
        Num before = correlation.getValue(2);
        assertTrue(before.isLessThan(numFactory.one()));

        // Appending a fourth bar advances the head past index 0. Index 2's
        // window shrinks to the perfectly linear pair [101, 105] vs [2, 3], so
        // the cached pre-advance correlation must not survive.
        barSeries.barBuilder().openPrice(109d).closePrice(109d).highPrice(110d).lowPrice(108d).volume(4d).add();
        assertEquals(1, barSeries.getBeginIndex());
        assertNumEquals(1, correlation.getValue(2));
    }

    @Test
    public void cachedIndicatorExposesRegisteredSourcesViaDependencyApi() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(barSeries);
        assertEquals(List.of(close), new EMAIndicator(close, 3).getDependencies());

        HighPriceIndicator high = new HighPriceIndicator(barSeries);
        LowPriceIndicator low = new LowPriceIndicator(barSeries);
        assertEquals(List.of(high, low, close), new TRIndicator(high, low, close).getDependencies());

        CachedIndicator<Num> seriesOnly = new CachedIndicator<Num>(barSeries) {
            @Override
            protected Num calculate(int index) {
                return barSeries.getBar(index).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        assertEquals(List.of(), seriesOnly.getDependencies());
    }

    private BarSeries flatTailSeries(final int totalBars, final int plateauStart) {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < totalBars; i++) {
            final double close = i < plateauStart ? 100d + i * 0.1d : flatPlateauClose(plateauStart);
            barSeries.barBuilder()
                    .openPrice(close)
                    .closePrice(close)
                    .highPrice(close + 1d)
                    .lowPrice(close - 1d)
                    .volume(1000d + i)
                    .add();
        }
        return barSeries;
    }

    private static double flatPlateauClose(final int plateauStart) {
        return 100d + (plateauStart - 1) * 0.1d;
    }

    private <T> void assertFarRetainedTailRecomputesWithoutRecursing(
            java.util.function.Function<BarSeries, Indicator<T>> indicatorFactory,
            java.util.function.BiConsumer<T, T> verifier) {
        final int retainedBars = 20_000;
        final int totalBars = 2 * retainedBars + 1;
        double[] closes = new double[totalBars];
        for (int i = 0; i < totalBars; i++) {
            closes[i] = 100d + i * 0.1d;
        }
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(Arrays.copyOf(closes, totalBars - 1))
                .build();

        Indicator<T> indicator = indicatorFactory.apply(barSeries);
        indicator.getValue(barSeries.getEndIndex()); // warm the cache over the full series

        // Head advance: the retained window keeps only the last 20k bars, so the
        // far tail must be recomputed through a cache whose sources rebased.
        barSeries.setMaximumBarCount(retainedBars);
        assertEquals(retainedBars, barSeries.getBeginIndex());
        barSeries.barBuilder()
                .openPrice(closes[totalBars - 1])
                .closePrice(closes[totalBars - 1])
                .highPrice(closes[totalBars - 1])
                .lowPrice(closes[totalBars - 1])
                .add();
        assertEquals(retainedBars + 1, barSeries.getBeginIndex());

        Indicator<T> freshIndicator = indicatorFactory.apply(barSeries);
        verifier.accept(freshIndicator.getValue(barSeries.getEndIndex()), indicator.getValue(barSeries.getEndIndex()));
    }

    /**
     * A dependency backed by a different series must be reconciled through
     * dependency observation: advancing seriesB's head changes the dependency's
     * values while seriesA never changes, so the dependent's cached results must
     * still be dropped and recomputed from the dependency's new state.
     */
    @Test
    public void crossSeriesDependencyHeadAdvanceInvalidatesDependentCache() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d, 40d, 50d)
                .build();
        RemovedBarsAwareDependency dependency = new RemovedBarsAwareDependency(seriesB);
        CrossSeriesDependentIndicator dependent = new CrossSeriesDependentIndicator(seriesA, dependency);

        Num before = dependent.getValue(1);
        assertNumEquals("22", before); // close(1) + dependency value at 1 with removedBarsCount 0
        assertEquals(1, dependency.calculations());

        seriesB.setMaximumBarCount(4); // head advances, removedBarsCount becomes 1
        Num after = dependent.getValue(1);

        assertNumEquals("23", after); // close(1) + dependency value at 1 with removedBarsCount 1
        assertEquals(2, dependency.calculations());
    }

    @Test
    public void sameSeriesDependencyViewKeepsRetainedCacheAfterHeadAdvance() {
        BaseBarSeries barSeries = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        Indicator<Num> close = new ClosePriceIndicator(barSeries);
        AtomicInteger calculations = new AtomicInteger();
        CachedIndicator<Num> dependent = new CachedIndicator<Num>(barSeries, close) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                calculations.incrementAndGet();
                return close.getValue(index);
            }
        };

        assertNumEquals("3", dependent.getValue(2));
        assertEquals(1, calculations.get());

        barSeries.setMaximumBarCount(4);

        assertNumEquals("3", dependent.getValue(2));
        assertEquals(1, calculations.get());
    }

    /**
     * A historical mutation in a cross-series dependency invalidates dependent
     * cache entries at and above the mutated index even though neither series
     * advances its head.
     */
    @Test
    public void crossSeriesDependencyMutationInvalidatesDependentCache() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d, 40d, 50d)
                .build();
        RemovedBarsAwareDependency dependency = new RemovedBarsAwareDependency(seriesB);
        CrossSeriesDependentIndicator dependent = new CrossSeriesDependentIndicator(seriesA, dependency);

        Num before = dependent.getValue(1);
        assertNumEquals("22", before);

        Bar replaced = new MockBarBuilder(seriesB.numFactory()).openPrice(100d)
                .highPrice(100d)
                .lowPrice(100d)
                .closePrice(100d)
                .build();
        seriesB.replaceBar(1, replaced);
        Num after = dependent.getValue(1);

        assertNumEquals("102", after); // close(1) + dependency value at 1 with removedBarsCount 0
        assertEquals(2, dependency.calculations());
    }

    /**
     * A cross-series dependency reached only through an intermediate indicator
     * backed by the consumer's own series must still be observed: the EMA consumes
     * a distance indicator on seriesA whose moving average reads seriesB, so
     * advancing seriesB alone must recompute the cached EMA values.
     */
    @Test
    public void nestedCrossSeriesDependencyHeadAdvanceInvalidatesDependentCache() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d, 40d, 50d, 60d)
                .build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 2));
        EMAIndicator ema = new EMAIndicator(distance, 2);
        Num before2 = ema.getValue(2);
        Num before3 = ema.getValue(3);
        seriesB.setMaximumBarCount(4); // head advances; the EMA never reads seriesB directly
        Num after2 = ema.getValue(2);
        Num after3 = ema.getValue(3);

        DistanceFromMAIndicator freshDistance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 2));
        EMAIndicator freshEma = new EMAIndicator(freshDistance, 2);
        assertNumEquals(freshEma.getValue(2), after2);
        assertNumEquals(freshEma.getValue(3), after3);
        assertThat(after2).isNotEqualTo(before2);
        assertThat(after3).isNotEqualTo(before3);
    }

    /**
     * The direct cross-series registration itself must reconcile the dependency:
     * advancing the moving average's series rebuilds the cached distance values
     * even though the distance indicator's own series never changes.
     */
    @Test
    public void distanceFromMovingAverageRecomputesWhenMovingAverageSeriesAdvances() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d, 40d, 50d, 60d)
                .build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 2));

        Num before = distance.getValue(2);
        seriesB.setMaximumBarCount(4); // moving-average series advances alone
        Num after = distance.getValue(2);

        DistanceFromMAIndicator freshDistance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 2));
        assertNumEquals(freshDistance.getValue(2), after);
        assertThat(after).isNotEqualTo(before);
    }

    /**
     * A dependency's mutable last bar does not publish a structural series change.
     * Its mutation must still invalidate a cached consumer value that is historical
     * in the consumer's own series.
     */
    @Test
    public void distanceFromMovingAverageRecomputesWhenMovingAverageLastBarMutates() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d)
                .build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 1));

        Num before = distance.getValue(2);
        seriesB.getLastBar().addPrice(numOf(300));
        Num after = distance.getValue(2);

        DistanceFromMAIndicator freshDistance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 1));
        assertNumEquals(freshDistance.getValue(2), after);
        assertThat(after).isNotEqualTo(before);
    }

    /**
     * Multi-input indicators must register every input as a logical source;
     * constructed from the shared series alone, a source that discards its whole
     * cache on head advance cannot propagate the full-invalidation floor through
     * the composed indicator.
     */
    @Test
    public void zScorePropagatesSourceFullInvalidationAfterHeadAdvance() {
        HeadAdvanceDiscardingIndicator deviation = new HeadAdvanceDiscardingIndicator(series, 3);
        HeadAdvanceDiscardingIndicator standardDeviation = new HeadAdvanceDiscardingIndicator(series, 3);
        ZScoreIndicator zScore = new ZScoreIndicator(deviation, standardDeviation);

        assertEquals(Integer.MAX_VALUE, zScore.minimumCacheableIndexAfterHeadAdvance(1));
    }

    /**
     * Restoring a dependency's close after an intrabar move still leaves the high
     * or low permanently changed. The last-bar fingerprint must cover the full
     * mutable OHLCV/amount state so an extrema consumer recomputes.
     */
    @Test
    public void distanceFromMovingAverageRecomputesWhenCloseRestoredButExtremaChanged() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d)
                .build();
        Bar last = seriesB.getLastBar();
        seriesB.replaceBar(2,
                seriesB.barBuilder()
                        .timePeriod(last.getTimePeriod())
                        .endTime(last.getEndTime())
                        .openPrice(30)
                        .highPrice(30)
                        .lowPrice(30)
                        .closePrice(30)
                        .volume(last.getVolume())
                        .build());
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new HighPriceIndicator(seriesB), 1));

        Num before = distance.getValue(2);
        Bar lastBar = seriesB.getLastBar();
        Num originalClose = lastBar.getClosePrice();
        lastBar.addPrice(numOf(300));
        lastBar.addPrice(originalClose);
        Num after = distance.getValue(2);

        DistanceFromMAIndicator freshDistance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new HighPriceIndicator(seriesB), 1));
        assertNumEquals(freshDistance.getValue(2), after);
        assertThat(after).isNotEqualTo(before);
    }

    /**
     * Mutating a dependency's terminal bar and then appending a bar before the next
     * consumer read must invalidate the former terminal index, not just the newly
     * appended suffix.
     */
    @Test
    public void distanceFromMovingAverageRecomputesWhenLastBarMutatesBeforeAppend() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d, 4d, 5d).build();
        BaseBarSeries seriesB = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10d, 20d, 30d)
                .build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 1));

        Num before = distance.getValue(2);
        seriesB.getLastBar().addPrice(numOf(300));
        seriesB.addBar(seriesB.barBuilder().openPrice(40).highPrice(40).lowPrice(40).closePrice(40).build());
        Num after = distance.getValue(2);

        DistanceFromMAIndicator freshDistance = new DistanceFromMAIndicator(seriesA,
                new SMAIndicator(new ClosePriceIndicator(seriesB), 1));
        assertNumEquals(freshDistance.getValue(2), after);
        assertThat(after).isNotEqualTo(before);
    }

    /**
     * Dependency groups must be keyed by series identity: two distinct series
     * instances that compare equal must each produce their own observation, so a
     * mutation in the second one still invalidates the dependent cache.
     */
    @Test
    public void equalsCollidingDependencySeriesAreObservedSeparately() {
        BarSeries seriesA = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d, 3d).build();
        BaseBarSeries seriesB = TestUtils.equalsCollidingSeries("B", collidingSeriesBars(10d, 20d, 30d), numFactory);
        BaseBarSeries seriesC = TestUtils.equalsCollidingSeries("C", collidingSeriesBars(10d, 20d, 30d), numFactory);
        SumDependenciesIndicator dependent = new SumDependenciesIndicator(seriesA,
                new RawSeriesClosePriceIndicator(seriesB), new RawSeriesClosePriceIndicator(seriesC));

        Num before = dependent.getValue(1);
        assertNumEquals("40", before);

        Bar replaced = new MockBarBuilder(numFactory).openPrice(100d)
                .highPrice(100d)
                .lowPrice(100d)
                .closePrice(100d)
                .build();
        seriesC.replaceBar(1, replaced);
        Num after = dependent.getValue(1);

        assertNumEquals("120", after);
    }

    private List<Bar> collidingSeriesBars(double... closes) {
        List<Bar> bars = new java.util.ArrayList<>();
        for (double close : closes) {
            bars.add(new MockBarBuilder(numFactory).openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .build());
        }
        return bars;
    }

    private static final class RawSeriesClosePriceIndicator implements Indicator<Num> {

        private final BarSeries series;

        private RawSeriesClosePriceIndicator(BarSeries series) {
            this.series = series;
        }

        @Override
        public Num getValue(int index) {
            return series.getBar(index).getClosePrice();
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }

    private static final class SumDependenciesIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> left;
        private final Indicator<Num> right;

        SumDependenciesIndicator(BarSeries series, Indicator<Num> left, Indicator<Num> right) {
            super(series, left, right);
            this.left = left;
            this.right = right;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            return left.getValue(index).plus(right.getValue(index));
        }
    }

    private static final class CrossSeriesDependentIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> dependency;

        CrossSeriesDependentIndicator(BarSeries series, Indicator<Num> dependency) {
            super(series, dependency);
            this.dependency = dependency;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            return getBarSeries().getBar(index).getClosePrice().plus(dependency.getValue(index));
        }
    }

    private static final class RemovedBarsAwareDependency extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        RemovedBarsAwareDependency(BarSeries series) {
            super(series);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            Num close = getBarSeries().getBar(index).getClosePrice();
            return close.plus(getBarSeries().numFactory().numOf(getBarSeries().getRemovedBarsCount()));
        }

        @Override
        protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
            return true;
        }

        int calculations() {
            return calculations.get();
        }
    }

    /**
     * Guards the head-advance reconciliation contract for saturated series whose
     * retained window legitimately reaches {@link Integer#MAX_VALUE}, where the
     * sentinel value "discard every cached entry" collides with a real bar index.
     */
    @Test
    public void fullDiscardAfterSaturatedHeadAdvanceRecomputesLastBarValue() {
        BaseBarSeries seeded = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(10, 20)
                .build();
        List<Bar> bars = seeded.getBarData();
        // Bar A occupies Integer.MAX_VALUE - 1, bar B Integer.MAX_VALUE. Every
        // index in the retained window is therefore >= MAX_VALUE - 1, which is
        // exactly where the discard-everything sentinel lives.
        BaseBarSeries series = saturatedRetainedWindowSeries(bars, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1);
        CountingFullDiscardIndicator indicator = new CountingFullDiscardIndicator(series);

        Num first = indicator.getValue(series.getEndIndex());
        assertNumEquals("20", first);
        assertEquals(1, indicator.calculations());

        // Evicting bar A moves the head to Integer.MAX_VALUE without touching
        // bar B, so no bar content changed and the last-bar cache would be
        // served as-is unless the sentinel is recognized as a discard signal.
        series.setMaximumBarCount(1);
        Num afterAdvance = indicator.getValue(series.getEndIndex());

        assertNumEquals("20", afterAdvance);
        assertEquals(2, indicator.calculations());
    }

    private static final class CountingFullDiscardIndicator extends CachedIndicator<Num> {

        private final AtomicInteger calculations = new AtomicInteger();

        CountingFullDiscardIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            calculations.incrementAndGet();
            return getBarSeries().getBar(index).getClosePrice();
        }

        @Override
        protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
            return true;
        }

        int calculations() {
            return calculations.get();
        }
    }
}
