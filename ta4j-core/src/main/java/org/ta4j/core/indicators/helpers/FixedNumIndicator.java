/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.math.BigDecimal;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * A fixed {@code Num} indicator.
 *
 * <p>
 * Returns constant {@link Num} values for a bar.
 */
public class FixedNumIndicator extends FixedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    public FixedNumIndicator(BarSeries series, double... values) {
        super(series);
        for (double value : values) {
            addValue(getBarSeries().numFactory().numOf(value));
        }
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    public FixedNumIndicator(BarSeries series, String... values) {
        super(series);
        for (String value : values) {
            addValue(getBarSeries().numFactory().numOf(new BigDecimal(value)));
        }
    }
}
