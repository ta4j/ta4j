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
package eu.verdelhan.ta4j.indicators;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;

import eu.verdelhan.ta4j.BaseTimeSeries;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.helpers.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.helpers.MinPriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class WilliamsRIndicatorTest {
    private TimeSeries data;

    @Before
    public void setUp() {

        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(44.98, 45.05, 45.17, 44.96));
        ticks.add(new MockTick(45.05, 45.10, 45.15, 44.99));
        ticks.add(new MockTick(45.11, 45.19, 45.32, 45.11));
        ticks.add(new MockTick(45.19, 45.14, 45.25, 45.04));
        ticks.add(new MockTick(45.12, 45.15, 45.20, 45.10));
        ticks.add(new MockTick(45.15, 45.14, 45.20, 45.10));
        ticks.add(new MockTick(45.13, 45.10, 45.16, 45.07));
        ticks.add(new MockTick(45.12, 45.15, 45.22, 45.10));
        ticks.add(new MockTick(45.15, 45.22, 45.27, 45.14));
        ticks.add(new MockTick(45.24, 45.43, 45.45, 45.20));
        ticks.add(new MockTick(45.43, 45.44, 45.50, 45.39));
        ticks.add(new MockTick(45.43, 45.55, 45.60, 45.35));
        ticks.add(new MockTick(45.58, 45.55, 45.61, 45.39));

        data = new BaseTimeSeries(ticks);
    }

    @Test
    public void williamsRUsingTimeFrame5UsingClosePrice() {
        WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 5,
                new MaxPriceIndicator(data),
                new MinPriceIndicator(data));

        assertDecimalEquals(wr.getValue(4), -47.2222);
        assertDecimalEquals(wr.getValue(5), -54.5454);
        assertDecimalEquals(wr.getValue(6), -78.5714);
        assertDecimalEquals(wr.getValue(7), -47.6190);
        assertDecimalEquals(wr.getValue(8), -25d);
        assertDecimalEquals(wr.getValue(9), -5.2632);
        assertDecimalEquals(wr.getValue(10), -13.9535);

    }

    @Test
    public void williamsRUsingTimeFrame10UsingClosePrice() {
        WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 10, new MaxPriceIndicator(data),
                new MinPriceIndicator(data));

        assertDecimalEquals(wr.getValue(9), -4.0816);
        assertDecimalEquals(wr.getValue(10), -11.7647);
        assertDecimalEquals(wr.getValue(11), -8.9286);
        assertDecimalEquals(wr.getValue(12), -10.5263);

    }

    @Test
    public void valueLessThenTimeFrame() {
        WilliamsRIndicator wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 100, new MaxPriceIndicator(data),
                new MinPriceIndicator(data));

        assertDecimalEquals(wr.getValue(0), -100d * (0.12 / 0.21));
        assertDecimalEquals(wr.getValue(1), -100d * (0.07 / 0.21));
        assertDecimalEquals(wr.getValue(2), -100d * (0.13 / 0.36));
        assertDecimalEquals(wr.getValue(3), -100d * (0.18 / 0.36));
    }
}
