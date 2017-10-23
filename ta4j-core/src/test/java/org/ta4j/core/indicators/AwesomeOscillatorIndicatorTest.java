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
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;

import java.util.ArrayList;
import java.util.List;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class AwesomeOscillatorIndicatorTest {
    private TimeSeries series;

    @Before
    public void setUp() {

        List<Tick> ticks = new ArrayList<Tick>();

        ticks.add(new MockTick(0, 0, 16, 8));
        ticks.add(new MockTick(0, 0, 12, 6));
        ticks.add(new MockTick(0, 0, 18, 14));
        ticks.add(new MockTick(0, 0, 10, 6));
        ticks.add(new MockTick(0, 0, 8, 4));

        this.series = new MockTimeSeries(ticks);
    }

    @Test
    public void calculateWithSma2AndSma3() {
        AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), 2, 3);

        assertDecimalEquals(awesome.getValue(0), 0);
        assertDecimalEquals(awesome.getValue(1), 0);
        assertDecimalEquals(awesome.getValue(2), 1d/6);
        assertDecimalEquals(awesome.getValue(3), 1);
        assertDecimalEquals(awesome.getValue(4), -3);
    }

    @Test
    public void withSma1AndSma2() {
        AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), 1, 2);

        assertDecimalEquals(awesome.getValue(0), 0);
        assertDecimalEquals(awesome.getValue(1), "-1.5");
        assertDecimalEquals(awesome.getValue(2), "3.5");
        assertDecimalEquals(awesome.getValue(3), -4);
        assertDecimalEquals(awesome.getValue(4), -1);
    }

    @Test
    public void withSmaDefault() {
        AwesomeOscillatorIndicator awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series));

        assertDecimalEquals(awesome.getValue(0), 0);
        assertDecimalEquals(awesome.getValue(1), 0);
        assertDecimalEquals(awesome.getValue(2), 0);
        assertDecimalEquals(awesome.getValue(3), 0);
        assertDecimalEquals(awesome.getValue(4), 0);
    }

}
