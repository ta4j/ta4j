package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple multiplier indicator.
 * </p>
 */
public class MultiplierIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num coefficient;
    
    public MultiplierIndicator(Indicator<Num> indicator, double coefficient) {
        super(indicator);
        this.indicator = indicator;
        this.coefficient = numOf(coefficient);
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index).multipliedBy(coefficient);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " Coefficient: " + coefficient;
    }
}
