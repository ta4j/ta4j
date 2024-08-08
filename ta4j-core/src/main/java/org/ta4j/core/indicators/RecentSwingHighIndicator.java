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
 * Recent Swing High Indicator.
 */
public class RecentSwingHighIndicator extends CachedIndicator<Num> {

    private final int surroundingLowerBars;
    private final int allowedEqualBars;

    /**
     * Constructs a RecentSwingHighIndicator
     *
     * @param series               The BarSeries to be analyzed.
     * @param surroundingLowerBars For a bar to be identified as a swing high, it
     *                             must have a higher high than this number of bars
     *                             both immediately preceding and immediately
     *                             following
     * @param allowedEqualBars     For a looser definition of peak, instead of
     *                             requiring the surrounding bars to be strictly
     *                             lower highs we allow this number of equal highs
     *                             to either side (i.e. flat-ish peak or plateau)
     * @throws IllegalArgumentException if surroundingLowerBars is less than or
     *                                  equal to 0.
     * @throws IllegalArgumentException if allowedEqualBars is less than 0.
     */
    public RecentSwingHighIndicator(BarSeries series, int surroundingLowerBars, int allowedEqualBars) {
        super(series);

        if (surroundingLowerBars <= 0) {
            throw new IllegalArgumentException("surroundingLowerBars must be greater than 0");
        }
        if (allowedEqualBars < 0) {
            throw new IllegalArgumentException("allowedEqualBars must be 0 or greater");
        }
        this.surroundingLowerBars = surroundingLowerBars;
        this.allowedEqualBars = allowedEqualBars;
    }

    /**
     * * Constructs a RecentSwingHighIndicator with the specified BarSeries and
     * surrounding lower bars count and a default allowed equal bars count of 0
     *
     * @param series
     * @param surroundingLowerBars
     */
    public RecentSwingHighIndicator(BarSeries series, int surroundingLowerBars) {
        this(series, surroundingLowerBars, 0);
    }

    /**
     * Constructs a RecentSwingHighIndicator with the specified BarSeries, a default
     * surrounding lower bars count of 2, and a default allowed equal bars count of
     * 0
     *
     * @param series The BarSeries to be analyzed.
     */
    public RecentSwingHighIndicator(BarSeries series) {
        this(series, 2, 0);
    }

    /**
     * Validates if the specified bar at currentIndex, considering the direction,
     * meets the criteria for being a swing high.
     *
     * @param currentIndex The index of the current bar.
     * @param direction    The direction for comparison (-1 for previous bars, 1 for
     *                     following bars).
     * @return true if the bar at currentIndex is a swing high considering the
     *         specified direction; false otherwise.
     */
    private boolean validateBars(int currentIndex, int direction) {
        Num currentHighPrice = getBarSeries().getBar(currentIndex).getHighPrice();
        int lowerBarsCount = 0;
        int equalBarsCount = 0;

        for (int i = currentIndex + direction; i >= getBarSeries().getBeginIndex()
                && i <= getBarSeries().getEndIndex(); i += direction) {
            Num comparisonHighPrice = getBarSeries().getBar(i).getHighPrice();

            if (currentHighPrice.isEqual(comparisonHighPrice)) {
                equalBarsCount++;

                if (equalBarsCount > allowedEqualBars) {
                    return false;
                }
            } else if (currentHighPrice.isGreaterThan(comparisonHighPrice)) {
                lowerBarsCount++;
                if (lowerBarsCount == surroundingLowerBars) {
                    return true;
                }
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Calculates the most recent swing high value up to the specified index.
     *
     * @param index The bar index up to which the calculation is done.
     * @return The value of the most recent swing high if it exists; NaN otherwise.
     */
    @Override
    protected Num calculate(int index) {
        if (index < surroundingLowerBars) {
            return NaN;
        }

        for (int i = index; i >= getBarSeries().getBeginIndex(); i--) {
            if (validateBars(i, -1) && validateBars(i, 1)) {
                return getBarSeries().getBar(i).getHighPrice();
            }
        }

        return NaN;
    }

    /**
     * Returns the number of unstable bars as defined by the surroundingBars
     * parameter.
     *
     * @return The number of unstable bars.
     */
    @Override
    public int getUnstableBars() {
        return surroundingLowerBars;
    }
}
