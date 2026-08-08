/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Ring-buffer backed cache for indicator values with O(1) eviction and
 * read-optimized locking.
 *
 * <p>
 * This class manages cached indicator results using a dynamically sized
 * circular buffer. It tracks {@code firstCachedIndex} and
 * {@code highestResultIndex} to map series indices to buffer slots efficiently.
 * When the buffer is full, the oldest entries are evicted in O(1) time by
 * advancing the logical range.
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
 * <li><strong>Bounded series</strong> (maximumBarCount set): Initial capacity
 * is at most 512 and grows lazily to the smaller of {@code maximumBarCount} and
 * 1,000,000.</li>
 * <li><strong>Unbounded series</strong>: Initial capacity is 512 and grows up
 * to 1,000,000 as needed.</li>
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
    private final AtomicLong writeStamp = new AtomicLong();

    /**
     * The ring buffer storing cached values. Uses {@link #NOT_COMPUTED} to
     * represent "not computed", allowing null values to be cached correctly.
     */
    private Object[] buffer;

    /** Current allocated capacity of the buffer. */
    private int capacity;

    /** Current cache-capacity ceiling derived from series.getMaximumBarCount(). */
    private int maximumCapacity;

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
        this.maximumCapacity = effectiveMaximumCapacity(maximumBarCount);
        this.capacity = Math.min(DEFAULT_UNBOUNDED_CAPACITY, this.maximumCapacity);
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
        try {
            onWriteLockAcquired();
            try {
                store(index, value);
            } finally {
                onBeforeWriteLockReleased();
            }
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
            onWriteLockAcquired();
            try {
                int fillStart = Math.max(startIndex, highestResultIndex + 1);
                for (int i = fillStart; i < targetIndex; i++) {
                    T value = calculator.apply(i);
                    store(i, value);
                }
            } finally {
                onBeforeWriteLockReleased();
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
            onWriteLockAcquired();
            try {
                clearInternal();
            } finally {
                onBeforeWriteLockReleased();
            }
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
            onWriteLockAcquired();
            try {
                if (firstCachedIndex < 0 || index > highestResultIndex) {
                    return;
                }
                if (index < 0 || index <= firstCachedIndex) {
                    clearInternal();
                    return;
                }
                highestResultIndex = index - 1;
            } finally {
                onBeforeWriteLockReleased();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reconciles the logical range and capacity with a current series snapshot.
     *
     * @param firstRetainedIndex first series index that remains available
     * @param maximumBarCount    current maximum number of retained bars
     * @param invalidateFrom     first changed index to invalidate, or {@code -1}
     *                           when no published value changed
     * @return highest still-cached index, or {@code -1} when empty
     */
    int synchronize(int firstRetainedIndex, int maximumBarCount, int invalidateFrom) {
        lock.writeLock().lock();
        try {
            onWriteLockAcquired();
            try {
                maximumCapacity = effectiveMaximumCapacity(maximumBarCount);

                if (firstCachedIndex >= 0 && firstRetainedIndex > firstCachedIndex) {
                    if (firstRetainedIndex > highestResultIndex) {
                        clearInternal();
                    } else {
                        firstCachedIndex = firstRetainedIndex;
                    }
                }

                if (firstCachedIndex >= 0 && invalidateFrom >= 0 && invalidateFrom <= highestResultIndex) {
                    if (invalidateFrom <= firstCachedIndex) {
                        clearInternal();
                    } else {
                        highestResultIndex = invalidateFrom - 1;
                    }
                }

                if (firstCachedIndex >= 0) {
                    long rangeSize = (long) highestResultIndex - firstCachedIndex + 1L;
                    if (rangeSize > maximumCapacity) {
                        firstCachedIndex = highestResultIndex - maximumCapacity + 1;
                    }
                }

                if (capacity > maximumCapacity) {
                    resizeBuffer(maximumCapacity);
                }
                return highestResultIndex;
            } finally {
                onBeforeWriteLockReleased();
            }
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

    int getCapacity() {
        lock.readLock().lock();
        try {
            return capacity;
        } finally {
            lock.readLock().unlock();
        }
    }

    boolean isWriteLockedByCurrentThread() {
        return lock.isWriteLockedByCurrentThread();
    }

    long getWriteStamp() {
        return writeStamp.get();
    }

    private void onWriteLockAcquired() {
        if (lock.getWriteHoldCount() == 1) {
            writeStamp.incrementAndGet();
        }
    }

    private void onBeforeWriteLockReleased() {
        if (lock.getWriteHoldCount() == 1) {
            writeStamp.incrementAndGet();
        }
    }

    private Object readAtOptimistic(int index) {
        if (index < 0) {
            return NOT_COMPUTED;
        }

        long stamp1 = writeStamp.get();
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

        long stamp2 = writeStamp.get();
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
            int previousHighestIndex = highestResultIndex;
            long requestedRangeSize = (long) index - firstCachedIndex + 1L;
            int newFirstIndex = requestedRangeSize > maximumCapacity ? index - maximumCapacity + 1 : firstCachedIndex;
            if (newFirstIndex > previousHighestIndex) {
                newFirstIndex = index;
            }
            int requiredSize = index - newFirstIndex + 1;
            ensureCapacity(requiredSize);
            clearRange(Math.max(previousHighestIndex + 1, newFirstIndex), index - 1);
            firstCachedIndex = Math.max(firstCachedIndex, newFirstIndex);
            highestResultIndex = index;
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;

        } else if (index >= firstCachedIndex) {
            // Within existing range; just update
            int slot = indexToSlot(index);
            buffer[slot] = valueToStore;

        } else {
            // Expand backward in place. Absolute slot mapping means an adjacent
            // reverse read overwrites exactly the slot evicted from the high end.
            int previousFirstIndex = firstCachedIndex;
            long requestedRangeSize = (long) highestResultIndex - index + 1L;
            int newHighestIndex = requestedRangeSize > maximumCapacity ? index + maximumCapacity - 1
                    : highestResultIndex;
            int requiredSize = newHighestIndex - index + 1;
            ensureCapacity(requiredSize);
            clearRange(index + 1, Math.min(previousFirstIndex - 1, newHighestIndex));
            firstCachedIndex = index;
            highestResultIndex = newHighestIndex;
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
        int newCapacity = (int) Math.min(Math.max((long) capacity * 2L, requiredSize), maximumCapacity);
        resizeBuffer(newCapacity);
    }

    private void resizeBuffer(int newCapacity) {
        Object[] newBuffer = new Object[newCapacity];

        // Copy existing values to new buffer using absolute slot mapping
        if (firstCachedIndex >= 0) {
            if ((long) highestResultIndex - firstCachedIndex + 1L > newCapacity) {
                firstCachedIndex = highestResultIndex - newCapacity + 1;
            }
            if (firstCachedIndex > highestResultIndex) {
                // Inconsistent empty range: adopt the empty resized buffer instead
                // of entering the copy loop, which is only bounded by breaking at
                // highestResultIndex and would otherwise loop until integer
                // wraparound.
                buffer = newBuffer;
                capacity = newCapacity;
                return;
            }
            for (int i = firstCachedIndex;; i++) {
                int oldSlot = indexToSlot(i);
                int newSlot = i % newCapacity;
                newBuffer[newSlot] = buffer[oldSlot];
                if (i == highestResultIndex) {
                    break;
                }
            }
        }

        buffer = newBuffer;
        capacity = newCapacity;
    }

    private void clearRange(int fromIndex, int toIndex) {
        for (int i = fromIndex; i <= toIndex; i++) {
            buffer[indexToSlot(i)] = NOT_COMPUTED;
            // Break at toIndex instead of relying on i <= toIndex alone: the
            // guard also keeps i++ from overflowing when toIndex == Integer.MAX_VALUE.
            if (i == toIndex) {
                break;
            }
        }
    }

    private static int effectiveMaximumCapacity(int maximumBarCount) {
        if (maximumBarCount <= 0) {
            throw new IllegalArgumentException("Maximum bar count must be strictly positive");
        }
        return initialMaximumCapacity(maximumBarCount);
    }

    private static int initialMaximumCapacity(int maximumBarCount) {
        return maximumBarCount == Integer.MAX_VALUE ? MAX_CAPACITY : Math.min(maximumBarCount, MAX_CAPACITY);
    }

    /**
     * Maps a series index to a buffer slot using absolute indexing. This ensures
     * slot mapping is stable regardless of eviction.
     */
    private int indexToSlot(int index) {
        return index % capacity;
    }

    private void clearInternal() {
        capacity = Math.min(DEFAULT_UNBOUNDED_CAPACITY, maximumCapacity);
        buffer = new Object[capacity];
        firstCachedIndex = -1;
        highestResultIndex = -1;
    }
}
