/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Triple exponential moving average indicator (also called "TRIX").
 *
 * <p>
 * TEMA needs "3 * period - 2" of data to start producing values in contrast to
 * the period samples needed by a regular EMA.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Triple_exponential_moving_average">https://en.wikipedia.org/wiki/Triple_exponential_moving_average</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp">https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp</a>
 */
public class TripleEMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;
    private final transient EMAIndicator ema;
    private final transient EMAIndicator emaEma;
    private final transient EMAIndicator emaEmaEma;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public TripleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.indicator = indicator;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
        this.emaEmaEma = new EMAIndicator(emaEma, barCount);
        this.unstableBars = indicator.getCountOfUnstableBars() + (barCount * 3);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        // trix = 3 * ( ema - emaEma ) + emaEmaEma
        final var numFactory = getBarSeries().numFactory();
        return numFactory.numOf(3)
                .multipliedBy(ema.getValue(index).minus(emaEma.getValue(index)))
                .plus(emaEmaEma.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
