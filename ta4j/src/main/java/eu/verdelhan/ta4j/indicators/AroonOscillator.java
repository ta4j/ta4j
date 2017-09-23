package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;

/**
 * Aroon Oscillator.
 * <p>
 * @see !http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator
 */
public class AroonOscillator extends CachedIndicator<Decimal>{

    private final AroonDownIndicator aroonDownIndicator;
    private final AroonUpIndicator aroonUpIndicator;

    public AroonOscillator(TimeSeries series, int timeFrame) {
        super(series);
        aroonDownIndicator = new AroonDownIndicator(series, timeFrame);
        aroonUpIndicator = new AroonUpIndicator(series, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return aroonUpIndicator.getValue(index).minus(aroonDownIndicator.getValue(index));
    }
}
