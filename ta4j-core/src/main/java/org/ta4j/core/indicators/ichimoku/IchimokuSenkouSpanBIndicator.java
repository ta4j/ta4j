package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.TimeSeries;

/**
 * Ichimoku clouds: Senkou Span B (Leading Span B) indicator
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanBIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuSenkouSpanBIndicator(TimeSeries series) {
        super(series, 52);
    }

    /**
     * Constructor.
     * @param series the series
     * @param barCount the time frame (usually 52)
     */
    public IchimokuSenkouSpanBIndicator(TimeSeries series, int barCount) {
        super(series, barCount);
    }

}
