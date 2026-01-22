/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.num;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Performance profiling for {@link DecimalNum} precision trade-offs.
 */
class DecimalNumPrecisionPerformanceTest {

    private static final Logger LOG = LogManager.getLogger(DecimalNumPrecisionPerformanceTest.class);

    private static final int SAMPLE_SIZE = 512;
    private static final String[] SAMPLE_VALUES = IntStream.range(0, SAMPLE_SIZE)
            .mapToObj(DecimalNumPrecisionPerformanceTest::createSampleValue)
            .toArray(String[]::new);
    private static final List<Integer> PRECISIONS = List.of(8, 12, 16, 24, 32, 48, 64);
    private static final int WARMUP_ITERATIONS = 64;
    private static final int MEASUREMENT_ITERATIONS = 128;
    private static final int MEASUREMENT_REPETITIONS = 6;
    private static final MathContext BASELINE_CONTEXT = new MathContext(64, RoundingMode.HALF_UP);

    public static void main(String[] args) {
        new DecimalNumPrecisionPerformanceTest().quantifyPrecisionPerformanceTradeOffs();
    }

    private static Summary computeSummary(final NumFactory factory) {
        final var alpha = (DecimalNum) factory.numOf("0.2");
        final var one = (DecimalNum) factory.one();
        final var decay = (DecimalNum) one.minus(alpha);
        DecimalNum ema = (DecimalNum) factory.numOf(SAMPLE_VALUES[0]);
        DecimalNum prev = ema;
        DecimalNum sum = (DecimalNum) factory.zero();
        DecimalNum sumSquared = (DecimalNum) factory.zero();
        DecimalNum volatility = (DecimalNum) factory.zero();

        for (final var valueStr : SAMPLE_VALUES) {
            final var value = (DecimalNum) factory.numOf(valueStr);
            sum = (DecimalNum) sum.plus(value);
            sumSquared = (DecimalNum) sumSquared.plus(value.multipliedBy(value));
            final var delta = (DecimalNum) value.minus(prev);
            volatility = (DecimalNum) volatility.plus(delta.abs());
            ema = (DecimalNum) ema.multipliedBy(decay).plus(value.multipliedBy(alpha));
            prev = value;
        }

        final var count = (DecimalNum) factory.numOf(SAMPLE_VALUES.length);
        final var mean = (DecimalNum) sum.dividedBy(count);
        final var variance = (DecimalNum) sumSquared.dividedBy(count).minus(mean.multipliedBy(mean));
        final var avgVolatility = (DecimalNum) volatility.dividedBy(count);
        return new Summary(ema, mean, variance, avgVolatility);
    }

    private static double nanosToMillis(final double nanos) {
        return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }

    private static String createSampleValue(final int index) {
        final double base = Math.sin(index / 10.0) * 250 + 1000 + index * 0.1;
        final var value = BigDecimal.valueOf(base).setScale(8, RoundingMode.HALF_UP);
        return value.toPlainString();
    }

    void quantifyPrecisionPerformanceTradeOffs() {
        final var results = new ArrayList<BenchmarkResult>();
        for (final var precision : PRECISIONS) {
            results.add(benchmark(precision));
        }

        final var baseline = results.stream()
                .filter(result -> result.precision() == BASELINE_CONTEXT.getPrecision())
                .findFirst()
                .orElseThrow();

        final var report = new StringBuilder();
        final double baselineMillis = nanosToMillis(baseline.medianDurationNanos());
        report.append("Precision,MedianMillis,AvgMillis,MinMillis,MaxMillis,RelativeDuration,MaxAbsoluteError\n");
        for (final var result : results) {
            final double medianMillis = nanosToMillis(result.medianDurationNanos());
            final double avgMillis = nanosToMillis(result.averageDurationNanos());
            final double minMillis = nanosToMillis(result.minDurationNanos());
            final double maxMillis = nanosToMillis(result.maxDurationNanos());
            final double relative = medianMillis / baselineMillis;
            final double maxError = result.summary().maxAbsoluteError(baseline.summary()).doubleValue();
            report.append(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.4f,%.3f,%.10f%n", result.precision(),
                    medianMillis, avgMillis, minMillis, maxMillis, relative, maxError));
        }

        LOG.debug(report.toString());
    }

    private BenchmarkResult benchmark(final int precision) {
        DecimalNum.configureDefaultPrecision(precision);
        try {
            final var factory = DecimalNumFactory.getInstance();
            // Warm-up ensures JIT compilation happens before measurements start.
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                computeSummary(factory);
            }
            final long[] durations = new long[MEASUREMENT_REPETITIONS];
            Summary summary = null;
            for (int run = 0; run < MEASUREMENT_REPETITIONS; run++) {
                final long start = System.nanoTime();
                for (int iteration = 0; iteration < MEASUREMENT_ITERATIONS; iteration++) {
                    summary = computeSummary(factory);
                }
                durations[run] = System.nanoTime() - start;
            }
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            long total = 0;
            for (final long duration : durations) {
                total += duration;
                if (duration < min) {
                    min = duration;
                }
                if (duration > max) {
                    max = duration;
                }
            }
            final double average = total / (double) durations.length;
            final long[] sorted = durations.clone();
            Arrays.sort(sorted);
            final double median;
            final int middle = sorted.length / 2;
            if (sorted.length % 2 == 0) {
                median = (sorted[middle - 1] + sorted[middle]) / 2.0;
            } else {
                median = sorted[middle];
            }
            return new BenchmarkResult(precision, median, average, min, max, summary);
        } finally {
            DecimalNum.resetDefaultPrecision();
        }
    }

    private record BenchmarkResult(int precision, double medianDurationNanos, double averageDurationNanos,
            long minDurationNanos, long maxDurationNanos, Summary summary) {
    }

    private record Summary(DecimalNum ema, DecimalNum mean, DecimalNum variance, DecimalNum avgVolatility) {

        private static BigDecimal difference(final DecimalNum first, final DecimalNum second) {
            return first.bigDecimalValue().subtract(second.bigDecimalValue()).abs();
        }

        private static BigDecimal max(final BigDecimal... values) {
            var result = values[0];
            for (int i = 1; i < values.length; i++) {
                if (values[i].compareTo(result) > 0) {
                    result = values[i];
                }
            }
            return result;
        }

        BigDecimal maxAbsoluteError(final Summary baseline) {
            final var emaDiff = difference(ema, baseline.ema);
            final var meanDiff = difference(mean, baseline.mean);
            final var varianceDiff = difference(variance, baseline.variance);
            final var volatilityDiff = difference(avgVolatility, baseline.avgVolatility);
            return max(emaDiff, meanDiff, varianceDiff, volatilityDiff);
        }
    }
}
