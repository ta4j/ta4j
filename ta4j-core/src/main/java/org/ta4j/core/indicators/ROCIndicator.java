package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change (ROCIndicator) indicator.
 * Aka. Momentum
 * </p>
 * The ROCIndicator calculation compares the current value with the value "n" periods ago.
 */
public class ROCIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 7983097470035346856L;

    private final Indicator<Num> indicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public ROCIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = indicator.getValue(nIndex);
        Num currentValue = indicator.getValue(index);
        return currentValue.minus(nPeriodsAgoValue).dividedBy(nPeriodsAgoValue).multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
