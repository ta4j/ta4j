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

import org.junit.Test;

/**
 * Smoke test for {@link CachedIndicatorBenchmark}.
 */
public class CachedIndicatorBenchmarkTest {

    @Test
    public void runSmokeBenchmark() throws Exception {
        int threads = intProperty("ta4j.bench.cachedIndicator.threads", 8);
        int batches = intProperty("ta4j.bench.cachedIndicator.batches", 1);
        int evictionBars = intProperty("ta4j.bench.cachedIndicator.evictionBars", 10_000);
        int cacheHitsPerThread = intProperty("ta4j.bench.cachedIndicator.cacheHitsPerThread", 50_000);
        int lastBarReads = intProperty("ta4j.bench.cachedIndicator.lastBarReads", 50_000);
        int maximumBarCountHint = intProperty("ta4j.bench.cachedIndicator.maxBarCountHint", 128);
        int lastBarSmaPeriod = intProperty("ta4j.bench.cachedIndicator.lastBarSmaPeriod", 20);

        CachedIndicatorBenchmark.main(new String[] { Integer.toString(threads), Integer.toString(batches),
                Integer.toString(evictionBars), Integer.toString(cacheHitsPerThread), Integer.toString(lastBarReads),
                Integer.toString(maximumBarCountHint), Integer.toString(lastBarSmaPeriod) });
    }

    private static int intProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }
}
