/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class RunWindowBarSeriesTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    public void getBarDataCutsBarsAndRemovalCountFromOneSnapshot() {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger outermostLeases = new AtomicInteger();
        AtomicReference<Runnable> writer = new AtomicReference<>();
        // Lets a feed writer slip in before the second outermost read lease of an
        // armed call, i.e. between two reads that were not taken under one lease.
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock() {
            private final ReadLock interleavingReadLock = new ReadLock(this) {
                @Override
                public void lock() {
                    if (armed.get() && getReadHoldCount() == 0 && outermostLeases.incrementAndGet() == 2) {
                        armed.set(false);
                        writer.get().run();
                    }
                    super.lock();
                }
            };

            @Override
            public ReadLock readLock() {
                return interleavingReadLock;
            }
        };
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        ConcurrentBarSeries delegate = ConstrainedSeriesSupport.seriesWithReadWriteLock(source, lock);
        List<Bar> windowBars = delegate.getBarData();
        Bar appended = delegate.barBuilder().timePeriod(Duration.ofDays(1)).closePrice(15).build();
        writer.set(() -> delegate.addBar(appended));
        RunWindowBarSeries window = new RunWindowBarSeries(delegate, 4);

        armed.set(true);
        List<Bar> bars = window.getBarData();
        armed.set(false);

        // Bars and removal count come from one snapshot, so a concurrent append
        // that evicts the first bar cannot shift the cut off the run window's end.
        assertEquals(windowBars, bars);
    }

    @Test
    public void changeSnapshotDelegatesToTheSourceAndCapsItsEndAtTheWindow() {
        BarSeries delegate = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        long revision = delegate.getBarHistoryRevision();
        delegate.getBar(1).addPrice(numFactory.numOf(15));
        RunWindowBarSeries window = new RunWindowBarSeries(delegate, 3);

        BarSeriesChangeSnapshot snapshot = window.getBarSeriesChangeSnapshot(revision);
        BarSeriesChangeSnapshot source = delegate.getBarSeriesChangeSnapshot(revision);

        // The source knows exactly which bar changed, where a coarse default would
        // report the whole history as changed from index 0.
        assertEquals(new BarSeriesChangeSnapshot(source.revision(), 1, source.removedThroughIndex(),
                source.maximumBarCount(), 3), snapshot);
    }

    @Test
    public void hidesBarsBeforeTheLogicalBegin() {
        BarSeries delegate = ConstrainedSeriesSupport.offsetSeries("run_window_begin", numFactory, 2, 3, 0, 10d, 11d,
                12d, 13d);
        RunWindowBarSeries window = new RunWindowBarSeries(delegate, 3);

        assertEquals(2, window.getBeginIndex());
        assertEquals(3, window.getEndIndex());
        assertEquals(2, window.getBarCount());
        assertThrows(IndexOutOfBoundsException.class, () -> window.getBar(1));
        List<Bar> visibleBars = List.of(delegate.getBar(2), delegate.getBar(3));
        assertEquals(visibleBars, window.getBarData());
        assertEquals(visibleBars, window.getSubSeries(0, 4).getBarData());
        assertThrows(UnsupportedOperationException.class, () -> window.getBarData().clear());
    }
}
