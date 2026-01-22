/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Zero-lag exponential moving average indicator.
 *
 * @see <a href=
 *      "http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm">
 *      http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm</a>
 */
public class ZLEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num two;
    private final Num k;
    private final int lag;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public ZLEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.two = getBarSeries().numFactory().numOf(2);
        this.k = two.dividedBy(getBarSeries().numFactory().numOf(barCount + 1));
        this.lag = (barCount - 1) / 2;
    }

    @Override
    protected Num calculate(int index) {
        if (index + 1 < barCount) {
            // Starting point of the ZLEMA
            return new SMAIndicator(indicator, barCount).getValue(index);
        }
        if (index == 0) {
            // If the barCount is bigger than the indicator's value count
            return indicator.getValue(0);
        }
        Num zlemaPrev = getValue(index - 1);
        return k.multipliedBy(two.multipliedBy(indicator.getValue(index)).minus(indicator.getValue(index - lag)))
                .plus(getBarSeries().numFactory().one().minus(k).multipliedBy(zlemaPrev));
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
