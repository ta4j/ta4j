/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;

/**
 * Calculates the difference between the current and the previous close price.
 *
 * <pre>
 * ClosePriceDifference = currentBarClosePrice - previousBarClosePrice
 * </pre>
 *
 * @deprecated Use {@link DifferenceIndicator} with {@link ClosePriceIndicator}
 *             instead. This class will be removed in a future release.
 */
@Deprecated
public class ClosePriceDifferenceIndicator extends DifferenceIndicator {

    /**
     * Constructor.
     *
     * @param series the bar series
     * @deprecated Use
     *             {@code new DifferenceIndicator(new ClosePriceIndicator(series))}
     *             instead.
     */
    @Deprecated
    public ClosePriceDifferenceIndicator(BarSeries series) {
        super(new ClosePriceIndicator(series));
    }
}
