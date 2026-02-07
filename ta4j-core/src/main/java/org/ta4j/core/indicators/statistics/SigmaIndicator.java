/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Sigma-Indicator (also called, "z-score" or "standard score").
 *
 * @see http://www.statisticshowto.com/probability-and-statistics/z-score/
 */
public class SigmaIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> ref;
    private final int barCount;

    private final SMAIndicator mean;
    private final StandardDeviationIndicator sd;

    /**
     * Constructor.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     */
    public SigmaIndicator(Indicator<Num> ref, int barCount) {
        super(ref);
        this.ref = ref;
        this.barCount = barCount;
        this.mean = new SMAIndicator(ref, barCount);
        this.sd = new StandardDeviationIndicator(ref, barCount);
    }

    @Override
    protected Num calculate(int index) {
        // z-score = (ref - mean) / sd
        return (ref.getValue(index).minus(mean.getValue(index))).dividedBy(sd.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(mean.getCountOfUnstableBars(), sd.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
