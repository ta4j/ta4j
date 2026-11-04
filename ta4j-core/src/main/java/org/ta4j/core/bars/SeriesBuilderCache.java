/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * Per-series builder cache for stateful bar builder factories.
 *
 * <p>
 * Keys are held weakly and compared by identity, and the cached builders reach
 * their series only through {@link #weakAppender(BarSeries)}, so a factory
 * shared by many short-lived series no longer retains every series (and its
 * bars) it has ever served. Entries for collected series are dropped on the
 * next access.
 *
 * @param <B> builder type
 */
final class SeriesBuilderCache<B> {

    private final ReferenceQueue<BarSeries> collected = new ReferenceQueue<>();
    private final Map<SeriesKey, B> builders = new HashMap<>();

    synchronized B get(final BarSeries series, final Function<BarSeries, B> factory) {
        Objects.requireNonNull(series);
        expungeCollected();
        final SeriesKey key = new SeriesKey(series, collected);
        final B cached = builders.get(key);
        if (cached != null) {
            return cached;
        }
        final B created = factory.apply(series);
        builders.put(key, created);
        return created;
    }

    synchronized int size() {
        expungeCollected();
        return builders.size();
    }

    /**
     * Appends to {@code series} while it is reachable. Once nothing else references
     * the series, nobody can observe its bars, so dropping later appends loses
     * nothing.
     */
    static WeakAppender weakAppender(final BarSeries series) {
        return new WeakAppender(Objects.requireNonNull(series));
    }

    /**
     * Clears and enqueues the weak key of {@code series}, exactly as the garbage
     * collector does once the series becomes unreachable, so eviction can be tested
     * without depending on collection timing.
     */
    synchronized void clearKey(final BarSeries series) {
        for (SeriesKey key : builders.keySet()) {
            if (key.get() == series) {
                key.enqueue();
                return;
            }
        }
    }

    private void expungeCollected() {
        Reference<? extends BarSeries> reference;
        while ((reference = collected.poll()) != null) {
            builders.remove(reference);
        }
    }

    /** Bar appender that holds its series only weakly. */
    static final class WeakAppender implements Consumer<Bar> {

        private final WeakReference<BarSeries> target;

        private WeakAppender(final BarSeries series) {
            this.target = new WeakReference<>(series);
        }

        @Override
        public void accept(final Bar bar) {
            final BarSeries live = target.get();
            if (live != null) {
                live.addBar(bar);
            }
        }

        /** Clears the reference as the garbage collector would. */
        void clear() {
            target.clear();
        }
    }

    private static final class SeriesKey extends WeakReference<BarSeries> {

        private final int hash;

        private SeriesKey(final BarSeries series, final ReferenceQueue<BarSeries> queue) {
            super(series, queue);
            this.hash = System.identityHashCode(series);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SeriesKey key)) {
                return false;
            }
            final BarSeries series = get();
            return series != null && series == key.get();
        }
    }
}
