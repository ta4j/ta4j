/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;

/**
 * Counts directional changes when the complete comparison window is retained.
 */
final class TrendStrengthIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> source;
    private final boolean rising;
    private final RunningTotalIndicator total;

    TrendStrengthIndicator(Indicator<Num> source, int barCount, boolean rising) {
        super(source);
        this.source = source;
        this.rising = rising;
        this.total = new RunningTotalIndicator(this, barCount);
    }

    int count(int index) {
        return (int) total.getValue(index).doubleValue();
    }

    @Override
    protected Num calculate(int index) {
        int begin = Math.max(0, getBarSeries().getBeginIndex());
        if (index <= begin) {
            return getBarSeries().numFactory().zero();
        }
        Num current = source.getValue(index);
        Num previous = source.getValue(index - 1);
        return rising
                ? (current.isGreaterThan(previous) ? getBarSeries().numFactory().one()
                        : getBarSeries().numFactory().zero())
                : (current.isLessThan(previous) ? getBarSeries().numFactory().one()
                        : getBarSeries().numFactory().zero());
    }

    @Override
    public int getCountOfUnstableBars() {
        return source.getCountOfUnstableBars();
    }
}
