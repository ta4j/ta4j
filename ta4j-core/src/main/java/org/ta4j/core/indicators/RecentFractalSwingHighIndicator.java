/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Recent Fractal Swing High Indicator.
 * <p>
 * Identifies the price of the most recently confirmed swing high, i.e., a bar
 * whose high is greater than the surrounding bars as defined by the supplied
 * look-back and look-forward windows. The definition mirrors the common
 * description of a swing high in technical analysis literature, using a
 * fractal-based window detection approach similar to Bill Williams' Fractal
 * indicator.
 *
 * @see RecentSwingIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 */
public class RecentFractalSwingHighIndicator extends AbstractRecentSwingIndicator {

    private final Indicator<Num> indicator;
    private final int precedingLowerBars;
    private final int followingLowerBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentFractalSwingHighIndicator.
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
    public RecentFractalSwingHighIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        super(indicator, precedingLowerBars + followingLowerBars);
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
     * Constructs a RecentFractalSwingHighIndicator that uses the high price of each
     * bar and a symmetric window on both sides of the candidate swing high.
     *
     * @param series               the {@link BarSeries} to analyse
     * @param surroundingLowerBars number of bars on each side that must remain
     *                             below the candidate swing high
     */
    public RecentFractalSwingHighIndicator(BarSeries series, int surroundingLowerBars) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0);
    }

    /**
     * Constructs a RecentFractalSwingHighIndicator that uses a 3-bar symmetric
     * window and no tolerance for equal highs.
     *
     * @param series the {@link BarSeries} to analyse
     */
    public RecentFractalSwingHighIndicator(BarSeries series) {
        this(series, 3);
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
    @Override
    protected int detectLatestSwingIndex(int index) {
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

    @Override
    public Indicator<Num> getPriceIndicator() {
        return indicator;
    }

    @Override
    protected boolean purgeOnNegativeDetection() {
        return true;
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
