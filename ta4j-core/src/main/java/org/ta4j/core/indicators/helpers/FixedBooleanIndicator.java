/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;

/**
 * A fixed boolean indicator.
 *
 * <p>
 * Returns constant {@link Boolean} values for a bar.
 */
public class FixedBooleanIndicator extends FixedIndicator<Boolean> {

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    public FixedBooleanIndicator(BarSeries series, Boolean... values) {
        super(series, values);
    }
}
