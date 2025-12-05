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
            int idx = i;
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
            int idx = i;
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
            int idx = i;
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
}
