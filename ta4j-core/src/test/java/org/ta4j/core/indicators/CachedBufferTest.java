/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for {@link CachedBuffer}.
 */
public class CachedBufferTest {

    @Test
    public void testBasicGetOrCompute() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);
        AtomicInteger computations = new AtomicInteger(0);

        Integer result = buffer.getOrCompute(5, i -> {
            computations.incrementAndGet();
            return i * 2;
        });

        assertEquals(Integer.valueOf(10), result);
        assertEquals(1, computations.get());

        // Second call should use cache
        result = buffer.getOrCompute(5, i -> {
            computations.incrementAndGet();
            return i * 2;
        });

        assertEquals(Integer.valueOf(10), result);
        assertEquals(1, computations.get());
    }

    @Test
    public void testWriteStampFlipsDuringWriteLockAndReturnsEvenAfterwards() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        long initialStamp = buffer.getWriteStamp();
        assertEquals("writeStamp should start even", 0L, initialStamp & 1L);

        AtomicLong stampDuringWrite = new AtomicLong(Long.MIN_VALUE);
        buffer.prefillUntil(0, 1, i -> {
            stampDuringWrite.set(buffer.getWriteStamp());
            return i;
        });

        long capturedStamp = stampDuringWrite.get();
        assertEquals("writeStamp should be odd while write lock held", 1L, capturedStamp & 1L);

        long finalStamp = buffer.getWriteStamp();
        assertEquals("writeStamp should be even after write completes", 0L, finalStamp & 1L);
        assertEquals("Outer write lock should flip stamp twice", initialStamp + 2L, finalStamp);
    }

    @Test
    public void testRingBufferEvictionWithSmallCapacity() {
        // Test with small maximumBarCount (3) and >10 bars to verify wraparound
        CachedBuffer<Integer> buffer = new CachedBuffer<>(3);
        AtomicInteger computations = new AtomicInteger(0);

        // Fill indices 0, 1, 2
        for (int i = 0; i < 3; i++) {
            buffer.getOrCompute(i, x -> {
                computations.incrementAndGet();
                return x * 10;
            });
        }
        assertEquals(3, computations.get());
        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(2, buffer.getHighestResultIndex());

        // Add indices 3, 4, 5 - should evict 0, 1, 2
        for (int i = 3; i < 6; i++) {
            buffer.getOrCompute(i, x -> {
                computations.incrementAndGet();
                return x * 10;
            });
        }
        assertEquals(6, computations.get());
        assertEquals(3, buffer.getFirstCachedIndex());
        assertEquals(5, buffer.getHighestResultIndex());

        // Verify values 3, 4, 5 are still cached
        int prevComputations = computations.get();
        assertEquals(Integer.valueOf(30), buffer.get(3));
        assertEquals(Integer.valueOf(40), buffer.get(4));
        assertEquals(Integer.valueOf(50), buffer.get(5));
        assertEquals(prevComputations, computations.get()); // No new computations

        // Values 0, 1, 2 should be evicted (null)
        assertNull(buffer.get(0));
        assertNull(buffer.get(1));
        assertNull(buffer.get(2));

        // Continue advancing to 10+ to test full wraparound
        for (int i = 6; i <= 12; i++) {
            buffer.getOrCompute(i, x -> {
                computations.incrementAndGet();
                return x * 10;
            });
        }

        // Buffer should contain 10, 11, 12
        assertEquals(10, buffer.getFirstCachedIndex());
        assertEquals(12, buffer.getHighestResultIndex());

        assertEquals(Integer.valueOf(100), buffer.get(10));
        assertEquals(Integer.valueOf(110), buffer.get(11));
        assertEquals(Integer.valueOf(120), buffer.get(12));
    }

    @Test
    public void testConcurrentAccessSingleComputationPerIndex() throws InterruptedException {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(100);
        AtomicInteger computations = new AtomicInteger(0);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    buffer.getOrCompute(42, x -> {
                        computations.incrementAndGet();
                        // Simulate some work
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return x * 2;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Concurrent tasks did not finish in time", done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Only one computation should occur despite concurrent access
        assertEquals("Only one computation should be performed for the same index", 1, computations.get());
    }

    @Test
    public void testPrefillUntil() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(100);
        AtomicInteger computations = new AtomicInteger(0);

        // Prefill 0 to 9 (exclusive of 10)
        buffer.prefillUntil(0, 10, i -> {
            computations.incrementAndGet();
            return i * 3;
        });

        assertEquals(10, computations.get());
        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(9, buffer.getHighestResultIndex());

        // Verify all values
        for (int i = 0; i < 10; i++) {
            assertEquals(Integer.valueOf(i * 3), buffer.get(i));
        }

        // Another prefill should not recompute existing values
        computations.set(0);
        buffer.prefillUntil(5, 15, i -> {
            computations.incrementAndGet();
            return i * 3;
        });

        // Should only compute 10-14 (5 values)
        assertEquals(5, computations.get());
        assertEquals(14, buffer.getHighestResultIndex());
    }

    @Test
    public void testClear() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        buffer.put(0, 100);
        buffer.put(1, 200);
        buffer.put(2, 300);

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(2, buffer.getHighestResultIndex());

        buffer.clear();

        assertEquals(-1, buffer.getFirstCachedIndex());
        assertEquals(-1, buffer.getHighestResultIndex());
        assertNull(buffer.get(0));
        assertNull(buffer.get(1));
        assertNull(buffer.get(2));
    }

    @Test
    public void testInvalidateFrom() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        for (int i = 0; i < 5; i++) {
            buffer.put(i, i * 10);
        }

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(4, buffer.getHighestResultIndex());

        // Invalidate from index 2
        buffer.invalidateFrom(2);

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(1, buffer.getHighestResultIndex());

        // Values 0, 1 should still be cached
        assertEquals(Integer.valueOf(0), buffer.get(0));
        assertEquals(Integer.valueOf(10), buffer.get(1));

        // Values 2, 3, 4 should be invalidated
        assertNull(buffer.get(2));
        assertNull(buffer.get(3));
        assertNull(buffer.get(4));
    }

    @Test
    public void testInvalidateFromNegativeClearsAll() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        buffer.put(0, 100);
        buffer.put(1, 200);

        buffer.invalidateFrom(-1);

        assertEquals(-1, buffer.getFirstCachedIndex());
        assertEquals(-1, buffer.getHighestResultIndex());
    }

    @Test
    public void testInvalidateFromBeyondHighestIsNoOp() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        buffer.put(0, 100);
        buffer.put(1, 200);

        buffer.invalidateFrom(10);

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(1, buffer.getHighestResultIndex());
        assertEquals(Integer.valueOf(100), buffer.get(0));
        assertEquals(Integer.valueOf(200), buffer.get(1));
    }

    @Test
    public void testUnboundedBufferGrows() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(Integer.MAX_VALUE);

        // Store values at large indices
        buffer.put(0, 0);
        buffer.put(1000, 1000);

        assertEquals(Integer.valueOf(0), buffer.get(0));
        assertEquals(Integer.valueOf(1000), buffer.get(1000));
        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(1000, buffer.getHighestResultIndex());
    }

    @Test
    public void testIsInRange() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        assertFalse(buffer.isInRange(0));

        buffer.put(5, 50);
        buffer.put(7, 70);

        assertTrue(buffer.isInRange(5));
        assertTrue(buffer.isInRange(6));
        assertTrue(buffer.isInRange(7));
        assertFalse(buffer.isInRange(4));
        assertFalse(buffer.isInRange(8));
    }

    @Test
    public void testStoreBeforeFirstCachedIndexInBoundedBuffer() {
        // Test storing an index before firstCachedIndex in a bounded buffer
        // This is the bug case: the slot mapping becomes inconsistent
        CachedBuffer<Integer> buffer = new CachedBuffer<>(5);

        // First, store values at indices 10, 11, 12, 13, 14 (fills capacity)
        for (int i = 10; i < 15; i++) {
            buffer.put(i, i * 100);
        }

        assertEquals(10, buffer.getFirstCachedIndex());
        assertEquals(14, buffer.getHighestResultIndex());

        // Verify initial values are correct
        assertEquals(Integer.valueOf(1000), buffer.get(10));
        assertEquals(Integer.valueOf(1100), buffer.get(11));
        assertEquals(Integer.valueOf(1200), buffer.get(12));
        assertEquals(Integer.valueOf(1300), buffer.get(13));
        assertEquals(Integer.valueOf(1400), buffer.get(14));

        // Now store at index 8 (before firstCachedIndex). This should clear the buffer
        // and restart since slot mapping would be broken
        buffer.put(8, 800);

        // The value at index 8 should be retrievable
        assertEquals(Integer.valueOf(800), buffer.get(8));

        Integer val10 = buffer.get(10);
        Integer val11 = buffer.get(11);

        // These should either be null (buffer was cleared) or correct values (buffer
        // rebuilt)
        // They should NOT be wrong values due to slot mapping inconsistency
        if (val10 != null) {
            assertEquals("Value at index 10 should be correct if still cached", Integer.valueOf(1000), val10);
        }
        if (val11 != null) {
            assertEquals("Value at index 11 should be correct if still cached", Integer.valueOf(1100), val11);
        }
    }

    @Test
    public void testStoreBeforeFirstCachedIndexWithEviction() {
        // Bounded buffer with backward insert should rebuild to avoid stale slots
        CachedBuffer<Integer> buffer = new CachedBuffer<>(3);

        // Store at indices 5, 6, 7 (fills capacity of 3)
        buffer.put(5, 500);
        buffer.put(6, 600);
        buffer.put(7, 700);

        assertEquals(5, buffer.getFirstCachedIndex());
        assertEquals(7, buffer.getHighestResultIndex());

        // Insert before firstCachedIndex; buffer should rebuild and keep consistency
        buffer.put(2, 200);

        // The value at index 2 should be retrievable
        assertEquals(Integer.valueOf(200), buffer.get(2));

        // After rebuild, firstCachedIndex should be 2 and highestResultIndex should
        // not exceed capacity window
        assertEquals(2, buffer.getFirstCachedIndex());
        assertTrue(buffer.getHighestResultIndex() >= 2 && buffer.getHighestResultIndex() <= 4);

        // Any retained overlapping values must be correct, not stale
        Integer val3 = buffer.get(3);
        Integer val4 = buffer.get(4);
        // Indices 3 and 4 were never stored, so they must be null
        assertNull("Index 3 was never stored, should be null", val3);
        assertNull("Index 4 was never stored, should be null", val4);
    }

    @Test
    public void testStoreBeforeFirstCachedIndexSlotMappingConsistency() {
        // This test specifically verifies that slot mapping remains consistent
        // after storing before firstCachedIndex
        CachedBuffer<Integer> buffer = new CachedBuffer<>(3);

        // Store at indices 5, 6, 7
        // Slot mapping: index 5 -> slot 0, index 6 -> slot 1, index 7 -> slot 2
        buffer.put(5, 555);
        buffer.put(6, 666);
        buffer.put(7, 777);

        // Verify initial state
        assertEquals(Integer.valueOf(555), buffer.get(5));
        assertEquals(Integer.valueOf(666), buffer.get(6));
        assertEquals(Integer.valueOf(777), buffer.get(7));

        // Now store at index 3 (before firstCachedIndex=5)
        buffer.put(3, 333);

        // The value we just stored should be correct (slot 0 was overwritten)
        assertEquals("Value at index 3 should be what we stored", Integer.valueOf(333), buffer.get(3));

        Integer val4 = buffer.get(4);
        assertNull("Index 4 was never stored, must be null (not stale value from old index 6)", val4);

        if (buffer.isInRange(5)) {
            Integer val5 = buffer.get(5);
            if (val5 != null) {
                assertEquals("Index 5 should have its original value 555, not stale 777", Integer.valueOf(555), val5);
            }
        }
    }

    @Test
    public void testNullValueCaching() {
        // Test that null values are cached correctly and not recomputed
        CachedBuffer<String> buffer = new CachedBuffer<>(10);
        AtomicInteger computations = new AtomicInteger(0);

        // First call: compute null value
        String result1 = buffer.getOrCompute(5, i -> {
            computations.incrementAndGet();
            return null; // Legitimate null return value
        });

        assertNull("First call should return null", result1);
        assertEquals("Should have computed once", 1, computations.get());

        // Second call: should use cached null, not recompute
        String result2 = buffer.getOrCompute(5, i -> {
            computations.incrementAndGet();
            return null;
        });

        assertNull("Second call should return cached null", result2);
        assertEquals("Should not recompute - null is cached", 1, computations.get());

        // Verify get() also returns cached null
        String result3 = buffer.get(5);
        assertNull("get() should return cached null", result3);
    }

    @Test
    public void testIsCachedDistinguishesNotComputedFromCachedNull() {
        CachedBuffer<String> buffer = new CachedBuffer<>(10);

        // Index 5 has not been computed yet
        assertFalse("Index 5 should not be cached initially", buffer.isCached(5));
        assertNull("get() should return null for not-computed index", buffer.get(5));

        // Cache a null value at index 5
        buffer.getOrCompute(5, i -> null);

        // Now index 5 is cached (with null value)
        assertTrue("Index 5 should be cached after computation", buffer.isCached(5));
        assertNull("get() should still return null for cached null value", buffer.get(5));

        // Index 6 is still not cached
        assertFalse("Index 6 should not be cached", buffer.isCached(6));
    }

    @Test
    public void testIsCachedWithNonNullValues() {
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        assertFalse("Index should not be cached initially", buffer.isCached(3));

        buffer.put(3, 300);

        assertTrue("Index should be cached after put", buffer.isCached(3));
        assertEquals(Integer.valueOf(300), buffer.get(3));

        // Out-of-range index
        assertFalse("Out-of-range index should not be cached", buffer.isCached(100));
    }

    @Test
    public void testConcurrentBufferGrowthStress() throws InterruptedException {
        // Stress test: multiple threads concurrently writing to an unbounded buffer
        // that must grow. Modest thread count is sufficient to expose race conditions.
        CachedBuffer<Integer> buffer = new CachedBuffer<>(Integer.MAX_VALUE);
        int threads = 8;
        int operationsPerThread = 200;
        AtomicInteger computations = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    // Each thread writes to its own range of indices
                    int baseIndex = threadId * operationsPerThread;
                    for (int i = 0; i < operationsPerThread; i++) {
                        int index = baseIndex + i;
                        buffer.getOrCompute(index, x -> {
                            computations.incrementAndGet();
                            return x * 2;
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Stress test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // All indices should have been computed exactly once
        assertEquals("Each index should be computed exactly once", threads * operationsPerThread, computations.get());

        // Verify all values are correct
        for (int t = 0; t < threads; t++) {
            int baseIndex = t * operationsPerThread;
            for (int i = 0; i < operationsPerThread; i++) {
                int index = baseIndex + i;
                Integer value = buffer.get(index);
                assertEquals("Value at index " + index + " should be correct", Integer.valueOf(index * 2), value);
            }
        }
    }

    @Test
    public void testConcurrentReadWriteStress() throws InterruptedException {
        // Stress test: mix of readers and writers operating concurrently.
        // Moderate iteration count catches race conditions without excessive runtime.
        CachedBuffer<Integer> buffer = new CachedBuffer<>(100);
        int threads = 8;
        int iterationsPerThread = 500;
        AtomicInteger computations = new AtomicInteger(0);
        AtomicLong readChecksum = new AtomicLong(0);

        // Pre-populate some values
        for (int i = 0; i < 50; i++) {
            buffer.put(i, i * 10);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long localChecksum = 0;
                    for (int i = 0; i < iterationsPerThread; i++) {
                        if (threadId % 2 == 0) {
                            // Reader thread: read random indices
                            int index = (threadId * 7 + i) % 100;
                            Integer value = buffer.get(index);
                            if (value != null) {
                                localChecksum += value;
                            }
                        } else {
                            // Writer thread: compute or update values
                            int index = 50 + (threadId * 3 + i) % 50;
                            buffer.getOrCompute(index, x -> {
                                computations.incrementAndGet();
                                return x * 10;
                            });
                        }
                    }
                    readChecksum.addAndGet(localChecksum);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Mixed read/write stress test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        // Verify buffer integrity: all cached values should be consistent
        for (int i = 0; i < 100; i++) {
            Integer value = buffer.get(i);
            if (value != null) {
                assertEquals("Cached value at index " + i + " should be index * 10", Integer.valueOf(i * 10), value);
            }
        }
    }

    @Test
    public void testLargeGapForwardEvictionDoesNotCorruptRange() {
        // Test case for bug: when a large gap requires evicting more items than
        // actually cached, firstCachedIndex was advanced by the calculated evictCount
        // instead of the actual number of evicted items, corrupting the range.
        CachedBuffer<Integer> buffer = new CachedBuffer<>(5);

        // Cache values at indices 0, 1, 2 (3 entries, capacity is 5)
        buffer.put(0, 1000);
        buffer.put(1, 1001);
        buffer.put(2, 1002);

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(2, buffer.getHighestResultIndex());

        // Verify initial values
        assertEquals(Integer.valueOf(1000), buffer.get(0));
        assertEquals(Integer.valueOf(1001), buffer.get(1));
        assertEquals(Integer.valueOf(1002), buffer.get(2));

        // Now store at index 100 - a large jump.
        // newSize = (highestResultIndex - firstCachedIndex + 1) + gap
        // = (2 - 0 + 1) + (100 - 2) = 3 + 98 = 101
        // evictCount = 101 - 5 = 96
        // But we only have 3 cached entries (0, 1, 2)!
        // The loop should only clear 3 entries, not advance firstCachedIndex by 96.
        buffer.put(100, 2000);

        // After storing, the range should be valid:
        // - highestResultIndex should be 100
        assertEquals(100, buffer.getHighestResultIndex());

        // Critical assertion: when we evict all existing entries due to a large gap,
        // firstCachedIndex should be set to the new index (100), not an incorrect
        // value derived from adding evictCount to the old firstCachedIndex.
        // BUG: firstCachedIndex would be 96 (0 + 96) instead of 100
        assertEquals("When all entries are evicted by large gap, firstCachedIndex should equal new index", 100,
                buffer.getFirstCachedIndex());

        // The stored value should be correct
        assertEquals(Integer.valueOf(2000), buffer.get(100));

        // Indices in the "phantom" range (if bug existed) should not return values
        Integer val96 = buffer.get(96);
        Integer val97 = buffer.get(97);
        Integer val98 = buffer.get(98);
        Integer val99 = buffer.get(99);

        // All these should be null - they were never stored
        assertNull("Index 96 was never stored, must be null", val96);
        assertNull("Index 97 was never stored, must be null", val97);
        assertNull("Index 98 was never stored, must be null", val98);
        assertNull("Index 99 was never stored, must be null", val99);

        // Also verify that old indices are properly evicted
        assertNull("Index 0 should be evicted", buffer.get(0));
        assertNull("Index 1 should be evicted", buffer.get(1));
        assertNull("Index 2 should be evicted", buffer.get(2));
    }

    @Test
    public void testLargeGapForwardEvictionPreservesInvariant() {
        // Additional test: verify the invariant
        // highestResultIndex - firstCachedIndex + 1 <= capacity
        CachedBuffer<Integer> buffer = new CachedBuffer<>(10);

        // Cache 5 values
        for (int i = 0; i < 5; i++) {
            buffer.put(i, i * 100);
        }

        assertEquals(0, buffer.getFirstCachedIndex());
        assertEquals(4, buffer.getHighestResultIndex());

        // Jump to index 1000
        buffer.put(1000, 9999);

        int first = buffer.getFirstCachedIndex();
        int highest = buffer.getHighestResultIndex();
        int rangeSize = highest - first + 1;

        // The invariant must hold: rangeSize <= capacity (10)
        assertTrue("Range size (" + rangeSize + ") must be <= capacity (10)", rangeSize <= 10);

        // The new value should be retrievable
        assertEquals(Integer.valueOf(9999), buffer.get(1000));
    }

    @Test
    public void testOptimisticReadWithBufferGrowth() throws InterruptedException {
        // Test that optimistic reads work correctly when the buffer grows.
        // This verifies that using localBuffer.length (not capacity) for slot
        // calculation
        // is correct, since it matches the buffer we're actually reading from.
        CachedBuffer<Integer> buffer = new CachedBuffer<>(Integer.MAX_VALUE); // Unbounded
        int readers = 4;
        int writers = 2;
        int iterations = 2000;
        AtomicInteger incorrectReads = new AtomicInteger(0);
        AtomicInteger maxIndex = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(readers + writers);
        CountDownLatch ready = new CountDownLatch(readers + writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers + writers);

        // Writer threads that grow the buffer by writing at increasing indices
        for (int w = 0; w < writers; w++) {
            final int writerId = w;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        // Write at progressively larger indices to trigger buffer growth
                        int index = writerId * iterations + i;
                        buffer.put(index, index); // Store index as value for verification
                        maxIndex.updateAndGet(current -> Math.max(current, index));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Reader threads verify values are correct (value == index when present)
        for (int r = 0; r < readers; r++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterations * 2; i++) {
                        int index = i % (maxIndex.get() + 1);
                        Integer value = buffer.get(index);
                        // Value should be either null (not yet written or evicted) or equal to index
                        if (value != null && !value.equals(index)) {
                            incorrectReads.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Buffer growth read test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("No incorrect reads should occur during buffer growth", 0, incorrectReads.get());
    }

    @Test
    public void testOptimisticReadCorrectnessUnderContention() throws InterruptedException {
        // Test that optimistic reads never return wrong values under write contention.
        // Lower iteration count is sufficient to validate correctness without long
        // runtime.
        CachedBuffer<Integer> buffer = new CachedBuffer<>(1000);
        int readers = 4;
        int writers = 2;
        int iterations = 1000;
        AtomicInteger incorrectReads = new AtomicInteger(0);

        // Pre-populate with known values
        for (int i = 0; i < 1000; i++) {
            buffer.put(i, i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(readers + writers);
        CountDownLatch ready = new CountDownLatch(readers + writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers + writers);

        // Reader threads verify values are correct
        for (int r = 0; r < readers; r++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        int index = i % 1000;
                        Integer value = buffer.get(index);
                        // Value should be either null (evicted) or equal to index
                        if (value != null && !value.equals(index)) {
                            incorrectReads.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // Writer threads invalidate and recompute
        for (int w = 0; w < writers; w++) {
            final int writerId = w;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < iterations / 10; i++) {
                        int index = (writerId * 100 + i) % 1000;
                        buffer.invalidateFrom(index);
                        buffer.put(index, index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertTrue("Optimistic read test did not complete in time", done.await(60, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("No incorrect reads should occur under contention", 0, incorrectReads.get());
    }
}
