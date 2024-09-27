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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ROCVIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    BarSeries series;

    public ROCVIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().closePrice(1355.69).volume(1000).add();
        series.barBuilder().closePrice(1325.51).volume(3000).add();
        series.barBuilder().closePrice(1335.02).volume(3500).add();
        series.barBuilder().closePrice(1313.72).volume(2200).add();
        series.barBuilder().closePrice(1319.99).volume(2300).add();
        series.barBuilder().closePrice(1331.85).volume(200).add();
        series.barBuilder().closePrice(1329.04).volume(2700).add();
        series.barBuilder().closePrice(1362.16).volume(5000).add();
        series.barBuilder().closePrice(1365.51).volume(1000).add();
        series.barBuilder().closePrice(1374.02).volume(2500).add();
    }

    @Test
    public void test() {
        ROCVIndicator roc = new ROCVIndicator(series, 3);

        assertNumEquals(0, roc.getValue(0));
        assertNumEquals(200, roc.getValue(1));
        assertNumEquals(250, roc.getValue(2));
        assertNumEquals(120, roc.getValue(3));
        assertNumEquals(-23.333333333333332, roc.getValue(4));
        assertNumEquals(-94.28571428571429, roc.getValue(5));
        assertNumEquals(22.727272727272727, roc.getValue(6));
        assertNumEquals(117.3913043478261, roc.getValue(7));
        assertNumEquals(400, roc.getValue(8));
        assertNumEquals(-7.407407407407407, roc.getValue(9));
    }
}
