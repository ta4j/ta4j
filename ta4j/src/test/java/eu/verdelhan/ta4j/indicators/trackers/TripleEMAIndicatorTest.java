/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class TripleEMAIndicatorTest {

    private TimeSeries data;

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        data = new MockTimeSeries(
                0.73, 0.72, 0.86, 0.72, 0.62,
                0.76, 0.84, 0.69, 0.65, 0.71,
                0.53, 0.73, 0.77, 0.67, 0.68
        );
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void tripleEMAUsingTimeFrame5UsingClosePrice() {
        TripleEMAIndicator tripleEma = new TripleEMAIndicator(closePrice, 5);

        assertDecimalEquals(tripleEma.getValue(0), 0.73);
        assertDecimalEquals(tripleEma.getValue(1), 0.7213);
        assertDecimalEquals(tripleEma.getValue(2), 0.818);

        assertDecimalEquals(tripleEma.getValue(6), 0.8034);
        assertDecimalEquals(tripleEma.getValue(7), 0.7329);
        assertDecimalEquals(tripleEma.getValue(8), 0.6723);

        assertDecimalEquals(tripleEma.getValue(12), 0.7385);
        assertDecimalEquals(tripleEma.getValue(13), 0.6993);
        assertDecimalEquals(tripleEma.getValue(14), 0.6876);
    }
}
