/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * Constant indicator.
 *
 * <p>
 * Returns a constant value for a bar.
 */
public class ConstantIndicator<T> extends AbstractIndicator<T> {

    private final T value;

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param t      the constant value
     */
    public ConstantIndicator(BarSeries series, T t) {
        super(series);
        this.value = t;
    }

    @Override
    public T getValue(int index) {
        return value;
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Value: " + value;
    }
}
