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
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Recent Swing Low Indicator.
 */
public class RecentSwingLowIndicator extends CachedIndicator<Num> {

    private final int precedingHigherBars;
    private final int followingHigherBars;
    private final int allowedEqualBars;
    Indicator<Num> indicator;

    /**
     * Constructs a RecentSwingLowIndicator
     *
     * @param indicator           The Indicator to be analyzed.
     * @param precedingHigherBars For a bar to be identified as a swing low, it must
     *                            have a lower low than this number of bars
     *                            immediately preceding it.
     * @param followingHigherBars For a bar to be identified as a swing low, it must
     *                            have a lower low than this number of bars
     *                            immediately following it.
     * @param allowedEqualBars    For a looser definition of swing low, instead of
     *                            requiring the surrounding bars to be strictly
     *                            higher lows we allow this number of equal lows to
     *                            either side (i.e. flat-ish valley)
     * @throws IllegalArgumentException if precedingHigherBars is less than or equal
     *                                  to 0.
     * @throws IllegalArgumentException if followingHigherBars is less 0.
     * @throws IllegalArgumentException if allowedEqualBars is less than 0.
     */
    public RecentSwingLowIndicator(Indicator<Num> indicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars) {
        super(indicator);

        if (precedingHigherBars <= 0) {
            throw new IllegalArgumentException("precedingHigherBars must be greater than 0");
        }
        if (followingHigherBars < 0) {
            throw new IllegalArgumentException("followingHigherBars must be 0 or greater");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.precedingHigherBars = precedingHigherBars;
        this.followingHigherBars = followingHigherBars;
        this.allowedEqualBars = allowedEqualBars;
        this.indicator = indicator;
    }

    /**
     * Constructs a RecentSwingLowIndicator with the specified BarSeries (and
     * defaulting to use LowPriceIndicator) and surrounding higher bars count and a
     * default allowed equal bars of 0
     *
     * @param series                The BarSeries to be analyzed.
     * @param surroundingHigherBars The number of bars to consider on each side that
     *                              must have higher lows to identify a swing low.
     */
    public RecentSwingLowIndicator(BarSeries series, int surroundingHigherBars) {
        this(new LowPriceIndicator(series), surroundingHigherBars, surroundingHigherBars, 0);
    }

    /**
     * Constructs a RecentSwingLowIndicator with the specified BarSeries (and
     * defaulting to use LowPriceIndicator), a default surrounding higher bars count
     * of 3, and a default allowed equal bars of 0
     *
     * @param series The BarSeries to be analyzed.
     */
    public RecentSwingLowIndicator(BarSeries series) {
        this(new LowPriceIndicator(series), 3, 0, 0);
    }

    private boolean isSwingLow(int index) {
        Num currentPrice = this.indicator.getValue(index);

        // Check bars before
        if (!checkPrecedingBars(index, currentPrice)) {
            return false;
        }

        // Check bars after (up to the current calculation index)
        return checkFollowingBars(index, currentPrice);
    }

    private boolean checkPrecedingBars(int index, Num currentPrice) {
        int higherBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = index - 1; i >= index - precedingHigherBars - allowedEqualBars && i >= 0; i--) {
            Num comparisonPrice = this.indicator.getValue(i);

            if (currentPrice.isEqual(comparisonPrice)) {
                equalBarsCount++;
                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentPrice.isGreaterThan(comparisonPrice)) {
                return false;
            } else {
                higherBarsCount++;
                if (higherBarsCount == precedingHigherBars) {
                    return true;
                }
            }
        }

        return higherBarsCount == precedingHigherBars;
    }

    private boolean checkFollowingBars(int index, Num currentPrice) {
        int higherBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = index + 1; i < index + followingHigherBars + allowedEqualBars + 1
                && i < getBarSeries().getBarCount(); i++) {
            Num comparisonPrice = this.indicator.getValue(i);

            if (currentPrice.isEqual(comparisonPrice)) {
                equalBarsCount++;
                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentPrice.isGreaterThan(comparisonPrice)) {
                return false;
            } else {
                higherBarsCount++;
                if (higherBarsCount == followingHigherBars) {
                    return true;
                }
            }
        }

        return higherBarsCount == followingHigherBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getUnstableBars() || index >= getBarSeries().getBarCount()) {
            return NaN;
        }

        for (int i = index; i >= getBarSeries().getBeginIndex(); i--) {
            if (isSwingLow(i)) {
                return this.indicator.getValue(i);
            }
        }
        return NaN;
    }

    /**
     * Returns the number of unstable bars as defined by the surroundingHigherBars
     * parameter.
     *
     * @return The number of unstable bars.
     */
    @Override
    public int getUnstableBars() {
        return precedingHigherBars + followingHigherBars;
    }
}
