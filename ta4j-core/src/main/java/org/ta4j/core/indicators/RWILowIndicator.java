/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Arrays;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.TRIndicator;
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

    /**
     * Largest accepted {@code barCount}. The indicator keeps one cached ATR per
     * look-back length, so its memory and per-bar work grow linearly with
     * {@code barCount}; the cap keeps descriptor-supplied values from allocating
     * millions of helper indicators.
     */
    static final int MAX_BAR_COUNT = 1_000;

    private final int barCount;
    private final transient ATRIndicator[] atrIndicators;
    private final transient Num[] sqrtValues;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame, at most 1,000
     * @throws IllegalArgumentException if {@code barCount} exceeds 1,000
     */
    public RWILowIndicator(BarSeries series, int barCount) {
        int helperCount = helperCount(barCount);
        ATRIndicator[] averages = new ATRIndicator[helperCount];
        TRIndicator trueRange = barCount >= 2 ? new TRIndicator(series) : null;
        for (int n = 2; n <= barCount; n++) {
            averages[n] = new ATRIndicator(trueRange, n);
        }
        super(series, Arrays.copyOfRange(averages, Math.min(2, helperCount), helperCount));
        this.barCount = barCount;
        this.atrIndicators = averages;
        this.sqrtValues = new Num[helperCount];
        for (int n = 2; n <= barCount; n++) {
            sqrtValues[n] = series.numFactory().numOf(n).sqrt();
        }
    }

    private static int helperCount(int barCount) {
        if (barCount > MAX_BAR_COUNT) {
            throw new IllegalArgumentException("barCount must be at most " + MAX_BAR_COUNT);
        }
        return Math.max(0, barCount + 1);
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
            if (Num.isFinite(rwiLow)) {
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
