/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.swing.ProminenceSwingConfig;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Reports the most recent confirmed swing low whose bounded topographic
 * prominence meets a configured threshold.
 *
 * <p>
 * This method is useful when a local minimum must also stand materially below
 * its surrounding baselines. Unlike ZigZag it does not require a full reversal
 * distance from the trough, and unlike a fractal it rejects locally dominant
 * but insignificant noise.
 *
 * @since 0.23.1
 */
public final class RecentProminenceSwingLowIndicator extends AbstractRecentProminenceSwingIndicator {

    /**
     * Creates an OHLC-aware indicator with
     * {@link ProminenceSwingConfig#defaults()}.
     *
     * @param series source bar series
     * @since 0.23.1
     */
    public RecentProminenceSwingLowIndicator(final BarSeries series) {
        this(series, ProminenceSwingConfig.defaults());
    }

    /**
     * Creates an OHLC-aware indicator with an ATR-scaled prominence threshold.
     *
     * @param series source bar series
     * @param config prominence configuration
     * @since 0.23.1
     */
    public RecentProminenceSwingLowIndicator(final BarSeries series, final ProminenceSwingConfig config) {
        this(new LowPriceIndicator(series), atrProminence(series, config), config);
    }

    /**
     * Creates an indicator using custom price and minimum-prominence sources.
     *
     * @param priceIndicator    candidate and baseline values
     * @param minimumProminence minimum required prominence in price units
     * @param config            prominence configuration
     * @since 0.23.1
     */
    public RecentProminenceSwingLowIndicator(final Indicator<Num> priceIndicator,
            final Indicator<Num> minimumProminence, final ProminenceSwingConfig config) {
        super(priceIndicator, minimumProminence, config, FractalDetectionHelper.Direction.LOW);
    }

    RecentProminenceSwingLowIndicator(final Indicator<Num> minimumProminence, final Indicator<Num> priceIndicator,
            final int lookbackBars, final int confirmationBars, final int allowedEqualBars, final int atrPeriod,
            final double minAtrProminence) {
        this(priceIndicator, minimumProminence, new ProminenceSwingConfig(lookbackBars, confirmationBars,
                allowedEqualBars, atrPeriod, minAtrProminence));
    }

    /**
     * @return prominence configuration
     * @since 0.23.1
     */
    public ProminenceSwingConfig getConfig() {
        return config();
    }
}
