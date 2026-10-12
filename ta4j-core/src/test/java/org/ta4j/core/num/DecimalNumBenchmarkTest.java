/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.MathContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class DecimalNumBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(DecimalNumBenchmarkTest.class);
    private static final int ITERATIONS = 100_000;

    @Test
    void benchmarkDecimalNumSqrtVariousPrecisions() {
        int[] precisions = { 16, 32, 64 };
        for (int precision : precisions) {
            MathContext mc = new MathContext(precision);
            Num[] numbers = new Num[100];
            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = DecimalNum.valueOf(String.format("%d.123456789012345678901234567890", i + 1), mc);
            }

            // Warmup
            for (int i = 0; i < 50_000; i++) {
                assertTrue(numbers[i % numbers.length].sqrt().doubleValue() > 0);
            }

            long start = System.nanoTime();
            double sum = 0.0;
            for (int i = 0; i < ITERATIONS; i++) {
                sum += numbers[i % numbers.length].sqrt().doubleValue();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(sum > 0);
            LOG.info("DecimalNum.sqrt benchmark (precision {}): {} iterations -> {} ms ({} ops/sec)", precision,
                    ITERATIONS, elapsedMs, ITERATIONS * 1000L / Math.max(1L, elapsedMs));
        }
    }
}
