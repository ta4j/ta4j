package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.TimeSeries;

/**
 * The Class IchimokuTenkanSenIndicator.
 */
public class IchimokuTenkanSenIndicator extends AbstractIchimokuLineIndicator {

    /**
     * Instantiates a new Ichimoku Tenkan-sen indicator.
     *
     * @param series the series
     */
    public IchimokuTenkanSenIndicator(TimeSeries series) {
        this(series, 9);
    }
    
    /**
     * Instantiates a new Ichimoku Tenkan-sen indicator.
     *
     * @param series the series
     * @param timeFrame the time frame (usually 9)
     */
    public IchimokuTenkanSenIndicator(TimeSeries series, int timeFrame) {
        super(series, timeFrame);
    }

}
