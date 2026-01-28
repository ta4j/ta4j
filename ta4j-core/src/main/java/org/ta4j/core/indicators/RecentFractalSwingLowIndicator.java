/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Recent Fractal Swing Low Indicator.
 * <p>
 * Identifies the price of the most recently confirmed swing low, defined as a
 * bar whose low is lower than its surrounding bars within the configured
 * windows. The behaviour aligns with the commonly used definition of swing lows
 * in technical analysis literature, using a fractal-based window detection
 * approach similar to Bill Williams' Fractal indicator.
 *
 * @see RecentSwingLowIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinglow.asp">Investopedia: Swing
 *      Low</a>
 * @since 0.20
 */
@SuppressWarnings("deprecation")
public class RecentFractalSwingLowIndicator extends AbstractRecentSwingIndicator implements RecentSwingLowIndicator {

    private final Indicator<Num> indicator;
    private final int precedingHigherBars;
    private final int followingHigherBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentFractalSwingLowIndicator.
     *
     * @param indicator           the indicator providing the values to inspect for
     *                            swing lows
     * @param precedingHigherBars number of immediately preceding bars that must
     *                            have strictly higher values than the candidate
     *                            swing low
     * @param followingHigherBars number of immediately following bars that must
     *                            have strictly higher values than the candidate
     *                            swing low
     * @param allowedEqualBars    number of bars on each side that are allowed to
     *                            match the candidate value (to support rounded
     *                            bottoms)
     * @throws IllegalArgumentException if {@code precedingHigherBars} is less than
     *                                  {@code 1}
     * @throws IllegalArgumentException if {@code followingHigherBars} is negative
     * @throws IllegalArgumentException if {@code allowedEqualBars} is negative
     */
    public RecentFractalSwingLowIndicator(Indicator<Num> indicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars) {
        super(indicator, precedingHigherBars + followingHigherBars);
        if (precedingHigherBars < 1) {
            throw new IllegalArgumentException("precedingHigherBars must be greater than 0");
        }
        if (followingHigherBars < 0) {
            throw new IllegalArgumentException("followingHigherBars must be 0 or greater");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.indicator = indicator;
        this.precedingHigherBars = precedingHigherBars;
        this.followingHigherBars = followingHigherBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * Constructs a RecentFractalSwingLowIndicator that uses the low price of each
     * bar and a symmetric window on both sides of the candidate swing low.
     *
     * @param series                the {@link BarSeries} to analyse
     * @param surroundingHigherBars number of bars on each side that must remain
     *                              above the candidate swing low
     */
    public RecentFractalSwingLowIndicator(BarSeries series, int surroundingHigherBars) {
        this(new LowPriceIndicator(series), surroundingHigherBars, surroundingHigherBars, 0);
    }

    /**
     * Constructs a RecentFractalSwingLowIndicator that uses a 3-bar symmetric
     * window and no tolerance for equal lows.
     *
     * @param series the {@link BarSeries} to analyse
     */
    public RecentFractalSwingLowIndicator(BarSeries series) {
        this(series, 3);
    }

    /**
     * Returns the index of the most recent confirmed swing low that can be
     * evaluated with the data available up to {@code index}.
     *
     * @param index the current evaluation index
     * @return the index of the most recent swing low or {@code -1} if none can be
     *         confirmed yet
     * @since 0.19
     */
    @Override
    protected int detectLatestSwingIndex(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return -1;
        }
        final int latestConfirmable = index - followingHigherBars;
        final int earliestCandidate = getBarSeries().getBeginIndex() + precedingHigherBars;
        if (latestConfirmable < earliestCandidate) {
            return -1;
        }
        for (int candidate = latestConfirmable; candidate >= earliestCandidate; candidate--) {
            if (isSwingLow(candidate, index)) {
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

    private boolean isSwingLow(int candidateIndex, int maxAvailableIndex) {
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
        return hasHigherPrecedingBars(plateauStart, candidateValue)
                && hasHigherFollowingBars(plateauEnd, maxAvailableIndex, candidateValue);
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

    private boolean hasHigherPrecedingBars(int plateauStartIndex, Num candidateValue) {
        if (precedingHigherBars == 0) {
            return true;
        }
        final int beginIndex = getBarSeries().getBeginIndex();
        if (plateauStartIndex - precedingHigherBars < beginIndex) {
            return false;
        }
        for (int i = plateauStartIndex - 1; i >= plateauStartIndex - precedingHigherBars; i--) {
            final Num value = indicator.getValue(i);
            if (value.isNaN() || !value.isGreaterThan(candidateValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasHigherFollowingBars(int plateauEndIndex, int maxAvailableIndex, Num candidateValue) {
        if (followingHigherBars == 0) {
            return true;
        }
        if (maxAvailableIndex - plateauEndIndex < followingHigherBars) {
            return false;
        }
        for (int i = plateauEndIndex + 1; i <= plateauEndIndex + followingHigherBars; i++) {
            if (i > maxAvailableIndex) {
                return false;
            }
            final Num value = indicator.getValue(i);
            if (value.isNaN() || !value.isGreaterThan(candidateValue)) {
                return false;
            }
        }
        return true;
    }
}
