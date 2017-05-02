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
import eu.verdelhan.ta4j.mocks.MockTimeSeries;

public class DPOIndicatorTest {

    private TimeSeries series;

    @Before
    public void setUp() {
        series = new MockTimeSeries(
                22.27, 22.19, 22.08, 22.17, 22.18, 22.13,
                22.23, 22.43, 22.24, 22.29, 22.15, 22.39,
                22.38, 22.61, 23.36, 24.05, 23.75, 23.83,
                23.95, 23.63, 23.82, 23.87, 23.65, 23.19,
                23.10, 23.33, 22.68, 23.10, 22.40, 22.17
        );
    }

    @Test
    public void dpo() {
        DPOIndicator dpo = new DPOIndicator(series, 9);

        assertDecimalEquals(dpo.getValue(9), -0.1633);
        assertDecimalEquals(dpo.getValue(10), -0.5056);
        assertDecimalEquals(dpo.getValue(11), -0.4122);
        assertDecimalEquals(dpo.getValue(12), -0.5989);
        assertDecimalEquals(dpo.getValue(13), -0.5533);
        assertDecimalEquals(dpo.getValue(14), 0.0322);
        assertDecimalEquals(dpo.getValue(15), 0.5633);
        assertDecimalEquals(dpo.getValue(16), 0.0978);
        assertDecimalEquals(dpo.getValue(17), 0.0622);
        assertDecimalEquals(dpo.getValue(18), 0.2011);
        assertDecimalEquals(dpo.getValue(19), -0.0133);
        assertDecimalEquals(dpo.getValue(20), 0.2233);
        assertDecimalEquals(dpo.getValue(21), 0.4011);
        assertDecimalEquals(dpo.getValue(22), 0.2756);
        assertDecimalEquals(dpo.getValue(23), -0.0478);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void dpoIOOBE() {
        DPOIndicator dpo = new DPOIndicator(series, 9);
        dpo.getValue(27);
    }
}
