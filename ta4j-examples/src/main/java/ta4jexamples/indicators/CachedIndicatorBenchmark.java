/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.indicators;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
public class CachedIndicatorBenchmark {

    private static final Logger LOG = LogManager.getLogger(CachedIndicatorBenchmark.class);

    private static final int DEFAULT_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_BATCHES = 3;

    private static final int DEFAULT_EVICTION_BAR_COUNT = 200_000;
    private static final int DEFAULT_MAXIMUM_BAR_COUNT_HINT = 512;

    private static final int DEFAULT_CACHE_HIT_READS_PER_THREAD = 1_000_000;
    private static final int DEFAULT_LAST_BAR_READS = 1_000_000;
    private static final int DEFAULT_LAST_BAR_SMA_PERIOD = 50;

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_THREADS;
        int batches = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_BATCHES;
        int evictionBars = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_EVICTION_BAR_COUNT;
        int cacheHitsPerThread = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_CACHE_HIT_READS_PER_THREAD;
        int lastBarReads = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_LAST_BAR_READS;
        int maximumBarCountHint = args.length > 5 ? Integer.parseInt(args[5]) : DEFAULT_MAXIMUM_BAR_COUNT_HINT;
        int lastBarSmaPeriod = args.length > 6 ? Integer.parseInt(args[6]) : DEFAULT_LAST_BAR_SMA_PERIOD;

        new CachedIndicatorBenchmark().run(threads, batches, evictionBars, cacheHitsPerThread, lastBarReads,
                maximumBarCountHint, lastBarSmaPeriod);
    }

    ScenarioResult runBoundedEvictionScenario(int barCount, int maximumBarCountHint) {
        BarSeries series = buildSeries(barCount);
        return benchmarkBoundedEviction(series, maximumBarCountHint);
    }

    ScenarioResult runConcurrentCacheHitsScenario(int barCount, int threads, int readsPerThread) {
        BarSeries series = buildSeries(barCount);
        return benchmarkConcurrentCacheHits(series, threads, readsPerThread);
    }

    ScenarioResult runLastBarHotReadsScenario(int barCount, int smaPeriod, int reads) {
        BarSeries series = buildSeries(barCount);
        return benchmarkLastBarHotReads(series, smaPeriod, reads);
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

        requireTrue(endIndex >= 1, "Series must contain at least one bar");
        requireEquals(Integer.valueOf(endIndex - 1), indicator.getValue(endIndex - 1), "Index indicator mismatch");

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

            List<CompletableFuture<Long>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
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
                }, pool));
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

            long operations = (long) threads * readsPerThread;
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

        requireEquals(expected, sma.getValue(endIndex), "Last bar SMA must remain stable across reads");

        return new ScenarioResult(reads, durationNanos, checksum);
    }

    static BarSeries buildSeries(int barCount) {
        var numFactory = DoubleNumFactory.getInstance();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        Duration timePeriod = Duration.ofDays(1);
        Instant endTime = Instant.EPOCH;
        for (int i = 0; i < barCount; i++) {
            endTime = endTime.plus(timePeriod);
            series.barBuilder()
                    .timePeriod(timePeriod)
                    .endTime(endTime)
                    .closePrice(i + 1d)
                    .openPrice(i + 1d)
                    .highPrice(i + 1d)
                    .lowPrice(i + 1d)
                    .volume(1d)
                    .add();
        }
        return series;
    }

    private static void awaitLatch(CountDownLatch latch, Duration timeout, String what) {
        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            requireTrue(completed, "Timed out waiting for " + what);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + what, e);
        }
    }

    private static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(message + " (expected=" + expected + ", actual=" + actual + ')');
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

    static final class ScenarioResult {

        private final long operations;
        private final long durationNanos;
        private final long checksum;
        private final double throughputOpsPerSecond;

        ScenarioResult(long operations, long durationNanos, long checksum) {
            this.operations = operations;
            this.durationNanos = durationNanos;
            this.checksum = checksum;
            this.throughputOpsPerSecond = operations / (durationNanos / 1_000_000_000d);
        }

        long getOperations() {
            return operations;
        }

        long getDurationNanos() {
            return durationNanos;
        }

        long getChecksum() {
            return checksum;
        }

        double getThroughputOpsPerSecond() {
            return throughputOpsPerSecond;
        }
    }

    private static final class ScenarioStats {

        private int runs;
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

    static final class IndexIndicator extends CachedIndicator<Integer> {

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

    static final class MaxBarCountHintSeries implements BarSeries {

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
