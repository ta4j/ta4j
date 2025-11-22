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
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Streak indicator.
 *
 * <p>
 * Tracks consecutive up or down movements in the indicator values. Returns
 * positive integers (1, 2, 3, ...) for consecutive up movements, negative
 * integers (-1, -2, -3, ...) for consecutive down movements, and zero when the
 * value doesn't change. Returns NaN during the unstable period (first bar).
 *
 * <p>
 * The streak resets when the direction changes. For example, if the indicator
 * values are [10, 11, 12, 11, 10], the streak values would be [0, 1, 2, -1,
 * -2].
 *
 * @since 0.20
 */
public class StreakIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator} to track streaks for
     * @since 0.20
     */
    public StreakIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN during unstable period
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        int beginIndex = getBarSeries().getBeginIndex();
        Num current = indicator.getValue(index);

        if (index <= beginIndex) {
            return getBarSeries().numFactory().zero();
        }
        Num previousPrice = indicator.getValue(index - 1);

        Num priceChange = current.minus(previousPrice);
        if (priceChange.isZero()) {
            return getBarSeries().numFactory().zero();
        }
        Num previousStreak = getValue(index - 1);

        Num one = getBarSeries().numFactory().one();
        if (priceChange.isPositive()) {
            return previousStreak.isPositive() ? previousStreak.plus(one) : one;
        }
        Num negativeOne = one.negate();
        return previousStreak.isNegative() ? previousStreak.minus(one) : negativeOne;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
