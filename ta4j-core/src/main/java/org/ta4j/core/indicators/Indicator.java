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

import java.time.Instant;

import org.ta4j.core.BarSeries;

/**
 * Indicator over a {@link BarSeries bar series}.
 *
 * <p>
 * Returns a value of type <b>T</b> for each index of the bar series.
 *
 * @param <T> the type of the returned value (Double, Boolean, etc.)
 */
public interface Indicator<T> {

    /**
     * @return the value of the indicator
     */
    T getValue();

    /**
     * updates its state based on current bar
     *
     * Implementation of indicator should be aware of that it may be called multiple
     * times for single bar. If there is extensive calculation, implementation may
     * count on that for each bar there will be discrete time passed that may be
     * used for caching purposes.
     *
     * Backtesting may rewind time to past, this event should invalidate calculated
     * value.
     *
     * @param tick current time
     */
    void refresh(Instant tick);

    /**
     * @return true if indicator is stabilized
     */
    boolean isStable();
}
