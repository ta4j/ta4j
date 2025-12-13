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
package ta4jexamples.indicators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Benchmark focused on the performance characteristics of
 * {@link CachedIndicator}.
 *
 * <p>
 * This is intended for before/after comparisons across branches: run this
 * benchmark on the feature branch, then checkout {@code master} and run it
 * again.
 *
 * <p>
 * Scenarios:
 * <ul>
 * <li>Bounded cache eviction (hot path for streaming/rolling windows)</li>
 * <li>Concurrent cache-hit reads (contention on read-mostly workloads)</li>
 * <li>Repeated reads of the last bar (common in live feeds where the last bar
 * is queried frequently)</li>
 * </ul>
 *
 * @since 0.22.0
 */
public class CachedIndicatorBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(CachedIndicatorBenchmarkTest.class);

    private static final int DEFAULT_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_BATCHES = 3;

    private static final int DEFAULT_EVICTION_BAR_COUNT = 200_000;
    private static final int DEFAULT_MAXIMUM_BAR_COUNT_HINT = 512;

    private static final int DEFAULT_CACHE_HIT_READS_PER_THREAD = 1_000_000;
    private static final int DEFAULT_LAST_BAR_READS = 1_000_000;
    private static final int DEFAULT_LAST_BAR_SMA_PERIOD = 50;

    public static void main(String[] args) throws Exception {
        int threads = DEFAULT_THREADS;
        int batches = DEFAULT_BATCHES;
        int evictionBars = DEFAULT_EVICTION_BAR_COUNT;
        int cacheHitsPerThread = DEFAULT_CACHE_HIT_READS_PER_THREAD;
        int lastBarReads = DEFAULT_LAST_BAR_READS;
        int maximumBarCountHint = DEFAULT_MAXIMUM_BAR_COUNT_HINT;
        int lastBarSmaPeriod = DEFAULT_LAST_BAR_SMA_PERIOD;

        try {
            if (args.length > 0) {
                threads = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                batches = Integer.parseInt(args[1]);
            }
            if (args.length > 2) {
                evictionBars = Integer.parseInt(args[2]);
            }
            if (args.length > 3) {
                cacheHitsPerThread = Integer.parseInt(args[3]);
            }
            if (args.length > 4) {
                lastBarReads = Integer.parseInt(args[4]);
            }
            if (args.length > 5) {
                maximumBarCountHint = Integer.parseInt(args[5]);
            }
            if (args.length > 6) {
                lastBarSmaPeriod = Integer.parseInt(args[6]);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format in command-line arguments.");
            System.err.println(
                    "Usage: CachedIndicatorBenchmarkTest [threads] [batches] [evictionBars] [cacheHitsPerThread] [lastBarReads] [maximumBarCountHint] [lastBarSmaPeriod]");
            System.err.println("All arguments must be valid integers. Using default values.");
            LOG.warn("Invalid number format in command-line arguments, using defaults", e);
        }

        new CachedIndicatorBenchmarkTest().run(threads, batches, evictionBars, cacheHitsPerThread, lastBarReads,
                maximumBarCountHint, lastBarSmaPeriod);
    }

    @Test
    public void runSmokeBenchmark() throws Exception {
        int defaultThreads = Math.min(DEFAULT_THREADS, 8);
        int threads = intProperty("ta4j.bench.cachedIndicator.threads", defaultThreads);
        int batches = intProperty("ta4j.bench.cachedIndicator.batches", 1);
        int evictionBars = intProperty("ta4j.bench.cachedIndicator.evictionBars", 10_000);
        int cacheHitsPerThread = intProperty("ta4j.bench.cachedIndicator.cacheHitsPerThread", 50_000);
        int lastBarReads = intProperty("ta4j.bench.cachedIndicator.lastBarReads", 50_000);
        int maximumBarCountHint = intProperty("ta4j.bench.cachedIndicator.maxBarCountHint", 128);
        int lastBarSmaPeriod = intProperty("ta4j.bench.cachedIndicator.lastBarSmaPeriod", 20);

        run(threads, batches, evictionBars, cacheHitsPerThread, lastBarReads, maximumBarCountHint, lastBarSmaPeriod);
    }

    private void run(int threads, int batches, int evictionBars, int cacheHitsPerThread, int lastBarReads,
            int maximumBarCountHint, int lastBarSmaPeriod) throws Exception {
        LOG.info(
                "Starting CachedIndicator benchmark: threads={}, batches={}, evictionBars={}, cacheHitsPerThread={}, lastBarReads={}, maxBarCountHint={}, lastBarSmaPeriod={}",
                threads, batches, formatLong(evictionBars), formatLong(cacheHitsPerThread), formatLong(lastBarReads),
                formatLong(maximumBarCountHint), formatLong(lastBarSmaPeriod));

        var evictionSeries = buildSeries(evictionBars);
        var cacheHitSeries = buildSeries(Math.max(5_000, lastBarSmaPeriod + 2));
        var lastBarSeries = buildSeries(Math.max(5_000, lastBarSmaPeriod + 2));

        Map<String, ScenarioStats> statsByScenario = new HashMap<>();
        for (int batch = 1; batch <= batches; batch++) {
            runScenario("Bounded eviction (monotonic indices)", batch, statsByScenario,
                    () -> benchmarkBoundedEviction(evictionSeries, maximumBarCountHint));
            runScenario("Concurrent cache hits (same index)", batch, statsByScenario,
                    () -> benchmarkConcurrentCacheHits(cacheHitSeries, threads, cacheHitsPerThread));
            runScenario("Last bar hot reads (SMA)", batch, statsByScenario,
                    () -> benchmarkLastBarHotReads(lastBarSeries, lastBarSmaPeriod, lastBarReads));
        }

        logSummary(statsByScenario);
    }

    private void runScenario(String name, int batch, Map<String, ScenarioStats> statsByScenario,
            Supplier<ScenarioResult> runner) throws Exception {
        LOG.info("Batch {}: {}", batch, name);

        ScenarioResult result = runner.get();
        ScenarioStats stats = statsByScenario.computeIfAbsent(name, ignored -> new ScenarioStats());
        stats.add(result);

        LOG.info("  duration={} ms, ops={}, throughput={} ops/s, checksum={}", formatMillis(result.durationNanos),
                formatLong(result.operations), formatDouble(result.throughputOpsPerSecond), result.checksum);
    }

    private void logSummary(Map<String, ScenarioStats> statsByScenario) {
        LOG.info("CachedIndicator benchmark summary (averages across batches):");
        for (Map.Entry<String, ScenarioStats> entry : statsByScenario.entrySet()) {
            ScenarioStats stats = entry.getValue();
            LOG.info("  {}: avgDuration={} ms, avgThroughput={} ops/s (runs={})", entry.getKey(),
                    formatMillis(stats.averageDurationNanos()), formatDouble(stats.averageThroughputOpsPerSecond()),
                    stats.runs);
        }
    }

    private ScenarioResult benchmarkBoundedEviction(BarSeries baseSeries, int maximumBarCountHint) {
        BarSeries series = new MaxBarCountHintSeries(baseSeries, maximumBarCountHint);
        Indicator<Integer> indicator = new IndexIndicator(series);
        int endIndex = series.getEndIndex();

        // Warm-up the cache machinery a bit (avoid timing the "first use" path).
        for (int i = 0; i < Math.min(endIndex, maximumBarCountHint + 16); i++) {
            indicator.getValue(i);
        }

        long checksum = 0;
        int operations = Math.max(0, endIndex);

        long startNanos = System.nanoTime();
        for (int i = 0; i < endIndex; i++) {
            checksum += indicator.getValue(i);
        }
        long durationNanos = System.nanoTime() - startNanos;

        assertTrue(endIndex >= 1);
        assertEquals(Integer.valueOf(endIndex - 1), indicator.getValue(endIndex - 1));

        return new ScenarioResult(operations, durationNanos, checksum);
    }

    private ScenarioResult benchmarkConcurrentCacheHits(BarSeries baseSeries, int threads, int readsPerThread) {
        BarSeries series = new MaxBarCountHintSeries(baseSeries, Integer.MAX_VALUE);
        Indicator<Integer> indicator = new IndexIndicator(series);
        int hitIndex = Math.max(0, series.getEndIndex() - 1);
        indicator.getValue(hitIndex);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            CompletableFuture<Long>[] futures = new CompletableFuture[threads];
            for (int i = 0; i < threads; i++) {
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return 0L;
                    }
                    long localChecksum = 0;
                    for (int j = 0; j < readsPerThread; j++) {
                        localChecksum += indicator.getValue(hitIndex);
                    }
                    done.countDown();
                    return localChecksum;
                }, pool);
            }

            awaitLatch(ready, Duration.ofSeconds(30), "workers to become ready");

            long startNanos = System.nanoTime();
            start.countDown();
            awaitLatch(done, Duration.ofMinutes(2), "workers to finish");
            long durationNanos = System.nanoTime() - startNanos;

            long checksum = 0;
            for (CompletableFuture<Long> future : futures) {
                checksum += future.join();
            }

            long operations = ((long) threads) * readsPerThread;
            return new ScenarioResult(operations, durationNanos, checksum);
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ScenarioResult benchmarkLastBarHotReads(BarSeries baseSeries, int smaPeriod, int reads) {
        BarSeries series = new MaxBarCountHintSeries(baseSeries, Integer.MAX_VALUE);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, smaPeriod);

        int endIndex = series.getEndIndex();

        // Warm up: compute once so cache-hit behavior is measured.
        Num expected = sma.getValue(endIndex);

        long checksum = 0;
        long startNanos = System.nanoTime();
        for (int i = 0; i < reads; i++) {
            Num value = sma.getValue(endIndex);
            checksum += value.hashCode();
        }
        long durationNanos = System.nanoTime() - startNanos;

        assertEquals(expected, sma.getValue(endIndex));

        return new ScenarioResult(reads, durationNanos, checksum);
    }

    private static BarSeries buildSeries(int barCount) {
        var numFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        for (int i = 0; i < barCount; i++) {
            series.barBuilder().closePrice(i + 1d).volume(1d).add();
        }
        return series;
    }

    private static void awaitLatch(CountDownLatch latch, Duration timeout, String what) {
        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            assertTrue("Timed out waiting for " + what, completed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + what, e);
        }
    }

    private static String formatLong(long value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }

    private static String formatMillis(double nanos) {
        double millis = nanos / 1_000_000d;
        return NumberFormat.getNumberInstance(Locale.US).format(millis);
    }

    private static String formatDouble(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private static int intProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value for system property '{}': '{}'. Using default value: {}", key, value,
                    defaultValue, e);
            return defaultValue;
        }
    }

    private static final class ScenarioResult {

        private final long operations;
        private final long durationNanos;
        private final long checksum;
        private final double throughputOpsPerSecond;

        private ScenarioResult(long operations, long durationNanos, long checksum) {
            this.operations = operations;
            this.durationNanos = durationNanos;
            this.checksum = checksum;
            this.throughputOpsPerSecond = operations / (durationNanos / 1_000_000_000d);
        }
    }

    private static final class ScenarioStats {

        private long runs;
        private double totalDurationNanos;
        private double totalThroughputOpsPerSecond;

        private void add(ScenarioResult result) {
            runs++;
            totalDurationNanos += result.durationNanos;
            totalThroughputOpsPerSecond += result.throughputOpsPerSecond;
        }

        private double averageDurationNanos() {
            return totalDurationNanos / runs;
        }

        private double averageThroughputOpsPerSecond() {
            return totalThroughputOpsPerSecond / runs;
        }
    }

    private static final class IndexIndicator extends CachedIndicator<Integer> {

        private IndexIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Integer calculate(int index) {
            return index;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class MaxBarCountHintSeries implements BarSeries {

        private static final long serialVersionUID = 3793483697719901088L;

        private final BarSeries delegate;
        private final int maximumBarCountHint;

        private MaxBarCountHintSeries(BarSeries delegate, int maximumBarCountHint) {
            this.delegate = delegate;
            this.maximumBarCountHint = maximumBarCountHint;
        }

        @Override
        public NumFactory numFactory() {
            return delegate.numFactory();
        }

        @Override
        public BarBuilder barBuilder() {
            return delegate.barBuilder();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Bar getBar(int i) {
            return delegate.getBar(i);
        }

        @Override
        public int getBarCount() {
            return delegate.getBarCount();
        }

        @Override
        public List<Bar> getBarData() {
            return delegate.getBarData();
        }

        @Override
        public int getBeginIndex() {
            return delegate.getBeginIndex();
        }

        @Override
        public int getEndIndex() {
            return delegate.getEndIndex();
        }

        @Override
        public int getMaximumBarCount() {
            return maximumBarCountHint;
        }

        @Override
        public void setMaximumBarCount(int maximumBarCount) {
            throw new UnsupportedOperationException("Maximum bar count is a hint-only override for benchmarking");
        }

        @Override
        public int getRemovedBarsCount() {
            return delegate.getRemovedBarsCount();
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            delegate.addBar(bar, replace);
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            delegate.addTrade(tradeVolume, tradePrice);
        }

        @Override
        public void addPrice(Num price) {
            delegate.addPrice(price);
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            return delegate.getSubSeries(startIndex, endIndex);
        }
    }
}
