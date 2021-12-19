package org.ta4j.core.indicators.numeric.facades;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AroonDownIndicator;
import org.ta4j.core.indicators.AroonUpIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the 2 Aroon indicators.
 * The Aroon Oscillator can also be created on demand.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. 
 */
public class Aroon {
    private final NumericIndicator up;
    private final NumericIndicator down;

    /**
     * Create the Aroon facade.
     * 
     * @param bs a bar series
     * @param n the number of periods (barCount) used for the indicators
     */
    public Aroon(BarSeries bs, int n) {
        this.up = NumericIndicator.of(new AroonUpIndicator(bs, n));
        this.down = NumericIndicator.of(new AroonDownIndicator(bs, n));
    }

    /**
     * A fluent AroonUp indicator. 
     * 
     * @return a NumericIndicator wrapped around a cached AroonUpIndicator
     */
    public NumericIndicator up() {
        return up;
    }

    /**
     * A fluent AroonDown indicator.
     *  
     * @return a NumericIndicator wrapped around a cached AroonDownIndicator
     */
    public NumericIndicator down() {
        return down;
    }

    /**
     * A lightweight fluent AroonOscillator.
     * 
     * @return an uncached object that calculates the difference between AoonUp and AroonDown
     */
    public NumericIndicator oscillator() {
        return up.minus(down);
    }


}
