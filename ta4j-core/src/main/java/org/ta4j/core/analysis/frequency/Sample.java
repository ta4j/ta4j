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
package org.ta4j.core.analysis.frequency;

import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Single observation for frequency-aware statistics.
 *
 * <p>
 * Each sample includes the observed {@code value} alongside the elapsed time in
 * years ({@code deltaYears}) since the previous observation. The time delta is
 * used to derive annualization factors when summarizing unevenly spaced series.
 * </p>
 *
 * @param value      the observed numeric value
 * @param deltaYears the elapsed time in years since the previous observation
 * @since 0.22.2
 */
public record Sample(Num value, Num deltaYears) {
    /**
     * Creates a sample with the provided value and elapsed time in years.
     *
     * @param value      the observed numeric value
     * @param deltaYears the elapsed time in years since the previous observation
     * @throws NullPointerException if {@code value} or {@code deltaYears} is
     *                              {@code null}
     */
    public Sample {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(deltaYears, "deltaYears must not be null");
    }
}
