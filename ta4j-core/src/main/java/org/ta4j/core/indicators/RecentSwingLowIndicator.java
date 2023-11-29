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

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NaN;
import static org.ta4j.core.num.NaN.NaN;
import org.ta4j.core.num.Num;

/**
 * Recent Swing Low Indicator.
 */
public class RecentSwingLowIndicator extends CachedIndicator<Num> {

    /**
     * A swing low is a bar with a lower low than the bars both before and after it.
     * Defines the number of bars to consider on each side (e.g., 2 bars on each
     * side).
     */
    private final int surroundingBars;

    /**
     * Full constructor
     *
     * @param series
     * @param surroundingBars
     */
    public RecentSwingLowIndicator(BarSeries series, int surroundingBars) {
        super(series);

        if (surroundingBars <= 0) {
            throw new IllegalArgumentException("surroundingBars must be greater than 0");
        }
        this.surroundingBars = surroundingBars;
    }

    /**
     * Convenience constructor defaulting surroundingBars to 2
     *
     * @param series
     */
    public RecentSwingLowIndicator(BarSeries series) {
        this(series, 2);
    }

    /**
     * Calculates the value of the most recent swing low
     *
     * @param index the bar index
     * @return the value of the most recent swing low, otherwise {@link NaN}
     */
    @Override
    protected Num calculate(int index) {
        if (index < surroundingBars) {
            return NaN;
        }

        int endIndex = getBarSeries().getEndIndex();

        for (int i = Math.min(index - 1, endIndex); i >= surroundingBars; i--) {
            boolean isSwingLow = true;
            Bar currentBar = getBarSeries().getBar(i);

            for (int j = 1; j <= surroundingBars; j++) {
                if (i + j > endIndex || i - j < 0
                        || currentBar.getLowPrice().isGreaterThanOrEqual(getBarSeries().getBar(i - j).getLowPrice())
                        || currentBar.getLowPrice().isGreaterThanOrEqual(getBarSeries().getBar(i + j).getLowPrice())) {
                    isSwingLow = false;
                    break;
                }
            }

            if (isSwingLow) {
                return currentBar.getLowPrice();
            }
        }

        return NaN;
    }

    @Override
    public int getUnstableBars() {
        return surroundingBars;
    }
}
