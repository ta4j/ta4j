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

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WilliamsRIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public WilliamsRIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {

        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        data.barBuilder().openPrice(44.98).closePrice(45.05).highPrice(45.17).lowPrice(44.96).add();
        data.barBuilder().openPrice(45.05).closePrice(45.10).highPrice(45.15).lowPrice(44.99).add();
        data.barBuilder().openPrice(45.11).closePrice(45.19).highPrice(45.32).lowPrice(45.11).add();
        data.barBuilder().openPrice(45.19).closePrice(45.14).highPrice(45.25).lowPrice(45.04).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.14).highPrice(45.20).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.13).closePrice(45.10).highPrice(45.16).lowPrice(45.07).add();
        data.barBuilder().openPrice(45.12).closePrice(45.15).highPrice(45.22).lowPrice(45.10).add();
        data.barBuilder().openPrice(45.15).closePrice(45.22).highPrice(45.27).lowPrice(45.14).add();
        data.barBuilder().openPrice(45.24).closePrice(45.43).highPrice(45.45).lowPrice(45.20).add();
        data.barBuilder().openPrice(45.43).closePrice(45.44).highPrice(45.50).lowPrice(45.39).add();
        data.barBuilder().openPrice(45.43).closePrice(45.55).highPrice(45.60).lowPrice(45.35).add();
        data.barBuilder().openPrice(45.58).closePrice(45.55).highPrice(45.61).lowPrice(45.39).add();

    }

    @Test
    public void williamsRUsingBarCount5UsingClosePrice() {
        var wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 5, new HighPriceIndicator(data),
                new LowPriceIndicator(data));

        assertNumEquals(-47.2222, wr.getValue(4));
        assertNumEquals(-54.5454, wr.getValue(5));
        assertNumEquals(-78.5714, wr.getValue(6));
        assertNumEquals(-47.6190, wr.getValue(7));
        assertNumEquals(-25d, wr.getValue(8));
        assertNumEquals(-5.2632, wr.getValue(9));
        assertNumEquals(-13.9535, wr.getValue(10));

    }

    @Test
    public void williamsRUsingBarCount10UsingClosePrice() {
        var wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 10, new HighPriceIndicator(data),
                new LowPriceIndicator(data));

        assertNumEquals(-4.0816, wr.getValue(9));
        assertNumEquals(-11.7647, wr.getValue(10));
        assertNumEquals(-8.9286, wr.getValue(11));
        assertNumEquals(-10.5263, wr.getValue(12));

    }

    @Test
    public void valueLessThenBarCount() {
        var wr = new WilliamsRIndicator(new ClosePriceIndicator(data), 100, new HighPriceIndicator(data),
                new LowPriceIndicator(data));

        assertNumEquals(-100d * (0.12 / 0.21), wr.getValue(0));
        assertNumEquals(-100d * (0.07 / 0.21), wr.getValue(1));
        assertNumEquals(-100d * (0.13 / 0.36), wr.getValue(2));
        assertNumEquals(-100d * (0.18 / 0.36), wr.getValue(3));
    }
}
