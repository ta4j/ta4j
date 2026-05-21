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
    private final transient ATRIndicator[] atrIndicators;
    private final transient Num[] sqrtValues;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public RWIHighIndicator(BarSeries series, int barCount) {
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

        Num maxRWIH = getBarSeries().numFactory().zero();
        boolean hasDefinedValue = false;
        for (int n = 2; n <= barCount; n++) {
            Num rwiHigh = calcRWIHFor(index, n);
            if (!IndicatorUtils.isInvalid(rwiHigh)) {
                maxRWIH = hasDefinedValue ? maxRWIH.max(rwiHigh) : rwiHigh;
                hasDefinedValue = true;
            }
        }

        return hasDefinedValue ? maxRWIH : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(0, barCount);
    }

    private Num calcRWIHFor(final int index, final int n) {
        BarSeries series = getBarSeries();
        Num high = series.getBar(index).getHighPrice();
        Num lowN = series.getBar(index + 1 - n).getLowPrice();
        Num atrN = atrIndicators[n].getValue(index);
        Num sqrtN = sqrtValues[n];

        return high.minus(lowN).dividedBy(atrN.multipliedBy(sqrtN));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
