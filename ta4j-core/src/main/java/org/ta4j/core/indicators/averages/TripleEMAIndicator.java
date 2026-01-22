/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

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
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;
    private final EMAIndicator emaEmaEma;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public TripleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
        this.emaEmaEma = new EMAIndicator(emaEma, barCount);
    }

    @Override
    protected Num calculate(int index) {
        // trix = 3 * ( ema - emaEma ) + emaEmaEma
        final var numFactory = getBarSeries().numFactory();
        return numFactory.numOf(3)
                .multipliedBy(ema.getValue(index).minus(emaEma.getValue(index)))
                .plus(emaEmaEma.getValue(index));
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
