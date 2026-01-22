/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Double exponential moving average indicator.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Double_exponential_moving_average">
 *      https://en.wikipedia.org/wiki/Double_exponential_moving_average</a>
 */
public class DoubleEMAIndicator extends CachedIndicator<Num> {

    private final Num two;
    private final int barCount;
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public DoubleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.two = getBarSeries().numFactory().two();
        this.barCount = barCount;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return ema.getValue(index).multipliedBy(two).minus(emaEma.getValue(index));
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
