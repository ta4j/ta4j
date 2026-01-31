/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;

import java.io.ObjectInputStream;
import java.io.IOException;
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
 * <p>
 * For real-time data feeds, prefer {@link #ingestTrade(Instant, Num, Num)} and
 * {@link #ingestTrade(Instant, Number, Number)} to let the configured
 * {@link BarBuilder} handle bar rollovers. Direct bar mutations remain
 * available for reconciliation and data correction workflows.
 *
 * <p>
 * Java serialization preserves bar data, the {@link NumFactory}, and the
 * {@link BarBuilderFactory} configuration. Transient locks are reinitialized on
 * deserialization, and the trade bar builder is recreated lazily on the next
 * ingestion call.
 *
 * @since 0.22.2
 */
public class ConcurrentBarSeries extends BaseBarSeries {

    private static final long serialVersionUID = -1868546230609071876L;

    private transient Lock readLock;
    private transient Lock writeLock;

    private transient BarBuilder tradeBarBuilder;

    /**
     * Indicates how a streaming bar was applied to the series.
     *
     * @since 0.22.2
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
     * @since 0.22.2
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
        this(name, bars, 0, bars.size() - 1, false, DecimalNumFactory.getInstance(), new TimeBarBuilderFactory(true),
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
        initLocks(readWriteLock);
        this.tradeBarBuilder = Objects.requireNonNull(super.barBuilder(), "barBuilder cannot be null");
    }

    private void initLocks(final ReadWriteLock readWriteLock) {
        ReadWriteLock rwLock = Objects.requireNonNull(readWriteLock, "readWriteLock cannot be null");
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initLocks(new ReentrantReadWriteLock());
        tradeBarBuilder = null;
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
                final var builder = new ConcurrentBarSeriesBuilder().withName(getName())
                        .withBars(cut(bars, start, end))
                        .withNumFactory(super.numFactory())
                        .withBarBuilderFactory(super.barBuilderFactory());
                if (!isConstrained()) {
                    builder.withMaxBarCount(super.getMaximumBarCount());
                }
                return builder.build();
            }
            final var builder = new ConcurrentBarSeriesBuilder().withNumFactory(super.numFactory())
                    .withBarBuilderFactory(super.barBuilderFactory())
                    .withName(getName());
            if (!isConstrained()) {
                builder.withMaxBarCount(super.getMaximumBarCount());
            }
            return builder.build();
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
     * Returns the builder used for streaming trade ingestion. Configure it (for
     * example, set the time period) before calling
     * {@link #ingestTrade(Instant, Num, Num)}.
     *
     * @return the trade bar builder
     *
     * @since 0.22.2
     */
    public BarBuilder tradeBarBuilder() {
        this.readLock.lock();
        try {
            if (tradeBarBuilder != null) {
                return tradeBarBuilder;
            }
        } finally {
            this.readLock.unlock();
        }
        this.writeLock.lock();
        try {
            if (tradeBarBuilder == null) {
                tradeBarBuilder = Objects.requireNonNull(super.barBuilder(), "barBuilder cannot be null");
            }
            return tradeBarBuilder;
        } finally {
            this.writeLock.unlock();
        }
    }

    /**
     * Runs the supplied action while holding the read lock.
     *
     * @param action read-only action to execute
     *
     * @since 0.22.2
     */
    public void withReadLock(final Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
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
     * @since 0.22.2
     */
    public <T> T withReadLock(final Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");
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
     * @since 0.22.2
     */
    public void withWriteLock(final Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
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
     * @since 0.22.2
     */
    public <T> T withWriteLock(final Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");
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
     * Ingests a trade event into the series using the configured bar builder.
     *
     * @param tradeTime   the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     *
     * @since 0.22.2
     */
    public void ingestTrade(final Instant tradeTime, final Number tradeVolume, final Number tradePrice) {
        ingestTrade(tradeTime, tradeVolume, tradePrice, null, null);
    }

    /**
     * Ingests a trade event into the series using the configured bar builder.
     *
     * @param tradeTime   the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     *
     * @since 0.22.2
     */
    public void ingestTrade(final Instant tradeTime, final Num tradeVolume, final Num tradePrice) {
        ingestTrade(tradeTime, tradeVolume, tradePrice, null, null);
    }

    /**
     * Ingests a trade event into the series using the configured bar builder.
     *
     * @param tradeTime   the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.2
     */
    public void ingestTrade(final Instant tradeTime, final Number tradeVolume, final Number tradePrice,
            final RealtimeBar.Side side, final RealtimeBar.Liquidity liquidity) {
        Objects.requireNonNull(tradeTime, "tradeTime cannot be null");
        Objects.requireNonNull(tradeVolume, "tradeVolume cannot be null");
        Objects.requireNonNull(tradePrice, "tradePrice cannot be null");
        final NumFactory factory = super.numFactory();
        ingestTrade(tradeTime, factory.numOf(tradeVolume), factory.numOf(tradePrice), side, liquidity);
    }

    /**
     * Ingests a trade event into the series using the configured bar builder.
     *
     * @param tradeTime   the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.2
     */
    public void ingestTrade(final Instant tradeTime, final Num tradeVolume, final Num tradePrice,
            final RealtimeBar.Side side, final RealtimeBar.Liquidity liquidity) {
        Objects.requireNonNull(tradeTime, "tradeTime cannot be null");
        Objects.requireNonNull(tradeVolume, "tradeVolume cannot be null");
        Objects.requireNonNull(tradePrice, "tradePrice cannot be null");
        if (!super.numFactory().produces(tradeVolume) || !super.numFactory().produces(tradePrice)) {
            throw new IllegalArgumentException(
                    String.format("Cannot ingest trade with data types: %s/%s into series with datatype: %s",
                            tradeVolume.getClass(), tradePrice.getClass(), super.numFactory().one().getClass()));
        }
        this.writeLock.lock();
        try {
            if (tradeBarBuilder == null) {
                tradeBarBuilder = Objects.requireNonNull(super.barBuilder(), "barBuilder");
            }
            tradeBarBuilder.addTrade(tradeTime, tradeVolume, tradePrice, side, liquidity);
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
     * @since 0.22.2
     */
    public StreamingBarIngestResult ingestStreamingBar(final Bar bar) {
        Objects.requireNonNull(bar, "bar cannot be null");
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
     * @since 0.22.2
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
        final Instant newEndTime = Objects.requireNonNull(newBar.getEndTime(), "Bar endTime cannot be null");
        final Bar lastBar = internal.get(internal.size() - 1);
        final Instant lastEndTime = Objects.requireNonNull(lastBar.getEndTime(), "Last bar endTime cannot be null");
        int endTimeComparison = newEndTime.compareTo(lastEndTime);
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
            final int seriesIndex = internalIndex + super.getRemovedBarsCount();
            // Replacing a historical bar doesn't change bar count or indices, so we bypass
            // addBar().
            super.replaceBar(seriesIndex, newBar);
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
