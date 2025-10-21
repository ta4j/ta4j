/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.supportresistance;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 *
 * @since 0.19
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    private static final class Pivot {
        private final int index;
        private final int confirmationIndex;

        private Pivot(int index, int confirmationIndex) {
            this.index = index;
            this.confirmationIndex = confirmationIndex;
        }
    }

    private final Indicator<Num> priceIndicator;
    private final List<Pivot> pivots = new ArrayList<>();
    private final int unstableBars;
    private int lastScannedIndex = Integer.MIN_VALUE;

    /**
     * Constructor.
     *
     * @param priceIndicator the indicator that supplies the pivot values
     * @param unstableBars   number of bars regarded as unstable by the indicator
     * @since 0.19
     */
    protected AbstractTrendLineIndicator(Indicator<Num> priceIndicator, int unstableBars) {
        super(priceIndicator);
        this.priceIndicator = priceIndicator;
        this.unstableBars = Math.max(0, unstableBars);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return NaN;
        }
        updatePivotCache(index);
        final PivotPair pair = findLatestConfirmedPivots(index);
        if (pair == null) {
            return NaN;
        }
        final int firstPivotIndex = pair.previous.index;
        final int secondPivotIndex = pair.recent.index;
        if (firstPivotIndex == secondPivotIndex) {
            return NaN;
        }
        final Num firstValue = priceIndicator.getValue(firstPivotIndex);
        final Num secondValue = priceIndicator.getValue(secondPivotIndex);
        if (firstValue.isNaN() || secondValue.isNaN()) {
            return NaN;
        }
        final NumFactory numFactory = getBarSeries().numFactory();
        final Num x1 = numFactory.numOf(firstPivotIndex);
        final Num x2 = numFactory.numOf(secondPivotIndex);
        final Num denominator = x2.minus(x1);
        if (denominator.isNaN() || denominator.isZero()) {
            return NaN;
        }
        final Num slope = secondValue.minus(firstValue).dividedBy(denominator);
        if (slope.isNaN()) {
            return NaN;
        }
        final Num intercept = secondValue.minus(slope.multipliedBy(x2));
        if (intercept.isNaN()) {
            return NaN;
        }
        final Num projection = slope.multipliedBy(numFactory.numOf(index)).plus(intercept);
        return projection.isNaN() ? NaN : projection;
    }

    private void updatePivotCache(int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        if (!pivots.isEmpty()) {
            int firstRetained = 0;
            while (firstRetained < pivots.size() && pivots.get(firstRetained).index < beginIndex) {
                firstRetained++;
            }
            if (firstRetained > 0) {
                pivots.subList(0, firstRetained).clear();
            }
        }
        if (lastScannedIndex < beginIndex - 1) {
            lastScannedIndex = beginIndex - 1;
        }
        if (index <= lastScannedIndex) {
            return;
        }
        for (int currentIndex = Math.max(beginIndex, lastScannedIndex + 1); currentIndex <= index; currentIndex++) {
            final int latestPivot = getLatestPivotIndex(currentIndex);
            if (latestPivot >= 0) {
                if (pivots.isEmpty()) {
                    pivots.add(new Pivot(latestPivot, currentIndex));
                } else {
                    final Pivot lastPivot = pivots.get(pivots.size() - 1);
                    if (latestPivot > lastPivot.index) {
                        pivots.add(new Pivot(latestPivot, currentIndex));
                    }
                }
            }
        }
        lastScannedIndex = index;
    }

    private PivotPair findLatestConfirmedPivots(int index) {
        Pivot recent = null;
        Pivot previous = null;
        for (int i = pivots.size() - 1; i >= 0; i--) {
            final Pivot pivot = pivots.get(i);
            if (pivot.confirmationIndex > index) {
                continue;
            }
            if (recent == null) {
                recent = pivot;
            } else {
                previous = pivot;
                break;
            }
        }
        if (recent == null || previous == null) {
            return null;
        }
        return new PivotPair(previous, recent);
    }

    protected abstract int getLatestPivotIndex(int index);

    /**
     * Returns the indexes of the confirmed pivot points tracked by the indicator.
     *
     * @return an immutable list containing the pivot indexes in chronological order
     * @since 0.19
     */
    public List<Integer> getPivotIndexes() {
        final List<Integer> result = new ArrayList<>(pivots.size());
        for (Pivot pivot : pivots) {
            result.add(pivot.index);
        }
        return Collections.unmodifiableList(result);
    }

    private static final class PivotPair {
        private final Pivot previous;
        private final Pivot recent;

        private PivotPair(Pivot previous, Pivot recent) {
            this.previous = previous;
            this.recent = recent;
        }
    }
}
