package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Base class for Exponential Moving Average implementations.
 * <p/>
 */
public abstract class AbstractEMAIndicator extends RecursiveCachedIndicator<Num> {

    private static final long serialVersionUID = -7312565662007443461L;
    private final Indicator<Num> indicator;

    private final int barCount;

    private final Num multiplier;

    public AbstractEMAIndicator(Indicator<Num> indicator, int barCount, double multiplier) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.multiplier = numOf(multiplier);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
        }
        Num prevValue = getValue(index - 1);
        return indicator.getValue(index).minus(prevValue).multipliedBy(multiplier).plus(prevValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
