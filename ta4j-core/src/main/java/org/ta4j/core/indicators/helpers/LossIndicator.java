/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Loss indicator.
 *
 * <p>
 * Returns the difference of the indicator value of a bar and its previous bar
 * if the indicator value of the current bar is less than the indicator value of
 * the previous bar (otherwise, {@code Num.zero()} is returned).
 */
public class LossIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     */
    public LossIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }
        Num actualValue = indicator.getValue(index);
        Num previousValue = indicator.getValue(index - 1);
        return actualValue.isLessThan(previousValue) ? previousValue.minus(actualValue)
                : getBarSeries().numFactory().zero();
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
