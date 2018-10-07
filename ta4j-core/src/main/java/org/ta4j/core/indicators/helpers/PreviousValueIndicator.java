package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
/**
 * Returns the previous (n-th) value of an indicator
 * </p>
 */
public class PreviousValueIndicator extends CachedIndicator<Num> {

    private int n;
    private Indicator<Num> indicator;

    /**
     * Constructor.
     * @param indicator the indicator of which the previous value should be calculated
     */
    public PreviousValueIndicator(Indicator<Num> indicator) {
        this(indicator,1);
    }

    /**
     * Constructor.
     * @param indicator the indicator of which the previous value should be calculated
     * @param n parameter defines the previous n-th value
     */
    public PreviousValueIndicator(Indicator<Num> indicator, int n){
        super(indicator);
        this.n = n;
        this.indicator = indicator;
    }

    protected Num calculate(int index) {
        int previousValue = Math.max(0, (index-n));
        return this.indicator.getValue(previousValue);
    }
}