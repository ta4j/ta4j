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

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalNotEquals;

public class SmoothedAverageGainIndicatorTest {

    private TimeSeries data;

    @Before
    public void prepare() {
        data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
    }

    @Test
    public void smoothedAverageGainUsingTimeFrame5UsingClosePrice() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 5);

        assertDecimalEquals(averageGain.getValue(5), "0.8");
        assertDecimalEquals(averageGain.getValue(6), "0.84");
        assertDecimalEquals(averageGain.getValue(7), "0.672");
        assertDecimalEquals(averageGain.getValue(8), "0.5376");
        assertDecimalEquals(averageGain.getValue(9), "0.43008");
        assertDecimalEquals(averageGain.getValue(10), "0.544064");
        assertDecimalEquals(averageGain.getValue(11), "0.4352512");
        assertDecimalEquals(averageGain.getValue(12), "0.34820096");
    }

    @Test
    public void smoothedAverageGainMustReturnNonZeroWhenDataGainedAtLeastOnce() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 3);
        assertDecimalNotEquals(averageGain.getValue(9), 0);
    }

    @Test
    public void smoothedAverageGainWhenTimeFrameIsGreaterThanIndicatorDataShouldBeCalculatedWithDataSize() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 1000);
        assertDecimalEquals(averageGain.getValue(12), 6d / data.getTickCount());
    }

    @Test
    public void smoothedAverageGainWhenIndexIsZeroMustBeZero() {
        SmoothedAverageGainIndicator averageGain = new SmoothedAverageGainIndicator(new ClosePriceIndicator(data), 10);
        assertDecimalEquals(averageGain.getValue(0), 0);
    }
}
