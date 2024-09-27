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
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MeanDeviationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public MeanDeviationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9).build();
    }

    @Test
    public void meanDeviationUsingBarCount5UsingClosePrice() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(2.44444444444444, meanDeviation.getValue(2));
        assertNumEquals(2.5, meanDeviation.getValue(3));
        assertNumEquals(2.16, meanDeviation.getValue(7));
        assertNumEquals(2.32, meanDeviation.getValue(8));
        assertNumEquals(2.72, meanDeviation.getValue(9));
    }

    @Test
    public void firstValueShouldBeZero() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        assertNumEquals(0, meanDeviation.getValue(0));
    }

    @Test
    public void meanDeviationShouldBeZeroWhenBarCountIs1() {
        var meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertNumEquals(0, meanDeviation.getValue(2));
        assertNumEquals(0, meanDeviation.getValue(7));
    }
}
