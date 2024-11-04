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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class KRIIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries series;

    public KRIIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        // Values borrowed from HMAIndicatorTest
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(84.53, 87.39, 84.55, 82.83, 82.58, 83.74, 83.33, 84.57, 86.98, 87.10, 83.11, 83.60, 83.66,
                        82.76, 79.22, 79.03, 78.18, 77.42, 74.65, 77.48, 76.87)
                .build();
    }

    @Test
    public void kriIndicatorTest() {
        KRIIndicator kriIndicator = new KRIIndicator(series, 10);

        assertNumEquals(-1.78212, kriIndicator.getValue(10));
        assertNumEquals(-0.75855, kriIndicator.getValue(11));
        assertNumEquals(-0.58229, kriIndicator.getValue(12));
        assertNumEquals(-1.64363, kriIndicator.getValue(13));
        assertNumEquals(-5.47328, kriIndicator.getValue(14));
        assertNumEquals(-5.16703, kriIndicator.getValue(15));
        assertNumEquals(-5.60365, kriIndicator.getValue(16));
        assertNumEquals(-5.70725, kriIndicator.getValue(17));
        assertNumEquals(-7.69478, kriIndicator.getValue(18));
        assertNumEquals(-3.04213, kriIndicator.getValue(19));
        assertNumEquals(-3.04841, kriIndicator.getValue(20));
    }
}
