package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Indicator that returns NaN in unstable period
 */
public class UnstableIndicator extends CachedIndicator<Num> {

    private final int unstablePeriod;
    private final Indicator<Num> indicator;

    public UnstableIndicator(Indicator<Num> indicator, int unstablePeriod) {
        super(indicator);
        this.indicator = indicator;
        this.unstablePeriod = unstablePeriod;
    }

    @Override
    protected Num calculate(int index) {
        if (index < unstablePeriod) {
            return NaN.NaN;
        }
        return indicator.getValue(index);
    }
}
