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
package org.ta4j.core.indicators.elliott;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Immutable representation of a single Elliott swing between two pivots.
 *
 * @since 0.22.0
 */
public record ElliottSwing(int fromIndex, int toIndex, Num fromPrice, Num toPrice, ElliottDegree degree) {

    public ElliottSwing {
        if (fromIndex < 0 || toIndex < 0) {
            throw new IllegalArgumentException("Swing indices must be non-negative");
        }
        if (fromIndex == toIndex) {
            throw new IllegalArgumentException("Swing indices must be different");
        }
        Objects.requireNonNull(fromPrice, "fromPrice");
        Objects.requireNonNull(toPrice, "toPrice");
        Objects.requireNonNull(degree, "degree");
    }

    /**
     * @return {@code true} if the swing is rising from the start pivot to the end
     *         pivot
     * @since 0.22.0
     */
    public boolean isRising() {
        return !toPrice.isLessThan(fromPrice);
    }

    /**
     * @return the absolute price displacement between both pivots
     * @since 0.22.0
     */
    public Num amplitude() {
        return toPrice.minus(fromPrice).abs();
    }

    /**
     * @return number of bars covered by the swing
     * @since 0.22.0
     */
    public int length() {
        return Math.abs(toIndex - fromIndex);
    }
}
