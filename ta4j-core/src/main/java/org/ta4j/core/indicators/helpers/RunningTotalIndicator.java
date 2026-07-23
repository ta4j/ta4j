/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Running Total aka Cumulative Sum indicator
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Running_total">https://en.wikipedia.org/wiki/Running_total</a>
 */
public class RunningTotalIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final int barCount;
    private transient Num previousSum;
    private transient int previousIndex;
    private transient long previousEpoch;
    private transient boolean hasPreviousSum;

    public RunningTotalIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.previousSum = indicator.getBarSeries().numFactory().zero();
    }

    @Override
    protected synchronized Num calculate(int index) {
        long currentEpoch = getBarSeries().getBarHistoryEpoch();
        // serial access can benefit from previous partial sums
        // which saves a lot of CPU work for very long barCounts
        if (hasPreviousSum && previousEpoch == currentEpoch && previousIndex == index - 1) {
            if (!Num.isFinite(previousSum)) {
                return slowPath(index, currentEpoch);
            }
            return fastPath(index, currentEpoch);
        }

        return slowPath(index, currentEpoch);
    }

    private Num fastPath(final int index, final long currentEpoch) {
        var newSum = partialSum(index);
        updatePartialSum(index, newSum, currentEpoch);
        return newSum;
    }

    private Num slowPath(final int index, final long currentEpoch) {
        Num sum = getBarSeries().numFactory().zero();
        for (int i = Math.max(0, index - barCount + 1); i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }
        updatePartialSum(index, sum, currentEpoch);
        return sum;
    }

    private void updatePartialSum(final int index, final Num sum, final long currentEpoch) {
        previousIndex = index;
        previousSum = sum;
        previousEpoch = currentEpoch;
        hasPreviousSum = true;
    }

    private Num partialSum(int index) {
        var sum = this.previousSum.plus(indicator.getValue(index));

        if (index >= barCount) {
            return sum.minus(indicator.getValue(index - barCount));
        }

        return sum;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }
}
