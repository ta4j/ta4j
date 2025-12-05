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
package org.ta4j.core.indicators;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntFunction;

/**
 * Ring-buffer backed cache for indicator values with O(1) eviction and
 * read-optimized locking.
 *
 * <p>
 * This class manages cached indicator results using a fixed-size circular
 * buffer. It tracks {@code firstCachedIndex} and {@code highestResultIndex} to
 * map series indices to buffer slots efficiently. When the buffer is full, the
 * oldest entries are evicted in O(1) time by advancing the head pointer.
 *
 * <p>
 * Thread-safety is achieved via a {@link ReentrantReadWriteLock}: cache hits
 * use read locks (allowing concurrent reads), while misses and invalidation
 * acquire write locks. The reentrant nature allows recursive indicators to
 * safely call getValue() from within calculate() without deadlocking.
 *
 * @param <T> the type of cached values
 *
 * @since 0.22.0
 */
class CachedBuffer<T> {

    /** Default capacity when maximumBarCount is unbounded. */
    private static final int DEFAULT_UNBOUNDED_CAPACITY = 512;

    /** Maximum reasonable capacity to prevent excessive memory usage. */
    private static final int MAX_CAPACITY = 1_000_000;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    /** The ring buffer storing cached values; null means "not computed". */
    private Object[] buffer;

    /** Current allocated capacity of the buffer. */
    private int capacity;

    /** The maximum capacity (from series.getMaximumBarCount()). */
    private final int maximumCapacity;

    /** Whether the buffer has a fixed maximum capacity. */
    private final boolean bounded;

    /** The series index of the first (oldest) cached value. */
    private int firstCachedIndex = -1;

    /** The series index of the last (newest) cached value. */
    private int highestResultIndex = -1;

    /**
     * Creates a new cached buffer.
     *
     * @param maximumBarCount the maximum bar count from the series, or
     *                        {@code Integer.MAX_VALUE} for unbounded
     */
    CachedBuffer(int maximumBarCount) {
        this.bounded = maximumBarCount != Integer.MAX_VALUE;
        this.maximumCapacity = bounded ? maximumBarCount : MAX_CAPACITY;
        this.capacity = bounded ? maximumBarCount : DEFAULT_UNBOUNDED_CAPACITY;
        this.buffer = new Object[capacity];
    }

    /**
     * Gets a cached value, computing it if necessary.
     *
     * @param index      the series index
     * @param calculator function to compute the value if not cached
     * @return the cached or computed value
     */
    T getOrCompute(int index, IntFunction<T> calculator) {
        // Fast-path: read lock for cache hits
        lock.readLock().lock();
        T result;
        try {
            result = readAt(index);
        } finally {
            lock.readLock().unlock();
        }
        if (result != null) {
            return result;
        }

        // Miss: compute under write lock (reentrant for recursive indicators)
        lock.writeLock().lock();
        try {
            result = readAt(index);
            if (result == null) {
                result = calculator.apply(index);
                store(index, result);
            }
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets a cached value without computing if missing.
     *
     * @param index the series index
     * @return the cached value, or null if not cached
     */
    T get(int index) {
        lock.readLock().lock();
        T result;
        try {
            result = readAt(index);
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    /**
     * Stores a value in the cache.
     *
     * @param index the series index
     * @param value the value to store
     */
    void put(int index, T value) {
        lock.writeLock().lock();
        try {
            store(index, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Prefills missing values up to (but not including) the target index.
     *
     * <p>
     * This method is designed for recursive indicators to avoid stack overflow by
     * iteratively computing values from the current highest index up to the target.
     * The caller provides a calculator that computes values without re-entering the
     * public getValue method.
     *
     * @param startIndex  the index to start filling from
     * @param targetIndex the target index (exclusive)
     * @param calculator  function to compute values
     */
    void prefillUntil(int startIndex, int targetIndex, IntFunction<T> calculator) {
        lock.writeLock().lock();
        try {
            int fillStart = Math.max(startIndex, highestResultIndex + 1);
            for (int i = fillStart; i < targetIndex; i++) {
                T value = calculator.apply(i);
                store(i, value);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cached values.
     */
    void clear() {
        lock.writeLock().lock();
        try {
            clearInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears cached values from the specified index (inclusive) to the end.
     *
     * @param index the first index to invalidate; if negative, clears all
     */
    void invalidateFrom(int index) {
        lock.writeLock().lock();
        try {
            if (firstCachedIndex < 0 || index > highestResultIndex) {
                return;
            }
            if (index < 0 || index <= firstCachedIndex) {
                clearInternal();
                return;
            }

            // Clear slots from index to highestResultIndex
            for (int i = index; i <= highestResultIndex; i++) {
                int slot = indexToSlot(i);
                buffer[slot] = null;
            }
            highestResultIndex = index - 1;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return the highest cached series index, or -1 if empty
     */
    int getHighestResultIndex() {
        lock.readLock().lock();
        try {
            return highestResultIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return the first cached series index, or -1 if empty
     */
    int getFirstCachedIndex() {
        lock.readLock().lock();
        try {
            return firstCachedIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if an index is within the currently cached range.
     *
     * @param index the series index
     * @return true if the index is cached (may still be null if not computed)
     */
    boolean isInRange(int index) {
        return firstCachedIndex >= 0 && index >= firstCachedIndex && index <= highestResultIndex;
    }

    // --- Internal methods (must be called under appropriate lock) ---

    @SuppressWarnings("unchecked")
    private T readAt(int index) {
        if (!isInRange(index)) {
            return null;
        }
        int slot = indexToSlot(index);
        return (T) buffer[slot];
    }

    private void store(int index, T value) {
        if (firstCachedIndex < 0) {
            // First value being cached
            firstCachedIndex = index;
            highestResultIndex = index;
            ensureCapacity(1);
            int slot = indexToSlot(index);
            buffer[slot] = value;
            return;
        }

        if (index > highestResultIndex) {
            // Extending forward
            int gap = index - highestResultIndex;
            int newSize = highestResultIndex - firstCachedIndex + 1 + gap;

            if (bounded && newSize > maximumCapacity) {
                // Need to evict oldest entries
                int evictCount = newSize - maximumCapacity;
                // Clear evicted slots and advance firstCachedIndex
                for (int i = 0; i < evictCount && firstCachedIndex + i <= highestResultIndex; i++) {
                    int slot = indexToSlot(firstCachedIndex + i);
                    buffer[slot] = null;
                }
                firstCachedIndex += evictCount;
            } else if (!bounded && newSize > capacity) {
                // Grow the buffer for unbounded series
                growBuffer(newSize);
            }

            highestResultIndex = index;
            int slot = indexToSlot(index);
            buffer[slot] = value;

        } else if (index >= firstCachedIndex) {
            // Within existing range; just update
            int slot = indexToSlot(index);
            buffer[slot] = value;

        } else {
            // Index is before firstCachedIndex; need to expand backward.
            // For bounded buffers, we must evict from the high end to make room.
            // For unbounded buffers, we grow if needed.
            int newSize = highestResultIndex - index + 1;

            if (bounded && newSize > maximumCapacity) {
                // Cannot fit entire range; evict from high end
                int evictCount = newSize - maximumCapacity;
                // Clear evicted slots at high end
                for (int i = 0; i < evictCount; i++) {
                    int evictIndex = highestResultIndex - i;
                    int slot = indexToSlot(evictIndex);
                    buffer[slot] = null;
                }
                highestResultIndex -= evictCount;
            }

            if (!bounded && newSize > capacity) {
                growBuffer(newSize);
            }

            firstCachedIndex = index;
            int slot = indexToSlot(index);
            buffer[slot] = value;
        }
    }

    private void ensureCapacity(int requiredSize) {
        if (requiredSize > capacity) {
            growBuffer(requiredSize);
        }
    }

    private void growBuffer(int requiredSize) {
        int newCapacity = Math.min(Math.max(capacity * 2, requiredSize), maximumCapacity);
        Object[] newBuffer = new Object[newCapacity];

        // Copy existing values to new buffer using absolute slot mapping
        if (firstCachedIndex >= 0) {
            for (int i = firstCachedIndex; i <= highestResultIndex; i++) {
                int oldSlot = indexToSlot(i);
                int newSlot = i % newCapacity;
                newBuffer[newSlot] = buffer[oldSlot];
            }
        }

        buffer = newBuffer;
        capacity = newCapacity;
    }

    /**
     * Maps a series index to a buffer slot using absolute indexing. This ensures
     * slot mapping is stable regardless of eviction.
     */
    private int indexToSlot(int index) {
        return index % capacity;
    }

    private void clearInternal() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        firstCachedIndex = -1;
        highestResultIndex = -1;
    }
}
