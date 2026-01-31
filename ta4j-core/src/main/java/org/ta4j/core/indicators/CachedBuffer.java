/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;
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
 * Thread-safety is achieved via a {@link ReentrantReadWriteLock} combined with
 * an optimistic, lock-free fast path for cache hits. Cache misses and
 * invalidation acquire write locks. The reentrant nature allows recursive
 * indicators to safely call getValue() from within calculate() without
 * deadlocking.
 *
 * <h2>Memory Usage</h2>
 * <p>
 * Each {@code CachedBuffer} allocates an {@code Object[]} array:
 * <ul>
 * <li><strong>Bounded series</strong> (maximumBarCount set): Array size equals
 * {@code maximumBarCount}.</li>
 * <li><strong>Unbounded series</strong>: Initial capacity is 512, growing up to
 * 1,000,000 as needed.</li>
 * </ul>
 *
 * <p>
 * For applications with many indicators on large unbounded series, memory usage
 * can be significant. Consider setting {@code maximumBarCount} on the series to
 * bound memory consumption, especially for live trading scenarios where only
 * recent bars are relevant.
 *
 * <h2>Null Value Handling</h2>
 * <p>
 * This cache correctly distinguishes between "not computed" and "computed as
 * null" using internal sentinel objects. However, the {@link #get(int)} method
 * returns {@code null} for both cases. Use {@link #isCached(int)} to explicitly
 * check if an index has a cached value (including cached null).
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

    /**
     * Sentinel object used to represent "not computed" in the cache. This allows
     * null values to be cached correctly, as null is a legitimate return value for
     * some indicators.
     */
    private static final Object NOT_COMPUTED = new Object();

    /**
     * Sentinel object used to represent a cached null value. This distinguishes
     * "not computed" from "computed and is null".
     */
    private static final Object NULL_VALUE = new Object();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);

    /**
     * Stamp used for optimistic reads.
     *
     * <p>
     * This is a <em>sequence counter</em> (seqlock-style) used to validate
     * lock-free cache hits. Writers flip it from even-&gt;odd when entering the
     * <em>outermost</em> write-locked section and from odd-&gt;even when the
     * protected state is stable again.
     *
     * <p>
     * Important: the odd-&gt;even transition is performed <em>while still
     * holding</em> the outermost write lock, immediately before {@code unlock()}.
     * This ensures that every other writer that successfully acquires the write
     * lock observes an even stamp on entry (preventing consecutive writers from
     * ever running with an even stamp).
     *
     * <p>
     * Readers speculatively read the cache without locking and validate the read by
     * checking the stamp did not change.
     */
    private volatile long writeStamp;

    /**
     * The ring buffer storing cached values. Uses {@link #NOT_COMPUTED} to
     * represent "not computed", allowing null values to be cached correctly.
     */
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
        return getOrCompute(index, calculator, null);
    }

    T getOrCompute(int index, IntFunction<T> calculator, IntConsumer onComputedIndex) {
        // Optimistic fast-path (lock-free) for cache hits.
        Object cached = readAtOptimistic(index);
        if (cached != NOT_COMPUTED) {
            if (cached == NULL_VALUE) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        }

        // Fast-path: read lock for cache hits
        lock.readLock().lock();
        try {
            cached = readAtUnlocked(index);
        } finally {
            lock.readLock().unlock();
        }
        if (cached != NOT_COMPUTED) {
            if (cached == NULL_VALUE) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        }

        // Miss: compute under write lock (reentrant for recursive indicators)
        lock.writeLock().lock();
        onWriteLockAcquired();
        try {
            cached = readAtUnlocked(index);
            if (cached == NOT_COMPUTED) {
                T result = calculator.apply(index);
                store(index, result);
                if (onComputedIndex != null) {
                    onComputedIndex.accept(index);
                }
                return result;
            }
            if (cached == NULL_VALUE) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T result = (T) cached;
            return result;
        } finally {
            onBeforeWriteLockReleased();
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets a cached value without computing if missing.
     *
     * <p>
     * <strong>Important:</strong> This method returns {@code null} for both "not
     * cached" and "cached null" cases. To distinguish between them, use
     * {@link #isCached(int)} before calling this method.
     *
     * @param index the series index
     * @return the cached value, or null if not cached or if the cached value is
     *         null
     * @see #isCached(int)
     */
    T get(int index) {
        lock.readLock().lock();
        Object cached;
        try {
            cached = readAtUnlocked(index);
        } finally {
            lock.readLock().unlock();
        }
        if (cached == NOT_COMPUTED) {
            return null;
        }
        if (cached == NULL_VALUE) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T result = (T) cached;
        return result;
    }

    /**
     * Checks if a value has been cached for the specified index.
     *
     * <p>
     * This method returns {@code true} if the index has a computed value in the
     * cache, including if that value is {@code null}. Use this to distinguish
     * between "not computed" and "computed as null".
     *
     * @param index the series index
     * @return {@code true} if the index has a cached value (including cached null),
     *         {@code false} if not computed or out of range
     * @see #get(int)
     */
    boolean isCached(int index) {
        lock.readLock().lock();
        try {
            Object cached = readAtUnlocked(index);
            return cached != NOT_COMPUTED;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stores a value in the cache.
     *
     * @param index the series index
     * @param value the value to store
     */
    void put(int index, T value) {
        lock.writeLock().lock();
        onWriteLockAcquired();
        try {
            store(index, value);
        } finally {
            onBeforeWriteLockReleased();
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
        onWriteLockAcquired();
        try {
            int fillStart = Math.max(startIndex, highestResultIndex + 1);
            for (int i = fillStart; i < targetIndex; i++) {
                T value = calculator.apply(i);
                store(i, value);
            }
        } finally {
            onBeforeWriteLockReleased();
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cached values.
     */
    void clear() {
        lock.writeLock().lock();
        onWriteLockAcquired();
        try {
            clearInternal();
        } finally {
            onBeforeWriteLockReleased();
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
        onWriteLockAcquired();
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
                buffer[slot] = NOT_COMPUTED;
            }
            highestResultIndex = index - 1;
        } finally {
            onBeforeWriteLockReleased();
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

    boolean isWriteLockedByCurrentThread() {
        return lock.isWriteLockedByCurrentThread();
    }

    long getWriteStamp() {
        return writeStamp;
    }

    private void onWriteLockAcquired() {
        if (lock.getWriteHoldCount() == 1) {
            writeStamp++;
        }
    }

    private void onBeforeWriteLockReleased() {
        if (lock.getWriteHoldCount() == 1) {
            writeStamp++;
        }
    }

    private Object readAtOptimistic(int index) {
        if (index < 0) {
            return NOT_COMPUTED;
        }

        long stamp1 = writeStamp;
        if ((stamp1 & 1L) != 0L) {
            return NOT_COMPUTED;
        }

        int localFirstCachedIndex = firstCachedIndex;
        if (localFirstCachedIndex < 0) {
            return NOT_COMPUTED;
        }

        int localHighestResultIndex = highestResultIndex;
        if (index < localFirstCachedIndex || index > localHighestResultIndex) {
            return NOT_COMPUTED;
        }

        // IMPORTANT: Use localBuffer.length (not capacity) for slot calculation.
        // This ensures we use the correct slot mapping for whichever buffer we're
        // reading from. If we read an old buffer, we need old buffer's slot mapping.
        // If we read new buffer, we need new buffer's slot mapping (which matches
        // how growBuffer() copies values). Using capacity could cause
        // ArrayIndexOutOfBoundsException if capacity was already updated but we're
        // reading from the old (smaller) buffer.
        Object[] localBuffer = buffer;
        int slot = index % localBuffer.length;
        Object value = localBuffer[slot];
        if (value == null || value == NOT_COMPUTED) {
            return NOT_COMPUTED;
        }

        long stamp2 = writeStamp;
        if (stamp1 != stamp2 || (stamp2 & 1L) != 0L) {
            return NOT_COMPUTED;
        }

        return value;
    }

    /**
     * Checks if an index is within the currently cached range.
     *
     * <p>
     * This method acquires a read lock internally. For internal use when a lock is
     * already held, use {@link #isInRangeUnlocked(int)} instead.
     *
     * @param index the series index
     * @return true if the index is within the cached range (may still be not
     *         computed)
     */
    boolean isInRange(int index) {
        lock.readLock().lock();
        try {
            return isInRangeUnlocked(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    // --- Internal methods (must be called under appropriate lock) ---

    /**
     * Checks if an index is within the currently cached range without acquiring a
     * lock. Callers must hold either the read lock or write lock before calling
     * this method.
     *
     * @param index the series index
     * @return true if the index is within the cached range (may still be not
     *         computed)
     */
    private boolean isInRangeUnlocked(int index) {
        return firstCachedIndex >= 0 && index >= firstCachedIndex && index <= highestResultIndex;
    }

    /**
     * Reads a value from the cache without acquiring a lock. Returns
     * {@link #NOT_COMPUTED} if the index is not in range or not computed.
     *
     * @param index the series index
     * @return the cached value, NULL_VALUE if cached null, or NOT_COMPUTED if not
     *         computed
     */
    private Object readAtUnlocked(int index) {
        if (!isInRangeUnlocked(index)) {
            return NOT_COMPUTED;
        }
        int slot = indexToSlot(index);
        Object value = buffer[slot];
        if (value == null || value == NOT_COMPUTED) {
            return NOT_COMPUTED;
        }
        return value;
    }

    private void store(int index, T value) {
        // Wrap null values in NULL_VALUE sentinel to distinguish from "not computed"
        Object valueToStore = (value == null) ? NULL_VALUE : value;
        if (firstCachedIndex < 0) {
            // First value being cached
            firstCachedIndex = index;
            highestResultIndex = index;
            ensureCapacity(1);
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;
            return;
        }

        if (index > highestResultIndex) {
            // Extending forward
            int gap = index - highestResultIndex;
            int newSize = highestResultIndex - firstCachedIndex + 1 + gap;

            if (bounded && newSize > maximumCapacity) {
                // Need to evict oldest entries
                int evictCount = newSize - maximumCapacity;
                int existingCount = highestResultIndex - firstCachedIndex + 1;

                if (evictCount >= existingCount) {
                    // Evicting all existing entries due to large gap - clear and start fresh
                    for (int i = firstCachedIndex; i <= highestResultIndex; i++) {
                        int slot = indexToSlot(i);
                        buffer[slot] = NOT_COMPUTED;
                    }
                    // Set firstCachedIndex to the new index since all old entries are evicted
                    firstCachedIndex = index;
                } else {
                    // Partial eviction - clear evicted slots and advance firstCachedIndex
                    for (int i = 0; i < evictCount; i++) {
                        int slot = indexToSlot(firstCachedIndex + i);
                        buffer[slot] = NOT_COMPUTED;
                    }
                    firstCachedIndex += evictCount;
                }
            } else if (!bounded && newSize > capacity) {
                // Grow the buffer for unbounded series
                growBuffer(newSize);
            }

            highestResultIndex = index;
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;

        } else if (index >= firstCachedIndex) {
            // Within existing range; just update
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;

        } else {
            // Index is before firstCachedIndex; need to expand backward.
            // For bounded buffers, we rebuild the buffer to avoid slot corruption.
            int newSize = highestResultIndex - index + 1;

            if (bounded && newSize > maximumCapacity) {
                // Cannot fit entire range; evict from high end
                int evictCount = newSize - maximumCapacity;
                highestResultIndex -= evictCount;
                newSize = maximumCapacity;
                // Note: highestResultIndex is now index + maximumCapacity - 1,
                // therefore it is guaranteed to be >= index.
            }

            if (!bounded && newSize > capacity) {
                growBuffer(newSize);
            }

            rebuildBufferForRange(index, highestResultIndex);
            firstCachedIndex = index;
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;
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

    /**
     * Rebuilds the buffer for the specified inclusive range, preserving any
     * existing cached values within the overlap and clearing others. Used when
     * expanding backward in a bounded buffer to avoid stale slot mappings.
     */
    private void rebuildBufferForRange(int newFirstIndex, int newHighestIndex) {
        Object[] newBuffer = new Object[capacity];
        if (firstCachedIndex >= 0) {
            int copyFrom = Math.max(newFirstIndex, firstCachedIndex);
            int copyTo = Math.min(newHighestIndex, highestResultIndex);
            for (int i = copyFrom; i <= copyTo; i++) {
                int oldSlot = indexToSlot(i);
                int newSlot = i % capacity;
                newBuffer[newSlot] = buffer[oldSlot];
            }
        }
        buffer = newBuffer;
    }

    private void clearInternal() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = NOT_COMPUTED;
        }
        firstCachedIndex = -1;
        highestResultIndex = -1;
    }
}
