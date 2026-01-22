/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 * A fixed indicator.
 *
 * <p>
 * Returns constant values for a bar.
 *
 * @param <T> the type of returned constant values (Double, Boolean, etc.)
 */
public class FixedIndicator<T> extends AbstractIndicator<T> {

    private final List<T> values = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    @SafeVarargs
    public FixedIndicator(BarSeries series, T... values) {
        super(series);
        this.values.addAll(Arrays.asList(values));
    }

    /**
     * Adds the {@code value} to {@link #values}.
     *
     * @param value the value to add
     */
    public void addValue(T value) {
        this.values.add(value);
    }

    @Override
    public T getValue(int index) {
        return values.get(index);
    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
