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

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Encapsulates a projected Elliott price channel for the current bar.
 *
 * @param upper  expected resistance boundary
 * @param lower  expected support boundary
 * @param median arithmetic midline between upper and lower bounds
 * @since 0.22.0
 */
public record ElliottChannel(Num upper, Num lower, Num median) {

    /**
     * @return {@code true} when both channel boundaries are valid numbers.
     * @since 0.22.0
     */
    public boolean isValid() {
        return upper != null && lower != null && !upper.isNaN() && !lower.isNaN();
    }

    /**
     * Checks whether a price is contained within the channel, optionally extended
     * by a symmetric tolerance.
     *
     * @param price     price to test
     * @param tolerance optional symmetric tolerance around the boundaries
     * @return {@code true} if the price lies between {@code lower - tolerance} and
     *         {@code upper + tolerance}
     * @since 0.22.0
     */
    public boolean contains(final Num price, final Num tolerance) {
        if (!isValid() || price == null || price.isNaN()) {
            return false;
        }
        final NumFactory factory = lower.getNumFactory();
        final Num effectiveTolerance = tolerance == null ? factory.zero() : tolerance;
        if (effectiveTolerance.isNaN()) {
            return false;
        }
        final Num min = lower.minus(effectiveTolerance);
        final Num max = upper.plus(effectiveTolerance);
        return price.isGreaterThanOrEqual(min) && price.isLessThanOrEqual(max);
    }
}
