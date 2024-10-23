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
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class NzIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private FixedIndicator<Num> indicator;

    private BarSeries barSeries;

    public NzIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        indicator = new FixedIndicator<>(barSeries, numFactory.numOf(1), NaN.NaN, numFactory.numOf(3), NaN.NaN,
                numFactory.numOf(5));
    }

    @Test
    public void indicatorShouldRetrieveZero() {
        NzIndicator nzIndicator = new NzIndicator(indicator);

        assertNumEquals(1, nzIndicator.getValue(0));
        assertNumEquals(0, nzIndicator.getValue(1));
        assertNumEquals(3, nzIndicator.getValue(2));
        assertNumEquals(0, nzIndicator.getValue(3));
        assertNumEquals(5, nzIndicator.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveCustom() {
        NzIndicator nzIndicator = new NzIndicator(indicator, 2);

        assertNumEquals(1, nzIndicator.getValue(0));
        assertNumEquals(2, nzIndicator.getValue(1));
        assertNumEquals(3, nzIndicator.getValue(2));
        assertNumEquals(2, nzIndicator.getValue(3));
        assertNumEquals(5, nzIndicator.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveCustomIndicator() {
        Indicator<Num> replacement = new FixedNumIndicator(barSeries, 5, 6, 7, 8, 9);
        NzIndicator nzIndicator = new NzIndicator(indicator, replacement);

        assertNumEquals(1, nzIndicator.getValue(0));
        assertNumEquals(6, nzIndicator.getValue(1));
        assertNumEquals(3, nzIndicator.getValue(2));
        assertNumEquals(8, nzIndicator.getValue(3));
        assertNumEquals(5, nzIndicator.getValue(4));
    }
}
