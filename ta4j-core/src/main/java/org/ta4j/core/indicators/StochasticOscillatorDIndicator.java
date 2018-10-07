package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Stochastic oscillator D.
 * </p>
 * Receive {@link StochasticOscillatorKIndicator} and returns its {@link SMAIndicator SMAIndicator(3)}.
 */
public class StochasticOscillatorDIndicator extends CachedIndicator<Num> {

    private Indicator<Num> indicator;

    public StochasticOscillatorDIndicator(StochasticOscillatorKIndicator k) {
        this(new SMAIndicator(k, 3));
    }

    public StochasticOscillatorDIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + indicator;
    }
}
