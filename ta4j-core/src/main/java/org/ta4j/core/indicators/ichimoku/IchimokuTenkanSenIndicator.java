/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;

/**
 * Ichimoku clouds: Tenkan-sen (Conversion line) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuTenkanSenIndicator extends IchimokuLineIndicator {

    /**
     * Constructor with {@code barCount} = 9.
     *
     * @param series the bar series
     */
    public IchimokuTenkanSenIndicator(BarSeries series) {
        this(series, 9);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (usually 9)
     */
    public IchimokuTenkanSenIndicator(BarSeries series, int barCount) {
        super(series, barCount);
    }
}
