/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Zig Zag Indicator.
 * <p>See <a href="https://www.investopedia.com/terms/z/zig_zag_indicator.asp">Zig Zag Indicator</a>
 */
public class ZigZagIndicator extends CachedIndicator<Num> {

    /**
     * The threshold ratio (positive number).
     */
    private final Num thresholdRatio;

    /**
     * The indicator to provide values.
     * <p>It can be {@link org.ta4j.core.indicators.helpers.ClosePriceIndicator} or something similar.
     */
    private final Indicator<Num> indicator;

    private Num lastExtreme;

    public ZigZagIndicator(Indicator<Num> indicator, Num thresholdRatio) {
        super(indicator);
        this.indicator = indicator;
        validateThreshold(thresholdRatio);
        this.thresholdRatio = thresholdRatio;
    }

    private void validateThreshold(Num threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold ratio is mandatory");
        }
        if (threshold.isNegativeOrZero()) {
            throw new IllegalArgumentException("Threshold ratio value must be positive");
        }
    }

    /**
     * @param index the bar index
     * @return the indicator's value if it changes over the threshold, otherwise {@link NaN}
     */
    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            lastExtreme = indicator.getValue(0);
            return lastExtreme;
        } else {
            if (lastExtreme.isZero()) {
                // Treat this case separately
                // because one cannot divide by zero
                return lastExtreme;
            } else {
                Num indicatorValue = indicator.getValue(index);
                Num differenceRatio = indicatorValue.minus(lastExtreme).dividedBy(lastExtreme);

                if (differenceRatio.abs().isGreaterThanOrEqual(thresholdRatio)) {
                    lastExtreme = indicatorValue;
                    return lastExtreme;
                } else {
                    return NaN.NaN;
                }
            }
        }
    }
}
