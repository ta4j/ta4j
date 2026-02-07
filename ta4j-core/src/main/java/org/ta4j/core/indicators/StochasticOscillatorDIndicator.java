/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Stochastic oscillator D.
 */
public class StochasticOscillatorDIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor with {@code indicator} = {@link SMAIndicator SMAIndicator(3)}.
     *
     * @param k the StochasticOscillatorKIndicator for the {@link SMAIndicator}
     */
    public StochasticOscillatorDIndicator(StochasticOscillatorKIndicator k) {
        this(new SMAIndicator(k, 3));
    }

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     */
    public StochasticOscillatorDIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + indicator;
    }
}
