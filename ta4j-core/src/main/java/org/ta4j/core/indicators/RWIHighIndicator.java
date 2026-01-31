/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The RandomWalkIndexHighIndicator.
 *
 * @see <a href=
 *      "http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source
 *      of formular</a>
 */
public class RWIHighIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public RWIHighIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index - barCount + 1 < getBarSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        Num maxRWIH = getBarSeries().numFactory().zero();
        for (int n = 2; n <= barCount; n++) {
            maxRWIH = maxRWIH.max(calcRWIHFor(index, n));
        }

        return maxRWIH;
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    private Num calcRWIHFor(final int index, final int n) {
        BarSeries series = getBarSeries();
        Num high = series.getBar(index).getHighPrice();
        Num lowN = series.getBar(index + 1 - n).getLowPrice();
        Num atrN = new ATRIndicator(series, n).getValue(index);
        Num sqrtN = getBarSeries().numFactory().numOf(n).sqrt();

        return high.minus(lowN).dividedBy(atrN.multipliedBy(sqrtN));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
