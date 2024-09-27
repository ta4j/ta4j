/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
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

import static junit.framework.TestCase.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StochasticOscillatorDIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public StochasticOscillatorDIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {

        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(119.13).highPrice(119.50).lowPrice(116.00).add();
        data.barBuilder().openPrice(45.05).closePrice(116.75).highPrice(119.94).lowPrice(116.00).add();
        data.barBuilder().openPrice(45.11).closePrice(113.50).highPrice(118.44).lowPrice(111.63).add();
        data.barBuilder().openPrice(45.19).closePrice(111.56).highPrice(114.19).lowPrice(110.06).add();
        data.barBuilder().openPrice(45.12).closePrice(112.25).highPrice(112.81).lowPrice(109.63).add();
        data.barBuilder().openPrice(45.15).closePrice(110.00).highPrice(113.44).lowPrice(109.13).add();
        data.barBuilder().openPrice(45.13).closePrice(113.50).highPrice(115.81).lowPrice(110.38).add();
        data.barBuilder().openPrice(45.12).closePrice(117.13).highPrice(117.50).lowPrice(114.06).add();
        data.barBuilder().openPrice(45.15).closePrice(115.63).highPrice(118.44).lowPrice(114.81).add();
        data.barBuilder().openPrice(45.24).closePrice(114.13).highPrice(116.88).lowPrice(113.13).add();
        data.barBuilder().openPrice(45.43).closePrice(118.81).highPrice(119.00).lowPrice(116.19).add();
        data.barBuilder().openPrice(45.43).closePrice(117.38).highPrice(119.75).lowPrice(117.00).add();
        data.barBuilder().openPrice(45.58).closePrice(119.13).highPrice(119.13).lowPrice(116.88).add();
        data.barBuilder().openPrice(45.58).closePrice(115.38).highPrice(119.44).lowPrice(114.56).add();

    }

    @Test
    public void stochasticOscilatorDParam14UsingSMA3AndGenericConstructer() {

        var sof = new StochasticOscillatorKIndicator(data, 14);
        var sma = new SMAIndicator(sof, 3);
        var sos = new StochasticOscillatorDIndicator(sma);

        assertEquals(sma.getValue(0), sos.getValue(0));
        assertEquals(sma.getValue(1), sos.getValue(1));
        assertEquals(sma.getValue(2), sos.getValue(2));
    }

    @Test
    public void stochasticOscilatorDParam14UsingSMA3() {

        var sof = new StochasticOscillatorKIndicator(data, 14);
        var sos = new StochasticOscillatorDIndicator(sof);
        var sma = new SMAIndicator(sof, 3);

        assertEquals(sma.getValue(0), sos.getValue(0));
        assertEquals(sma.getValue(1), sos.getValue(1));
        assertEquals(sma.getValue(2), sos.getValue(2));
        assertEquals(sma.getValue(13), sos.getValue(13));
    }
}
