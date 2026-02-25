/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * @deprecated use {@link org.ta4j.core.indicators.macd.MACDVIndicator}. This
 *             compatibility shim is scheduled for removal in 0.24.0.
 * @since 0.19
 */
@Deprecated(since = "0.22.3", forRemoval = true)
public class MACDVIndicator extends org.ta4j.core.indicators.macd.MACDVIndicator {

    {
        DeprecationNotifier.warnOnce(MACDVIndicator.class, org.ta4j.core.indicators.macd.MACDVIndicator.class.getName(),
                "0.24.0");
    }

    /**
     * Constructor with defaults.
     *
     * @param series the bar series
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series) {
        super(series);
    }

    /**
     * Constructor with defaults.
     *
     * @param priceIndicator the price indicator
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator) {
        super(priceIndicator);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(series, shortBarCount, longBarCount);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount, int signalBarCount) {
        super(series, shortBarCount, longBarCount, signalBarCount);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount) {
        super(priceIndicator, shortBarCount, longBarCount);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.3
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount, int signalBarCount) {
        super(priceIndicator, shortBarCount, longBarCount, signalBarCount);
    }
}
