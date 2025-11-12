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
package org.ta4j.core;

import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe {@link BarSeries} implementation for concurrent read/write use
 * cases.
 *
 * @since 0.19
 */
public class ConcurrentBarSeries extends BaseBarSeries {

    private static final long serialVersionUID = -1868546230609071876L;

    private final Lock readLock;
    private final Lock writeLock;

    ConcurrentBarSeries(final String name, final List<Bar> bars) {
        this(name, bars, 0, bars.size() - 1, false, DecimalNumFactory.getInstance(), new TimeBarBuilderFactory(),
                new ReentrantReadWriteLock());
    }

    ConcurrentBarSeries(final String name, final List<Bar> bars, final int seriesBeginIndex, final int seriesEndIndex,
            final boolean constrained, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory) {
        this(name, bars, seriesBeginIndex, seriesEndIndex, constrained, numFactory, barBuilderFactory,
                new ReentrantReadWriteLock());
    }

    ConcurrentBarSeries(final String name, final List<Bar> bars, final int seriesBeginIndex, final int seriesEndIndex,
            final boolean constrained, final NumFactory numFactory, final BarBuilderFactory barBuilderFactory,
            final ReadWriteLock readWriteLock) {
        super(name, bars, seriesBeginIndex, seriesEndIndex, constrained, numFactory, barBuilderFactory);
        ReadWriteLock rwLock = Objects.requireNonNull(readWriteLock, "readWriteLock");
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    private static List<Bar> cut(final List<Bar> bars, final int startIndex, final int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    @Override
    public ConcurrentBarSeries getSubSeries(final int startIndex, final int endIndex) {
        this.readLock.lock();
        try {
            if (startIndex < 0) {
                throw new IllegalArgumentException(
                        String.format("the startIndex: %s must not be negative", startIndex));
            }
            if (startIndex >= endIndex) {
                throw new IllegalArgumentException(
                        String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
            }
            final List<Bar> bars = super.getBarData();
            if (!bars.isEmpty()) {
                final int start = startIndex - super.getRemovedBarsCount();
                final int end = Math.min(endIndex - super.getRemovedBarsCount(), super.getEndIndex() + 1);
                return new ConcurrentBarSeriesBuilder().withName(getName())
                        .withBars(cut(bars, start, end))
                        .withNumFactory(super.numFactory())
                        .build();
            }
            return new ConcurrentBarSeriesBuilder().withNumFactory(super.numFactory()).withName(getName()).build();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public BarBuilder barBuilder() {
        this.readLock.lock();
        try {
            return super.barBuilder();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public Bar getBar(final int i) {
        this.readLock.lock();
        try {
            return super.getBar(i);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getBarCount() {
        this.readLock.lock();
        try {
            return super.getBarCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public List<Bar> getBarData() {
        this.readLock.lock();
        try {
            return List.copyOf(super.getBarData());
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getBeginIndex() {
        this.readLock.lock();
        try {
            return super.getBeginIndex();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getEndIndex() {
        this.readLock.lock();
        try {
            return super.getEndIndex();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public int getMaximumBarCount() {
        this.readLock.lock();
        try {
            return super.getMaximumBarCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void setMaximumBarCount(final int maximumBarCount) {
        this.writeLock.lock();
        try {
            super.setMaximumBarCount(maximumBarCount);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public int getRemovedBarsCount() {
        this.readLock.lock();
        try {
            return super.getRemovedBarsCount();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void addBar(final Bar bar, final boolean replace) {
        this.writeLock.lock();
        try {
            super.addBar(bar, replace);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addTrade(final Number tradeVolume, final Number tradePrice) {
        this.writeLock.lock();
        try {
            super.addTrade(tradeVolume, tradePrice);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addTrade(final Num tradeVolume, final Num tradePrice) {
        this.writeLock.lock();
        try {
            super.addTrade(tradeVolume, tradePrice);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public void addPrice(final Num price) {
        this.writeLock.lock();
        try {
            super.addPrice(price);
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * Ingests a single streaming bar (e.g., one emitted from an exchange WebSocket
     * candles) and appends or replaces the matching interval.
     *
     * <p>
     * Unlike {@link #addBar(Bar, boolean)}, this method can replace historical bars
     * when exchanges replay snapshots that include prior intervals.
     *
     * @param bar streaming bar payload
     *
     * @since 0.19
     */
    public void ingestStreamingBar(final Bar bar) {
        Objects.requireNonNull(bar, "bar");
        this.writeLock.lock();
        try {
            addStreamingBarUnsafe(bar);
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * Bulk-ingests streaming bars. Incoming payloads are sorted by their end time
     * to gracefully handle candle snapshots that are emitted with the most recent
     * intervals first.
     *
     * @param bars streaming bars to ingest
     *
     * @since 0.19
     */
    public void ingestStreamingBars(final Collection<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }
        final List<Bar> ordered = new ArrayList<>(bars);
        ordered.removeIf(Objects::isNull);
        if (ordered.isEmpty()) {
            return;
        }
        ordered.sort(Comparator.comparing(Bar::getEndTime));
        this.writeLock.lock();
        try {
            ordered.forEach(this::addStreamingBarUnsafe);
        } finally {
            this.writeLock.unlock();
        }
    }

    private void addStreamingBarUnsafe(final Bar newBar) {
        final List<Bar> internal = super.getBarData();
        if (internal.isEmpty()) {
            super.addBar(newBar, false);
            return;
        }
        final Bar lastBar = internal.get(internal.size() - 1);
        if (lastBar.getEndTime().equals(newBar.getEndTime())) {
            super.addBar(newBar, true);
            return;
        }
        if (lastBar.getEndTime().isBefore(newBar.getEndTime())) {
            super.addBar(newBar, false);
            return;
        }
        for (int idx = internal.size() - 2; idx >= 0; idx--) {
            final Bar candidate = internal.get(idx);
            if (candidate.getEndTime().equals(newBar.getEndTime())) {
                internal.set(idx, newBar);
                return;
            }
            if (candidate.getEndTime().isBefore(newBar.getEndTime())) {
                break;
            }
        }
        throw new IllegalArgumentException(
                String.format("Cannot insert streaming bar ending at %s because series end time is %s",
                        newBar.getEndTime(), lastBar.getEndTime()));
    }

    @Override
    public String getSeriesPeriodDescription() {
        this.readLock.lock();
        try {
            return super.getSeriesPeriodDescription();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public String getSeriesPeriodDescriptionInSystemTimeZone() {
        this.readLock.lock();
        try {
            return super.getSeriesPeriodDescriptionInSystemTimeZone();
        } finally {
            this.readLock.unlock();
        }
    }
}
