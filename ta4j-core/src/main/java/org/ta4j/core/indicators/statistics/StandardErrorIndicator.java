/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Standard error indicator.
 */
public class StandardErrorIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final StandardDeviationIndicator sdev;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public StandardErrorIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.sdev = new StandardDeviationIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        return sdev.getValue(index).dividedBy(getBarSeries().numFactory().numOf(numberOfObservations).sqrt());
    }

    @Override
    public int getCountOfUnstableBars() {
        return sdev.getCountOfUnstableBars();
    }
}
