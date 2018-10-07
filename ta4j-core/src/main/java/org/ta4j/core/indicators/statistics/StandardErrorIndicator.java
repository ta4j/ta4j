package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Standard error indicator.
 * </p>
 */
public class StandardErrorIndicator extends CachedIndicator<Num> {

    private int barCount;
    
    private StandardDeviationIndicator sdev;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param barCount the time frame
     */
    public StandardErrorIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        sdev = new StandardDeviationIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        return sdev.getValue(index).dividedBy(numOf(numberOfObservations).sqrt());
    }
}
