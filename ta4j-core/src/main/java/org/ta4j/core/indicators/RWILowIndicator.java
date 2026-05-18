/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Class RandomWalkIndexLowIndicator.
 *
 * @see <a href=
 *      "http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source
 *      of formular</a>
 */
public class RWILowIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final transient ATRIndicator[] atrIndicators;
    private final transient Num[] sqrtValues;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public RWILowIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        int helperCount = Math.max(0, barCount + 1);
        this.atrIndicators = new ATRIndicator[helperCount];
        this.sqrtValues = new Num[helperCount];
        for (int n = 2; n <= barCount; n++) {
            atrIndicators[n] = new ATRIndicator(series, n);
            sqrtValues[n] = series.numFactory().numOf(n).sqrt();
        }
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars() || index - barCount + 1 < getBarSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        Num maxRWIL = getBarSeries().numFactory().zero();
        boolean hasDefinedValue = false;
        for (int n = 2; n <= barCount; n++) {
            Num rwiLow = calcRWILFor(index, n);
            if (!IndicatorUtils.isInvalid(rwiLow)) {
                maxRWIL = hasDefinedValue ? maxRWIL.max(rwiLow) : rwiLow;
                hasDefinedValue = true;
            }
        }

        return hasDefinedValue ? maxRWIL : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(0, barCount);
    }

    private Num calcRWILFor(final int index, final int n) {
        BarSeries series = getBarSeries();
        Num low = series.getBar(index).getLowPrice();
        Num highN = series.getBar(index + 1 - n).getHighPrice();
        Num atrN = atrIndicators[n].getValue(index);
        Num sqrtN = sqrtValues[n];

        return highN.minus(low).dividedBy(atrN.multipliedBy(sqrtN));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
