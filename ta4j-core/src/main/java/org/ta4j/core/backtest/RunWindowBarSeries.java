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
        if (i > windowEndIndex) {
            throw new IndexOutOfBoundsException("Index " + i + " is after the run window ending at " + windowEndIndex);
        }
        return delegate.getBar(i);
    }

    @Override
    public int getBarCount() {
        return delegate.withReadLock(() -> {
            int hiddenBars = Math.max(0, delegate.getEndIndex() - windowEndIndex);
            return Math.max(0, delegate.getBarCount() - hiddenBars);
        });
    }

    /**
     * Returns a snapshot of the bars inside the run window. The delegate's bars and
     * removal count are read under one read lease so a concurrent append or
     * eviction cannot shift the cut, and the result is copied so later appends
     * never become visible through it.
     */
    @Override
    public List<Bar> getBarData() {
        return delegate.withReadLock(() -> {
            List<Bar> bars = delegate.getBarData();
            long visible = (long) windowEndIndex - delegate.getRemovedBarsCount() + 1L;
            return List.copyOf(bars.subList(0, (int) Math.max(0L, Math.min(bars.size(), visible))));
        });
    }

    @Override
    public long getBarHistoryRevision() {
        return delegate.getBarHistoryRevision();
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
        return delegate.getBeginIndex();
    }

    @Override
    public int getEndIndex() {
        return Math.min(delegate.getEndIndex(), windowEndIndex);
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
        return delegate.getSubSeries(startIndex, (int) Math.min(endIndex, windowEndIndex + 1L));
    }
}
