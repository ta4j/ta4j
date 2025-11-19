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
package org.ta4j.core.mocks;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;

public class MockIndicator implements Indicator<Num> {

    private final int unstableBarsCount;
    private final BarSeries series;
    private final List<Num> values;

    /**
     * Constructs a MockIndicator with the specified bar series and values. The
     * unstable bars count is set to zero by default.
     *
     * @param series the bar series associated with this indicator
     * @param values the list of numeric values for this indicator
     */
    public MockIndicator(BarSeries series, List<Num> values) {
        this(series, 0, values);
    }

    /**
     * Constructs a MockIndicator with the specified bar series, unstable bars
     * count, and values.
     *
     * @param series            the bar series associated with this indicator
     * @param unstableBarsCount the number of unstable bars for this indicator
     * @param values            the numeric values for this indicator
     */
    public MockIndicator(BarSeries series, int unstableBarsCount, Num... values) {
        this(series, unstableBarsCount, List.of(values));
    }

    /**
     * Constructs a MockIndicator with the specified bar series, unstable bars
     * count, and values.
     *
     * @param series            the bar series associated with this indicator
     * @param unstableBarsCount the number of unstable bars for this indicator
     * @param values            the list of numeric values for this indicator
     */
    public MockIndicator(BarSeries series, int unstableBarsCount, List<Num> values) {
        this.unstableBarsCount = unstableBarsCount;
        this.series = series;
        this.values = values;
    }

    /**
     * Returns the numeric value at the specified index from the indicator's value
     * list.
     *
     * @param index the position of the value to retrieve
     * @return the numeric value at the given index
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * Returns the number of unstable bars for this indicator. Unstable bars are
     * typically excluded from certain calculations or analyses to ensure
     * reliability and stability of the results.
     *
     * @return the count of unstable bars
     */
    @Override
    public int getCountOfUnstableBars() {
        return unstableBarsCount;
    }

    /**
     * Returns the bar series associated with this indicator.
     *
     * @return the bar series instance
     */
    @Override
    public BarSeries getBarSeries() {
        return series;
    }
}
