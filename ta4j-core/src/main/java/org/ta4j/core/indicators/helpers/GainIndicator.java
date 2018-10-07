package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Gain indicator.
 */
public class GainIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = -4323131155633745356L;
    private final Indicator<Num> indicator;

    public GainIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        if (indicator.getValue(index).isGreaterThan(indicator.getValue(index - 1))) {
            return indicator.getValue(index).minus(indicator.getValue(index - 1));
        } else {
            return numOf(0);
        }
    }
}
