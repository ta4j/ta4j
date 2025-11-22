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
 * <p>
 * Once a new swing point is confirmed, values are backfilled along the line
 * connecting the two most recent swing points so historical points align with
 * the straight trend line between swing points.
 *
 * @since 0.20
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    private static final class SwingPoint {
        private final int index;
        private final int confirmationIndex;

        private SwingPoint(int index, int confirmationIndex) {
            this.index = index;
            this.confirmationIndex = confirmationIndex;
        }
    }

    private final Indicator<Num> priceIndicator;
    private final List<SwingPoint> swingPoints = new ArrayList<>();
    private final int unstableBars;
    private transient int lastScannedIndex = Integer.MIN_VALUE;

    /**
     * Constructor.
     *
     * @param priceIndicator the indicator that supplies the swing point values
     * @param unstableBars   number of bars regarded as unstable by the indicator
     * @since 0.20
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
        updateSwingPointCache(index);
        final int beginIndex = getBarSeries().getBeginIndex();
        final SwingPointPair pair = findSwingPointPairForIndex(index);
        if (pair == null) {
            return NaN;
        }
        final int firstSwingPointIndex = pair.previous.index;
        final int secondSwingPointIndex = pair.recent.index;
        if (firstSwingPointIndex < beginIndex || secondSwingPointIndex < beginIndex) {
            return NaN;
        }
        if (firstSwingPointIndex == secondSwingPointIndex) {
            return NaN;
        }
        final Num firstValue = priceIndicator.getValue(firstSwingPointIndex);
        final Num secondValue = priceIndicator.getValue(secondSwingPointIndex);
        if (firstValue.isNaN() || secondValue.isNaN()) {
            return NaN;
        }
        if (index == firstSwingPointIndex) {
            return firstValue;
        }
        if (index == secondSwingPointIndex) {
            return secondValue;
        }
        final NumFactory numFactory = getBarSeries().numFactory();
        final Num x1 = numFactory.numOf(firstSwingPointIndex);
        final Num x2 = numFactory.numOf(secondSwingPointIndex);
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

    private void updateSwingPointCache(int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        if (!swingPoints.isEmpty()) {
            int firstRetained = 0;
            while (firstRetained < swingPoints.size() && swingPoints.get(firstRetained).index < beginIndex) {
                firstRetained++;
            }
            if (firstRetained > 0) {
                swingPoints.subList(0, firstRetained).clear();
            }
        }
        if (lastScannedIndex < beginIndex - 1) {
            lastScannedIndex = beginIndex - 1;
        }
        if (index <= lastScannedIndex) {
            return;
        }
        for (int currentIndex = Math.max(beginIndex, lastScannedIndex + 1); currentIndex <= index; currentIndex++) {
            final int latestSwingPoint = getLatestSwingPointIndex(currentIndex);
            if (latestSwingPoint >= 0) {
                if (swingPoints.isEmpty()) {
                    swingPoints.add(new SwingPoint(latestSwingPoint, currentIndex));
                } else {
                    final SwingPoint lastSwingPoint = swingPoints.get(swingPoints.size() - 1);
                    if (latestSwingPoint > lastSwingPoint.index) {
                        swingPoints.add(new SwingPoint(latestSwingPoint, currentIndex));
                    }
                }
            }
        }
        lastScannedIndex = index;
    }

    private SwingPointPair findSwingPointPairForIndex(int index) {
        if (swingPoints.size() < 2) {
            return null;
        }
        SwingPoint lower = null;
        SwingPoint upper = null;
        for (SwingPoint swingPoint : swingPoints) {
            if (swingPoint.index <= index) {
                lower = swingPoint;
                continue;
            }
            upper = swingPoint;
            break;
        }
        if (lower == null) {
            return null;
        }
        if (upper == null) {
            lower = swingPoints.get(swingPoints.size() - 2);
            upper = swingPoints.get(swingPoints.size() - 1);
        }
        return new SwingPointPair(lower, upper);
    }

    /**
     * Returns the index of the most recent confirmed swing point that can be
     * evaluated with the data available up to the given index.
     *
     * @param index the current evaluation index
     * @return the index of the most recent swing point or {@code -1} if none can be
     *         confirmed yet
     * @since 0.20
     */
    protected abstract int getLatestSwingPointIndex(int index);

    /**
     * @deprecated Use {@link #getLatestSwingPointIndex(int)} instead. This method
     *             will be removed in a future version.
     */
    @Deprecated
    protected int getLatestPivotIndex(int index) {
        return getLatestSwingPointIndex(index);
    }

    /**
     * Returns the indexes of the confirmed swing points tracked by the indicator.
     *
     * @return an immutable list containing the swing point indexes in chronological
     *         order
     * @since 0.20
     */
    public List<Integer> getSwingPointIndexes() {
        final List<Integer> result = new ArrayList<>(swingPoints.size());
        for (SwingPoint swingPoint : swingPoints) {
            result.add(swingPoint.index);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the indexes of the confirmed pivot points tracked by the indicator.
     *
     * @return an immutable list containing the pivot indexes in chronological order
     * @deprecated Use {@link #getSwingPointIndexes()} instead. This method will be
     *             removed in a future version.
     * @since 0.20
     */
    @Deprecated
    public List<Integer> getPivotIndexes() {
        return getSwingPointIndexes();
    }

    private static final class SwingPointPair {
        private final SwingPoint previous;
        private final SwingPoint recent;

        private SwingPointPair(SwingPoint previous, SwingPoint recent) {
            this.previous = previous;
            this.recent = recent;
        }
    }
}
