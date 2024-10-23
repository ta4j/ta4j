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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class ChangeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private ChangeIndicator changeIndicator;

    private BarSeries barSeries;

    public ChangeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> fixedIndicator = new FixedIndicator<>(barSeries, numFactory.numOf(1), numFactory.numOf(1),
                numFactory.numOf(2), numFactory.numOf(3), numFactory.numOf(5), numFactory.numOf(8),
                numFactory.numOf(13), numFactory.numOf(10), numFactory.numOf(4));
        changeIndicator = new ChangeIndicator(fixedIndicator);
    }

    @Test
    public void indicatorShouldRetrieveChangeValues() {
        assertNumEquals(0, changeIndicator.getValue(0));
        assertNumEquals(0, changeIndicator.getValue(1));
        assertNumEquals(1, changeIndicator.getValue(2));
        assertNumEquals(1, changeIndicator.getValue(3));
        assertNumEquals(2, changeIndicator.getValue(4));
        assertNumEquals(3, changeIndicator.getValue(5));
        assertNumEquals(5, changeIndicator.getValue(6));
        assertNumEquals(-3, changeIndicator.getValue(7));
        assertNumEquals(-6, changeIndicator.getValue(8));
    }
}
