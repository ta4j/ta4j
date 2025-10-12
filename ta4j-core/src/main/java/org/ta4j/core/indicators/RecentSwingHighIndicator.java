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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Recent Swing High Indicator.
 * <p>
 * Identifies the price of the most recently confirmed swing high, i.e., a bar
 * whose high is greater than the surrounding bars as defined by the supplied
 * look-back and look-forward windows. The definition mirrors the common
 * description of a swing high in technical analysis literature.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.19
 */
public class RecentSwingHighIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int precedingLowerBars;
    private final int followingLowerBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentSwingHighIndicator.
     *
     * @param indicator          the indicator providing the values to inspect for
     *                           swing highs
     * @param precedingLowerBars number of immediately preceding bars that must have
     *                           strictly lower values than the candidate swing high
     * @param followingLowerBars number of immediately following bars that must have
     *                           strictly lower values than the candidate swing high
     * @param allowedEqualBars   number of bars on each side that are allowed to
     *                           match the candidate value (to support flat tops)
     * @throws IllegalArgumentException if {@code precedingLowerBars} is less than
     *                                  {@code 1}
     * @throws IllegalArgumentException if {@code followingLowerBars} is negative
     * @throws IllegalArgumentException if {@code allowedEqualBars} is negative
     */
    public RecentSwingHighIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        super(indicator);
        if (precedingLowerBars < 1) {
            throw new IllegalArgumentException("precedingLowerBars must be greater than 0");
        }
        if (followingLowerBars < 0) {
            throw new IllegalArgumentException("followingLowerBars must be 0 or greater");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.indicator = indicator;
        this.precedingLowerBars = precedingLowerBars;
        this.followingLowerBars = followingLowerBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * Constructs a RecentSwingHighIndicator that uses the high price of each bar
     * and a symmetric window on both sides of the candidate swing high.
     *
     * @param series               the {@link BarSeries} to analyse
     * @param surroundingLowerBars number of bars on each side that must remain
     *                             below the candidate swing high
     */
    public RecentSwingHighIndicator(BarSeries series, int surroundingLowerBars) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0);
    }

    /**
     * Constructs a RecentSwingHighIndicator that uses a 3-bar symmetric window and
     * no tolerance for equal highs.
     *
     * @param series the {@link BarSeries} to analyse
     */
    public RecentSwingHighIndicator(BarSeries series) {
        this(series, 3);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return NaN;
        }
        final int swingIndex = getLatestSwingIndex(index);
        if (swingIndex < 0) {
            return NaN;
        }
        return indicator.getValue(swingIndex);
    }

    @Override
    public int getCountOfUnstableBars() {
        return precedingLowerBars + followingLowerBars;
    }

    /**
     * Returns the index of the most recent confirmed swing high that can be
     * evaluated with the data available up to {@code index}.
     *
     * @param index the current evaluation index
     * @return the index of the most recent swing high or {@code -1} if none can be
     *         confirmed yet
     * @since 0.19
     */
    public int getLatestSwingIndex(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return -1;
        }
        final int latestConfirmable = index - followingLowerBars;
        final int earliestCandidate = getBarSeries().getBeginIndex() + precedingLowerBars;
        if (latestConfirmable < earliestCandidate) {
            return -1;
        }
        for (int candidate = latestConfirmable; candidate >= earliestCandidate; candidate--) {
            if (isSwingHigh(candidate, index)) {
                return candidate;
            }
        }
        return -1;
    }

    private boolean isSwingHigh(int candidateIndex, int maxAvailableIndex) {
        final Num candidateValue = indicator.getValue(candidateIndex);
        if (candidateValue.isNaN()) {
            return false;
        }
        final int plateauStart = findPlateauStart(candidateIndex, candidateValue);
        if (plateauStart < 0) {
            return false;
        }
        final int plateauEnd = findPlateauEnd(candidateIndex, maxAvailableIndex, candidateValue);
        if (plateauEnd < 0) {
            return false;
        }
        return hasLowerPrecedingBars(plateauStart, candidateValue)
                && hasLowerFollowingBars(plateauEnd, maxAvailableIndex, candidateValue);
    }

    private int findPlateauStart(int candidateIndex, Num candidateValue) {
        final int beginIndex = getBarSeries().getBeginIndex();
        int equalsUsed = 0;
        int index = candidateIndex;
        while (index > beginIndex && equalsUsed < allowedEqualBars) {
            final Num previousValue = indicator.getValue(index - 1);
            if (previousValue.isNaN()) {
                return -1;
            }
            if (!previousValue.isEqual(candidateValue)) {
                break;
            }
            equalsUsed++;
            index--;
        }
        if (index > beginIndex) {
            final Num previousValue = indicator.getValue(index - 1);
            if (previousValue.isEqual(candidateValue)) {
                return -1;
            }
        }
        return index;
    }

    private int findPlateauEnd(int candidateIndex, int maxAvailableIndex, Num candidateValue) {
        int equalsUsed = 0;
        int index = candidateIndex;
        while (index < maxAvailableIndex && equalsUsed < allowedEqualBars) {
            final Num nextValue = indicator.getValue(index + 1);
            if (nextValue.isNaN()) {
                return -1;
            }
            if (!nextValue.isEqual(candidateValue)) {
                break;
            }
            equalsUsed++;
            index++;
        }
        if (index < maxAvailableIndex) {
            final Num nextValue = indicator.getValue(index + 1);
            if (nextValue.isEqual(candidateValue)) {
                return -1;
            }
        }
        return index;
    }

    private boolean hasLowerPrecedingBars(int plateauStartIndex, Num candidateValue) {
        if (precedingLowerBars == 0) {
            return true;
        }
        final int beginIndex = getBarSeries().getBeginIndex();
        if (plateauStartIndex - precedingLowerBars < beginIndex) {
            return false;
        }
        for (int i = plateauStartIndex - 1; i >= plateauStartIndex - precedingLowerBars; i--) {
            final Num value = indicator.getValue(i);
            if (value.isNaN() || !value.isLessThan(candidateValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasLowerFollowingBars(int plateauEndIndex, int maxAvailableIndex, Num candidateValue) {
        if (followingLowerBars == 0) {
            return true;
        }
        if (maxAvailableIndex - plateauEndIndex < followingLowerBars) {
            return false;
        }
        for (int i = plateauEndIndex + 1; i <= plateauEndIndex + followingLowerBars; i++) {
            if (i > maxAvailableIndex) {
                return false;
            }
            final Num value = indicator.getValue(i);
            if (value.isNaN() || !value.isLessThan(candidateValue)) {
                return false;
            }
        }
        return true;
    }
}
