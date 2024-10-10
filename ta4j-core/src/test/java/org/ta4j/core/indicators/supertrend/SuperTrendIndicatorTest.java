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
package org.ta4j.core.indicators.supertrend;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class SuperTrendIndicatorTest {

    private BarSeries data;
    private NumFactory numFactory = DoubleNumFactory.getInstance();

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        data.barBuilder().openPrice(23.17).closePrice(21.48).highPrice(23.39).lowPrice(21.35).add();
        data.barBuilder().openPrice(21.25).closePrice(19.94).highPrice(21.29).lowPrice(20.07).add();
        data.barBuilder().openPrice(20.08).closePrice(21.97).highPrice(24.30).lowPrice(20.01).add();
        data.barBuilder().openPrice(22.17).closePrice(20.87).highPrice(22.64).lowPrice(20.78).add();
        data.barBuilder().openPrice(21.67).closePrice(21.65).highPrice(22.80).lowPrice(21.59).add();
        data.barBuilder().openPrice(21.47).closePrice(22.14).highPrice(22.26).lowPrice(20.96).add();
        data.barBuilder().openPrice(22.25).closePrice(21.44).highPrice(22.31).lowPrice(21.36).add();
        data.barBuilder().openPrice(21.83).closePrice(21.67).highPrice(22.40).lowPrice(21.59).add();
        data.barBuilder().openPrice(23.09).closePrice(22.90).highPrice(23.76).lowPrice(22.73).add();
        data.barBuilder().openPrice(22.93).closePrice(22.01).highPrice(23.27).lowPrice(21.94).add();
        data.barBuilder().openPrice(19.89).closePrice(19.20).highPrice(20.47).lowPrice(18.91).add();
        data.barBuilder().openPrice(21.56).closePrice(18.83).highPrice(21.80).lowPrice(18.83).add();
        data.barBuilder().openPrice(19.00).closePrice(18.35).highPrice(19.41).lowPrice(18.01).add();
        data.barBuilder().openPrice(19.89).closePrice(6.36).highPrice(20.22).lowPrice(6.21).add();
        data.barBuilder().openPrice(19.28).closePrice(10.34).highPrice(20.58).lowPrice(10.11).add();
        // this mock bar exemplify an edge case, the close price is the same as the
        // previous Super Trend value
        data.barBuilder().openPrice(19.28).closePrice(22.78938583966133).highPrice(23.58).lowPrice(10.11).add();
        data.barBuilder().openPrice(19.28).closePrice(10.34).highPrice(20.58).lowPrice(10.11).add();
        data.barBuilder().openPrice(10.34).closePrice(9.83).highPrice(12.80).lowPrice(8.83).add();
        data.barBuilder().openPrice(11.83).closePrice(7.35).highPrice(11.41).lowPrice(5.01).add();
    }

    @Test
    public void testSuperTrendIndicator() {
        var superTrendIndicator = new SuperTrendIndicator(data);

        assertNumEquals(numFactory.numOf(15.730621000000003), superTrendIndicator.getValue(4));
        assertNumEquals(numFactory.numOf(17.602360938100002), superTrendIndicator.getValue(9));
        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(14));
    }

    @Test
    public void regressionTestOnZeroSuperTrendValueWhenClosePriceIsEqualToPreviousSuperTrendValue() {
        // bug: https://github.com/ta4j/ta4j/issues/1120
        var superTrendIndicator = new SuperTrendIndicator(data);

        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(14));
        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(15));
        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(16));
        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(17));
        assertNumEquals(numFactory.numOf(22.78938583966133), superTrendIndicator.getValue(18));
    }
}
