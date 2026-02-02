/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.donchian;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the 3 Donchian Channel indicators.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall,
 * there is less caching and probably better performance.
 *
 * @since 0.22.2
 */
public class DonchianChannelFacade {
    private final NumericIndicator lower;
    private final NumericIndicator upper;
    private final NumericIndicator middle;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     *
     * @since 0.22.2
     */
    public DonchianChannelFacade(BarSeries series, int barCount) {
        DonchianChannelLowerIndicator donchianChannelLowerIndicator = new DonchianChannelLowerIndicator(series,
                barCount);
        DonchianChannelUpperIndicator donchianChannelUpperIndicator = new DonchianChannelUpperIndicator(series,
                barCount);
        this.lower = NumericIndicator.of(donchianChannelLowerIndicator);
        this.upper = NumericIndicator.of(donchianChannelUpperIndicator);
        this.middle = NumericIndicator.of(new DonchianChannelMiddleIndicator(series, barCount,
                donchianChannelLowerIndicator, donchianChannelUpperIndicator));
    }

    /**
     * A fluent DonchianChannelLower indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelLowerIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator lower() {
        return NumericIndicator.of(lower);
    }

    /**
     * A fluent DonchianChannelUpper indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelUpperIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator upper() {
        return NumericIndicator.of(upper);
    }

    /**
     * A fluent DonchianChannelMiddle indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelMiddleIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator middle() {
        return NumericIndicator.of(middle);
    }

}
