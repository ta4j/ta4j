/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class EMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public EMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new EMAIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "EMA.xls", 6, numFactory);
    }

    private BarSeries data;

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37, 61.33, 61.51)
                .build();
    }

    @Test
    public void dependentReseedsWhenRecursiveSourceLosesItsBridge() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator actual = new EMAIndicator(new OnBalanceVolumeIndicator(series), 2);
        for (int index = 0; index < 6; index++) {
            series.barBuilder().closePrice(10 + index).volume(10).add();
            actual.getValue(index);
        }
        series.setMaximumBarCount(1);
        series.barBuilder().closePrice(20).volume(10).add();
        EMAIndicator fresh = new EMAIndicator(new OnBalanceVolumeIndicator(series), 2);
        assertNumEquals(fresh.getValue(series.getEndIndex()), actual.getValue(series.getEndIndex()));
        assertTrue(actual.getValue(series.getEndIndex()).isNaN());
    }

    @Test
    public void removedReadPreservesCachedTerminalHeadWithoutSourceLookback() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator actual = new EMAIndicator(new LogReturnIndicator(series), 2);
        for (int index = 0; index < 6; index++) {
            series.barBuilder().closePrice(10 + index * index).add();
            actual.getValue(index);
        }
        Num consumed = actual.getValue(series.getEndIndex());
        series.setMaximumBarCount(1);
        assertNumEquals(consumed, actual.getValue(series.getBeginIndex()));
        assertNumEquals(consumed, actual.getValue(0));
        assertNumEquals(consumed, actual.getValue(series.getBeginIndex()));
    }

    @Test
    public void firstValueShouldBeNaNDuringUnstablePeriod() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        // With barCount=1, unstable period is 1, so index 0 should return NaN
        assertThat(Double.isNaN(indicator.getValue(0).doubleValue())).isTrue();
    }

    @Test
    public void movingSeriesContinuesRecurrenceAcrossHeadAdvances() {
        // #1666: a moving series drops a bar on every append. Each appended bar
        // must extend the cached recurrence, so live and historical reads match
        // an unbounded series instead of a chain rebuilt from the retained head.
        double[] closes = { 10, 13, 9, 15, 11, 17, 12, 19, 14, 21, 13, 18 };
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(4);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(moving), 3);
        EMAIndicator reference = new EMAIndicator(new ClosePriceIndicator(unbounded), 3);
        for (int i = 0; i < closes.length; i++) {
            moving.barBuilder().closePrice(closes[i]).add();
            unbounded.barBuilder().closePrice(closes[i]).add();
            assertNumEquals(reference.getValue(i), ema.getValue(i));
        }
        assertEquals(8, moving.getBeginIndex());
        for (int i = moving.getBeginIndex(); i <= moving.getEndIndex(); i++) {
            assertNumEquals(reference.getValue(i), ema.getValue(i));
        }

        // Without cached predecessors the chain re-seeds at the retained head
        // and warms up from there.
        EMAIndicator fresh = new EMAIndicator(new ClosePriceIndicator(moving), 3);
        assertTrue(fresh.getValue(10).isNaN());
        assertNumEquals(18, fresh.getValue(11));
    }

    @Test
    public void twoBarWindowContinuesChainFromFormerTerminalValue() {
        // With two retained bars the predecessor of each appended bar was the
        // terminal bar on the previous read. Its value must survive the append, or
        // the chain would have to recompute it after its own predecessor left the
        // window and re-seed on every bar.
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(2);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(moving), 3);
        EMAIndicator reference = new EMAIndicator(new ClosePriceIndicator(unbounded), 3);
        for (int i = 0; i < 10; i++) {
            moving.barBuilder().closePrice(10 + 3 * (i % 3)).add();
            unbounded.barBuilder().closePrice(10 + 3 * (i % 3)).add();
            assertNumEquals(reference.getValue(i), ema.getValue(i));
        }
        assertThat(ema.getValue(9).isNaN()).isFalse();
    }

    @Test
    public void oneBarWindowPreservesWarmUpAndConsumedHistory() {
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(1);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator actual = new EMAIndicator(new ClosePriceIndicator(moving), 3);
        EMAIndicator expected = new EMAIndicator(new ClosePriceIndicator(unbounded), 3);
        assertEquals(3, actual.getCountOfUnstableBars());
        for (int index = 0; index < 8; index++) {
            moving.barBuilder().closePrice(10 + index * 7 % 11).add();
            unbounded.barBuilder().closePrice(10 + index * 7 % 11).add();
            assertNumEquals(expected.getValue(index), actual.getValue(index));
            moving.getLastBar().addPrice(numOf(20 + index));
            unbounded.getLastBar().addPrice(numOf(20 + index));
            assertNumEquals(expected.getValue(index), actual.getValue(index));
            assertNumEquals(actual.getValue(index), actual.getValue(-1));
        }
        assertTrue(new EMAIndicator(new ClosePriceIndicator(moving), 3).getValue(7).isNaN());
    }

    @Test
    public void warmedRecursiveSourceContinuesBelowItsInitialWarmUpCapacity() {
        BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator actual = new EMAIndicator(new EMAIndicator(new ClosePriceIndicator(bounded), 4), 2);
        EMAIndicator expected = new EMAIndicator(new EMAIndicator(new ClosePriceIndicator(unbounded), 4), 2);
        for (int index = 0; index < 10; index++) {
            bounded.barBuilder().closePrice(100 + index * 7 % 19).add();
            unbounded.barBuilder().closePrice(100 + index * 7 % 19).add();
            assertNumEquals(expected.getValue(index), actual.getValue(index));
        }
        bounded.setMaximumBarCount(1);
        assertNumEquals(expected.getValue(9), actual.getValue(9));
        bounded.barBuilder().closePrice(123).add();
        unbounded.barBuilder().closePrice(123).add();
        assertNumEquals(expected.getValue(10), actual.getValue(10));
    }

    @Test
    public void retainedHeadEditReseedsWhenSourceLookbackWasRemoved() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(3);
        EMAIndicator actual = new EMAIndicator(new LogReturnIndicator(series), 2);
        for (int index = 0; index < 8; index++) {
            series.barBuilder().closePrice(100 + index * 7 % 19).add();
            actual.getValue(index);
        }
        series.getBar(series.getBeginIndex()).addPrice(numOf(150));
        EMAIndicator fresh = new EMAIndicator(new LogReturnIndicator(series), 2);
        assertNumEquals(fresh.getValue(7), actual.getValue(7));
        series.barBuilder().closePrice(127).add();
        assertNumEquals(fresh.getValue(8), actual.getValue(8));
    }

    @Test
    public void retainedHeadEditReseedsWhenSourceCacheFloorExceedsItsWarmUp() {
        for (boolean wrapped : List.of(false, true)) {
            for (boolean observeHeadAdvance : List.of(false, true)) {
                BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
                series.setMaximumBarCount(3);
                Indicator<Num> source = new TRIndicator(series);
                EMAIndicator actual = new EMAIndicator(wrapped ? UnaryOperationIndicator.abs(source) : source, 2);
                for (int index = 0; index < 8; index++) {
                    double close = 100 + index * 7 % 19;
                    series.barBuilder()
                            .openPrice(close)
                            .highPrice(close + 1)
                            .lowPrice(close - 1)
                            .closePrice(close)
                            .add();
                    actual.getValue(index);
                }
                series.barBuilder().openPrice(127).highPrice(128).lowPrice(126).closePrice(127).add();
                if (observeHeadAdvance) {
                    actual.getValue(8);
                }
                series.getBar(series.getBeginIndex()).addPrice(numOf(150));
                Indicator<Num> freshSource = new TRIndicator(series);
                EMAIndicator fresh = new EMAIndicator(wrapped ? UnaryOperationIndicator.abs(freshSource) : freshSource,
                        2);
                assertNumEquals(fresh.getValue(8), actual.getValue(8));
                series.barBuilder().openPrice(115).highPrice(116).lowPrice(114).closePrice(115).add();
                assertNumEquals(fresh.getValue(9), actual.getValue(9));
            }
        }
    }

    @Test
    public void retainedInteriorEditReseedsOnlyBelowSourceCacheFloor() {
        for (int changedOffset : List.of(1, 3, 4)) {
            for (boolean wrapped : List.of(false, true)) {
                for (boolean observeHeadAdvance : List.of(false, true)) {
                    BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
                    BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
                    Indicator<Num> source = new SMAIndicator(new ClosePriceIndicator(series), 5);
                    EMAIndicator actual = new EMAIndicator(wrapped ? UnaryOperationIndicator.abs(source) : source, 2);
                    for (int index = 0; index < 12; index++) {
                        double close = 100 + index * 7 % 19;
                        series.barBuilder().closePrice(close).add();
                        unbounded.barBuilder().closePrice(close).add();
                        actual.getValue(index);
                    }
                    series.setMaximumBarCount(7);
                    if (observeHeadAdvance) {
                        actual.getValue(11);
                    }
                    int changedIndex = series.getBeginIndex() + changedOffset;
                    series.getBar(changedIndex).addPrice(numOf(150));
                    unbounded.getBar(changedIndex).addPrice(numOf(150));
                    BarSeries expectedSeries = changedOffset < 4 ? series : unbounded;
                    Indicator<Num> freshSource = new SMAIndicator(new ClosePriceIndicator(expectedSeries), 5);
                    EMAIndicator expected = new EMAIndicator(
                            wrapped ? UnaryOperationIndicator.abs(freshSource) : freshSource, 2);
                    assertNumEquals(expected.getValue(11), actual.getValue(11));
                    series.barBuilder().closePrice(127).add();
                    unbounded.barBuilder().closePrice(127).add();
                    assertNumEquals(expected.getValue(12), actual.getValue(12));
                }
            }
        }
    }

    @Test
    public void terminalEditOverRawRecursiveSourceKeepsFullHistoryRecurrence() {
        for (boolean observeEdit : List.of(false, true)) {
            BarSeries bounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
            bounded.setMaximumBarCount(3);
            EMAIndicator actual = new EMAIndicator(new OnBalanceVolumeIndicator(bounded), 2);
            EMAIndicator expected = new EMAIndicator(new OnBalanceVolumeIndicator(unbounded), 2);
            for (int index = 0; index < 8; index++) {
                double close = 100 + index * 7 % 19;
                bounded.barBuilder().closePrice(close).volume(10 + index).add();
                unbounded.barBuilder().closePrice(close).volume(10 + index).add();
                assertNumEquals(expected.getValue(index), actual.getValue(index));
            }

            bounded.getLastBar().addPrice(numOf(150));
            unbounded.getLastBar().addPrice(numOf(150));
            if (observeEdit) {
                assertNumEquals(expected.getValue(7), actual.getValue(7));
            }
            for (int index = 8; index < 11; index++) {
                double close = 100 + index * 7 % 19;
                bounded.barBuilder().closePrice(close).volume(10 + index).add();
                unbounded.barBuilder().closePrice(close).volume(10 + index).add();
                assertNumEquals(expected.getValue(index), actual.getValue(index));
            }
        }
    }

    @Test
    public void emptySeriesReturnsNaNWithoutRecursing() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), 1);

        assertTrue(new EMAIndicator(new ClosePriceIndicator(series), 3).getValue(0).isNaN());
        assertTrue(ema.getValue(0).isNaN());

        // The empty-series read must not leave a value that later appends reuse.
        EMAIndicator reference = new EMAIndicator(new ClosePriceIndicator(unbounded), 1);
        for (int i = 0; i < 4; i++) {
            series.barBuilder().closePrice(10 + i).add();
            unbounded.barBuilder().closePrice(10 + i).add();
        }
        for (int i = 0; i < 4; i++) {
            assertNumEquals(reference.getValue(i), ema.getValue(i));
        }
    }

    @Test
    public void removedIndexReadServesRetainedFirstAvailableValue() {
        // A removed index maps to the first available bar. Once the chain has
        // continued past warm-up, that bar holds a retained full-history value, and
        // both requests must agree instead of the removed read re-seeding.
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(3);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(moving), 2);
        for (int i = 0; i < 8; i++) {
            moving.barBuilder().closePrice(10 + 3 * (i % 3)).add();
            ema.getValue(i);
        }
        int beginIndex = moving.getBeginIndex();
        assertThat(ema.getValue(beginIndex).isNaN()).isFalse();
        assertNumEquals(ema.getValue(beginIndex), ema.getValue(beginIndex - 1));
        assertNumEquals(ema.getValue(beginIndex), ema.getValue(0));
    }

    @Test
    public void readRecomputesWhenHeadAdvancesMidRead() {
        HeadAdvancingSource.assertReadRecomputesAfterMidReadHeadAdvance(numFactory,
                source -> new EMAIndicator(source, 2));
    }

    @Test
    public void continuedChainKeepsFullHistoryWarmUpWhenWindowIsShorterThanWarmUp() {
        // The window (3 bars) is shorter than the warm-up (5 bars). Warm-up is
        // counted from where the chain was seeded, so the continued chain still
        // turns stable at index 5 like an unbounded series; a read of a pruned
        // index must not re-seed the live chain.
        BarSeries moving = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        moving.setMaximumBarCount(3);
        BarSeries unbounded = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(moving), 5);
        EMAIndicator reference = new EMAIndicator(new ClosePriceIndicator(unbounded), 5);
        for (int i = 0; i < 10; i++) {
            moving.barBuilder().closePrice(10 + i * (i % 2 == 0 ? 1 : -1)).add();
            unbounded.barBuilder().closePrice(10 + i * (i % 2 == 0 ? 1 : -1)).add();
            if (i == 7) {
                ema.getValue(0);
            }
            Num value = ema.getValue(i);
            assertNumEquals(reference.getValue(i), value);
            assertThat(value.isNaN()).isEqualTo(i < 5);
        }
    }

    @Test
    public void negativeIndexRemainsNaNDuringWarmup() {
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 2);

        assertThat(Double.isNaN(ema.getValue(-1).doubleValue())).isTrue();
    }

    @Test
    public void removedIndicesMapToRetainedHeadWarmupValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        series.setMaximumBarCount(5);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), 2);

        assertEquals(1, series.getBeginIndex());
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        assertNumEquals(4, ema.getValue(3));
    }

    @Test
    public void removedIndexReadRefreshesToNewRetainedHeadAfterPrune() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        series.setMaximumBarCount(3);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), 2);

        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();

        series.barBuilder().endTime(series.getLastBar().getEndTime().plusSeconds(1)).closePrice(8).add();

        assertEquals(2, series.getBeginIndex());
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(series.getBeginIndex()).doubleValue())).isTrue();
        assertNumEquals(8, ema.getValue(4));
    }

    @Test
    public void removedIndexReadDoesNotLookAheadWhenStableBarBecomesAvailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        series.setMaximumBarCount(3);
        EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(series), 3);

        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();

        series.setMaximumBarCount(4);
        series.barBuilder().endTime(series.getLastBar().getEndTime().plusSeconds(1)).closePrice(5).add();

        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(series.getBeginIndex()).doubleValue())).isTrue();
        assertNumEquals(5, ema.getValue(4));
    }

    @Test
    public void concurrentPruneDoesNotDeadlockRecursiveRead() throws Exception {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        series.setMaximumBarCount(4);
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch allowCalculation = new CountDownLatch(1);
        AtomicBoolean blockCurrentIndex = new AtomicBoolean(true);
        Indicator<Num> source = new AbstractIndicator<Num>(series) {
            @Override
            public Num getValue(int index) {
                if (index == 3 && blockCurrentIndex.compareAndSet(true, false)) {
                    calculationStarted.countDown();
                    try {
                        assertTrue(allowCalculation.await(5, TimeUnit.SECONDS),
                                "recursive calculation was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return getBarSeries().getBar(index).getClosePrice();
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EMAIndicator ema = new EMAIndicator(source, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "ema-prune-regression");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch resetReaderStarted = new CountDownLatch(1);
        AtomicReference<Thread> resetReaderThread = new AtomicReference<>();

        try {
            Future<Num> recursiveRead = executor.submit(() -> ema.getValue(3));
            assertTrue(calculationStarted.await(5, TimeUnit.SECONDS), "recursive calculation never started");
            series.barBuilder().endTime(series.getLastBar().getEndTime().plusSeconds(1)).closePrice(5).add();

            Future<Num> resetRead = executor.submit(() -> {
                resetReaderThread.set(Thread.currentThread());
                resetReaderStarted.countDown();
                return ema.getValue(4);
            });
            assertTrue(resetReaderStarted.await(5, TimeUnit.SECONDS), "reset reader never started");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            Thread.State resetReaderState;
            do {
                resetReaderState = resetReaderThread.get().getState();
                if (resetReaderState == Thread.State.BLOCKED || resetReaderState == Thread.State.WAITING
                        || resetReaderState == Thread.State.TIMED_WAITING) {
                    break;
                }
                Thread.onSpinWait();
            } while (System.nanoTime() < deadline);
            assertThat(resetReaderState).isIn(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING);

            allowCalculation.countDown();
            assertNumEquals(4, recursiveRead.get(5, TimeUnit.SECONDS));
            assertNumEquals(5, resetRead.get(5, TimeUnit.SECONDS));
        } finally {
            allowCalculation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void usingBarCount10UsingClosePrice() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 10);
        // Index 9 is in unstable period (barCount=10, so indices 0-9 are unstable)
        assertThat(Double.isNaN(indicator.getValue(9).doubleValue())).isTrue();
        // Index 10 is first valid value after unstable period - initializes to current
        // value
        assertNumEquals(61.33, indicator.getValue(10)); // First value after unstable period = current value
        // Index 11 is first calculated EMA value (from index 10)
        // With barCount=10, multiplier = 2/(10+1) = 2/11 ≈ 0.1818
        // EMA(11) = 61.33 + (61.51 - 61.33) * 0.1818 ≈ 61.33 + 0.18 * 0.1818 ≈ 61.33 +
        // 0.0327 ≈ 61.3627
        Num value11 = indicator.getValue(11);
        assertThat(value11.doubleValue()).isGreaterThan(61.33);
        assertThat(value11.doubleValue()).isLessThan(62.0);
    }

    @Test
    public void stackOverflowError() throws Exception {
        var bigSeries = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 10000; i++) {
            bigSeries.barBuilder().closePrice(i).add();
        }
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(bigSeries), 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertNumEquals(9994.5, indicator.getValue(9999));
    }

    @Test
    public void externalData() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> closePrice = new ClosePriceIndicator(xlsSeries);
        Indicator<Num> indicator;

        indicator = getIndicator(closePrice, 1);
        // With barCount=1, unstable period is 1, so index 0 returns NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(329.0, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 3);
        // With barCount=3, unstable period is 3, so indices 0-2 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(327.7748, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 13);
        // With barCount=13, unstable period is 13, so indices 0-12 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(327.4076, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void returnsNaNWhenCurrentValueIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();
    }

    @Test
    public void recoversFromNaNInPreviousValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover - reset to current value (14.0) when previous is NaN
        Num value4 = ema.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));
    }

    @Test
    public void returnsNaNWhenFirstValueIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        List<Num> values = Arrays.asList(NaN, numFactory.numOf(11.0), numFactory.numOf(12.0), numFactory.numOf(13.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: first valid value after unstable period, but previous was NaN, so
        // should return current value
        Num value3 = ema.getValue(3);
        assertThat(value3).isEqualByComparingTo(numFactory.numOf(13.0));
    }

    @Test
    public void handlesNonZeroBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(closePrice, 3);

        int beginIndex = series.getBeginIndex();
        int unstableBars = ema.getCountOfUnstableBars();
        // During unstable period, should return NaN
        for (int i = beginIndex; i < beginIndex + unstableBars; i++) {
            assertThat(Double.isNaN(ema.getValue(i).doubleValue())).isTrue();
        }
        // After unstable period, should return valid values
        Num valueAfterUnstable = ema.getValue(beginIndex + unstableBars);
        assertThat(Double.isNaN(valueAfterUnstable.doubleValue())).isFalse();
    }

    @Test
    public void handlesNaNInInputValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN);
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();
    }

    @Test
    public void preventsNaNContamination() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16)
                .build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0), numFactory.numOf(15.0), numFactory.numOf(16.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover (reset to current value 14.0)
        Num value4 = ema.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));

        // Index 5 should continue normal EMA calculation (not contaminated)
        Num value5 = ema.getValue(5);
        assertThat(value5).isNotNull();
        assertThat(Double.isNaN(value5.doubleValue())).isFalse();
        // Should be a valid EMA calculation from 14.0
        assertThat(value5.doubleValue()).isGreaterThan(14.0);
    }

    @Test
    public void recoversWhenChainedAfterRsiIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 3);
        EMAIndicator emaOnRsi = new EMAIndicator(rsi, 2);

        // EMA unstable period is shorter than RSI, so NaN should propagate through the
        // RSI unstable window
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(1))).isTrue();
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(2))).isTrue();

        Num firstRsiValue = rsi.getValue(3);
        assertThat(firstRsiValue).isEqualByComparingTo(numFactory.hundred());
        // Recovery: EMA resets to the first stable RSI value instead of remaining NaN
        assertThat(emaOnRsi.getValue(3)).isEqualByComparingTo(firstRsiValue);

        // Independently computed reference values: Wilder RSI(3) on the closes
        // [1,2,3,4,3,4,5] gives RSI(4) = 100 - 100/(1 + (2/3)/(1/3)) = 66.666..., so
        // EMA(4) = 100 + (66.666... - 100) * 2/3 = 77.777...; RSI(5) equals 77.777...,
        // so the recurrence leaves EMA(5) = EMA(4) = 77.777... (agreed by both Num
        // factories within GENERAL_OFFSET).
        assertNumEquals(77.7778, emaOnRsi.getValue(4));
        assertNumEquals(77.7778, emaOnRsi.getValue(5));
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                .build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        EMAIndicator original = new EMAIndicator(base, 5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(EMAIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());

        // Compare values after unstable period
        int unstableBars = original.getCountOfUnstableBars();
        for (int i = series.getBeginIndex() + unstableBars; i <= series.getEndIndex(); i++) {
            Num expected = original.getValue(i);
            Num actual = reconstructed.getValue(i);
            if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
                assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            } else {
                assertThat(actual).isEqualTo(expected);
            }
        }
    }
}
