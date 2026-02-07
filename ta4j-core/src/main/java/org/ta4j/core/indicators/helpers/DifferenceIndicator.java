/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Calculates the difference between the current and the previous value of an
 * indicator.
 *
 * <pre>
 * Difference = currentValue - previousValue
 * </pre>
 *
 * @since 0.20
 */
public class DifferenceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator to calculate the difference for
     * @since 0.20
     */
    public DifferenceIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    /**
     * Calculates the difference between the current value and the previous value of
     * the indicator.
     *
     * @param index the index of the current bar
     * @return the difference between the current and previous values, or NaN if
     *         index is within the unstable bar period
     */
    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        // Get the value of the previous bar
        Num previousValue = indicator.getValue(index - 1);

        // Get the value of the current bar
        Num currentValue = indicator.getValue(index);

        // Calculate the difference between the values
        return currentValue.minus(previousValue);
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + 1;
    }
}
