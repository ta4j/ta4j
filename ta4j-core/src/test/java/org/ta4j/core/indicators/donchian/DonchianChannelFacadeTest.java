/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.donchian;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DonchianChannelFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public DonchianChannelFacadeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withName("DonchianChannelFacadeTestSeries")
                .withNumFactory(numFactory)
                .build();

        series.barBuilder().openPrice(100d).highPrice(105d).lowPrice(95d).closePrice(100d).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(120).highPrice(125).lowPrice(115).closePrice(120).add();
        series.barBuilder().openPrice(115).highPrice(120).lowPrice(110).closePrice(115).add();
        series.barBuilder().openPrice(110).highPrice(115).lowPrice(105).closePrice(110).add();
        series.barBuilder().openPrice(105).highPrice(110).lowPrice(100).closePrice(105).add();
        series.barBuilder().openPrice(100).highPrice(105).lowPrice(95).closePrice(100).add();
    }

    @Test
    public void testNumericFacadesSameAsDefaultIndicators() {
        var donchianChannelMiddle = new DonchianChannelMiddleIndicator(series, 3);
        var donchianChannelUpper = new DonchianChannelUpperIndicator(series, 3);
        var donchianChannelLower = new DonchianChannelLowerIndicator(series, 3);
        var donchianChannelFacade = new DonchianChannelFacade(series, 3);

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(donchianChannelFacade.lower().getValue(i), donchianChannelLower.getValue(i));
            assertNumEquals(donchianChannelFacade.middle().getValue(i), donchianChannelMiddle.getValue(i));
            assertNumEquals(donchianChannelFacade.upper().getValue(i), donchianChannelUpper.getValue(i));
        }
    }

}
