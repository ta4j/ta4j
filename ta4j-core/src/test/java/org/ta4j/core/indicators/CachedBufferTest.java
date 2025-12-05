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

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        // Test the specific bug case: bounded buffer with eviction needed
        CachedBuffer<Integer> buffer = new CachedBuffer<>(3);

        // Store at indices 5, 6, 7 (fills capacity of 3)
        buffer.put(5, 500);
        buffer.put(6, 600);
        buffer.put(7, 700);

        assertEquals(5, buffer.getFirstCachedIndex());
        assertEquals(7, buffer.getHighestResultIndex());

        // This triggers the problematic code path
        buffer.put(2, 200);

        // The value at index 2 should be retrievable
        assertEquals(Integer.valueOf(200), buffer.get(2));

        // After the operation, firstCachedIndex should be 2
        assertEquals(2, buffer.getFirstCachedIndex());

        // The buffer can only hold 3 values, so indices would be 2, 3, 4 at most
        assertTrue("highestResultIndex should be reasonable",
                buffer.getHighestResultIndex() >= 2 && buffer.getHighestResultIndex() <= 4);

        // Test that values outside the valid range return null
        if (buffer.getHighestResultIndex() < 5) {
            assertNull("Index 5 should not be cached after eviction", buffer.get(5));
        }
        if (buffer.getHighestResultIndex() < 6) {
            assertNull("Index 6 should not be cached after eviction", buffer.get(6));
        }
        if (buffer.getHighestResultIndex() < 7) {
            assertNull("Index 7 should not be cached after eviction", buffer.get(7));
        }
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
}
