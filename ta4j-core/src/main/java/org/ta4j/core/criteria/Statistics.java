/*
 * SPDX-License-Identifier: MIT
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
        var zero = numFactory.zero();
        if (values.length == 0) {
            return zero;
        }
        return switch (this) {
        case MEDIAN -> percentile(values, 0.5);
        case P95 -> percentile(values, 0.95);
        case P99 -> percentile(values, 0.99);
        case MEAN -> mean(numFactory, values);
        case MIN -> Arrays.stream(values).min(Num::compareTo).orElse(zero);
        case MAX -> Arrays.stream(values).max(Num::compareTo).orElse(zero);
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
