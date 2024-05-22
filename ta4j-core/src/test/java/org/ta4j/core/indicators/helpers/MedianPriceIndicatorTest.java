/**
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
package org.ta4j.core.indicators.helpers;

import static junit.framework.TestCase.assertEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MedianPriceIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private MedianPriceIndicator average;

    private BacktestBarSeries barSeries;

    public MedianPriceIndicatorTest(final NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        this.barSeries = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();

        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(16).lowPrice(8).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(6).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(18).lowPrice(14).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(10).lowPrice(6).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(32).lowPrice(6).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(2).lowPrice(2).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(0).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(8).lowPrice(1).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(83).lowPrice(32).add();
        this.barSeries.barBuilder().openPrice(0).closePrice(0).highPrice(9).lowPrice(3).add();

        this.average = new MedianPriceIndicator(this.barSeries);
        this.barSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(this.average))));
    }

    @Test
    public void indicatorShouldRetrieveBarClosePrice() {
        for (int i = 0; i < 10; i++) {
            this.barSeries.advance();

            final var result = this.barSeries.getBar()
                    .highPrice()
                    .plus(this.barSeries.getBar().lowPrice())
                    .dividedBy(this.numFactory.two());
            assertEquals(this.average.getValue(), result);
        }
    }
}
