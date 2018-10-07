package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
/**
 * Absolute indicator.
 * </p>
 */
public class AbsoluteIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    
    public AbsoluteIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index).abs();
    }
}
