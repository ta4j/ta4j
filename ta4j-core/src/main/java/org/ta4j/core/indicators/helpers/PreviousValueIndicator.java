/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Returns the (n-th) previous value of an indicator.
 *
 * If the (n-th) previous index is below the first index from the bar series,
 * then {@link NaN#NaN} is returned.
 */
public class PreviousValueIndicator extends CachedIndicator<Num> {

    private final int n;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     */
    public PreviousValueIndicator(Indicator<Num> indicator) {
        this(indicator, 1);
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator from which to calculate the previous value
     * @param n         parameter defines the previous n-th value
     */
    public PreviousValueIndicator(Indicator<Num> indicator, int n) {
        super(indicator);
        if (n < 1) {
            throw new IllegalArgumentException("n must be positive number, but was: " + n);
        }
        this.n = n;
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        int previousIndex = index - n;
        return previousIndex < 0 ? NaN.NaN : indicator.getValue(previousIndex);
    }

    /** @return {@link #n} */
    @Override
    public int getCountOfUnstableBars() {
        return n;
    }

    @Override
    public String toString() {
        final String nInfo = n == 1 ? "" : "(" + n + ")";
        return getClass().getSimpleName() + nInfo + "[" + this.indicator + "]";
    }
}
