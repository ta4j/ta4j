/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;

/**
 * Ichimoku clouds: Kijun-sen (Base line) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuKijunSenIndicator extends IchimokuLineIndicator {

    /**
     * Constructor with {@code barCount} = 26.
     *
     * @param series the bar series
     */
    public IchimokuKijunSenIndicator(BarSeries series) {
        super(series, 26);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (usually 26)
     */
    public IchimokuKijunSenIndicator(BarSeries series, int barCount) {
        super(series, barCount);
    }
}
