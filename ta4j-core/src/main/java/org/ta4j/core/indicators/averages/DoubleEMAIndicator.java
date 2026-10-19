/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

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
    private final Indicator<Num> indicator;
    private final transient EMAIndicator ema;
    private final transient EMAIndicator emaEma;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public DoubleEMAIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, new EMAIndicator(indicator, barCount));
    }

    private DoubleEMAIndicator(Indicator<Num> indicator, int barCount, EMAIndicator ema) {
        this(indicator, barCount, ema, new EMAIndicator(ema, barCount));
    }

    private DoubleEMAIndicator(Indicator<Num> indicator, int barCount, EMAIndicator ema, EMAIndicator emaEma) {
        super(emaEma);
        this.two = getBarSeries().numFactory().two();
        this.barCount = barCount;
        this.indicator = indicator;
        this.ema = ema;
        this.emaEma = emaEma;
        this.unstableBars = indicator.getCountOfUnstableBars() + (barCount * 2);
    }

    @Override
    protected Num calculate(int index) {
        Num firstAverage = ema.getValue(index);
        Num secondAverage = emaEma.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return firstAverage.multipliedBy(two).minus(secondAverage);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * @return {@code true}, because both EMA stages retain full-history state
     * @since 0.27.1
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
