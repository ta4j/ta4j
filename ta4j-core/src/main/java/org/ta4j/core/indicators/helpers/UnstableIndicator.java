/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Indicator that returns {@link NaN#NaN NaN} in unstable bars.
 */
public class UnstableIndicator extends CachedIndicator<Num> {

    private final int unstableBars;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator    the indicator
     * @param unstableBars the number of first bars of the barSeries to be unstable
     */
    public UnstableIndicator(Indicator<Num> indicator, int unstableBars) {
        super(indicator);
        this.indicator = indicator;
        this.unstableBars = unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < unstableBars) {
            return NaN.NaN;
        }
        return indicator.getValue(index);
    }

    /** @return {@link #unstableBars} */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(unstableBars, indicator.getCountOfUnstableBars());
    }
}
