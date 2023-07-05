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
package org.ta4j.core.indicators.supertrend;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * The SuperTrend indicator.
 */
public class SuperTrendIndicator extends RecursiveCachedIndicator<Num> {

    private final Num ZERO = zero();
    private final SuperTrendUpperBandIndicator superTrendUpperBandIndicator;
    private final SuperTrendLowerBandIndicator superTrendLowerBandIndicator;

    /**
     * Constructor.
     * 
     * @param series     the bar series
     * @param barCount   the time frame for the {@code ATRIndicator}
     * @param multiplier the multiplier for the
     *                   {@link #superTrendUpperBandIndicator} and
     *                   {@link #superTrendLowerBandIndicator}
     */
    public SuperTrendIndicator(final BarSeries series, int barCount, final Integer multiplier) {
        super(series);
        ATRIndicator atrIndicator = new ATRIndicator(series, barCount);
        this.superTrendUpperBandIndicator = new SuperTrendUpperBandIndicator(series, atrIndicator, multiplier);
        this.superTrendLowerBandIndicator = new SuperTrendLowerBandIndicator(series, atrIndicator, multiplier);
    }

    /**
     * Constructor with {@code barCount} = 10 and {@code multiplier} = 3.
     * 
     * @param series the bar series
     */
    public SuperTrendIndicator(final BarSeries series) {
        this(series, 10, 3);
    }

    @Override
    protected Num calculate(int i) {
        Num value = ZERO;

        if (i == 0) {
            return value;
        }
        Bar bar = getBarSeries().getBar(i);

        if (this.getValue(i - 1).isEqual(this.superTrendUpperBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isLessThan(this.superTrendUpperBandIndicator.getValue(i))) {
            value = this.superTrendUpperBandIndicator.getValue(i);
        }

        if (this.getValue(i - 1).isEqual(this.superTrendUpperBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isGreaterThan(this.superTrendUpperBandIndicator.getValue(i))) {
            value = this.superTrendLowerBandIndicator.getValue(i);
        } else if (this.getValue(i - 1).isEqual(this.superTrendLowerBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isGreaterThan(this.superTrendLowerBandIndicator.getValue(i))) {
            value = this.superTrendLowerBandIndicator.getValue(i);
        } else if (this.getValue(i - 1).isEqual(this.superTrendLowerBandIndicator.getValue(i - 1))
                && bar.getClosePrice().isLessThan(this.superTrendLowerBandIndicator.getValue(i))) {
            value = this.superTrendUpperBandIndicator.getValue(i);
        }

        return value;
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    /** @return the {@link #superTrendLowerBandIndicator} */
    public SuperTrendLowerBandIndicator getSuperTrendLowerBandIndicator() {
        return superTrendLowerBandIndicator;
    }

    /** @return the {@link #superTrendUpperBandIndicator} */
    public SuperTrendUpperBandIndicator getSuperTrendUpperBandIndicator() {
        return superTrendUpperBandIndicator;
    }
}
