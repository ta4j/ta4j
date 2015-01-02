/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TADecimal;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Test;

public class WMAIndicatorTest {

    @Test
    public void WMACalculate() {
        MockTimeSeries series = new MockTimeSeries(1d, 2d, 3d, 4d, 5d, 6d);
        Indicator<TADecimal> close = new ClosePriceIndicator(series);
        Indicator<TADecimal> wmaIndicator = new WMAIndicator(close, 3);

        assertDecimalEquals(wmaIndicator.getValue(0), 1);
        assertDecimalEquals(wmaIndicator.getValue(1), 1.6667);
        assertDecimalEquals(wmaIndicator.getValue(2), 2.3333);
        assertDecimalEquals(wmaIndicator.getValue(3), 3.3333);
        assertDecimalEquals(wmaIndicator.getValue(4), 4.3333);
        assertDecimalEquals(wmaIndicator.getValue(5), 5.3333);
    }
    
    @Test
    public void WMACalculateWithTimeFrameGreaterThanSeriesSize() {
        MockTimeSeries series = new MockTimeSeries(1d, 2d, 3d, 4d, 5d, 6d);
        Indicator<TADecimal> close = new ClosePriceIndicator(series);
        Indicator<TADecimal> wmaIndicator = new WMAIndicator(close, 55);

        assertDecimalEquals(wmaIndicator.getValue(0), 1);
        assertDecimalEquals(wmaIndicator.getValue(1), 1.6667);
        assertDecimalEquals(wmaIndicator.getValue(2), 2.3333);
        assertDecimalEquals(wmaIndicator.getValue(3), 3);
        assertDecimalEquals(wmaIndicator.getValue(4), 3.6666);
        assertDecimalEquals(wmaIndicator.getValue(5), 4.3333);
    }
}
