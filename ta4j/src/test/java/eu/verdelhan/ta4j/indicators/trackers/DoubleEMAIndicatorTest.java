/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Test;

public class DoubleEMAIndicatorTest {

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
    public void doubleEMAUsingTimeFrame5UsingClosePrice() {
        DoubleEMAIndicator doubleEma = new DoubleEMAIndicator(closePrice, 5);

        assertThat(doubleEma.getValue(0)).isEqualTo(0.73, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(1)).isEqualTo(0.722, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(2)).isEqualTo(0.798, TATestsUtils.SHORT_OFFSET);

        assertThat(doubleEma.getValue(6)).isEqualTo(0.787, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(7)).isEqualTo(0.738, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(8)).isEqualTo(0.689, TATestsUtils.SHORT_OFFSET);

        assertThat(doubleEma.getValue(12)).isEqualTo(0.718, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(13)).isEqualTo(0.694, TATestsUtils.SHORT_OFFSET);
        assertThat(doubleEma.getValue(14)).isEqualTo(0.686, TATestsUtils.SHORT_OFFSET);
    }
}
