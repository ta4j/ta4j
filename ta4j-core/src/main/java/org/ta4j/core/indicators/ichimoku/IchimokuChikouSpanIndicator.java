package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Chikou Span indicator
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuChikouSpanIndicator extends CachedIndicator<Num> {

    /** The close price */
    private final ClosePriceIndicator closePriceIndicator;
    
    /** The time delay */
    private final int timeDelay;
    
    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuChikouSpanIndicator(TimeSeries series) {
        this(series, 26);
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param timeDelay the time delay (usually 26)
     */
    public IchimokuChikouSpanIndicator(TimeSeries series, int timeDelay) {
        super(series);
        closePriceIndicator = new ClosePriceIndicator(series);
        this.timeDelay = timeDelay;
    }

    @Override
    protected Num calculate(int index) {
        return closePriceIndicator.getValue(Math.max(0, index - timeDelay));
    }

}
