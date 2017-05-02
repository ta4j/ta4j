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
package eu.verdelhan.ta4j.indicators.oscillators;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import org.junit.Before;
import org.junit.Test;

public class PPOIndicatorTest {

    private TimeSeries series;

    private ClosePriceIndicator closePriceIndicator;

    @Before
    public void setUp() {
        series = new MockTimeSeries(
                22.27, 22.19, 22.08, 22.17, 22.18, 22.13,
                22.23, 22.43, 22.24, 22.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 23.75, 23.83,
                23.95, 23.63, 23.82, 23.87, 23.65, 23.19,
                23.10, 23.33, 22.68, 23.10, 22.40, 22.17
                );
        closePriceIndicator = new ClosePriceIndicator(series);
    }

    @Test
    public void getValueWithEma10AndEma20() {

        PPOIndicator ppo = new PPOIndicator(closePriceIndicator, 10, 20);

        assertDecimalEquals(ppo.getValue(21), 2.4043);
        assertDecimalEquals(ppo.getValue(22), 2.2224);
        assertDecimalEquals(ppo.getValue(23), 1.88);
        
        assertDecimalEquals(ppo.getValue(28), 0.4408);
        assertDecimalEquals(ppo.getValue(29), 0.0559);
    }
}
