/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
import static org.ta4j.core.num.NaN.NaN;
import org.ta4j.core.num.Num;

/**
 * Recent Swing Low Indicator.
 */
public class RecentSwingLowIndicator extends CachedIndicator<Num> {

    private final int surroundingHigherBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentSwingLowIndicator with the specified BarSeries and
     * surrounding bars count.
     *
     * @param series                The BarSeries to be analyzed.
     * @param surroundingHigherBars The number of bars to consider on each side that
     *                              must have higher lows to identify a swing low.
     * @param allowedEqualBars      the number of bars on each side that can have
     *                              equal lows and the swing low still be considered
     *                              valid
     * @throws IllegalArgumentException if surroundingHigherBars is less than or
     *                                  equal to 0.
     */
    public RecentSwingLowIndicator(BarSeries series, int surroundingHigherBars, int allowedEqualBars) {
        super(series);

        if (surroundingHigherBars <= 0) {
            throw new IllegalArgumentException("surroundingHigherBars must be greater than 0");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.surroundingHigherBars = surroundingHigherBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * Constructs a RecentSwingLowIndicator with the specified BarSeries and
     * surrounding higher bars count and a default allowed equal bars of 0
     *
     * @param series                The BarSeries to be analyzed.
     * @param surroundingHigherBars The number of bars to consider on each side that
     *                              must have higher lows to identify a swing low.
     */
    public RecentSwingLowIndicator(BarSeries series, int surroundingHigherBars) {
        this(series, surroundingHigherBars, 0);
    }

    /**
     * Constructs a RecentSwingLowIndicator with the specified BarSeries, a default
     * surrounding higher bars count of 2, and a default allowed equal bars of 0
     *
     * @param series The BarSeries to be analyzed.
     */
    public RecentSwingLowIndicator(BarSeries series) {
        this(series, 2, 0);
    }

    /**
     * Validates if the specified bar at currentIndex, considering the direction,
     * meets the criteria for being a swing high.
     *
     * @param currentIndex The index of the current bar.
     * @param direction    The direction for comparison (-1 for previous bars, 1 for
     *                     following bars).
     * @return true if the bar at currentIndex is a swing low considering the
     *         specified direction; false otherwise.
     */
    private boolean validateBars(int currentIndex, int direction) {
        Num currentLowPrice = getBarSeries().getBar(currentIndex).getLowPrice();
        int higherBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = currentIndex + direction; i >= getBarSeries().getBeginIndex()
                && i <= getBarSeries().getEndIndex(); i += direction) {
            Num comparisonLowPrice = getBarSeries().getBar(i).getLowPrice();

            if (currentLowPrice.isEqual(comparisonLowPrice)) {
                equalBarsCount++;

                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentLowPrice.isLessThan(comparisonLowPrice)) {
                higherBarsCount++;
                if (higherBarsCount == surroundingHigherBars) {
                    return true;
                }
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Calculates the most recent swing low value up to the specified index.
     *
     * @param index The bar index up to which the calculation is done.
     * @return The value of the most recent swing low if it exists; NaN otherwise.
     */
    @Override
    protected Num calculate(int index) {
        if (index < surroundingHigherBars) {
            return NaN;
        }

        for (int i = index; i >= getBarSeries().getBeginIndex(); i--) {
            if (validateBars(i, -1) && validateBars(i, 1)) {
                return getBarSeries().getBar(i).getLowPrice();
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
        return surroundingHigherBars;
    }
}
