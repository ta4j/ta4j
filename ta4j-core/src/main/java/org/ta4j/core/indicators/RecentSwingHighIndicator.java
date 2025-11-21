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
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Interface for indicators that identify the most recently confirmed swing
 * high.
 * <p>
 * A swing high is a price point that is higher than the surrounding price
 * points. Different implementations may use different algorithms to identify
 * swing highs (e.g., fractal-based window detection, ZigZag pattern detection).
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/swinghigh.asp">Investopedia: Swing
 *      High</a>
 * @since 0.20
 */
public interface RecentSwingHighIndicator extends Indicator<Num> {

    /**
     * Returns the index of the most recent confirmed swing high that can be
     * evaluated with the data available up to {@code index}.
     *
     * @param index the current evaluation index
     * @return the index of the most recent swing high or {@code -1} if none can be
     *         confirmed yet
     */
    int getLatestSwingIndex(int index);

    /**
     * Returns the underlying price indicator used by this swing high indicator to
     * retrieve price values at pivot indices.
     *
     * @return the price indicator that supplies the values at pivot indices
     */
    Indicator<Num> getPriceIndicator();
}
