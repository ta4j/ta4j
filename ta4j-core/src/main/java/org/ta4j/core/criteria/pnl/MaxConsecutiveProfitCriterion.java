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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.num.Num;

/**
 * Analysis criterion that finds the most profitable streak of consecutive
 * positions.
 *
 * <p>
 * The criterion sums profits across positive positions and returns the highest
 * value reached.
 * </p>
 *
 * @since 0.19
 */
public class MaxConsecutiveProfitCriterion extends AbstractConsecutivePnlCriterion {

    @Override
    protected boolean accepts(Num profit) {
        return profit.isPositive();
    }

    @Override
    protected boolean preferForStreak(Num candidate, Num best) {
        return candidate.isGreaterThan(best);
    }

    /**
     * Indicates whether the first profit streak is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a larger gain)
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
