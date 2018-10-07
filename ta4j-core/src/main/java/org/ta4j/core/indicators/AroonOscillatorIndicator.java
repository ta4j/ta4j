package org.ta4j.core.indicators;


import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

/**
 * Aroon Oscillator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator</a>
 */
public class AroonOscillatorIndicator extends CachedIndicator<Num>{

    private final AroonDownIndicator aroonDownIndicator;
    private final AroonUpIndicator aroonUpIndicator;
    private final int barCount;

    public AroonOscillatorIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        aroonDownIndicator = new AroonDownIndicator(series, barCount);
        aroonUpIndicator = new AroonUpIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return aroonUpIndicator.getValue(index).minus(aroonDownIndicator.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+" barCount: "+barCount;
    }
}
