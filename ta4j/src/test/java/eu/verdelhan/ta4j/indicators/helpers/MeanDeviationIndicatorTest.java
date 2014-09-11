/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Test;

public class MeanDeviationIndicatorTest {

    private TimeSeries data;

    @Before
    public void setUp() {
        data = new MockTimeSeries(1, 2, 7, 6, 3, 4, 5, 11, 3, 0, 9);
    }

    @Test
    public void meanDeviationUsingTimeFrame5UsingClosePrice() throws Exception {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);

        assertThat(meanDeviation.getValue(2)).isEqualTo(2.4444444444444d, TATestsUtils.LONG_OFFSET);
        assertThat(meanDeviation.getValue(3)).isEqualTo(2.5d, TATestsUtils.LONG_OFFSET);
        assertThat(meanDeviation.getValue(7)).isEqualTo(2.16d, TATestsUtils.LONG_OFFSET);
        assertThat(meanDeviation.getValue(8)).isEqualTo(2.32d, TATestsUtils.LONG_OFFSET);
        assertThat(meanDeviation.getValue(9)).isEqualTo(2.72d, TATestsUtils.LONG_OFFSET);

    }

    @Test
    public void firstValueShouldBeZero() throws Exception {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 5);
        assertThat(meanDeviation.getValue(0)).isEqualTo(0);
    }

    @Test
    public void meanDeviationShouldBeZeroWhenTimeFrameIs1() {
        MeanDeviationIndicator meanDeviation = new MeanDeviationIndicator(new ClosePriceIndicator(data), 1);
        assertThat(meanDeviation.getValue(2)).isEqualTo(0d);
        assertThat(meanDeviation.getValue(7)).isEqualTo(0d);
    }
}
