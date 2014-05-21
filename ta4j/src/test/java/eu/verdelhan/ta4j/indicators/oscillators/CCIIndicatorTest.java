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
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Test;

public class CCIIndicatorTest {

    private double[] typicalPrices = new double[] {
        23.98, 23.92, 23.79, 23.67, 23.54,
        23.36, 23.65, 23.72, 24.16, 23.91,
        23.81, 23.92, 23.74, 24.68, 24.94,
        24.93, 25.10, 25.12, 25.20, 25.06,
        24.50, 24.31, 24.57, 24.62, 24.49,
        24.37, 24.41, 24.35, 23.75, 24.09
    };

    private MockTimeSeries series;

    @Before
    public void setUp() {
        ArrayList<Tick> ticks = new ArrayList<Tick>();
        for (Double price : typicalPrices) {
            ticks.add(new MockTick(price, price, price, price));
        }
        series = new MockTimeSeries(ticks);
    }

    @Test
    public void getValueWhenTimeFrameIs20() {
        CCIIndicator cci = new CCIIndicator(series, 20);

        // Incomplete time frame
        assertThat(cci.getValue(0)).isNaN();
        assertThat(cci.getValue(1)).isEqualTo(-66.66, TATestsUtils.SHORT_OFFSET);
        assertThat(cci.getValue(2)).isEqualTo(-100.0, TATestsUtils.SHORT_OFFSET);
        assertThat(cci.getValue(10)).isEqualTo(14.36, TATestsUtils.SHORT_OFFSET);
        assertThat(cci.getValue(11)).isEqualTo(54.25, TATestsUtils.SHORT_OFFSET);

        // Complete time frame
        double[] results20to30 = new double[] { 101.91, 31.19, 6.55, 33.60, 34.96, 13.60, -10.68, -11.47, -29.26, -128.60, -72.72 };
        for (int i = 0; i < results20to30.length; i++) {
            assertThat(cci.getValue(i + 19)).isEqualTo(results20to30[i], TATestsUtils.SHORT_OFFSET);
        }
    }
}
