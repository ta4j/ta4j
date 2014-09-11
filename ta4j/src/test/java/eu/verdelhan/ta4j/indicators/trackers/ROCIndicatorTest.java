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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Test;

public class ROCIndicatorTest {

    private double[] closePriceValues = new double[] {
        11045.27, 11167.32, 11008.61, 11151.83,
        10926.77, 10868.12, 10520.32, 10380.43,
        10785.14, 10748.26, 10896.91, 10782.95,
        10620.16, 10625.83, 10510.95, 10444.37,
        10068.01, 10193.39, 10066.57, 10043.75
    };

    private ClosePriceIndicator closePrice;

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockTimeSeries(closePriceValues));
    }

    @Test
    public void getValueWhenTimeFrameIs12() {
        ROCIndicator roc = new ROCIndicator(closePrice, 12);

        // Incomplete time frame
        assertThat(roc.getValue(0)).isZero();
        assertThat(roc.getValue(1)).isEqualTo(1.10, TATestsUtils.SHORT_OFFSET);
        assertThat(roc.getValue(2)).isEqualTo(-0.33, TATestsUtils.SHORT_OFFSET);
        assertThat(roc.getValue(3)).isEqualTo(0.96, TATestsUtils.SHORT_OFFSET);

        // Complete time frame
        double[] results13to20 = new double[] { -3.85, -4.85, -4.52, -6.34, -7.86, -6.21, -4.31, -3.24 };
        for (int i = 0; i < results13to20.length; i++) {
            assertThat(roc.getValue(i + 12)).isEqualTo(results13to20[i], TATestsUtils.SHORT_OFFSET);
        }
    }
}
