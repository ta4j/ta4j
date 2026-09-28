/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Read-only view of a run's series that ends at the run's last index.
 *
 * <p>
 * Execution models, position sizing and entry estimates see this view, so a run
 * can never fill on, size from, or peek at a bar after its window: not a later
 * bar that already exists (the next bar after a sub-range or walk-forward fold,
 * or raw bars retained past a constrained series' logical end) and not a bar a
 * live feed appends while the run executes. A signal on the run's last bar that
 * needs a later bar to fill simply does not fill, leaving the position open at
 * the window end.
 * </p>
 */
final class RunWindowBarSeries implements BarSeries {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BarSeries delegate;
    private final int windowEndIndex;
    private boolean processedBar;

    void markBarProcessed() {
        processedBar = true;
    }

    boolean hasProcessedBar() {
        return processedBar;
    }

    RunWindowBarSeries(BarSeries delegate, int windowEndIndex) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.windowEndIndex = windowEndIndex;
    }

    @Override
    public NumFactory numFactory() {
        return delegate.numFactory();
    }

    @Override
    public BarBuilder barBuilder() {
        throw new UnsupportedOperationException("Run window series are read-only");
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Bar getBar(int i) {
        return delegate.withReadLock(() -> {
            int beginIndex = Math.max(delegate.getBeginIndex(), delegate.getRemovedBarsCount());
            int endIndex = Math.min(delegate.getEndIndex(), windowEndIndex);
            if (i < beginIndex || i > endIndex) {
                throw new IndexOutOfBoundsException(
                        "Index " + i + " is outside the run window [" + beginIndex + ", " + endIndex + "]");
            }
            return delegate.getBar(i);
        });
    }

    @Override
    public int getBarCount() {
        return delegate.withReadLock(() -> {
            long beginIndex = Math.max(delegate.getBeginIndex(), delegate.getRemovedBarsCount());
            long endIndex = Math.min(delegate.getEndIndex(), windowEndIndex);
            long visibleCount = Math.max(0L, endIndex - beginIndex + 1L);
            return (int) Math.min(delegate.getBarCount(), visibleCount);
        });
    }

    /**
     * Returns a snapshot of bars in the run window, copying a coherent delegate
     * range so later appends cannot appear in this view.
     */
    @Override
    public List<Bar> getBarData() {
        return delegate.withReadLock(() -> {
            List<Bar> bars = delegate.getBarData();
            int removedBarsCount = delegate.getRemovedBarsCount();
            long beginIndex = Math.max(delegate.getBeginIndex(), removedBarsCount);
            long endIndex = Math.min(delegate.getEndIndex(), windowEndIndex);
            int start = (int) Math.max(0L, Math.min(bars.size(), beginIndex - removedBarsCount));
            int end = (int) Math.max(start, Math.min(bars.size(), endIndex - removedBarsCount + 1L));
            return List.copyOf(bars.subList(start, end));
        });
    }

    @Override
    public long getBarHistoryRevision() {
        return delegate.getBarHistoryRevision();
    }

    /**
     * Delegates to the source's snapshot, which describes one coherent state, and
     * caps its end at the run window.
     */
    @Override
    public BarSeriesChangeSnapshot getBarSeriesChangeSnapshot(long sinceRevision) {
        BarSeriesChangeSnapshot snapshot = delegate.getBarSeriesChangeSnapshot(sinceRevision);
        return new BarSeriesChangeSnapshot(snapshot.revision(), snapshot.earliestChangedIndex(),
                snapshot.removedThroughIndex(), snapshot.maximumBarCount(),
                Math.min(snapshot.endIndex(), windowEndIndex));
    }

    @Override
    public void withReadLock(Runnable action) {
        delegate.withReadLock(action);
    }

    @Override
    public <T> T withReadLock(Supplier<T> action) {
        return delegate.withReadLock(action);
    }

    @Override
    public int getBeginIndex() {
        return delegate.withReadLock(() -> Math.max(delegate.getBeginIndex(), delegate.getRemovedBarsCount()));
    }

    @Override
    public int getEndIndex() {
        return delegate.withReadLock(() -> Math.min(delegate.getEndIndex(), windowEndIndex));
    }

    @Override
    public int getMaximumBarCount() {
        return delegate.getMaximumBarCount();
    }

    @Override
    public void setMaximumBarCount(int maximumBarCount) {
        throw new UnsupportedOperationException("Run window series are read-only");
    }

    @Override
    public int getRemovedBarsCount() {
        return delegate.getRemovedBarsCount();
    }

    @Override
    public void addBar(Bar bar, boolean replace) {
        throw new UnsupportedOperationException("Run window series are read-only");
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        throw new UnsupportedOperationException("Run window series are read-only");
    }

    @Override
    public void addPrice(Num price) {
        throw new UnsupportedOperationException("Run window series are read-only");
    }

    @Override
    public BarSeries getSubSeries(int startIndex, int endIndex) {
        return delegate.withReadLock(() -> {
            int beginIndex = Math.max(delegate.getBeginIndex(), delegate.getRemovedBarsCount());
            int windowEnd = Math.min(delegate.getEndIndex(), windowEndIndex);
            long start = Math.max(startIndex, beginIndex);
            long end = Math.min((long) endIndex, (long) windowEnd + 1L);
            if (end < start) {
                end = start;
            }
            return delegate.getSubSeries((int) start, (int) end);
        });
    }
}
