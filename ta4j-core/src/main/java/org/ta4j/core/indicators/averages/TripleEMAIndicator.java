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
        this(indicator, barCount, new EMAIndicator(indicator, barCount));
    }

    private TripleEMAIndicator(Indicator<Num> indicator, int barCount, EMAIndicator ema) {
        this(indicator, barCount, ema, new EMAIndicator(ema, barCount));
    }

    private TripleEMAIndicator(Indicator<Num> indicator, int barCount, EMAIndicator ema, EMAIndicator emaEma) {
        this(indicator, barCount, ema, emaEma, new EMAIndicator(emaEma, barCount));
    }

    private TripleEMAIndicator(Indicator<Num> indicator, int barCount, EMAIndicator ema, EMAIndicator emaEma,
            EMAIndicator emaEmaEma) {
        super(emaEmaEma);
        this.barCount = barCount;
        this.indicator = indicator;
        this.ema = ema;
        this.emaEma = emaEma;
        this.emaEmaEma = emaEmaEma;
        this.unstableBars = indicator.getCountOfUnstableBars() + (barCount * 3);
    }

    @Override
    protected Num calculate(int index) {
        Num firstAverage = ema.getValue(index);
        Num secondAverage = emaEma.getValue(index);
        Num thirdAverage = emaEmaEma.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        // trix = 3 * ( ema - emaEma ) + emaEmaEma
        Num three = getBarSeries().numFactory().numOf(3);
        return three.multipliedBy(firstAverage.minus(secondAverage)).plus(thirdAverage);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    /**
     * @return {@code true}, because all EMA stages retain full-history state
     * @since 0.26.1
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
