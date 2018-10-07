package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Acceleration-deceleration indicator.
 * </p>
 */
public class AccelerationDecelerationIndicator extends CachedIndicator<Num> {
    
    private AwesomeOscillatorIndicator awesome;
    
    private SMAIndicator sma5;

    public AccelerationDecelerationIndicator(TimeSeries series, int barCountSma1, int barCountSma2) {
        super(series);
        this.awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), barCountSma1, barCountSma2);
        this.sma5 = new SMAIndicator(awesome, barCountSma1);
    }
    
    public AccelerationDecelerationIndicator(TimeSeries series) {
        this(series, 5, 34);
    }
    
    @Override
    protected Num calculate(int index) {
        return awesome.getValue(index).minus(sma5.getValue(index));
    }
}
