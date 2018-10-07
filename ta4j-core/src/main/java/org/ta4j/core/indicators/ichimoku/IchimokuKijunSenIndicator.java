package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.TimeSeries;

/**
 * Ichimoku clouds: Kijun-sen (Base line) indicator
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuKijunSenIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuKijunSenIndicator(TimeSeries series) {
        super(series, 26);
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param barCount the time frame (usually 26)
     */
    public IchimokuKijunSenIndicator(TimeSeries series, int barCount) {
        super(series, barCount);
    }
}
