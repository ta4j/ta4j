package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.TimeSeries;

/**
 * Ichimoku clouds: Tenkan-sen (Conversion line) indicator
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuTenkanSenIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuTenkanSenIndicator(TimeSeries series) {
        this(series, 9);
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param barCount the time frame (usually 9)
     */
    public IchimokuTenkanSenIndicator(TimeSeries series, int barCount) {
        super(series, barCount);
    }
}
