package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Gain indicator.
 */
public class LossIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = -3848368003378457940L;
    private final Indicator<Num> indicator;

    public LossIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        if (indicator.getValue(index).isLessThan(indicator.getValue(index - 1))) {
            return indicator.getValue(index - 1).minus(indicator.getValue(index));
        } else {
            return numOf(0);
        }
    }
}
