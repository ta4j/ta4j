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

import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class CMOIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        series = new MockTimeSeries(
                21.27, 22.19, 22.08, 22.47, 22.48, 22.53,
                22.23, 21.43, 21.24, 21.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 24.75, 24.83,
                23.95, 23.63, 23.82, 23.87, 23.15, 23.19,
                23.10, 22.65, 22.48, 22.87, 22.93, 22.91
        );
    }

    @Test
    public void dpo() {
        CMOIndicator cmo = new CMOIndicator(new ClosePriceIndicator(series), 9);

        assertDecimalEquals(cmo.getValue(5), 85.1351);
        assertDecimalEquals(cmo.getValue(6), 53.9326);
        assertDecimalEquals(cmo.getValue(7), 6.2016);
        assertDecimalEquals(cmo.getValue(8), -1.083);
        assertDecimalEquals(cmo.getValue(9), 0.7092);
        assertDecimalEquals(cmo.getValue(10), -1.4493);
        assertDecimalEquals(cmo.getValue(11), 10.7266);
        assertDecimalEquals(cmo.getValue(12), -3.5857);
        assertDecimalEquals(cmo.getValue(13), 4.7619);
        assertDecimalEquals(cmo.getValue(14), 24.1983);
        assertDecimalEquals(cmo.getValue(15), 47.644);
    }
}
