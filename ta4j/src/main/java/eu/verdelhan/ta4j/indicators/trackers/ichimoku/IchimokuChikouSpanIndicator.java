package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;

/**
 * The Class IchimokuChikouSpanIndicator.
 */
public class IchimokuChikouSpanIndicator extends CachedIndicator<Decimal> {

    /** The close price indicator. */
    private final ClosePriceIndicator closePriceIndicator;
    
    /** The time delay. */
    private final int timeDelay;
    
    /**
     * Instantiates a new ichimoku chikou span indicator.
     *
     * @param series the series
     */
    public IchimokuChikouSpanIndicator(TimeSeries series) {
        this(series, 26);
    }
    
    /**
     * Instantiates a new ichimoku chikou span indicator.
     *
     * @param series the series
     * @param timeDelay the time delay (usually 26)
     */
    public IchimokuChikouSpanIndicator(TimeSeries series, int timeDelay) {
        super(series);
        closePriceIndicator = new ClosePriceIndicator(series);
        this.timeDelay = timeDelay;
    }

    @Override
    protected Decimal calculate(int index) {
        return closePriceIndicator.getValue(Math.max(0, index - timeDelay));
    }

}
