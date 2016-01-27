package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.TimeSeries;

/**
 * The Class IchimokuSenkouSpanBIndicator.
 */
public class IchimokuSenkouSpanBIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Instantiates a new Ichimoku Senkou Span B indicator.
     *
     * @param series the series
     */
    public IchimokuSenkouSpanBIndicator(TimeSeries series) {
        super(series, 52);
    }

    /**
     * Instantiates a new Ichimoku Senkou Span B indicator.
     *
     * @param series the series
     * @param timeFrame the time frame (usually 52)
     */
    public IchimokuSenkouSpanBIndicator(TimeSeries series, int timeFrame) {
        super(series, timeFrame);
    }

}
