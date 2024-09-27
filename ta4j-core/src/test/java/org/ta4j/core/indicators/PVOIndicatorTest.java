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
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PVOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries barSeries;

    public PVOIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        barSeries.barBuilder().closePrice(0).volume(10).add();
        barSeries.barBuilder().closePrice(0).volume(11).add();
        barSeries.barBuilder().closePrice(0).volume(12).add();
        barSeries.barBuilder().closePrice(0).volume(13).add();
        barSeries.barBuilder().closePrice(0).volume(150).add();
        barSeries.barBuilder().closePrice(0).volume(155).add();
        barSeries.barBuilder().closePrice(0).volume(160).add();
    }

    @Test
    public void createPvoIndicator() {
        var pvo = new PVOIndicator(barSeries);
        assertNumEquals(0.791855204, pvo.getValue(1));
        assertNumEquals(2.164434756, pvo.getValue(2));
        assertNumEquals(3.925400464, pvo.getValue(3));
        assertNumEquals(55.296120101, pvo.getValue(4));
        assertNumEquals(66.511722064, pvo.getValue(5));
    }
}
