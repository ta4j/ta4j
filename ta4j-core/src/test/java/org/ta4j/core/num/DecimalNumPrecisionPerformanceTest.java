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
package org.ta4j.core.num;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Performance profiling for {@link DecimalNum} precision trade-offs.
 */
class DecimalNumPrecisionPerformanceTest {

    private static final int SAMPLE_SIZE = 512;
    private static final String[] SAMPLE_VALUES = IntStream.range(0, SAMPLE_SIZE)
            .mapToObj(DecimalNumPrecisionPerformanceTest::createSampleValue)
            .toArray(String[]::new);
    private static final List<Integer> PRECISIONS = List.of(8, 12, 16, 20, 24, 32, 48, 64);
    private static final int ITERATIONS = 200;
    private static final MathContext BASELINE_CONTEXT = new MathContext(64, RoundingMode.HALF_UP);

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
            sumSquared = (DecimalNum) sumSquared.plus((DecimalNum) value.multipliedBy(value));
            final var delta = (DecimalNum) value.minus(prev);
            volatility = (DecimalNum) volatility.plus((DecimalNum) delta.abs());
            ema = (DecimalNum) ((DecimalNum) ema.multipliedBy(decay)).plus((DecimalNum) value.multipliedBy(alpha));
            prev = value;
        }

        final var count = (DecimalNum) factory.numOf(Integer.valueOf(SAMPLE_VALUES.length));
        final var mean = (DecimalNum) sum.dividedBy(count);
        final var variance = (DecimalNum) sumSquared.dividedBy(count).minus((DecimalNum) mean.multipliedBy(mean));
        final var avgVolatility = (DecimalNum) volatility.dividedBy(count);
        return new Summary(ema, mean, variance, avgVolatility);
    }

    private static double nanosToMillis(final long nanos) {
        return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }

    private static String createSampleValue(final int index) {
        final double base = Math.sin(index / 10.0) * 250 + 1000 + index * 0.1;
        final var value = BigDecimal.valueOf(base).setScale(8, RoundingMode.HALF_UP);
        return value.toPlainString();
    }

    @AfterEach
    void restoreDefaults() {
        DecimalNum.resetDefaultPrecision();
    }

    @Test
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
        report.append("Precision,DurationMillis,RelativeDuration,MaxAbsoluteError\n");
        for (final var result : results) {
            final double millis = nanosToMillis(result.durationNanos());
            final double relative = millis / nanosToMillis(baseline.durationNanos());
            final double maxError = result.summary().maxAbsoluteError(baseline.summary()).doubleValue();
            report.append(
                    String.format(Locale.ROOT, "%d,%.4f,%.3f,%.10f%n", result.precision(), millis, relative, maxError));
        }

        System.out.println(report);
    }

    private BenchmarkResult benchmark(final int precision) {
        DecimalNum.configureDefaultPrecision(precision);
        try {
            final var factory = DecimalNumFactory.getInstance();
            Summary summary = null;
            final long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                summary = computeSummary(factory);
            }
            final long duration = System.nanoTime() - start;
            return new BenchmarkResult(precision, duration, summary);
        } finally {
            DecimalNum.resetDefaultPrecision();
        }
    }

    private record BenchmarkResult(int precision, long durationNanos, Summary summary) {
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
