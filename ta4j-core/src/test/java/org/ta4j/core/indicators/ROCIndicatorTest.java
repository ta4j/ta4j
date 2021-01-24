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

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class ROCIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private double[] closePriceValues = new double[] { 11045.27, 11167.32, 11008.61, 11151.83, 10926.77, 10868.12,
            10520.32, 10380.43, 10785.14, 10748.26, 10896.91, 10782.95, 10620.16, 10625.83, 10510.95, 10444.37,
            10068.01, 10193.39, 10066.57, 10043.75 };

    private ClosePriceIndicator closePrice;

    public ROCIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeries(numFunction, closePriceValues));
    }

    @Test
    public void getValueWhenBarCountIs12() {
        ROCIndicator roc = new ROCIndicator(closePrice, 12);

        // Incomplete time frame
        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(1.105, roc.getValue(1));
        assertNumEquals(-0.3319, roc.getValue(2));
        assertNumEquals(0.9648, roc.getValue(3));

        // Complete time frame
        double[] results13to20 = new double[] { -3.8488, -4.8489, -4.5206, -6.3439, -7.8592, -6.2083, -4.3131,
                -3.2434 };
        for (int i = 0; i < results13to20.length; i++) {
            assertNumEquals(results13to20[i], roc.getValue(i + 12));
        }
    }
}
