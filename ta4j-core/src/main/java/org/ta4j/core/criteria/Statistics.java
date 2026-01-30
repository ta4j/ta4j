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
package org.ta4j.core.criteria;

import java.util.Arrays;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Summary statistics options and helpers.
 *
 * @since 0.22.2
 */
public enum Statistics {
    MEDIAN, P95, P99, MEAN, MIN, MAX;

    /**
     * Calculates a summary statistic for the provided values.
     *
     * @param numFactory num factory for zero and conversions
     * @param values     values to summarize
     * @return summary statistic result
     *
     * @since 0.22.2
     */
    public Num calculate(NumFactory numFactory, Num[] values) {
        if (values.length == 0) {
            return numFactory.zero();
        }
        return switch (this) {
        case MEDIAN -> percentile(values, 0.5);
        case P95 -> percentile(values, 0.95);
        case P99 -> percentile(values, 0.99);
        case MEAN -> mean(numFactory, values);
        case MIN -> Arrays.stream(values).min(Num::compareTo).orElse(numFactory.zero());
        case MAX -> Arrays.stream(values).max(Num::compareTo).orElse(numFactory.zero());
        };
    }

    private Num mean(NumFactory numFactory, Num[] values) {
        var sum = numFactory.zero();
        for (var value : values) {
            sum = sum.plus(value);
        }
        return sum.dividedBy(numFactory.numOf(values.length));
    }

    private Num percentile(Num[] values, double level) {
        var sorted = Arrays.copyOf(values, values.length);
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
