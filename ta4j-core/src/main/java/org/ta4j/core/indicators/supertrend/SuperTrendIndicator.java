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
package org.ta4j.core.indicators.supertrend;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * The SuperTrend indicator.
 *
 * <p>
 * NaN Handling Strategy:
 * <ul>
 * <li>During the unstable period (when band indicators return NaN), this
 * indicator returns zero. The NaN checks in comparisons ensure that invalid
 * band values do not contaminate the calculation.</li>
 * <li>After the unstable period, the indicator recovers gracefully and produces
 * valid values based on the relationship between the close price and the
 * upper/lower bands.</li>
 * <li>The unstable period is the maximum of the unstable periods from both
 * {@link #superTrendUpperBandIndicator} and
 * {@link #superTrendLowerBandIndicator}, ensuring that all component indicators
 * have stabilized before producing reliable values.</li>
 * </ul>
 *
 * <p>
 * The indicator switches between upper and lower bands based on whether the
 * previous SuperTrend value was equal to the previous upper or lower band
 * value, and compares the current close price against the current band values
 * to determine the new SuperTrend value.
 */
public class SuperTrendIndicator extends RecursiveCachedIndicator<Num> {

    private final SuperTrendUpperBandIndicator superTrendUpperBandIndicator;
    private final SuperTrendLowerBandIndicator superTrendLowerBandIndicator;

    /**
     * Constructor with {@code barCount} = 10 and {@code multiplier} = 3.
     *
     * @param series the bar series
     */
    public SuperTrendIndicator(final BarSeries series) {
        this(series, 10, 3d);
    }

    /**
     * Constructor.
     *
     * @param series     the bar series
     * @param barCount   the time frame for the {@code ATRIndicator}
     * @param multiplier the multiplier for the
     *                   {@link #superTrendUpperBandIndicator} and
     *                   {@link #superTrendLowerBandIndicator}
     */
    public SuperTrendIndicator(final BarSeries series, int barCount, final Double multiplier) {
        super(series);
        ATRIndicator atrIndicator = new ATRIndicator(series, barCount);
        this.superTrendUpperBandIndicator = new SuperTrendUpperBandIndicator(series, atrIndicator, multiplier);
        this.superTrendLowerBandIndicator = new SuperTrendLowerBandIndicator(series, atrIndicator, multiplier);
    }

    @Override
    protected Num calculate(int i) {
        Num value = getBarSeries().numFactory().zero();
        if (i == 0) {
            return value;
        }

        Bar bar = getBarSeries().getBar(i);
        Num closePrice = bar.getClosePrice();
        Num previousValue = this.getValue(i - 1);
        Num lowerBand = superTrendLowerBandIndicator.getValue(i);
        Num upperBand = superTrendUpperBandIndicator.getValue(i);

        // If bands are NaN, comparisons will return false and value remains zero
        // This is the expected behavior during unstable period
        if (previousValue.isEqual(superTrendUpperBandIndicator.getValue(i - 1))) {
            if (!Num.isNaNOrNull(upperBand) && closePrice.isLessThanOrEqual(upperBand)) {
                value = upperBand;
            } else if (!Num.isNaNOrNull(upperBand) && !Num.isNaNOrNull(lowerBand)
                    && closePrice.isGreaterThan(upperBand)) {
                value = lowerBand;
            }
        }

        if (previousValue.isEqual(superTrendLowerBandIndicator.getValue(i - 1))) {
            if (!Num.isNaNOrNull(lowerBand) && closePrice.isGreaterThanOrEqual(lowerBand)) {
                value = lowerBand;
            } else if (!Num.isNaNOrNull(lowerBand) && !Num.isNaNOrNull(upperBand) && closePrice.isLessThan(lowerBand)) {
                value = upperBand;
            }
        }

        return value;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(superTrendUpperBandIndicator.getCountOfUnstableBars(),
                superTrendLowerBandIndicator.getCountOfUnstableBars());
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
