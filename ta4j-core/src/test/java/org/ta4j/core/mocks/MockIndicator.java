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
package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public class MockIndicator implements Indicator<Num> {

    private BarSeries series;
    private List<Num> values;

    /**
     * Constructor.
     * 
     * @param series BarSeries of the Indicator
     * @param values Indicator values
     */
    public MockIndicator(BarSeries series, List<Num> values) {
        this.series = series;
        this.values = values;
    }

    /**
     * Gets a value from the Indicator
     * 
     * @param index Indicator value to get
     * @return Num Indicator value at index
     */
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * Gets the Indicator TimeSeries.
     * 
     * @return TimeSeries of the Indicator
     */
    public BarSeries getBarSeries() {
        return series;
    }

    @Override
    public Num numOf(Number number) {
        return series.numOf(number);
    }

}
