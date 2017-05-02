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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class AverageLossIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void averageLossUsingTimeFrame5UsingClosePrice() {
        AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(averageLoss.getValue(5), "0.2");
        assertDecimalEquals(averageLoss.getValue(6), "0.2");
        assertDecimalEquals(averageLoss.getValue(7), "0.4");
        assertDecimalEquals(averageLoss.getValue(8), "0.6");
        assertDecimalEquals(averageLoss.getValue(9), "0.4");
        assertDecimalEquals(averageLoss.getValue(10), "0.4");
        assertDecimalEquals(averageLoss.getValue(11), "0.6");
        assertDecimalEquals(averageLoss.getValue(12), "0.6");

    }

    @Test
    public void averageLossMustReturnZeroWhenTheDataGain() {
        AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 4);
        assertDecimalEquals(averageLoss.getValue(3), 0);
    }

    @Test
    public void averageLossWhenTimeFrameIsGreaterThanIndex() {
        AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 1000);
        assertDecimalEquals(averageLoss.getValue(12), 5d / data.getTickCount());
    }

    @Test
    public void averageLossWhenIndexIsZeroMustBeZero() {
        AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(averageLoss.getValue(0), 0);
    }
}
