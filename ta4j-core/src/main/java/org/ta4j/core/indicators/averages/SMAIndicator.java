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
        this(SMAIndicator.class, indicator, barCount);
    }

    /**
     * Constructor for subclasses that provide their own audited cache identity.
     *
     * @param identityClass exact concrete indicator class eligible for sharing
     * @param indicator     the {@link Indicator}
     * @param barCount      the time frame
     * @since 0.23.1
     */
    protected SMAIndicator(Class<?> identityClass, Indicator<Num> indicator, int barCount) {
        super(indicator, identityOfExact(identityClass, indicator, barCount));
        this.previousSum = new RunningTotalIndicator(indicator, barCount);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final int realBarCount = Math.min(barCount, index + 1);
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
