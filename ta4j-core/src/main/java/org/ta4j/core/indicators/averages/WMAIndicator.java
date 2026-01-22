/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * WMA indicator.
 */
public class WMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public WMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return indicator.getValue(0);
        }

        final var numFactory = getBarSeries().numFactory();
        Num value = numFactory.zero();
        int loopLength = (index - barCount < 0) ? index + 1 : barCount;
        int actualIndex = index;
        for (int i = loopLength; i > 0; i--) {
            value = value.plus(numFactory.numOf(i).multipliedBy(indicator.getValue(actualIndex)));
            actualIndex--;
        }

        return value.dividedBy(numFactory.numOf((loopLength * (loopLength + 1)) / 2));
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
