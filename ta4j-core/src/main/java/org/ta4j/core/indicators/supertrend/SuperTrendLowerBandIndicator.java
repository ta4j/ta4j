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
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * The lower band of the SuperTrend indicator.
 *
 * <p>
 * NaN Handling Strategy:
 * <ul>
 * <li>During the unstable period (when ATR returns NaN), this indicator returns
 * zero (via previousValue) to maintain consistency with its initial value at
 * index 0.</li>
 * <li>When recovering from NaN (i.e., when previousValue is NaN but
 * currentBasic is valid), the indicator returns currentBasic to allow graceful
 * recovery without contaminating future values.</li>
 * <li>This differs from {@link SuperTrendUpperBandIndicator}, which returns NaN
 * during the unstable period to signal that the value is not yet reliable.</li>
 * </ul>
 *
 * <p>
 * The unstable period is determined by the underlying {@link ATRIndicator}'s
 * {@link ATRIndicator#getCountOfUnstableBars()}.
 */
public class SuperTrendLowerBandIndicator extends RecursiveCachedIndicator<Num> {

    private final ATRIndicator atrIndicator;
    private final Num multiplier;
    private final MedianPriceIndicator medianPriceIndicator;

    /**
     * Constructor with {@code multiplier} = 3.
     *
     * @param barSeries the bar series
     */
    public SuperTrendLowerBandIndicator(final BarSeries barSeries) {
        this(barSeries, new ATRIndicator(barSeries, 10), 3d);
    }

    /**
     * Constructor.
     *
     * @param barSeries    the bar series
     * @param atrIndicator the {@link ATRIndicator}
     * @param multiplier   the multiplier
     */
    public SuperTrendLowerBandIndicator(final BarSeries barSeries, final ATRIndicator atrIndicator,
            final Double multiplier) {
        super(barSeries);
        this.atrIndicator = atrIndicator;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
        this.medianPriceIndicator = new MedianPriceIndicator(barSeries);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }

        Bar bar = getBarSeries().getBar(index - 1);
        Num previousValue = this.getValue(index - 1);
        Num currentBasic = medianPriceIndicator.getValue(index)
                .minus(multiplier.multipliedBy(atrIndicator.getValue(index)));
        // If currentBasic is NaN (during unstable period), return previousValue (zero)
        // This maintains the zero behavior during unstable period
        if (Num.isNaNOrNull(currentBasic)) {
            return previousValue;
        }
        // If previousValue is NaN, recover by returning currentBasic
        if (Num.isNaNOrNull(previousValue)) {
            return currentBasic;
        }

        return currentBasic.isGreaterThan(previousValue) || bar.getClosePrice().isLessThan(previousValue) ? currentBasic
                : previousValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return atrIndicator.getCountOfUnstableBars();
    }
}
