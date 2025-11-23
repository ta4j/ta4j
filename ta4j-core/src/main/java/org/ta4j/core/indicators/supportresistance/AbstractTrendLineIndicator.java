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

import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 * <p>
 * Swing point discovery and lifecycle management is delegated to the underlying
 * {@link RecentSwingIndicator}. The trend line simply connects the two most
 * recent swing points available at the evaluation index and backfills values
 * between them.
 *
 * @since 0.20
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    private final RecentSwingIndicator swingIndicator;
    private final Indicator<Num> priceIndicator;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param swingIndicator the swing-point indicator providing both prices and
     *                       indexes
     * @param unstableBars   number of bars regarded as unstable by the indicator
     * @since 0.20
     */
    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int unstableBars) {
        super(swingIndicator.getPriceIndicator());
        this.swingIndicator = swingIndicator;
        this.priceIndicator = swingIndicator.getPriceIndicator();
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
        final List<Integer> swingPointIndexes = swingIndicator.getSwingPointIndexesUpTo(index);
        final int beginIndex = getBarSeries().getBeginIndex();
        if (swingPointIndexes.size() < 2) {
            return NaN;
        }
        Integer firstSwingPointIndex = null;
        Integer secondSwingPointIndex = null;
        for (int swingPointIndex : swingPointIndexes) {
            if (swingPointIndex < beginIndex) {
                continue;
            }
            if (swingPointIndex <= index) {
                firstSwingPointIndex = swingPointIndex;
                continue;
            }
            secondSwingPointIndex = swingPointIndex;
            break;
        }
        if (firstSwingPointIndex == null || swingPointIndexes.size() < 2) {
            return NaN;
        }
        if (secondSwingPointIndex == null) {
            final int size = swingPointIndexes.size();
            firstSwingPointIndex = swingPointIndexes.get(size - 2);
            secondSwingPointIndex = swingPointIndexes.get(size - 1);
        }
        if (firstSwingPointIndex < beginIndex || secondSwingPointIndex < beginIndex
                || firstSwingPointIndex.equals(secondSwingPointIndex)) {
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

    /**
     * Returns the indexes of the confirmed swing points tracked by the indicator.
     *
     * @return an immutable list containing the swing point indexes in chronological
     *         order
     * @since 0.20
     */
    public List<Integer> getSwingPointIndexes() {
        return swingIndicator.getSwingPointIndexes();
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
}
