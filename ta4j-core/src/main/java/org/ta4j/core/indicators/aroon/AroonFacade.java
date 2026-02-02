/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the two Aroon indicators. The Aroon Oscillator can also be
 * created on demand.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects.
 */
public class AroonFacade {

    private final NumericIndicator up;
    private final NumericIndicator down;

    /**
     * Create the Aroon facade.
     *
     * @param series   the bar series
     * @param barCount the number of periods used for the indicators
     */
    public AroonFacade(BarSeries series, int barCount) {
        this.up = NumericIndicator.of(new AroonUpIndicator(series, barCount));
        this.down = NumericIndicator.of(new AroonDownIndicator(series, barCount));
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
     * @return an uncached object that calculates the difference between AoonUp and
     *         AroonDown
     */
    public NumericIndicator oscillator() {
        return up.minus(down);
    }

}
