/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon Oscillator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator</a>
 */
public class AroonOscillatorIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final AroonUpIndicator aroonUpIndicator;
    private final AroonDownIndicator aroonDownIndicator;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the number of periods used for the indicators
     */
    public AroonOscillatorIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.aroonUpIndicator = new AroonUpIndicator(series, barCount);
        this.aroonDownIndicator = new AroonDownIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return aroonUpIndicator.getValue(index).minus(aroonDownIndicator.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(aroonUpIndicator.getCountOfUnstableBars(), aroonDownIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    /** @return the {@link #aroonUpIndicator} */
    public AroonUpIndicator getAroonUpIndicator() {
        return aroonUpIndicator;
    }

    /** @return the {@link #aroonDownIndicator} */
    public AroonDownIndicator getAroonDownIndicator() {
        return aroonDownIndicator;
    }

}
