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
package org.ta4j.core.indicators.helpers;

import java.math.BigDecimal;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * A fixed {@code Num} indicator.
 * 
 * <p>
 * Returns constant {@link Num} values for a bar.
 */
public class FixedDecimalIndicator extends FixedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    public FixedDecimalIndicator(BarSeries series, double... values) {
        super(series);
        for (double value : values) {
            addValue(numOf(value));
        }
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     * @param values the values to be returned by this indicator
     */
    public FixedDecimalIndicator(BarSeries series, String... values) {
        super(series);
        for (String value : values) {
            addValue(numOf(new BigDecimal(value)));
        }
    }
}
