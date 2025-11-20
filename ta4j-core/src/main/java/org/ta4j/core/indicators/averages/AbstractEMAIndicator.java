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
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Base class for Exponential Moving Average implementations.
 *
 * <p>
 * Provides robust NaN handling to prevent contamination of future values when
 * invalid data is encountered. When a NaN value is detected, it is returned
 * immediately. If a previous value is NaN, the indicator resets to the current
 * value to allow graceful recovery.
 */
public abstract class AbstractEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num multiplier;

    /**
     * Constructor.
     *
     * @param indicator  the {@link Indicator}
     * @param barCount   the time frame
     * @param multiplier the multiplier
     */
    protected AbstractEMAIndicator(Indicator<Num> indicator, int barCount, double multiplier) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.multiplier = getBarSeries().numFactory().numOf(multiplier);
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN during unstable period
        int beginIndex = getBarSeries().getBeginIndex();
        int unstableBars = getCountOfUnstableBars();
        if (index < beginIndex + unstableBars) {
            return NaN;
        }

        Num current = indicator.getValue(index);

        // Check for NaN in current value
        if (Num.isNaNOrNull(current)) {
            return NaN;
        }

        // Get previous value and check for NaN
        Num prevValue = getValue(index - 1);
        if (Num.isNaNOrNull(prevValue)) {
            // Graceful recovery: reset to current value when previous is NaN
            // This prevents contamination of future values
            return current;
        }

        // Standard EMA calculation
        return prevValue.plus(current.minus(prevValue).multipliedBy(multiplier));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    public int getBarCount() {
        return barCount;
    }
}
