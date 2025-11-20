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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Calculates the difference between the current and the previous value of an
 * indicator.
 *
 * <pre>
 * Difference = currentValue - previousValue
 * </pre>
 *
 * @since 0.20
 */
public class DifferenceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator to calculate the difference for
     * @since 0.20
     */
    public DifferenceIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    /**
     * Calculates the difference between the current value and the previous value of
     * the indicator.
     *
     * @param index the index of the current bar
     * @return the difference between the current and previous values, or NaN if
     *         index is within the unstable bar period
     */
    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        // Get the value of the previous bar
        Num previousValue = indicator.getValue(index - 1);

        // Get the value of the current bar
        Num currentValue = indicator.getValue(index);

        // Calculate the difference between the values
        return currentValue.minus(previousValue);
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
