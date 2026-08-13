/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
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

    // serial access detection
    private transient int previousIndex = -1;
    private transient BarSeries.BarSeriesChangeSnapshot previousSnapshot;

    public RunningTotalIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        final BarSeries series = indicator.getBarSeries();
        this.previousSum = series.numFactory().zero();
        this.previousSnapshot = series.getBarSeriesChangeSnapshot(-1L);
    }

    @Override
    protected Num calculate(int index) {
        final BarSeries series = getBarSeries();
        BarSeries.BarSeriesChangeSnapshot previous = previousSnapshot;
        if (previous == null) {
            // transient field lost after deserialization: re-initialize
            previous = series.getBarSeriesChangeSnapshot(-1L);
            previousSnapshot = previous;
        }
        final BarSeries.BarSeriesChangeSnapshot snapshot = series
                .getBarSeriesChangeSnapshot(previous.revision());
        final boolean appendOnly = snapshot.endIndex() == previous.endIndex() + 1
                && snapshot.earliestChangedIndex() == snapshot.endIndex()
                && snapshot.removedThroughIndex() == previous.removedThroughIndex()
                && snapshot.maximumBarCount() == previous.maximumBarCount();
        // serial access can benefit from previous partial sums
        // which saves a lot of CPU work for very long barCounts.
        // A partial sum remains valid as long as the series is unchanged or has
        // only been appended to, because neither change alters previous values.
        final boolean partialSumUsable = previousIndex != -1 && previousIndex == index - 1
                && previousIndex >= Math.max(0, series.getBeginIndex())
                && (snapshot.revision() == previousSnapshot.revision() || appendOnly);
        if (partialSumUsable && Num.isFinite(previousSum)) {
            return fastPath(index, snapshot);
        }

        return slowPath(index, snapshot);
    }

    private Num fastPath(final int index, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        Num newSum = partialSum(index);
        updatePartialSum(index, newSum, snapshot);
        return newSum;
    }

    private Num slowPath(final int index, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        final BarSeries series = getBarSeries();
        Num sum = series.numFactory().zero();
        final int beginIndex = series.getBeginIndex();
        // Below beginIndex the series clamps reads to the first remaining bar,
        // so the legacy 0-anchored window is preserved (existing contract).
        // At/above beginIndex the window is anchored at beginIndex so that
        // getValue(beginIndex) equals the front bar's value, not barCount times it.
        final int firstInWindow = index < beginIndex
                ? Math.max(0, index - barCount + 1)
                : Math.max(Math.max(0, beginIndex), index - barCount + 1);
        for (int i = firstInWindow; i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }

        updatePartialSum(index, sum, snapshot);
        return sum;
    }

    private void updatePartialSum(final int index, final Num sum, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        previousIndex = index;
        previousSum = sum;
        previousSnapshot = snapshot;
    }

    private Num partialSum(int index) {
        Num sum = this.previousSum.plus(indicator.getValue(index));

        final int firstInWindow = index - barCount + 1;
        if (firstInWindow > Math.max(0, getBarSeries().getBeginIndex())) {
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
