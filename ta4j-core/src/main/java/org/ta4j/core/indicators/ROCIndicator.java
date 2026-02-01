/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change (ROCIndicator) indicator (also called "Momentum").
 *
 * <p>
 * The ROCIndicator calculation compares the current value with the value "n"
 * periods ago.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/pricerateofchange.asp">https://www.investopedia.com/terms/p/pricerateofchange.asp</a>
 */
public class ROCIndicator extends CachedIndicator<Num> {

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
        return currentValue.minus(nPeriodsAgoValue)
                .dividedBy(nPeriodsAgoValue)
                .multipliedBy(getBarSeries().numFactory().hundred());
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
