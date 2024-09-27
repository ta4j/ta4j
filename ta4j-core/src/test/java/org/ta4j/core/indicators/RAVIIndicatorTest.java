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

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RAVIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public RAVIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {

        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(110.00, 109.27, 104.69, 107.07, 107.92, 107.95, 108.70, 107.97, 106.09, 106.03, 108.65,
                        109.54, 112.26, 114.38, 117.94)
                .build();
    }

    @Test
    public void ravi() {
        var closePrice = new ClosePriceIndicator(data);
        var ravi = new RAVIIndicator(closePrice, 3, 8);

        assertNumEquals(0, ravi.getValue(0));
        assertNumEquals(0, ravi.getValue(1));
        assertNumEquals(0, ravi.getValue(2));
        assertNumEquals(-0.6937, ravi.getValue(3));
        assertNumEquals(-1.1411, ravi.getValue(4));
        assertNumEquals(-0.1577, ravi.getValue(5));
        assertNumEquals(0.229, ravi.getValue(6));
        assertNumEquals(0.2412, ravi.getValue(7));
        assertNumEquals(0.1202, ravi.getValue(8));
        assertNumEquals(-0.3324, ravi.getValue(9));
        assertNumEquals(-0.5804, ravi.getValue(10));
        assertNumEquals(0.2013, ravi.getValue(11));
        assertNumEquals(1.6156, ravi.getValue(12));
        assertNumEquals(2.6167, ravi.getValue(13));
        assertNumEquals(4.0799, ravi.getValue(14));
    }
}
