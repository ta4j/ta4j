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
package org.ta4j.core.criteria.helpers;

import java.util.Arrays;

/**
 * Utility for summary statistics.
 *
 * @since 0.22.2
 */
public final class Statistics {

    private Statistics() {
    }

    /**
     * Calculates a summary statistic for the provided values.
     *
     * @param values    values to summarize
     * @param statistic statistic to return
     * @return summary statistic result
     *
     * @since 0.22.2
     */
    public static double calculate(double[] values, Statistic statistic) {
        if (values.length == 0) {
            return 0;
        }
        return switch (statistic) {
        case MEDIAN -> percentile(values, 0.5);
        case P95 -> percentile(values, 0.95);
        case P99 -> percentile(values, 0.99);
        case MEAN -> Arrays.stream(values).average().orElse(0);
        case MIN -> Arrays.stream(values).min().orElse(0);
        case MAX -> Arrays.stream(values).max().orElse(0);
        };
    }

    private static double percentile(double[] values, double level) {
        var sorted = values.clone();
        Arrays.sort(sorted);
        var index = (int) Math.ceil(level * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }
}
