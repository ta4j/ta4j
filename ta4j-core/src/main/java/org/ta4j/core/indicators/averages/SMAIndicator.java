/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple moving average (SMA) indicator.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/sma.asp">https://www.investopedia.com/terms/s/sma.asp</a>
 */
public class SMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;
    private final transient RunningTotalIndicator previousSum;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public SMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.previousSum = new RunningTotalIndicator(indicator, barCount);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // The denominator must mirror RunningTotalIndicator's window exactly:
        // below beginIndex the series clamps reads to the first remaining bar,
        // at/above beginIndex the window is anchored at beginIndex.
        final int beginIndex = getBarSeries().getBeginIndex();
        final int firstInWindow = index < beginIndex ? Math.max(0, index - barCount + 1)
                : Math.max(Math.max(0, beginIndex), index - barCount + 1);
        final int realBarCount = index - firstInWindow + 1;
        final var sum = partialSum(index);
        return sum.dividedBy(getBarSeries().numFactory().numOf(realBarCount));
    }

    private Num partialSum(int index) {
        return this.previousSum.getValue(index);
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
