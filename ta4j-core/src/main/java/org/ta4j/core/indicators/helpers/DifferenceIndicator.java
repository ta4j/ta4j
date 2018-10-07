package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Difference indicator.
 * </p>
 * I.e.: first - second
 */
public class DifferenceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    
    /**
     * Constructor.
     * (first minus second)
     * @param first the first indicator
     * @param second the second indicator
     */
    public DifferenceIndicator(Indicator<Num> first, Indicator<Num> second) {
        // TODO: check if first series is equal to second one
        super(first);
        this.first = first;
        this.second = second;
    }

    @Override
    protected Num calculate(int index) {
        return first.getValue(index).minus(second.getValue(index));
    }
}
