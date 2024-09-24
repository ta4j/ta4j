/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
 */
public class RecentSwingHighIndicator extends CachedIndicator<Num> {

    private final int precedingLowerBars;
    private final int followingLowerBars;
    private final int allowedEqualBars;
    Indicator<Num> indicator;

    /**
     * Constructs a RecentSwingHighIndicator
     *
     * @param indicator          The Indicator to be analyzed.
     * @param precedingLowerBars For a bar to be identified as a swing high, it must
     *                           have a higher high than this number of bars
     *                           immediately preceding it.
     * @param followingLowerBars For a bar to be identified as a swing high, it must
     *                           have a higher high than this number of bars
     *                           immediately following it.
     * @param allowedEqualBars   For a looser definition of swing high, instead of
     *                           requiring the surrounding bars to be strictly lower
     *                           highs we allow this number of equal highs to either
     *                           side (i.e. flat-ish valley)
     * @throws IllegalArgumentException if precedingLowerBars is less than or equal
     *                                  to 0.
     * @throws IllegalArgumentException if followingLowerBars is less than 0.
     * @throws IllegalArgumentException if allowedEqualBars is less than 0.
     */
    public RecentSwingHighIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        super(indicator);

        if (precedingLowerBars <= 0) {
            throw new IllegalArgumentException("precedingLowerBars must be greater than 0");
        }
        if (followingLowerBars < 0) {
            throw new IllegalArgumentException("followingLowerBars must be 0 or greater");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.precedingLowerBars = precedingLowerBars;
        this.followingLowerBars = followingLowerBars;
        this.allowedEqualBars = allowedEqualBars;
        this.indicator = indicator;
    }

    /**
     * Constructs a RecentSwingHighIndicator with the specified BarSeries (and
     * defaulting to use HighPriceIndicator) and surrounding higher bars count and a
     * default allowed equal bars of 0
     *
     * @param series               The BarSeries to be analyzed.
     * @param surroundingLowerBars The number of bars to consider on each side that
     *                             must have lower highs to identify a swing high.
     */
    public RecentSwingHighIndicator(BarSeries series, int surroundingLowerBars) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0);
    }

    /**
     * Constructs a RecentSwingHighIndicator with the specified BarSeries (and
     * defaulting to use HighPriceIndicator) and surrounding higher bars count
     * defaulted to 3, and a default allowed equal bars of 0
     *
     * @param series The BarSeries to be analyzed. must have lower highs to identify
     *               a swing high.
     */
    public RecentSwingHighIndicator(BarSeries series) {
        this(new HighPriceIndicator(series), 3, 0, 0);
    }

    private boolean isSwingHigh(int index) {
        Num currentPrice = this.indicator.getValue(index);

        // Check bars before
        if (!checkPrecedingBars(index, currentPrice)) {
            return false;
        }

        // Check bars after (up to the current calculation index)
        return checkFollowingBars(index, currentPrice);
    }

    private boolean checkPrecedingBars(int index, Num currentPrice) {
        int lowerBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = index - 1; i >= index - precedingLowerBars - allowedEqualBars && i >= 0; i--) {
            Num comparisonPrice = this.indicator.getValue(i);

            if (currentPrice.isEqual(comparisonPrice)) {
                equalBarsCount++;
                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentPrice.isLessThan(comparisonPrice)) {
                return false;
            } else {
                lowerBarsCount++;
                if (lowerBarsCount == precedingLowerBars) {
                    return true;
                }
            }
        }

        return lowerBarsCount == precedingLowerBars;
    }

    private boolean checkFollowingBars(int index, Num currentPrice) {
        int lowerBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = index + 1; i < index + followingLowerBars + allowedEqualBars + 1
                && i < getBarSeries().getBarCount(); i++) {
            Num comparisonPrice = this.indicator.getValue(i);

            if (currentPrice.isEqual(comparisonPrice)) {
                equalBarsCount++;
                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentPrice.isLessThan(comparisonPrice)) {
                return false;
            } else {
                lowerBarsCount++;
                if (lowerBarsCount == followingLowerBars) {
                    return true;
                }
            }
        }

        return lowerBarsCount == followingLowerBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getUnstableBars() || index >= getBarSeries().getBarCount()) {
            return NaN;
        }

        for (int i = index; i >= getBarSeries().getBeginIndex(); i--) {
            if (isSwingHigh(i)) {
                return this.indicator.getValue(i);
            }
        }
        return NaN;
    }

    public int getUnstableBars() {
        return precedingLowerBars + followingLowerBars;
    }
}