package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.TimeSeries;

/**
 * The Class IchimokuKijunSenIndicator.
 */
public class IchimokuKijunSenIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Instantiates a new Ichimoku Kijun-sen indicator.
     *
     * @param series the series
     */
    public IchimokuKijunSenIndicator(TimeSeries series) {
        super(series, 26);
    }
    
    /**
     * Instantiates a new Ichimoku Kijun-sen indicator.
     *
     * @param series the series
     * @param timeFrame the time frame (usually 26)
     */
    public IchimokuKijunSenIndicator(TimeSeries series, int timeFrame) {
        super(series, timeFrame);
    }

}
