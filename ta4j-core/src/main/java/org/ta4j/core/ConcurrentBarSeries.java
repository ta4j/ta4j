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
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Objects;
import java.time.Instant;
import java.util.List;

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

    /**
     * Indicates how a streaming bar was applied to the series.
     *
     * @since 0.19
     */
    public enum StreamingBarIngestAction {
        APPENDED, REPLACED_LAST, REPLACED_HISTORICAL
    }

    /**
     * Describes the outcome of ingesting a streaming bar.
     *
     * @param action indicates how the bar was applied
     * @param index  the affected series index
     *
     * @since 0.19
     */
    public record StreamingBarIngestResult(StreamingBarIngestAction action, int index) {
        public StreamingBarIngestResult {
            Objects.requireNonNull(action, "action cannot be null");
            if (index < 0) {
                throw new IllegalArgumentException("index cannot be negative");
            }
        }
    }

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
                throw new IllegalArgumentException(String.format("startIndex: %s cannot be negative", startIndex));
            }
            if (startIndex >= endIndex) {
                throw new IllegalArgumentException(
                        String.format("endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
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
    public String getName() {
        this.readLock.lock();
        try {
            return super.getName();
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public NumFactory numFactory() {
        this.readLock.lock();
        try {
            return super.numFactory();
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
    public Bar getFirstBar() {
        this.readLock.lock();
        try {
            return super.getBar(super.getBeginIndex());
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public Bar getLastBar() {
        this.readLock.lock();
        try {
            return super.getBar(super.getEndIndex());
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

    /**
     * Runs the supplied action while holding the read lock.
     *
     * @param action read-only action to execute
     *
     * @since 0.19
     */
    public void withReadLock(final Runnable action) {
        Objects.requireNonNull(action, "action");
        this.readLock.lock();
        try {
            action.run();
        } finally {
            this.readLock.unlock();
        }
    }

    /**
     * Runs the supplied action while holding the read lock.
     *
     * @param action read-only action to execute
     * @param <T>    return type
     * @return the action result
     *
     * @since 0.19
     */
    public <T> T withReadLock(final Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        this.readLock.lock();
        try {
            return action.get();
        } finally {
            this.readLock.unlock();
        }
    }

    /**
     * Runs the supplied action while holding the write lock.
     *
     * @param action mutating action to execute
     *
     * @since 0.19
     */
    public void withWriteLock(final Runnable action) {
        Objects.requireNonNull(action, "action");
        this.writeLock.lock();
        try {
            action.run();
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * Runs the supplied action while holding the write lock.
     *
     * @param action mutating action to execute
     * @param <T>    return type
     * @return the action result
     *
     * @since 0.19
     */
    public <T> T withWriteLock(final Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        this.writeLock.lock();
        try {
            return action.get();
        } finally {
            this.writeLock.unlock();
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
     * @return the applied action and affected series index
     *
     * @since 0.19
     */
    public StreamingBarIngestResult ingestStreamingBar(final Bar bar) {
        Objects.requireNonNull(bar, "bar");
        this.writeLock.lock();
        try {
            return addStreamingBarUnsafe(bar);
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
     * @return applied actions in ascending end-time order
     *
     * @since 0.19
     */
    public List<StreamingBarIngestResult> ingestStreamingBars(final Collection<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        final List<Bar> ordered = new ArrayList<>(bars);
        ordered.removeIf(Objects::isNull);
        if (ordered.isEmpty()) {
            return List.of();
        }
        ordered.sort(Comparator.comparing(Bar::getEndTime));
        this.writeLock.lock();
        try {
            final List<StreamingBarIngestResult> results = new ArrayList<>(ordered.size());
            for (Bar bar : ordered) {
                results.add(addStreamingBarUnsafe(bar));
            }
            return List.copyOf(results);
        } finally {
            this.writeLock.unlock();
        }
    }

    private StreamingBarIngestResult addStreamingBarUnsafe(final Bar newBar) {
        validateBarMatchesSeries(newBar);
        final List<Bar> internal = super.getBarData();
        if (internal.isEmpty()) {
            super.addBar(newBar, false);
            return new StreamingBarIngestResult(StreamingBarIngestAction.APPENDED, super.getEndIndex());
        }
        final Instant newEndTime = newBar.getEndTime();
        final Bar lastBar = internal.get(internal.size() - 1);
        int endTimeComparison = newEndTime.compareTo(lastBar.getEndTime());
        if (endTimeComparison == 0) {
            super.addBar(newBar, true);
            return new StreamingBarIngestResult(StreamingBarIngestAction.REPLACED_LAST, super.getEndIndex());
        }
        if (endTimeComparison > 0) {
            super.addBar(newBar, false);
            return new StreamingBarIngestResult(StreamingBarIngestAction.APPENDED, super.getEndIndex());
        }
        final int internalIndex = findBarIndexByEndTime(internal, newEndTime);
        if (internalIndex >= 0) {
            internal.set(internalIndex, newBar);
            final int seriesIndex = internalIndex + super.getRemovedBarsCount();
            return new StreamingBarIngestResult(StreamingBarIngestAction.REPLACED_HISTORICAL, seriesIndex);
        }
        throw new IllegalArgumentException(
                String.format("Cannot insert streaming bar ending at %s because series end time is %s",
                        newBar.getEndTime(), lastBar.getEndTime()));
    }

    private void validateBarMatchesSeries(final Bar bar) {
        if (!super.numFactory().produces(bar.getClosePrice())) {
            throw new IllegalArgumentException(
                    String.format("Cannot add Bar with data type: %s to series with datatype: %s",
                            bar.getClosePrice().getClass(), super.numFactory().one().getClass()));
        }
    }

    private static int findBarIndexByEndTime(final List<Bar> bars, final Instant endTime) {
        int low = 0;
        int high = bars.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            final Instant midTime = bars.get(mid).getEndTime();
            int comparison = midTime.compareTo(endTime);
            if (comparison < 0) {
                low = mid + 1;
            } else if (comparison > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
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
