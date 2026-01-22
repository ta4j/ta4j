/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.indicators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Regression coverage + opt-in perf harness for
 * {@link CachedIndicatorBenchmark}.
 */
class CachedIndicatorBenchmarkTest {

    private static final String BENCHMARK_PROPERTY = "ta4j.runBenchmarks";

    private final CachedIndicatorBenchmark benchmark = new CachedIndicatorBenchmark();

    @Test
    void boundedEvictionScenarioProducesExpectedChecksums() {
        int barCount = 64;
        int maximumBarCountHint = 12;

        CachedIndicatorBenchmark.ScenarioResult result = benchmark.runBoundedEvictionScenario(barCount,
                maximumBarCountHint);

        int endIndex = barCount - 1;
        long expectedOperations = endIndex;
        long expectedChecksum = expectedBoundedEvictionChecksum(endIndex);

        assertEquals(expectedOperations, result.getOperations(), "Operations should match processed indices");
        assertEquals(expectedChecksum, result.getChecksum(), "Checksum should reflect monotonic index values");
        assertTrue(result.getThroughputOpsPerSecond() > 0d, "Throughput should be positive");
    }

    @Test
    void concurrentCacheHitsScenarioReturnsStableCachedValue() {
        int barCount = 32;
        int threads = 2;
        int readsPerThread = 500;

        CachedIndicatorBenchmark.ScenarioResult result = benchmark.runConcurrentCacheHitsScenario(barCount, threads,
                readsPerThread);

        int hitIndex = Math.max(0, barCount - 2);
        long expectedOperations = (long) threads * readsPerThread;
        long expectedChecksum = expectedOperations * hitIndex;

        assertEquals(expectedOperations, result.getOperations(), "All concurrent reads should be counted");
        assertEquals(expectedChecksum, result.getChecksum(), "Cached indicator should always return the hit index");
        assertTrue(result.getThroughputOpsPerSecond() > 0d, "Concurrent scenario should report throughput");
    }

    @Test
    void lastBarHotReadsScenarioKeepsSmaStableAcrossHits() {
        int barCount = 48;
        int smaPeriod = 8;
        int reads = 750;

        CachedIndicatorBenchmark.ScenarioResult result = benchmark.runLastBarHotReadsScenario(barCount, smaPeriod,
                reads);

        var series = CachedIndicatorBenchmark.buildSeries(barCount);
        var closePrice = new ClosePriceIndicator(series);
        var sma = new SMAIndicator(closePrice, smaPeriod);
        int endIndex = series.getEndIndex();

        long expectedChecksum = (long) reads * sma.getValue(endIndex).hashCode();

        assertEquals(reads, result.getOperations(), "Each SMA read should be counted");
        assertEquals(expectedChecksum, result.getChecksum(), "Hot reads should return a stable cached SMA value");
        assertTrue(result.getThroughputOpsPerSecond() > 0d, "Hot-read scenario should report throughput");
    }

    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = BENCHMARK_PROPERTY, matches = "true")
    void benchmarksRunWhenExplicitlyEnabled() throws Exception {
        int threads = intProperty("ta4j.bench.cachedIndicator.threads", 8);
        int batches = intProperty("ta4j.bench.cachedIndicator.batches", 1);
        int evictionBars = intProperty("ta4j.bench.cachedIndicator.evictionBars", 50_000);
        int cacheHitsPerThread = intProperty("ta4j.bench.cachedIndicator.cacheHitsPerThread", 100_000);
        int lastBarReads = intProperty("ta4j.bench.cachedIndicator.lastBarReads", 100_000);
        int maximumBarCountHint = intProperty("ta4j.bench.cachedIndicator.maxBarCountHint", 256);
        int lastBarSmaPeriod = intProperty("ta4j.bench.cachedIndicator.lastBarSmaPeriod", 32);

        CachedIndicatorBenchmark.main(new String[] { Integer.toString(threads), Integer.toString(batches),
                Integer.toString(evictionBars), Integer.toString(cacheHitsPerThread), Integer.toString(lastBarReads),
                Integer.toString(maximumBarCountHint), Integer.toString(lastBarSmaPeriod) });
    }

    private long expectedBoundedEvictionChecksum(int endIndex) {
        // Sum of indices [0, endIndex)
        return (long) (endIndex - 1) * endIndex / 2;
    }

    private static int intProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }
}
