/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Acceleration-deceleration indicator.
 */
public class AccelerationDecelerationIndicator extends CachedIndicator<Num> {

    private final AwesomeOscillatorIndicator awesome;
    private final SMAIndicator sma;

    /**
     * Constructor.
     *
     * @param series       the bar series
     * @param barCountSma1 the bar count for {@link #awesome}
     * @param barCountSma2 the bar count for {@link #sma}
     */
    public AccelerationDecelerationIndicator(BarSeries series, int barCountSma1, int barCountSma2) {
        super(series);
        this.awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), barCountSma1, barCountSma2);
        this.sma = new SMAIndicator(awesome, barCountSma1);
    }

    /**
     * Constructor with {@code barCountSma1} = 5 and {@code barCountSma2} = 34.
     *
     * @param series the bar series
     */
    public AccelerationDecelerationIndicator(BarSeries series) {
        this(series, 5, 34);
    }

    @Override
    protected Num calculate(int index) {
        return awesome.getValue(index).minus(sma.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(awesome.getCountOfUnstableBars(), sma.getCountOfUnstableBars());
    }
}
